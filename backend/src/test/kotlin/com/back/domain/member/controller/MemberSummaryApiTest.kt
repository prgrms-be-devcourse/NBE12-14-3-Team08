package com.back.domain.member.controller

import com.back.domain.group.entity.Group
import com.back.domain.group.entity.GroupStatus
import com.back.domain.group.repository.GroupRepository
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.habit.entity.Habit
import com.back.domain.habit.repository.HabitRepository
import com.back.domain.habitVerify.entity.HabitVerify
import com.back.domain.habitVerify.repository.HabitVerifyRepository
import com.back.domain.member.entity.Member
import com.back.domain.member.service.MemberSummaryService
import com.back.domain.penaltyverify.entity.PenaltyVerify
import com.back.domain.penaltyverify.repository.PenaltyVerifyRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

// 메인(방 목록) 화면의 내 요약: 이번 주 승인 수와 목표, 누적 벌칙 수
class MemberSummaryApiTest : MemberApiTestSupport() {

    @Autowired private lateinit var summaryService: MemberSummaryService
    @Autowired private lateinit var groupRepository: GroupRepository
    @Autowired private lateinit var groupMemberRepository: GroupMemberRepository
    @Autowired private lateinit var habitRepository: HabitRepository
    @Autowired private lateinit var habitVerifyRepository: HabitVerifyRepository
    @Autowired private lateinit var penaltyVerifyRepository: PenaltyVerifyRepository
    @Autowired private lateinit var entityManager: EntityManager

    // 수요일. 방을 토요일(10-24)에 만들었다면 이번 주는 10-24부터다.
    private val today = LocalDate.of(2026, 10, 28)
    private val saturday = LocalDate.of(2026, 10, 24)

    private fun uniqueName(prefix: String) = prefix + "-" + UUID.randomUUID().toString().substring(0, 8)

    private fun saveMember(): Member = memberRepository.save(Member.create("회원", uniqueName("m"), "pass1234"))

    private fun saveGroup(createdOn: LocalDate, status: GroupStatus = GroupStatus.ACTIVE, deadline: LocalDate? = null): Group {
        val group = groupRepository.save(
            Group(title = "방", penalty = "커피", password = "1234", inviteCode = uniqueName("c"), memberLimit = 10, status = status, deadline = deadline),
        )
        ReflectionTestUtils.setField(group, "createDate", createdOn.atStartOfDay())
        return group
    }

    private fun join(group: Group, member: Member): GroupMember =
        groupMemberRepository.save(GroupMember.create(group, member, GroupMemberRole.MEMBER))

    private fun saveHabit(gm: GroupMember, days: Int): Habit = habitRepository.save(Habit.create(gm, "습관", null, days))

    private fun verify(habit: Habit, date: LocalDate, approve: Boolean = true, reject: Boolean = false) {
        val hv = HabitVerify.create(habit, date, "설명", null)
        if (approve) hv.approve()
        if (reject) hv.reject()
        habitVerifyRepository.save(hv)
    }

    private fun flushAndClear() {
        entityManager.flush()
        entityManager.clear()
    }

    @Test
    @DisplayName("이번 주(방을 만든 요일부터)에 승인된 인증만 세고, 목표는 활성 습관의 주당 횟수를 합한다")
    fun 이번_주_승인만_센다() {
        val me = saveMember()
        val group = saveGroup(saturday)
        val habit = saveHabit(join(group, me), days = 4)
        verify(habit, saturday.minusDays(1))            // 지난 주
        verify(habit, saturday)                         // 이번 주 시작일
        verify(habit, LocalDate.of(2026, 10, 26))       // 이번 주
        verify(habit, LocalDate.of(2026, 10, 27), approve = false) // 검토 중
        verify(habit, LocalDate.of(2026, 10, 25), approve = false, reject = true) // 반려
        flushAndClear()

        val summary = summaryService.getSummary(me.id!!, today)

        assertThat(summary.weeklyApprovedCount).isEqualTo(2)
        assertThat(summary.weeklyGoalCount).isEqualTo(4)
    }

