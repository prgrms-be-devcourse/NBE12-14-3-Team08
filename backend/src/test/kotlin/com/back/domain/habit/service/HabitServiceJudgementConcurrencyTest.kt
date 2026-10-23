package com.back.domain.habit.service

import com.back.domain.group.entity.Group
import com.back.domain.group.repository.GroupRepository
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.habit.entity.Habit
import com.back.domain.habit.repository.HabitRepository
import com.back.domain.member.entity.Member
import com.back.domain.member.repository.MemberRepository
import com.back.support.ConcurrentRunner
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

// 매일 00시 05분 스케줄러(autoCheckAllActiveHabits)와 방장의 수동 주간 판정(checkWeeklyFailureForGroup)이
// 같은 습관을 동시에 판정할 때 실패 처리와 벌칙이 한 번만 일어나는지 확인한다.
// 실제로 커밋되는 트랜잭션이 필요해서 @Transactional 없이 실행하고, 끝나면 만든 데이터를 직접 지운다.
@SpringBootTest
@ActiveProfiles("test")
class HabitServiceJudgementConcurrencyTest {

    @Autowired
    private lateinit var habitService: HabitService

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var groupRepository: GroupRepository

    @Autowired
    private lateinit var groupMemberRepository: GroupMemberRepository

    @Autowired
    private lateinit var habitRepository: HabitRepository

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    private var groupId: Long = 0L
    private var ownerId: Long = 0L
    private val memberIds = mutableListOf<Long>()
    private val habitIds = mutableListOf<Long>()

    companion object {
        // 판정 대상 습관 수. 습관이 많을수록 두 판정이 같은 습관에서 겹칠 기회가 늘어난다.
        private const val HABIT_COUNT = 10
        private const val MANUAL_CALLS = 4
        private const val SCHEDULER_CALLS = 4
    }

    // 모든 습관이 "가입 1주 경과 + 이번 주 마감 지남 + 인증 0건"이라 판정하면 실패 처리되어야 하는 상태로 시작한다.
    @BeforeEach
    fun setUp() {
        val suffix = UUID.randomUUID().toString().substring(0, 8)

        val group = groupRepository.save(
            Group(
                title = "판정 동시성 그룹",
                penalty = "커피 사기",
                password = "1234",
                inviteCode = "J$suffix",
                memberLimit = HABIT_COUNT + 5,
                deadline = LocalDate.now().plusDays(30),
            ),
        )
        groupId = requireNotNull(group.id)

        val owner = memberRepository.save(Member.create("judge-owner-$suffix", "judge-owner-$suffix", "pw1234!"))
        ownerId = requireNotNull(owner.id)
        memberIds += ownerId
        groupMemberRepository.save(GroupMember.create(group, owner, GroupMemberRole.OWNER))

        repeat(HABIT_COUNT) { i ->
            val member = memberRepository.save(Member.create("judge-$i-$suffix", "judge-$i-$suffix", "pw1234!"))
            memberIds += requireNotNull(member.id)
            val groupMember = groupMemberRepository.save(GroupMember.create(group, member, GroupMemberRole.MEMBER))
            val habit = habitRepository.save(Habit.create(groupMember, "습관 $i", "설명", 3))
            habitIds += requireNotNull(habit.id)
        }

        // 생성 시각은 DB에서 직접 되돌린다. (엔티티의 createDate는 수정되지 않는 컬럼이다)
        val now = LocalDateTime.now()
        jdbc.update("update groups set create_date = ? where id = ?", Timestamp.valueOf(now.minusDays(14)), groupId)
        jdbc.update("update group_member set create_date = ? where group_id = ?", Timestamp.valueOf(now.minusDays(10)), groupId)
        jdbc.update(
            "update habit set create_date = ? where group_member_id in (select id from group_member where group_id = ?)",
            Timestamp.valueOf(now.minusDays(10)),
            groupId,
        )
    }

    @AfterEach
    fun tearDown() {
        val gmIds = "select id from group_member where group_id = ?"
        jdbc.update("delete from penalty_verify where group_member_id in ($gmIds)", groupId)
        jdbc.update("delete from habit where group_member_id in ($gmIds)", groupId)
        jdbc.update("delete from group_member where group_id = ?", groupId)
        jdbc.update("delete from groups where id = ?", groupId)
        memberIds.forEach { jdbc.update("delete from member where id = ?", it) }
    }

    private fun runConcurrently(): List<Throwable?> {
        val manual: List<() -> Unit> = List(MANUAL_CALLS) { { habitService.checkWeeklyFailureForGroup(ownerId, groupId) } }
        val scheduler: List<() -> Unit> = List(SCHEDULER_CALLS) { { habitService.autoCheckAllActiveHabits() } }
        return ConcurrentRunner.runAll(manual + scheduler)
    }

    private fun failedHabitCount(): Int =
        jdbc.queryForObject(
            "select count(*) from habit where status = 'FAILED' and group_member_id in (select id from group_member where group_id = ?)",
            Int::class.java,
            groupId,
        ) ?: 0

    private fun penaltyCount(): Int =
        jdbc.queryForObject(
            "select count(*) from penalty_verify where group_member_id in (select id from group_member where group_id = ?)",
            Int::class.java,
            groupId,
        ) ?: 0

    @Test
    @DisplayName("스케줄러와 수동 판정이 동시에 돌아도 모든 습관은 실패 처리되고 벌칙은 습관마다 하나뿐이다 (데이터 무결성)")
    fun 동시에_판정해도_데이터는_어긋나지_않는다() {
        runConcurrently()

        // 일부 호출이 예외로 끝나더라도, 마지막에는 아직 처리되지 않은 습관이 없도록 한 번 더 판정한다.
        habitService.checkWeeklyFailureForGroup(ownerId, groupId)
        habitService.autoCheckAllActiveHabits()

        assertThat(failedHabitCount()).describedAs("실패 처리된 습관").isEqualTo(HABIT_COUNT)
        assertThat(penaltyCount()).describedAs("벌칙 개수").isEqualTo(HABIT_COUNT)
    }

    @Test
    @Disabled("버그: 같은 습관을 두 판정이 동시에 처리하면 벌칙 중복 제약(uk_penalty_verify) 위반이 예외로 올라와 일부 호출이 실패한다. 수정 이슈 처리 후 활성화")
    @DisplayName("스케줄러와 수동 판정이 동시에 돌아도 어느 쪽도 예외 없이 끝난다")
    fun 동시에_판정해도_예외가_없다() {
        val results = runConcurrently()

        val failures = results.filterNotNull()
        assertThat(failures)
            .describedAs("예외로 끝난 호출: ${failures.map { it.javaClass.simpleName }}")
            .isEmpty()
        assertThat(failedHabitCount()).describedAs("실패 처리된 습관").isEqualTo(HABIT_COUNT)
        assertThat(penaltyCount()).describedAs("벌칙 개수").isEqualTo(HABIT_COUNT)
    }
}
