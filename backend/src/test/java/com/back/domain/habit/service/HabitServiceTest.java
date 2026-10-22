package com.back.domain.habit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.back.domain.group.entity.Group;
import com.back.domain.group.entity.GroupStatus;
import com.back.domain.groupMember.entity.GroupMember;
import com.back.domain.groupMember.entity.GroupMemberRole;
import com.back.domain.groupMember.repository.GroupMemberRepository;
import com.back.domain.habit.dto.CreateHabitRequest;
import com.back.domain.habit.dto.HabitResponse;
import com.back.domain.habit.dto.HabitWeeklyCheckResponse;
import com.back.domain.habit.dto.WeeklyCheckResult;
import com.back.domain.habit.entity.Habit;
import com.back.domain.habit.entity.HabitStatus;
import com.back.domain.habit.repository.HabitRepository;
import com.back.domain.habitVerify.repository.HabitVerifyRepository;
import com.back.domain.member.entity.Member;
import com.back.domain.penaltyverify.service.PenaltyVerifyService;
import com.back.global.exception.BusinessRuleException;
import com.back.global.exception.EntityNotFoundException;
import com.back.global.exception.ForbiddenException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class HabitServiceTest {

    @InjectMocks
    private HabitService habitService;

    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private HabitRepository habitRepository;
    @Mock
    private HabitVerifyRepository habitVerifyRepository;
    @Mock
    private PenaltyVerifyService penaltyVerifyService;

    private static final Long GROUP_ID = 100L;
    private static final Long MEMBER_ID = 1L;
    private static final Long GROUP_MEMBER_ID = 10L;
    private static final Long HABIT_ID = 50L;

    private Group group(GroupStatus status, LocalDate deadline) {
        Group group = Group.builder()
            .title("아침 기상 챌린지")
            .penalty("커피 1잔 쏘기")
            .memberLimit(10)
            .status(status)
            .deadline(deadline)
            .build();
        ReflectionTestUtils.setField(group, "id", GROUP_ID);
        return group;
    }

    private Group activeGroup() {
        return group(GroupStatus.ACTIVE, null);
    }

    private GroupMember groupMember(Group group, GroupMemberRole role) {
        Member member = Member.create("일반회원", "member", "pass1234");
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        GroupMember groupMember = GroupMember.create(group, member, role);
        ReflectionTestUtils.setField(groupMember, "id", GROUP_MEMBER_ID);
        return groupMember;
    }

    private Habit habit(GroupMember groupMember) {
        Habit habit = Habit.create(groupMember, "기상 후 스트레칭", "10분 스트레칭", 3);
        ReflectionTestUtils.setField(habit, "id", HABIT_ID);
        return habit;
    }

    // 판정 대상이 되는 상태(그룹 생성 14일 전, 가입·습관 생성 10일 전, 인증 기록 없음)의 습관
    private Habit overdueHabit(Group group) {
        LocalDateTime now = LocalDateTime.now();
        ReflectionTestUtils.setField(group, "createDate", now.minusDays(14));
        GroupMember groupMember = groupMember(group, GroupMemberRole.MEMBER);
        ReflectionTestUtils.setField(groupMember, "createDate", now.minusDays(10));
        Habit habit = habit(groupMember);
        ReflectionTestUtils.setField(habit, "createDate", now.minusDays(10));
        return habit;
    }

    @Nested
    @DisplayName("습관 생성(createHabit)")
    class CreateHabit {

        private final CreateHabitRequest request = new CreateHabitRequest("기상 후 스트레칭", "10분 스트레칭", 3);

        @Test
        @DisplayName("활성 습관이 없으면 ACTIVE 습관이 생성된다")
        void 생성_성공() {
            GroupMember groupMember = groupMember(activeGroup(), GroupMemberRole.MEMBER);
            given(groupMemberRepository.findByGroupIdAndMemberIdForUpdate(GROUP_ID, MEMBER_ID))
                .willReturn(Optional.of(groupMember));
            given(habitRepository.existsByGroupMember_IdAndStatus(GROUP_MEMBER_ID, HabitStatus.ACTIVE))
                .willReturn(false);
            given(habitRepository.save(any(Habit.class))).willAnswer(invocation -> invocation.getArgument(0));

            HabitResponse response = habitService.createHabit(GROUP_ID, MEMBER_ID, request);

            assertThat(response.getTitle()).isEqualTo("기상 후 스트레칭");
            assertThat(response.getDescription()).isEqualTo("10분 스트레칭");
            assertThat(response.getDays()).isEqualTo(3);
            assertThat(response.getStatus()).isEqualTo(HabitStatus.ACTIVE);
            assertThat(response.getGroupMemberId()).isEqualTo(GROUP_MEMBER_ID);
        }

        @Test
        @DisplayName("그룹 멤버가 아니면 IllegalArgumentException이 발생한다")
        void 그룹_멤버가_아니면_예외() {
            given(groupMemberRepository.findByGroupIdAndMemberIdForUpdate(GROUP_ID, MEMBER_ID))
                .willReturn(Optional.empty());

            assertThatThrownBy(() -> habitService.createHabit(GROUP_ID, MEMBER_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("해당 그룹의 멤버를 찾을 수 없습니다.");
            verify(habitRepository, never()).save(any(Habit.class));
        }

        @Test
        @DisplayName("종료된 그룹이면 BusinessRuleException이 발생한다")
        void 종료된_그룹이면_예외() {
            GroupMember groupMember = groupMember(group(GroupStatus.FINISH, null), GroupMemberRole.MEMBER);
            given(groupMemberRepository.findByGroupIdAndMemberIdForUpdate(GROUP_ID, MEMBER_ID))
                .willReturn(Optional.of(groupMember));

            assertThatThrownBy(() -> habitService.createHabit(GROUP_ID, MEMBER_ID, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("이미 종료된 그룹에서는 습관을 추가할 수 없습니다.");
            verify(habitRepository, never()).save(any(Habit.class));
        }

        @Test
        @DisplayName("이미 활성 습관이 있으면 IllegalStateException이 발생하고 저장하지 않는다")
        void 활성_습관이_이미_있으면_예외() {
            GroupMember groupMember = groupMember(activeGroup(), GroupMemberRole.MEMBER);
            given(groupMemberRepository.findByGroupIdAndMemberIdForUpdate(GROUP_ID, MEMBER_ID))
                .willReturn(Optional.of(groupMember));
            given(habitRepository.existsByGroupMember_IdAndStatus(GROUP_MEMBER_ID, HabitStatus.ACTIVE))
                .willReturn(true);

            assertThatThrownBy(() -> habitService.createHabit(GROUP_ID, MEMBER_ID, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 활성화된 습관이 존재합니다.");
            verify(habitRepository, never()).save(any(Habit.class));
        }
    }

    @Nested
    @DisplayName("습관 포기(failHabit)")
    class FailHabit {

        @Test
        @DisplayName("본인의 활성 습관을 포기하면 FAILED가 되고 벌칙이 생성된다")
        void 포기_성공() {
            Habit habit = habit(groupMember(activeGroup(), GroupMemberRole.MEMBER));
            given(habitRepository.findByIdAndGroupMember_Member_Id(HABIT_ID, MEMBER_ID))
                .willReturn(Optional.of(habit));

            habitService.failHabit(MEMBER_ID, HABIT_ID);

            assertThat(habit.getStatus()).isEqualTo(HabitStatus.FAILED);
            verify(penaltyVerifyService).createPenaltyVerify(habit);
        }

        @Test
        @DisplayName("본인의 습관이 아니거나 없으면 IllegalArgumentException이 발생하고 벌칙은 생성되지 않는다")
        void 습관이_없으면_예외() {
            given(habitRepository.findByIdAndGroupMember_Member_Id(HABIT_ID, MEMBER_ID))
                .willReturn(Optional.empty());

            assertThatThrownBy(() -> habitService.failHabit(MEMBER_ID, HABIT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("해당 회원의 습관을 찾을 수 없습니다.");
            verify(penaltyVerifyService, never()).createPenaltyVerify(any(Habit.class));
        }

        @Test
        @DisplayName("종료된 그룹의 습관은 포기할 수 없고 상태도 바뀌지 않는다")
        void 종료된_그룹이면_예외() {
            Habit habit = habit(groupMember(group(GroupStatus.FINISH, null), GroupMemberRole.MEMBER));
            given(habitRepository.findByIdAndGroupMember_Member_Id(HABIT_ID, MEMBER_ID))
                .willReturn(Optional.of(habit));

            assertThatThrownBy(() -> habitService.failHabit(MEMBER_ID, HABIT_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("이미 종료된 그룹의 습관은 포기할 수 없습니다.");
            assertThat(habit.getStatus()).isEqualTo(HabitStatus.ACTIVE);
            verify(penaltyVerifyService, never()).createPenaltyVerify(any(Habit.class));
        }

        @Test
        @DisplayName("이미 FAILED인 습관을 다시 포기하면 IllegalStateException이 발생하고 벌칙은 중복 생성되지 않는다")
        void 이미_실패한_습관이면_예외() {
            Habit habit = habit(groupMember(activeGroup(), GroupMemberRole.MEMBER));
            habit.fail();
            given(habitRepository.findByIdAndGroupMember_Member_Id(HABIT_ID, MEMBER_ID))
                .willReturn(Optional.of(habit));

            assertThatThrownBy(() -> habitService.failHabit(MEMBER_ID, HABIT_ID))
                .isInstanceOf(IllegalStateException.class);
            verify(penaltyVerifyService, never()).createPenaltyVerify(any(Habit.class));
        }
    }

    @Nested
    @DisplayName("습관 조회")
    class GetHabit {

        @Test
        @DisplayName("활성 습관이 있으면 그 습관을 반환한다")
        void 활성_습관_조회_성공() {
            Habit habit = habit(groupMember(activeGroup(), GroupMemberRole.MEMBER));
            given(habitRepository.findByGroupMember_Member_IdAndGroupMember_Group_IdAndStatus(
                MEMBER_ID, GROUP_ID, HabitStatus.ACTIVE)).willReturn(Optional.of(habit));

            HabitResponse response = habitService.getActiveHabit(MEMBER_ID, GROUP_ID);

            assertThat(response.getId()).isEqualTo(HABIT_ID);
            assertThat(response.getStatus()).isEqualTo(HabitStatus.ACTIVE);
        }

        @Test
        @DisplayName("활성 습관이 없으면 IllegalArgumentException이 발생한다")
        void 활성_습관이_없으면_예외() {
            given(habitRepository.findByGroupMember_Member_IdAndGroupMember_Group_IdAndStatus(
                MEMBER_ID, GROUP_ID, HabitStatus.ACTIVE)).willReturn(Optional.empty());

            assertThatThrownBy(() -> habitService.getActiveHabit(MEMBER_ID, GROUP_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("현재 진행 중인 습관이 없습니다.");
        }

        @Test
        @DisplayName("실패한 습관 목록은 저장소가 돌려준 순서(최신순)를 그대로 유지한다")
        void 실패한_습관_목록_조회() {
            GroupMember groupMember = groupMember(activeGroup(), GroupMemberRole.MEMBER);
            Habit newer = habit(groupMember);
            ReflectionTestUtils.setField(newer, "id", 2L);
            Habit older = habit(groupMember);
            ReflectionTestUtils.setField(older, "id", 1L);
            given(habitRepository.findAllByGroupMember_Member_IdAndGroupMember_Group_IdAndStatusOrderByCreateDateDesc(
                MEMBER_ID, GROUP_ID, HabitStatus.FAILED)).willReturn(List.of(newer, older));

            List<HabitResponse> responses = habitService.getFailedHabits(MEMBER_ID, GROUP_ID);

            assertThat(responses).extracting(HabitResponse::getId).containsExactly(2L, 1L);
        }

        @Test
        @DisplayName("실패한 습관이 없으면 빈 목록을 반환한다")
        void 실패한_습관이_없으면_빈_목록() {
            given(habitRepository.findAllByGroupMember_Member_IdAndGroupMember_Group_IdAndStatusOrderByCreateDateDesc(
                MEMBER_ID, GROUP_ID, HabitStatus.FAILED)).willReturn(List.of());

            assertThat(habitService.getFailedHabits(MEMBER_ID, GROUP_ID)).isEmpty();
        }

        @Test
        @DisplayName("같은 그룹 멤버는 습관 상세를 조회할 수 있다")
        void 상세_조회_성공() {
            Habit habit = habit(groupMember(activeGroup(), GroupMemberRole.MEMBER));
            given(habitRepository.findById(HABIT_ID)).willReturn(Optional.of(habit));
            given(groupMemberRepository.existsByGroupIdAndMemberId(GROUP_ID, MEMBER_ID)).willReturn(true);

            assertThat(habitService.getHabit(MEMBER_ID, HABIT_ID).getId()).isEqualTo(HABIT_ID);
        }

        @Test
        @DisplayName("존재하지 않는 습관이면 EntityNotFoundException이 발생한다")
        void 상세_조회_없는_습관_예외() {
            given(habitRepository.findById(HABIT_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> habitService.getHabit(MEMBER_ID, HABIT_ID))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("존재하지 않는 습관입니다.");
        }

        @Test
        @DisplayName("다른 그룹의 습관이면 ForbiddenException이 발생한다")
        void 상세_조회_다른_그룹_예외() {
            Habit habit = habit(groupMember(activeGroup(), GroupMemberRole.MEMBER));
            given(habitRepository.findById(HABIT_ID)).willReturn(Optional.of(habit));
            given(groupMemberRepository.existsByGroupIdAndMemberId(GROUP_ID, MEMBER_ID)).willReturn(false);

            assertThatThrownBy(() -> habitService.getHabit(MEMBER_ID, HABIT_ID))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("해당 그룹의 습관만 조회할 수 있습니다.");
        }
    }

    @Nested
    @DisplayName("그룹 주간 판정(checkWeeklyFailureForGroup)")
    class CheckWeeklyFailure {

        @Test
        @DisplayName("그룹 멤버가 아니면 IllegalArgumentException이 발생한다")
        void 그룹_멤버가_아니면_예외() {
            given(groupMemberRepository.findByGroupIdAndMemberId(GROUP_ID, MEMBER_ID))
                .willReturn(Optional.empty());

            assertThatThrownBy(() -> habitService.checkWeeklyFailureForGroup(MEMBER_ID, GROUP_ID))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("방장이 아니면 ForbiddenException이 발생하고 습관을 조회하지 않는다")
        void 방장이_아니면_예외() {
            given(groupMemberRepository.findByGroupIdAndMemberId(GROUP_ID, MEMBER_ID))
                .willReturn(Optional.of(groupMember(activeGroup(), GroupMemberRole.MEMBER)));

            assertThatThrownBy(() -> habitService.checkWeeklyFailureForGroup(MEMBER_ID, GROUP_ID))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("주간 판정은 방장만 실행할 수 있습니다.");
            verify(habitRepository, never()).findAllByGroupMember_Group_IdAndStatus(anyLong(), any(HabitStatus.class));
        }

        @Test
        @DisplayName("종료된 그룹이면 BusinessRuleException이 발생하고 습관을 조회하지 않는다")
        void 종료된_그룹이면_예외() {
            given(groupMemberRepository.findByGroupIdAndMemberId(GROUP_ID, MEMBER_ID))
                .willReturn(Optional.of(groupMember(group(GroupStatus.FINISH, null), GroupMemberRole.OWNER)));

            assertThatThrownBy(() -> habitService.checkWeeklyFailureForGroup(MEMBER_ID, GROUP_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("이미 종료된 그룹은 주간 판정을 실행할 수 없습니다.");
            verify(habitRepository, never()).findAllByGroupMember_Group_IdAndStatus(anyLong(), any(HabitStatus.class));
        }

        @Test
        @DisplayName("방장이 호출하면 그룹의 활성 습관마다 판정 결과를 반환한다")
        void 판정_결과_반환() {
            Group group = activeGroup();
            GroupMember owner = groupMember(group, GroupMemberRole.OWNER);
            // 가입 직후라 1주가 지나지 않은 습관은 TOO_EARLY
            ReflectionTestUtils.setField(owner, "createDate", LocalDateTime.now());
            Habit habit = habit(owner);
            given(groupMemberRepository.findByGroupIdAndMemberId(GROUP_ID, MEMBER_ID))
                .willReturn(Optional.of(owner));
            given(habitRepository.findAllByGroupMember_Group_IdAndStatus(GROUP_ID, HabitStatus.ACTIVE))
                .willReturn(List.of(habit));

            List<HabitWeeklyCheckResponse> results = habitService.checkWeeklyFailureForGroup(MEMBER_ID, GROUP_ID);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getHabitId()).isEqualTo(HABIT_ID);
            assertThat(results.get(0).getResult()).isEqualTo(WeeklyCheckResult.TOO_EARLY);
            assertThat(results.get(0).getHabitStatus()).isEqualTo(HabitStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("스케줄러(autoCheckAllActiveHabits)")
    class AutoCheck {

        @Test
        @DisplayName("진행 중인 그룹의 활성 습관만 조회해서 판정한다 (종료 그룹 제외 쿼리 사용)")
        void 종료_그룹_제외_쿼리를_사용한다() {
            given(habitRepository.findAllByStatusInProgressGroup(HabitStatus.ACTIVE)).willReturn(List.of());

            habitService.autoCheckAllActiveHabits();

            verify(habitRepository).findAllByStatusInProgressGroup(HabitStatus.ACTIVE);
            verify(habitRepository, never()).findAllByGroupMember_Group_IdAndStatus(anyLong(), any(HabitStatus.class));
        }

        @Test
        @DisplayName("진행 중인 그룹에서 승인 횟수가 모자라면 습관이 실패 처리되고 벌칙이 생성된다 (대조군)")
        void 진행중인_그룹은_판정된다() {
            Habit habit = overdueHabit(activeGroup());
            given(habitRepository.findAllByStatusInProgressGroup(HabitStatus.ACTIVE)).willReturn(List.of(habit));

            habitService.autoCheckAllActiveHabits();

            assertThat(habit.getStatus()).isEqualTo(HabitStatus.FAILED);
            verify(penaltyVerifyService).createPenaltyVerify(habit);
        }

        @Test
        @DisplayName("마감일은 지났지만 아직 FINISH 처리 전인 그룹의 습관은 판정하지 않는다")
        void 마감일이_지난_그룹은_판정하지_않는다() {
            Habit habit = overdueHabit(group(GroupStatus.ACTIVE, LocalDate.now().minusDays(1)));
            given(habitRepository.findAllByStatusInProgressGroup(HabitStatus.ACTIVE)).willReturn(List.of(habit));

            habitService.autoCheckAllActiveHabits();

            assertThat(habit.getStatus()).isEqualTo(HabitStatus.ACTIVE);
            verify(penaltyVerifyService, never()).createPenaltyVerify(any(Habit.class));
        }
    }
}
