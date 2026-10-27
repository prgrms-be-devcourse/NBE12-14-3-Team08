package com.back.domain.habit.service

import com.back.domain.group.entity.Group
import com.back.domain.group.entity.GroupStatus
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.habit.dto.WeeklyCheckResult
import com.back.domain.habit.entity.Habit
import com.back.domain.habit.entity.HabitStatus
import com.back.domain.habit.repository.HabitRepository
import com.back.domain.habitVerify.entity.HabitVerify
import com.back.domain.habitVerify.entity.HabitVerifyStatus
import com.back.domain.habitVerify.repository.HabitVerifyRepository
import com.back.domain.member.entity.Member
import com.back.domain.penaltyverify.service.PenaltyVerifyService
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.justRun
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional

// 주 경계 도중에 만든 습관(부분 주)의 판정 규칙 (#124).
// 습관을 만든 뒤 처음 맞는 주 경계의 판정은 면제하고, 그다음 온전한 주부터 days와 비교한다.
@ExtendWith(MockKExtension::class)
@DisplayName("주 경계 도중에 만든 습관의 주간 판정")
class HabitServiceMidWeekCreationTest {

    @InjectMockKs
    private lateinit var habitService: HabitService

    @MockK
    private lateinit var groupMemberRepository: GroupMemberRepository

    @MockK
    private lateinit var habitRepository: HabitRepository

    @MockK
    private lateinit var habitVerifyRepository: HabitVerifyRepository

    @MockK
    private lateinit var penaltyVerifyService: PenaltyVerifyService

    companion object {
        private const val GROUP_ID = 100L
        private const val MEMBER_ID = 1L
        private const val GROUP_MEMBER_ID = 10L
        private const val HABIT_ID = 50L
    }

    // 방 생성 요일이 "오늘 - offsetDays"의 요일이라 이번 주 경계(deadline)는 offsetDays일 전 00시가 된다.
    // 가입은 오래전이라 가입 1주 면제(TOO_EARLY)에는 걸리지 않는다.
    private inner class Fixture(
        days: Int,
        val offsetDays: Long = 0,
        habitCreatedAt: (deadline: LocalDateTime) -> LocalDateTime,
    ) {
        val today: LocalDate = LocalDate.now()
        val deadline: LocalDateTime = today.minusDays(offsetDays).atStartOfDay()
        val weekStart: LocalDateTime = deadline.minusWeeks(1)
        val habit: Habit

        init {
            val now = LocalDateTime.now()
            val group = Group.builder()
                .title("아침 기상 챌린지")
                .penalty("커피 1잔 쏘기")
                .memberLimit(10)
                .status(GroupStatus.ACTIVE)
                .build()
            ReflectionTestUtils.setField(group, "id", GROUP_ID)
            // 14일(2주)은 요일이 같으므로 방 생성 요일은 "오늘 - offsetDays"가 된다.
            ReflectionTestUtils.setField(group, "createDate", now.minusDays(14 + offsetDays))

            val member = Member.create("일반회원", "member", "pass1234")
            ReflectionTestUtils.setField(member, "id", MEMBER_ID)
            val owner = GroupMember.create(group, member, GroupMemberRole.OWNER)
            ReflectionTestUtils.setField(owner, "id", GROUP_MEMBER_ID)
            ReflectionTestUtils.setField(owner, "createDate", now.minusDays(30))

            habit = Habit.create(owner, "백준 실버 2문제 풀기", "매일 2문제", days)
            ReflectionTestUtils.setField(habit, "id", HABIT_ID)
            ReflectionTestUtils.setField(habit, "createDate", habitCreatedAt(deadline))

            every { groupMemberRepository.findByGroupIdAndMemberId(GROUP_ID, MEMBER_ID) } returns Optional.of(owner)
            every { habitRepository.findIdsByGroupIdAndStatus(GROUP_ID, HabitStatus.ACTIVE) } returns listOf(HABIT_ID)
            every { habitRepository.findByIdForUpdate(HABIT_ID) } returns habit
        }

        // 지정한 구간(weekStart ~ deadline)으로 조회될 때만 값을 돌려준다. 구간이 다르면 MockK가 예외를 던져 테스트가 실패한다.
        fun stubCount(status: HabitVerifyStatus, count: Long) {
            every {
                habitVerifyRepository.countByHabit_IdAndCreateDateBetweenAndStatus(HABIT_ID, weekStart, deadline, status)
            } returns count
        }

        fun check() = habitService.checkWeeklyFailureForGroup(MEMBER_ID, GROUP_ID).map { it.result }

        // 테스트가 자정을 넘기면 기대값이 어긋나므로 실패 대신 중단한다.
        fun assumeSameDay() = Assumptions.assumeTrue(LocalDate.now() == today, "테스트 도중 날짜가 바뀌었습니다.")
    }

