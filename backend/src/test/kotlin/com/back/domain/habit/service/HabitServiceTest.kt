package com.back.domain.habit.service

import com.back.domain.group.entity.Group
import com.back.domain.group.entity.GroupStatus
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.habit.dto.CreateHabitRequest
import com.back.domain.habit.dto.WeeklyCheckResult
import com.back.domain.habit.entity.Habit
import com.back.domain.habit.entity.HabitStatus
import com.back.domain.habit.repository.HabitRepository
import com.back.domain.habitVerify.repository.HabitVerifyRepository
import com.back.domain.member.entity.Member
import com.back.domain.penaltyverify.service.PenaltyVerifyService
import com.back.global.exception.BusinessRuleException
import com.back.global.exception.EntityNotFoundException
import com.back.global.exception.ForbiddenException
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.justRun
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional

@ExtendWith(MockKExtension::class)
class HabitServiceTest {

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

    private fun group(status: GroupStatus, deadline: LocalDate?): Group {
        val group = Group.builder()
            .title("아침 기상 챌린지")
            .penalty("커피 1잔 쏘기")
            .memberLimit(10)
            .status(status)
            .deadline(deadline)
            .build()
        ReflectionTestUtils.setField(group, "id", GROUP_ID)
        return group
    }

    private fun activeGroup(): Group = group(GroupStatus.ACTIVE, null)

    private fun groupMember(group: Group, role: GroupMemberRole): GroupMember {
        val member = Member.create("일반회원", "member", "pass1234")
        ReflectionTestUtils.setField(member, "id", MEMBER_ID)
        val groupMember = GroupMember.create(group, member, role)
        ReflectionTestUtils.setField(groupMember, "id", GROUP_MEMBER_ID)
        return groupMember
    }

    private fun habit(groupMember: GroupMember): Habit {
        val habit = Habit.create(groupMember, "기상 후 스트레칭", "10분 스트레칭", 3)
        ReflectionTestUtils.setField(habit, "id", HABIT_ID)
        return habit
    }

    // 판정 대상이 되는 상태(그룹 생성 14일 전, 가입·습관 생성 10일 전, 인증 기록 없음)의 습관
    private fun overdueHabit(group: Group): Habit {
        val now = LocalDateTime.now()
        ReflectionTestUtils.setField(group, "createDate", now.minusDays(14))
        val groupMember = groupMember(group, GroupMemberRole.MEMBER)
        ReflectionTestUtils.setField(groupMember, "createDate", now.minusDays(10))
        val habit = habit(groupMember)
        ReflectionTestUtils.setField(habit, "createDate", now.minusDays(10))
        return habit
    }

