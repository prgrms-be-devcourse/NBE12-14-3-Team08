package com.back.domain.habit.service

import com.back.domain.group.dto.GroupRequest
import com.back.domain.group.repository.GroupRepository
import com.back.domain.group.service.GroupService
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.habit.dto.CreateHabitRequest
import com.back.domain.habit.entity.HabitStatus
import com.back.domain.habit.repository.HabitRepository
import com.back.domain.member.entity.Member
import com.back.domain.member.repository.MemberRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

// 개발용 DB(local 프로필)를 건드리지 않도록 Testcontainers PostgreSQL을 쓰는 test 프로필로 실행한다.
@SpringBootTest
@ActiveProfiles("test")
class HabitServiceConcurrencyTest {

    @Autowired
    private lateinit var habitService: HabitService

    @Autowired
    private lateinit var groupService: GroupService

    @Autowired
    private lateinit var groupRepository: GroupRepository

    @Autowired
    private lateinit var groupMemberRepository: GroupMemberRepository

    @Autowired
    private lateinit var habitRepository: HabitRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    private lateinit var owner: Member
    private var groupId: Long = 0L

    companion object {
        private const val THREAD_COUNT = 8
    }

    @BeforeEach
    fun setUp() {
        val suffix = UUID.randomUUID().toString().substring(0, 8)
        owner = memberRepository.save(
            Member.create("habit-$suffix", "habit-$suffix", requireNotNull(passwordEncoder.encode("pw1234!"))),
        )

        val created = groupService.createGroup(
            owner.id,
            GroupRequest.Create("습관 동시성 그룹", "설명", LocalDate.now().plusDays(7), "벌칙", "pw1234", 5),
        )
        groupId = requireNotNull(created.id)
    }

    @AfterEach
    fun tearDown() {
        val members = groupMemberRepository.findAll().filter { it.group.id == groupId }
        habitRepository.deleteAll(
            habitRepository.findAllByGroupMember_Group_IdAndStatus(groupId, HabitStatus.ACTIVE),
        )
        groupMemberRepository.deleteAll(members)
        groupRepository.deleteById(groupId)
        memberRepository.deleteById(owner.id)
    }

    @Test
    @DisplayName("같은 멤버가 동시에 습관을 여러 번 생성해도 활성 습관은 1개만 만들어진다")
    fun 동시에_습관을_생성해도_활성_습관은_하나만_생긴다() {
        val executor = Executors.newFixedThreadPool(THREAD_COUNT)
        val readyLatch = CountDownLatch(THREAD_COUNT)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(THREAD_COUNT)

        val successCount = AtomicInteger()
        val rejectedCount = AtomicInteger()
        val unexpectedCount = AtomicInteger()

        val request = CreateHabitRequest("동시성 습관", "설명", 3)

        repeat(THREAD_COUNT) {
            executor.submit(
                Runnable {
                    try {
                        readyLatch.countDown()
                        startLatch.await()
                        habitService.createHabit(groupId, owner.id, request)
                        successCount.incrementAndGet()
                    } catch (e: IllegalStateException) {
                        rejectedCount.incrementAndGet()
                    } catch (e: Exception) {
                        unexpectedCount.incrementAndGet()
                        e.printStackTrace()
                    } finally {
                        doneLatch.countDown()
                    }
                },
            )
        }

        readyLatch.await()
        startLatch.countDown()
        doneLatch.await()
        executor.shutdown()

        val activeHabits = habitRepository.findAllByGroupMember_Group_IdAndStatus(groupId, HabitStatus.ACTIVE)

        assertThat(unexpectedCount.get()).isZero()
        assertThat(successCount.get()).isEqualTo(1)
        assertThat(rejectedCount.get()).isEqualTo(THREAD_COUNT - 1)
        assertThat(activeHabits).hasSize(1)

        // 활성 습관이 중복되면 단건 조회가 예외(500)를 내던 문제의 회귀 확인
        assertThatCode { habitService.getActiveHabit(owner.id, groupId) }.doesNotThrowAnyException()
    }
}