    // 면제·건너뜀이면 승인 횟수를 세지 않고 실패 처리나 벌칙 생성도 하지 않는다.
    // (인증 기록 저장소의 count는 스텁이 없으면 MockK가 예외를 던지므로 호출되면 테스트가 실패한다)
    private fun verifyNeverJudged() {
        verify(exactly = 0) { penaltyVerifyService.createPenaltyVerify(any()) }
        verify(exactly = 0) {
            habitVerifyRepository.countByHabit_IdAndCreateDateBetweenAndStatus(any(), any(), any(), any())
        }
    }

    @Test
    @DisplayName("경계 이틀 전에 만든 주 5회 습관은 이틀 인증을 모두 승인받아도 첫 판정에서 실패하지 않고 면제된다")
    fun 이틀_전에_만든_주5회_습관은_면제된다() {
        val fixture = Fixture(days = 5) { it.minusDays(2) }

        val results = fixture.check()

        fixture.assumeSameDay()
        assertThat(results).containsExactly(WeeklyCheckResult.FIRST_WEEK_EXEMPT)
        assertThat(fixture.habit.status).isEqualTo(HabitStatus.ACTIVE)
        verifyNeverJudged()
    }

    @Test
    @DisplayName("같은 조건의 주 2회 습관도 첫 부분 주는 승인 횟수와 관계없이 면제된다")
    fun 이틀_전에_만든_주2회_습관도_면제된다() {
        val fixture = Fixture(days = 2) { it.minusDays(2) }

        val results = fixture.check()

        fixture.assumeSameDay()
        assertThat(results).containsExactly(WeeklyCheckResult.FIRST_WEEK_EXEMPT)
        assertThat(fixture.habit.status).isEqualTo(HabitStatus.ACTIVE)
        verifyNeverJudged()
    }

    @ParameterizedTest(name = "경계 {0}일 전에 만든 습관")
    @ValueSource(longs = [1, 2, 3, 6])
    @DisplayName("판정 구간 도중(경계 6일 전까지)에 만든 습관은 부분 주라서 면제된다")
    fun 구간_도중에_만든_습관은_면제된다(daysBeforeDeadline: Long) {
        val fixture = Fixture(days = 5) { it.minusDays(daysBeforeDeadline) }

        val results = fixture.check()

        fixture.assumeSameDay()
        assertThat(results).containsExactly(WeeklyCheckResult.FIRST_WEEK_EXEMPT)
        verifyNeverJudged()
    }

    @Test
    @DisplayName("경계 1나노초 전에 만든 습관도 면제된다")
    fun 경계_직전에_만든_습관은_면제된다() {
        val fixture = Fixture(days = 5) { it.minusNanos(1) }

        val results = fixture.check()

        fixture.assumeSameDay()
        assertThat(results).containsExactly(WeeklyCheckResult.FIRST_WEEK_EXEMPT)
        verifyNeverJudged()
    }

    @Test
    @DisplayName("구간이 시작한 직후(1나노초 뒤)에 만든 습관은 거의 한 주를 채웠어도 면제된다")
    fun 구간_시작_직후에_만든_습관은_면제된다() {
        val fixture = Fixture(days = 5) { it.minusWeeks(1).plusNanos(1) }

        val results = fixture.check()

        fixture.assumeSameDay()
        assertThat(results).containsExactly(WeeklyCheckResult.FIRST_WEEK_EXEMPT)
        verifyNeverJudged()
    }