    @Test
    @DisplayName("방마다 주의 시작일이 다르면 습관별로 따로 센다")
    fun 방마다_주_시작일이_다르다() {
        val me = saveMember()
        // 월요일(10-26)에 만든 방: 이번 주는 10-26부터
        val monday = saveHabit(join(saveGroup(LocalDate.of(2026, 10, 26)), me), days = 3)
        // 토요일(10-24)에 만든 방: 이번 주는 10-24부터
        val saturdayHabit = saveHabit(join(saveGroup(saturday), me), days = 5)
        verify(monday, LocalDate.of(2026, 10, 25))      // 월요일 방 기준으로는 지난 주
        verify(monday, LocalDate.of(2026, 10, 26))
        verify(saturdayHabit, LocalDate.of(2026, 10, 25)) // 토요일 방 기준으로는 이번 주
        flushAndClear()

        val summary = summaryService.getSummary(me.id!!, today)

        assertThat(summary.weeklyApprovedCount).isEqualTo(2)
        assertThat(summary.weeklyGoalCount).isEqualTo(8)
    }

    @Test
    @DisplayName("포기한 습관, 종료된 방, 떠난 방, 다른 회원의 습관은 세지 않는다")
    fun 대상_아닌_습관은_제외한다() {
        val me = saveMember()
        val other = saveMember()
        val running = saveGroup(saturday)
        val counted = saveHabit(join(running, me), days = 4)
        verify(counted, saturday)

        val failed = saveHabit(join(saveGroup(saturday), me), days = 3).also { it.fail() }
        verify(failed, saturday)

        val finishedGroup = saveGroup(saturday, status = GroupStatus.FINISH)
        verify(saveHabit(join(finishedGroup, me), days = 2), saturday)

        val expiredGroup = saveGroup(saturday, deadline = today.minusDays(1))
        verify(saveHabit(join(expiredGroup, me), days = 2), saturday)

        val leftGm = join(saveGroup(saturday), me).also { it.leave() }
        verify(saveHabit(leftGm, days = 6), saturday)

        verify(saveHabit(join(running, other), days = 7), saturday)
        flushAndClear()

        val summary = summaryService.getSummary(me.id!!, today)

        assertThat(summary.weeklyApprovedCount).isEqualTo(1)
        assertThat(summary.weeklyGoalCount).isEqualTo(4)
    }

    @Test
    @DisplayName("마감일 당일인 방은 아직 진행 중이라 센다")
    fun 마감일_당일은_진행_중이다() {
        val me = saveMember()
        val group = saveGroup(saturday, deadline = today)
        saveHabit(join(group, me), days = 3)
        flushAndClear()

        assertThat(summaryService.getSummary(me.id!!, today).weeklyGoalCount).isEqualTo(3)
    }

    @Test
    @DisplayName("습관이 없으면 0 / 0이고, 벌칙은 방 상태와 상관없이 내 벌칙 인증 전체를 센다")
    fun 벌칙은_방_상태와_무관하게_센다() {
        val me = saveMember()
        val other = saveMember()
        assertThat(summaryService.getSummary(me.id!!, today).weeklyGoalCount).isZero()

        val activeGm = join(saveGroup(saturday), me)
        val finishedGm = join(saveGroup(saturday, status = GroupStatus.FINISH), me)
        val h1 = saveHabit(activeGm, 3).also { it.fail() }
        val h2 = saveHabit(finishedGm, 3).also { it.fail() }
        val otherHabit = saveHabit(join(saveGroup(saturday), other), 3).also { it.fail() }
        penaltyVerifyRepository.save(PenaltyVerify.create(activeGm, h1))
        penaltyVerifyRepository.save(PenaltyVerify.create(finishedGm, h2))
        penaltyVerifyRepository.save(PenaltyVerify.create(otherHabit.groupMember, otherHabit))
        flushAndClear()

        val summary = summaryService.getSummary(me.id!!, today)

        assertThat(summary.penaltyCount).isEqualTo(2)
        assertThat(summary.weeklyApprovedCount).isZero()
        assertThat(summary.weeklyGoalCount).isZero()
    }

    @Test
    @DisplayName("GET /api/members/me/summary: 로그인한 회원의 요약을 돌려주고, 토큰이 없으면 401이다")
    fun 요약_API() {
        signUpOk("summaryuser")
        val tokens = loginOk("summaryuser")

        val result = perform(HttpMethod.GET, "/api/members/me/summary", tokens.accessToken)
        assertThat(result.andReturn().response.status).isEqualTo(200)
        val body = json(result)
        assertThat(keys(body)).containsExactlyInAnyOrder("weeklyApprovedCount", "weeklyGoalCount", "penaltyCount")
        assertThat(body.get("weeklyApprovedCount").asInt()).isZero()
        assertThat(body.get("weeklyGoalCount").asInt()).isZero()
        assertThat(body.get("penaltyCount").asLong()).isZero()

        val unauthorized = perform(HttpMethod.GET, "/api/members/me/summary")
        assertThat(unauthorized.andReturn().response.status).isEqualTo(401)
    }
}