    @Nested
    @DisplayName("습관 생성(createHabit)")
    inner class CreateHabit {

        private val request = CreateHabitRequest("기상 후 스트레칭", "10분 스트레칭", 3)

        @Test
        @DisplayName("활성 습관이 없으면 ACTIVE 습관이 생성된다")
        fun 생성_성공() {
            val groupMember = groupMember(activeGroup(), GroupMemberRole.MEMBER)
            every { groupMemberRepository.findByGroupIdAndMemberIdForUpdate(GROUP_ID, MEMBER_ID) } returns
                Optional.of(groupMember)
            every { habitRepository.existsByGroupMember_IdAndStatus(GROUP_MEMBER_ID, HabitStatus.ACTIVE) } returns false
            every { habitRepository.save(any<Habit>()) } answers { firstArg() }

            val response = habitService.createHabit(GROUP_ID, MEMBER_ID, request)

            assertThat(response.title).isEqualTo("기상 후 스트레칭")
            assertThat(response.description).isEqualTo("10분 스트레칭")
            assertThat(response.days).isEqualTo(3)
            assertThat(response.status).isEqualTo(HabitStatus.ACTIVE)
            assertThat(response.groupMemberId).isEqualTo(GROUP_MEMBER_ID)
        }

        @Test
        @DisplayName("그룹 멤버가 아니면 IllegalArgumentException이 발생한다")
        fun 그룹_멤버가_아니면_예외() {
            every { groupMemberRepository.findByGroupIdAndMemberIdForUpdate(GROUP_ID, MEMBER_ID) } returns
                Optional.empty()

            assertThatThrownBy { habitService.createHabit(GROUP_ID, MEMBER_ID, request) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("해당 그룹의 멤버를 찾을 수 없습니다.")
            verify(exactly = 0) { habitRepository.save(any<Habit>()) }
        }

        @Test
        @DisplayName("종료된 그룹이면 BusinessRuleException이 발생한다")
        fun 종료된_그룹이면_예외() {
            val groupMember = groupMember(group(GroupStatus.FINISH, null), GroupMemberRole.MEMBER)
            every { groupMemberRepository.findByGroupIdAndMemberIdForUpdate(GROUP_ID, MEMBER_ID) } returns
                Optional.of(groupMember)

            assertThatThrownBy { habitService.createHabit(GROUP_ID, MEMBER_ID, request) }
                .isInstanceOf(BusinessRuleException::class.java)
                .hasMessageContaining("이미 종료된 그룹에서는 습관을 추가할 수 없습니다.")
            verify(exactly = 0) { habitRepository.save(any<Habit>()) }
        }

        @Test
        @DisplayName("이미 활성 습관이 있으면 IllegalStateException이 발생하고 저장하지 않는다")
        fun 활성_습관이_이미_있으면_예외() {
            val groupMember = groupMember(activeGroup(), GroupMemberRole.MEMBER)
            every { groupMemberRepository.findByGroupIdAndMemberIdForUpdate(GROUP_ID, MEMBER_ID) } returns
                Optional.of(groupMember)
            every { habitRepository.existsByGroupMember_IdAndStatus(GROUP_MEMBER_ID, HabitStatus.ACTIVE) } returns true

            assertThatThrownBy { habitService.createHabit(GROUP_ID, MEMBER_ID, request) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("이미 활성화된 습관이 존재합니다.")
            verify(exactly = 0) { habitRepository.save(any<Habit>()) }
        }
    }

    @Nested
    @DisplayName("습관 포기(failHabit)")
    inner class FailHabit {

        @Test
        @DisplayName("본인의 활성 습관을 포기하면 FAILED가 되고 벌칙이 생성된다")
        fun 포기_성공() {
            val habit = habit(groupMember(activeGroup(), GroupMemberRole.MEMBER))
            every { habitRepository.findByIdAndGroupMember_Member_Id(HABIT_ID, MEMBER_ID) } returns Optional.of(habit)
            justRun { penaltyVerifyService.createPenaltyVerify(habit) }

            habitService.failHabit(MEMBER_ID, HABIT_ID)

            assertThat(habit.status).isEqualTo(HabitStatus.FAILED)
            verify { penaltyVerifyService.createPenaltyVerify(habit) }
        }

        @Test
        @DisplayName("본인의 습관이 아니거나 없으면 IllegalArgumentException이 발생하고 벌칙은 생성되지 않는다")
        fun 습관이_없으면_예외() {
            every { habitRepository.findByIdAndGroupMember_Member_Id(HABIT_ID, MEMBER_ID) } returns Optional.empty()

            assertThatThrownBy { habitService.failHabit(MEMBER_ID, HABIT_ID) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("해당 회원의 습관을 찾을 수 없습니다.")
            verify(exactly = 0) { penaltyVerifyService.createPenaltyVerify(any()) }
        }

        @Test
        @DisplayName("종료된 그룹의 습관은 포기할 수 없고 상태도 바뀌지 않는다")
        fun 종료된_그룹이면_예외() {
            val habit = habit(groupMember(group(GroupStatus.FINISH, null), GroupMemberRole.MEMBER))
            every { habitRepository.findByIdAndGroupMember_Member_Id(HABIT_ID, MEMBER_ID) } returns Optional.of(habit)

            assertThatThrownBy { habitService.failHabit(MEMBER_ID, HABIT_ID) }
                .isInstanceOf(BusinessRuleException::class.java)
                .hasMessageContaining("이미 종료된 그룹의 습관은 포기할 수 없습니다.")
            assertThat(habit.status).isEqualTo(HabitStatus.ACTIVE)
            verify(exactly = 0) { penaltyVerifyService.createPenaltyVerify(any()) }
        }

        @Test
        @DisplayName("이미 FAILED인 습관을 다시 포기하면 IllegalStateException이 발생하고 벌칙은 중복 생성되지 않는다")
        fun 이미_실패한_습관이면_예외() {
            val habit = habit(groupMember(activeGroup(), GroupMemberRole.MEMBER))
            habit.fail()
            every { habitRepository.findByIdAndGroupMember_Member_Id(HABIT_ID, MEMBER_ID) } returns Optional.of(habit)

            assertThatThrownBy { habitService.failHabit(MEMBER_ID, HABIT_ID) }
                .isInstanceOf(IllegalStateException::class.java)
            verify(exactly = 0) { penaltyVerifyService.createPenaltyVerify(any()) }
        }
    }

    @Nested
    @DisplayName("습관 조회")
    inner class GetHabit {

        @Test
        @DisplayName("활성 습관이 있으면 그 습관을 반환한다")
        fun 활성_습관_조회_성공() {
            val habit = habit(groupMember(activeGroup(), GroupMemberRole.MEMBER))
            every {
                habitRepository.findByGroupMember_Member_IdAndGroupMember_Group_IdAndStatus(
                    MEMBER_ID, GROUP_ID, HabitStatus.ACTIVE,
                )
            } returns habit

            val response = habitService.getActiveHabit(MEMBER_ID, GROUP_ID)

            assertThat(response.id).isEqualTo(HABIT_ID)
            assertThat(response.status).isEqualTo(HabitStatus.ACTIVE)
        }

        @Test
        @DisplayName("활성 습관이 없으면 IllegalArgumentException이 발생한다")
        fun 활성_습관이_없으면_예외() {
            every {
                habitRepository.findByGroupMember_Member_IdAndGroupMember_Group_IdAndStatus(
                    MEMBER_ID, GROUP_ID, HabitStatus.ACTIVE,
                )
            } returns null

            assertThatThrownBy { habitService.getActiveHabit(MEMBER_ID, GROUP_ID) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("현재 진행 중인 습관이 없습니다.")
        }

        @Test
        @DisplayName("실패한 습관 목록은 저장소가 돌려준 순서(최신순)를 그대로 유지한다")
        fun 실패한_습관_목록_조회() {
            val groupMember = groupMember(activeGroup(), GroupMemberRole.MEMBER)
            val newer = habit(groupMember)
            ReflectionTestUtils.setField(newer, "id", 2L)
            val older = habit(groupMember)
            ReflectionTestUtils.setField(older, "id", 1L)
            every {
                habitRepository.findAllByGroupMember_Member_IdAndGroupMember_Group_IdAndStatusOrderByCreateDateDesc(
                    MEMBER_ID, GROUP_ID, HabitStatus.FAILED,
                )
            } returns listOf(newer, older)

            val responses = habitService.getFailedHabits(MEMBER_ID, GROUP_ID)

            assertThat(responses).extracting<Long?> { it.id }.containsExactly(2L, 1L)
        }

        @Test
        @DisplayName("실패한 습관이 없으면 빈 목록을 반환한다")
        fun 실패한_습관이_없으면_빈_목록() {
            every {
                habitRepository.findAllByGroupMember_Member_IdAndGroupMember_Group_IdAndStatusOrderByCreateDateDesc(
                    MEMBER_ID, GROUP_ID, HabitStatus.FAILED,
                )
            } returns emptyList()

            assertThat(habitService.getFailedHabits(MEMBER_ID, GROUP_ID)).isEmpty()
        }

        @Test
        @DisplayName("같은 그룹 멤버는 습관 상세를 조회할 수 있다")
        fun 상세_조회_성공() {
            val habit = habit(groupMember(activeGroup(), GroupMemberRole.MEMBER))
            every { habitRepository.findById(HABIT_ID) } returns Optional.of(habit)
            every { groupMemberRepository.existsByGroupIdAndMemberId(GROUP_ID, MEMBER_ID) } returns true

            assertThat(habitService.getHabit(MEMBER_ID, HABIT_ID).id).isEqualTo(HABIT_ID)
        }

        @Test
        @DisplayName("존재하지 않는 습관이면 EntityNotFoundException이 발생한다")
        fun 상세_조회_없는_습관_예외() {
            every { habitRepository.findById(HABIT_ID) } returns Optional.empty()

            assertThatThrownBy { habitService.getHabit(MEMBER_ID, HABIT_ID) }
                .isInstanceOf(EntityNotFoundException::class.java)
                .hasMessageContaining("존재하지 않는 습관입니다.")
        }

        @Test
        @DisplayName("다른 그룹의 습관이면 ForbiddenException이 발생한다")
        fun 상세_조회_다른_그룹_예외() {
            val habit = habit(groupMember(activeGroup(), GroupMemberRole.MEMBER))
            every { habitRepository.findById(HABIT_ID) } returns Optional.of(habit)
            every { groupMemberRepository.existsByGroupIdAndMemberId(GROUP_ID, MEMBER_ID) } returns false

            assertThatThrownBy { habitService.getHabit(MEMBER_ID, HABIT_ID) }
                .isInstanceOf(ForbiddenException::class.java)
                .hasMessageContaining("해당 그룹의 습관만 조회할 수 있습니다.")
        }
    }

    @Nested
    @DisplayName("그룹 주간 판정(checkWeeklyFailureForGroup)")
    inner class CheckWeeklyFailure {

        @Test
        @DisplayName("그룹 멤버가 아니면 IllegalArgumentException이 발생한다")
        fun 그룹_멤버가_아니면_예외() {
            every { groupMemberRepository.findByGroupIdAndMemberId(GROUP_ID, MEMBER_ID) } returns Optional.empty()

            assertThatThrownBy { habitService.checkWeeklyFailureForGroup(MEMBER_ID, GROUP_ID) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        @DisplayName("방장이 아니면 ForbiddenException이 발생하고 습관을 조회하지 않는다")
        fun 방장이_아니면_예외() {
            every { groupMemberRepository.findByGroupIdAndMemberId(GROUP_ID, MEMBER_ID) } returns
                Optional.of(groupMember(activeGroup(), GroupMemberRole.MEMBER))

            assertThatThrownBy { habitService.checkWeeklyFailureForGroup(MEMBER_ID, GROUP_ID) }
                .isInstanceOf(ForbiddenException::class.java)
                .hasMessageContaining("주간 판정은 방장만 실행할 수 있습니다.")
            verify(exactly = 0) { habitRepository.findAllByGroupMember_Group_IdAndStatus(any(), any()) }
        }

        @Test
        @DisplayName("종료된 그룹이면 BusinessRuleException이 발생하고 습관을 조회하지 않는다")
        fun 종료된_그룹이면_예외() {
            every { groupMemberRepository.findByGroupIdAndMemberId(GROUP_ID, MEMBER_ID) } returns
                Optional.of(groupMember(group(GroupStatus.FINISH, null), GroupMemberRole.OWNER))

            assertThatThrownBy { habitService.checkWeeklyFailureForGroup(MEMBER_ID, GROUP_ID) }
                .isInstanceOf(BusinessRuleException::class.java)
                .hasMessageContaining("이미 종료된 그룹은 주간 판정을 실행할 수 없습니다.")
            verify(exactly = 0) { habitRepository.findAllByGroupMember_Group_IdAndStatus(any(), any()) }
        }

        @Test
        @DisplayName("방장이 호출하면 그룹의 활성 습관마다 판정 결과를 반환한다")
        fun 판정_결과_반환() {
            val group = activeGroup()
            val owner = groupMember(group, GroupMemberRole.OWNER)
            // 가입 직후라 1주가 지나지 않은 습관은 TOO_EARLY
            ReflectionTestUtils.setField(owner, "createDate", LocalDateTime.now())
            val habit = habit(owner)
            every { groupMemberRepository.findByGroupIdAndMemberId(GROUP_ID, MEMBER_ID) } returns Optional.of(owner)
            every { habitRepository.findAllByGroupMember_Group_IdAndStatus(GROUP_ID, HabitStatus.ACTIVE) } returns
                listOf(habit)

            val results = habitService.checkWeeklyFailureForGroup(MEMBER_ID, GROUP_ID)

            assertThat(results).hasSize(1)
            assertThat(results[0].habitId).isEqualTo(HABIT_ID)
            assertThat(results[0].result).isEqualTo(WeeklyCheckResult.TOO_EARLY)
            assertThat(results[0].habitStatus).isEqualTo(HabitStatus.ACTIVE)
        }
    }

    @Nested
    @DisplayName("스케줄러(autoCheckAllActiveHabits)")
    inner class AutoCheck {

        @Test
        @DisplayName("진행 중인 그룹의 활성 습관만 조회해서 판정한다 (종료 그룹 제외 쿼리 사용)")
        fun 종료_그룹_제외_쿼리를_사용한다() {
            every { habitRepository.findAllByStatusInProgressGroup(HabitStatus.ACTIVE) } returns emptyList()

            habitService.autoCheckAllActiveHabits()

            verify { habitRepository.findAllByStatusInProgressGroup(HabitStatus.ACTIVE) }
            verify(exactly = 0) { habitRepository.findAllByGroupMember_Group_IdAndStatus(any(), any()) }
        }

        @Test
        @DisplayName("진행 중인 그룹에서 승인 횟수가 모자라면 습관이 실패 처리되고 벌칙이 생성된다 (대조군)")
        fun 진행중인_그룹은_판정된다() {
            val habit = overdueHabit(activeGroup())
            every { habitRepository.findAllByStatusInProgressGroup(HabitStatus.ACTIVE) } returns listOf(habit)
            // 인증 기록이 하나도 없는 상태
            every {
                habitVerifyRepository.countByHabit_IdAndCreateDateBetweenAndStatus(any(), any(), any(), any())
            } returns 0L
            justRun { penaltyVerifyService.createPenaltyVerify(habit) }

            habitService.autoCheckAllActiveHabits()

            assertThat(habit.status).isEqualTo(HabitStatus.FAILED)
            verify { penaltyVerifyService.createPenaltyVerify(habit) }
        }

        @Test
        @DisplayName("마감일은 지났지만 아직 FINISH 처리 전인 그룹의 습관은 판정하지 않는다")
        fun 마감일이_지난_그룹은_판정하지_않는다() {
            val habit = overdueHabit(group(GroupStatus.ACTIVE, LocalDate.now().minusDays(1)))
            every { habitRepository.findAllByStatusInProgressGroup(HabitStatus.ACTIVE) } returns listOf(habit)

            habitService.autoCheckAllActiveHabits()

            assertThat(habit.status).isEqualTo(HabitStatus.ACTIVE)
            verify(exactly = 0) { penaltyVerifyService.createPenaltyVerify(any()) }
        }
    }
}