    @Test
    @DisplayName("경계 정각 이후에 만든 습관은 이번 구간에 없던 습관이라 BEFORE_DEADLINE이다 (기존 규칙 유지)")
    fun 경계_이후에_만든_습관은_이전_규칙대로_건너뛴다() {
        val fixture = Fixture(days = 5) { it }

        val results = fixture.check()

        fixture.assumeSameDay()
        assertThat(results).containsExactly(WeeklyCheckResult.BEFORE_DEADLINE)
        assertThat(fixture.habit.status).isEqualTo(HabitStatus.ACTIVE)
        verifyNeverJudged()
    }

    @Test
    @DisplayName("구간 시작 정각에 만든 습관은 온전한 한 주를 채운 것이라 판정하고, days 미달이면 실패 처리한다")
    fun 구간_시작_정각에_만든_습관은_판정한다() {
        val fixture = Fixture(days = 5) { it.minusWeeks(1) }
        fixture.stubCount(HabitVerifyStatus.PENDING, 0L)
        fixture.stubCount(HabitVerifyStatus.APPROVED, 2L)
        justRun { penaltyVerifyService.createPenaltyVerify(fixture.habit) }

        val results = fixture.check()

        fixture.assumeSameDay()
        assertThat(results).containsExactly(WeeklyCheckResult.FAILED_INSUFFICIENT_SUBMISSION)
        assertThat(fixture.habit.status).isEqualTo(HabitStatus.FAILED)
        verify { penaltyVerifyService.createPenaltyVerify(fixture.habit) }
    }

    @Test
    @DisplayName("구간 시작보다 먼저 만든 습관(면제 다음 주의 습관)은 days를 채우면 PASSED이다")
    fun 면제_다음_주에는_온전한_주로_판정한다() {
        val fixture = Fixture(days = 5) { it.minusWeeks(1).minusDays(2) }
        fixture.stubCount(HabitVerifyStatus.PENDING, 0L)
        fixture.stubCount(HabitVerifyStatus.APPROVED, 5L)

        val results = fixture.check()

        fixture.assumeSameDay()
        assertThat(results).containsExactly(WeeklyCheckResult.PASSED)
        assertThat(fixture.habit.status).isEqualTo(HabitStatus.ACTIVE)
    }

    @Test
    @DisplayName("면제된 주의 미검토 건은 검토 유예시간(경계 다음날 24시)이 지나면 자동 승인한다")
    fun 면제된_주의_미검토_건은_유예시간_후_자동_승인된다() {
        val fixture = Fixture(days = 5, offsetDays = 2) { it.minusDays(2) }
        val pending = HabitVerify.create(fixture.habit, LocalDate.now().minusDays(5), "설명", "https://x.com/a.jpg")
        every {
            habitVerifyRepository.findAllByHabit_IdAndCreateDateBetweenAndStatus(
                HABIT_ID, fixture.weekStart, fixture.deadline, HabitVerifyStatus.PENDING,
            )
        } returns listOf(pending)

        val results = fixture.check()

        fixture.assumeSameDay()
        assertThat(results).containsExactly(WeeklyCheckResult.FIRST_WEEK_EXEMPT)
        assertThat(pending.status).isEqualTo(HabitVerifyStatus.APPROVED)
        assertThat(fixture.habit.status).isEqualTo(HabitStatus.ACTIVE)
        verifyNeverJudged()
    }

    @Test
    @DisplayName("면제된 주라도 검토 유예시간이 남아 있으면 미검토 건을 건드리지 않는다")
    fun 유예시간이_남았으면_자동_승인하지_않는다() {
        val fixture = Fixture(days = 5, offsetDays = 0) { it.minusDays(2) }

        val results = fixture.check()

        fixture.assumeSameDay()
        assertThat(results).containsExactly(WeeklyCheckResult.FIRST_WEEK_EXEMPT)
        verify(exactly = 0) {
            habitVerifyRepository.findAllByHabit_IdAndCreateDateBetweenAndStatus(any(), any(), any(), any())
        }
    }
}
