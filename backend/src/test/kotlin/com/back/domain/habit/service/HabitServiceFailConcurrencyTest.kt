package com.back.domain.habit.service

import com.back.domain.group.dto.GroupRequest
import com.back.domain.group.service.GroupService
import com.back.domain.habit.dto.CreateHabitRequest
import com.back.domain.member.entity.Member
import com.back.domain.member.repository.MemberRepository
import com.back.support.ConcurrentRunner
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.util.UUID

// 같은 습관을 여러 요청이 동시에 포기할 때(더블클릭, 재시도) 성공은 한 번이고 벌칙도 하나만 만들어지는지 확인한다.
// 실제로 커밋되는 트랜잭션이 필요해서 @Transactional 없이 실행하고, 끝나면 만든 데이터를 직접 지운다.
@SpringBootTest
@ActiveProfiles("test")
class HabitServiceFailConcurrencyTest {

    @Autowired
    private lateinit var habitService: HabitService

    @Autowired
    private lateinit var groupService: GroupService

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    private var ownerId: Long = 0L
    private var groupId: Long = 0L
    private var habitId: Long = 0L

    companion object {
        private const val THREAD_COUNT = 8
    }

    @BeforeEach
    fun setUp() {
        val suffix = UUID.randomUUID().toString().substring(0, 8)
        val owner = memberRepository.save(
            Member.create("fail-$suffix", "fail-$suffix", requireNotNull(passwordEncoder.encode("pw1234!"))),
        )
        ownerId = requireNotNull(owner.id)

        val group = groupService.createGroup(
            ownerId,
            GroupRequest.Create("포기 동시성 그룹", "설명", LocalDate.now().plusDays(7), "벌칙", "pw1234", 5),
        )
        groupId = requireNotNull(group.id)

        habitId = requireNotNull(habitService.createHabit(groupId, ownerId, CreateHabitRequest("동시성 습관", "설명", 3)).id)
    }

    @AfterEach
    fun tearDown() {
        jdbc.update("delete from penalty_verify where habit_id = ?", habitId)
        jdbc.update("delete from habit where id = ?", habitId)
        jdbc.update("delete from group_member where group_id = ?", groupId)
        jdbc.update("delete from groups where id = ?", groupId)
        jdbc.update("delete from member where id = ?", ownerId)
    }

    private fun penaltyCount(): Int =
        jdbc.queryForObject("select count(*) from penalty_verify where habit_id = ?", Int::class.java, habitId) ?: 0

    private fun habitStatus(): String =
        jdbc.queryForObject("select status from habit where id = ?", String::class.java, habitId) ?: ""

    private fun failConcurrently(): List<Throwable?> =
        ConcurrentRunner.runAll(List(THREAD_COUNT) { { habitService.failHabit(ownerId, habitId) } })

    @Test
    @DisplayName("같은 습관을 동시에 여러 번 포기해도 성공은 한 번이고 벌칙은 하나만 생기며 습관은 FAILED가 된다 (데이터 무결성)")
    fun 동시에_포기해도_벌칙은_하나만_생긴다() {
        val results = failConcurrently()

        assertThat(results.count { it == null }).describedAs("성공 횟수").isEqualTo(1)
        assertThat(penaltyCount()).describedAs("벌칙 개수").isEqualTo(1)
        assertThat(habitStatus()).isEqualTo("FAILED")
    }

    @Test
    @DisplayName("동시에 포기했을 때 진 요청은 충돌(IllegalStateException, 409)로 끝난다")
    fun 동시에_포기하면_진_요청은_충돌로_끝난다() {
        val results = failConcurrently()

        val unexpected = results.filterNotNull().filter { it !is IllegalStateException }
        assertThat(unexpected)
            .describedAs("충돌(IllegalStateException) 외의 예외: ${unexpected.map { it.javaClass.simpleName }}")
            .isEmpty()
        assertThat(results.count { it is IllegalStateException }).describedAs("충돌 횟수").isEqualTo(THREAD_COUNT - 1)
    }
}
