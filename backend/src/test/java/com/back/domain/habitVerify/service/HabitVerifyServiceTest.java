package com.back.domain.habitVerify.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.back.domain.group.entity.Group;
import com.back.domain.groupMember.entity.GroupMember;
import com.back.domain.groupMember.entity.GroupMemberRole;
import com.back.domain.groupMember.repository.GroupMemberRepository;
import com.back.domain.habit.entity.Habit;
import com.back.domain.habit.repository.HabitRepository;
import com.back.domain.habitVerify.dto.BulkActionRequest;
import com.back.domain.habitVerify.dto.HabitVerifyResponse;
import com.back.domain.habitVerify.dto.HabitVerifySummaryResponse;
import com.back.domain.habitVerify.entity.HabitVerify;
import com.back.domain.habitVerify.entity.HabitVerifyStatus;
import com.back.domain.habitVerify.repository.HabitVerifyRepository;
import com.back.domain.member.entity.Member;
import com.back.global.exception.BusinessRuleException;
import com.back.global.exception.EntityNotFoundException;
import com.back.global.exception.ForbiddenException;
import com.back.global.storage.StorageService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class HabitVerifyServiceTest {

    private static final Long GROUP_ID = 100L;
    private static final Long OWNER_ID = 1L;
    private static final Long MEMBER_ID = 2L;
    private static final Long OUTSIDER_ID = 9L;

    @Mock
    private HabitRepository habitRepository;
    @Mock
    private HabitVerifyRepository habitVerifyRepository;
    @Mock
    private StorageService storageService;
    @Mock
    private GroupMemberRepository groupMemberRepository;

    @InjectMocks
    private HabitVerifyService habitVerifyService;

    private Group group;
    private GroupMember ownerGroupMember;
    private GroupMember memberGroupMember;
    private Habit habit;

    @BeforeEach
    void setUp() {
        group = Group.builder()
            .title("아침 기상 챌린지")
            .penalty("커피 1잔 쏘기")
            .memberLimit(10)
            .build();
        ReflectionTestUtils.setField(group, "id", GROUP_ID);

        Member owner = Member.create("방장", "owner", "pass1234");
        ReflectionTestUtils.setField(owner, "id", OWNER_ID);
        ownerGroupMember = GroupMember.create(group, owner, GroupMemberRole.OWNER);

        Member member = Member.create("일반회원", "member", "pass1234");
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        memberGroupMember = GroupMember.create(group, member, GroupMemberRole.MEMBER);

        habit = Habit.create(memberGroupMember, "기상 후 스트레칭", "10분 스트레칭", 3);
        ReflectionTestUtils.setField(habit, "id", 50L);
    }

    private HabitVerify verifyIn(Long id, HabitVerifyStatus status) {
        HabitVerify habitVerify = HabitVerify.create(habit, LocalDate.of(2026, 10, 21), "스트레칭 완료", "https://example.com/a.png");
        ReflectionTestUtils.setField(habitVerify, "id", id);
        ReflectionTestUtils.setField(habitVerify, "status", status);
        return habitVerify;
    }

    private void givenGroupMember(Long memberId, GroupMember groupMember) {
        given(groupMemberRepository.findByGroupIdAndMemberId(GROUP_ID, memberId)).willReturn(Optional.ofNullable(groupMember));
    }

    @Nested
    @DisplayName("단건 승인(approve)")
    class Approve {

        @Test
        @DisplayName("방장이 검토 대기 인증을 승인하면 APPROVED가 된다")
        void 방장이_승인() {
            HabitVerify habitVerify = verifyIn(70L, HabitVerifyStatus.PENDING);
            given(habitVerifyRepository.findById(70L)).willReturn(Optional.of(habitVerify));
            givenGroupMember(OWNER_ID, ownerGroupMember);

            HabitVerifyResponse response = habitVerifyService.approve(OWNER_ID, 70L);

            assertThat(habitVerify.getStatus()).isEqualTo(HabitVerifyStatus.APPROVED);
            assertThat(response.status()).isEqualTo(HabitVerifyStatus.APPROVED);
        }

        @Test
        @DisplayName("없는 인증이면 404이고 권한 확인까지 가지 않는다")
        void 없는_인증_404() {
            given(habitVerifyRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> habitVerifyService.approve(OWNER_ID, 999L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("존재하지 않는 인증 기록입니다.");

            verify(groupMemberRepository, never()).findByGroupIdAndMemberId(anyLong(), anyLong());
        }

        @Test
        @DisplayName("그룹 멤버가 아니면 403이고 상태는 그대로다")
        void 그룹_멤버가_아니면_403() {
            HabitVerify habitVerify = verifyIn(70L, HabitVerifyStatus.PENDING);
            given(habitVerifyRepository.findById(70L)).willReturn(Optional.of(habitVerify));
            givenGroupMember(OUTSIDER_ID, null);

            assertThatThrownBy(() -> habitVerifyService.approve(OUTSIDER_ID, 70L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("승인/거절 권한이 없습니다.");

            assertThat(habitVerify.getStatus()).isEqualTo(HabitVerifyStatus.PENDING);
        }

        @Test
        @DisplayName("일반 멤버(방장 아님)면 403이고 상태는 그대로다")
        void 일반_멤버면_403() {
            HabitVerify habitVerify = verifyIn(70L, HabitVerifyStatus.PENDING);
            given(habitVerifyRepository.findById(70L)).willReturn(Optional.of(habitVerify));
            givenGroupMember(MEMBER_ID, memberGroupMember);

            assertThatThrownBy(() -> habitVerifyService.approve(MEMBER_ID, 70L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("승인/거절 권한이 없습니다.");

            assertThat(habitVerify.getStatus()).isEqualTo(HabitVerifyStatus.PENDING);
        }

        @Test
        @DisplayName("종료된 그룹이면 400이고 상태는 그대로다")
        void 종료된_그룹이면_400() {
            group.finish();
            HabitVerify habitVerify = verifyIn(70L, HabitVerifyStatus.PENDING);
            given(habitVerifyRepository.findById(70L)).willReturn(Optional.of(habitVerify));
            givenGroupMember(OWNER_ID, ownerGroupMember);

            assertThatThrownBy(() -> habitVerifyService.approve(OWNER_ID, 70L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("이미 종료된 그룹은 인증을 검토할 수 없습니다.");

            assertThat(habitVerify.getStatus()).isEqualTo(HabitVerifyStatus.PENDING);
        }

        @Test
        @DisplayName("이미 처리된 인증이면 409다")
        void 이미_처리된_인증이면_409() {
            HabitVerify habitVerify = verifyIn(70L, HabitVerifyStatus.REJECTED);
            given(habitVerifyRepository.findById(70L)).willReturn(Optional.of(habitVerify));
            givenGroupMember(OWNER_ID, ownerGroupMember);

            assertThatThrownBy(() -> habitVerifyService.approve(OWNER_ID, 70L))
                .isInstanceOf(IllegalStateException.class);

            assertThat(habitVerify.getStatus()).isEqualTo(HabitVerifyStatus.REJECTED);
        }
    }

    @Nested
    @DisplayName("단건 반려(reject)")
    class Reject {

        @Test
        @DisplayName("방장이 검토 대기 인증을 반려하면 REJECTED가 된다")
        void 방장이_반려() {
            HabitVerify habitVerify = verifyIn(70L, HabitVerifyStatus.PENDING);
            given(habitVerifyRepository.findById(70L)).willReturn(Optional.of(habitVerify));
            givenGroupMember(OWNER_ID, ownerGroupMember);

            HabitVerifyResponse response = habitVerifyService.reject(OWNER_ID, 70L);

            assertThat(habitVerify.getStatus()).isEqualTo(HabitVerifyStatus.REJECTED);
            assertThat(response.status()).isEqualTo(HabitVerifyStatus.REJECTED);
        }

        @Test
        @DisplayName("없는 인증이면 404이고 권한 확인까지 가지 않는다")
        void 없는_인증_404() {
            given(habitVerifyRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> habitVerifyService.reject(OWNER_ID, 999L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("존재하지 않는 인증 기록입니다.");

            verify(groupMemberRepository, never()).findByGroupIdAndMemberId(anyLong(), anyLong());
        }

        @Test
        @DisplayName("일반 멤버(방장 아님)면 403이고 상태는 그대로다")
        void 일반_멤버면_403() {
            HabitVerify habitVerify = verifyIn(70L, HabitVerifyStatus.PENDING);
            given(habitVerifyRepository.findById(70L)).willReturn(Optional.of(habitVerify));
            givenGroupMember(MEMBER_ID, memberGroupMember);

            assertThatThrownBy(() -> habitVerifyService.reject(MEMBER_ID, 70L))
                .isInstanceOf(AccessDeniedException.class);

            assertThat(habitVerify.getStatus()).isEqualTo(HabitVerifyStatus.PENDING);
        }

        @Test
        @DisplayName("이미 처리된 인증이면 409다")
        void 이미_처리된_인증이면_409() {
            HabitVerify habitVerify = verifyIn(70L, HabitVerifyStatus.APPROVED);
            given(habitVerifyRepository.findById(70L)).willReturn(Optional.of(habitVerify));
            givenGroupMember(OWNER_ID, ownerGroupMember);

            assertThatThrownBy(() -> habitVerifyService.reject(OWNER_ID, 70L))
                .isInstanceOf(IllegalStateException.class);

            assertThat(habitVerify.getStatus()).isEqualTo(HabitVerifyStatus.APPROVED);
        }
    }

    @Nested
    @DisplayName("일괄 승인·반려(bulkApprove, bulkReject)")
    class Bulk {

        @Test
        @DisplayName("방장이 일괄 승인하면 가져온 인증이 모두 APPROVED가 된다")
        void 일괄_승인() {
            HabitVerify first = verifyIn(70L, HabitVerifyStatus.PENDING);
            HabitVerify second = verifyIn(71L, HabitVerifyStatus.PENDING);
            givenGroupMember(OWNER_ID, ownerGroupMember);
            given(habitVerifyRepository.findAllByIdInAndGroupId(List.of(70L, 71L), GROUP_ID)).willReturn(List.of(first, second));

            List<HabitVerifyResponse> responses =
                habitVerifyService.bulkApprove(OWNER_ID, GROUP_ID, new BulkActionRequest(List.of(70L, 71L)));

            assertThat(responses).extracting(HabitVerifyResponse::status)
                .containsExactly(HabitVerifyStatus.APPROVED, HabitVerifyStatus.APPROVED);
        }

        @Test
        @DisplayName("방장이 일괄 반려하면 가져온 인증이 모두 REJECTED가 된다")
        void 일괄_반려() {
            HabitVerify first = verifyIn(70L, HabitVerifyStatus.PENDING);
            HabitVerify second = verifyIn(71L, HabitVerifyStatus.PENDING);
            givenGroupMember(OWNER_ID, ownerGroupMember);
            given(habitVerifyRepository.findAllByIdInAndGroupId(List.of(70L, 71L), GROUP_ID)).willReturn(List.of(first, second));

            List<HabitVerifyResponse> responses =
                habitVerifyService.bulkReject(OWNER_ID, GROUP_ID, new BulkActionRequest(List.of(70L, 71L)));

            assertThat(responses).extracting(HabitVerifyResponse::status)
                .containsExactly(HabitVerifyStatus.REJECTED, HabitVerifyStatus.REJECTED);
        }

        @Test
        @DisplayName("일반 멤버(방장 아님)면 403이고 대상 인증을 조회하지 않는다")
        void 일반_멤버면_403() {
            givenGroupMember(MEMBER_ID, memberGroupMember);

            assertThatThrownBy(() -> habitVerifyService.bulkApprove(MEMBER_ID, GROUP_ID, new BulkActionRequest(List.of(70L))))
                .isInstanceOf(AccessDeniedException.class);

            verify(habitVerifyRepository, never()).findAllByIdInAndGroupId(any(), any());
        }

        @Test
        @DisplayName("종료된 그룹이면 400이고 대상 인증을 조회하지 않는다")
        void 종료된_그룹이면_400() {
            group.finish();
            givenGroupMember(OWNER_ID, ownerGroupMember);

            assertThatThrownBy(() -> habitVerifyService.bulkReject(OWNER_ID, GROUP_ID, new BulkActionRequest(List.of(70L))))
                .isInstanceOf(BusinessRuleException.class);

            verify(habitVerifyRepository, never()).findAllByIdInAndGroupId(any(), any());
        }

        @Test
        @DisplayName("하나라도 검토 대기가 아니면 409다 (실제 롤백은 HabitVerifyAdminControllerTest에서 확인)")
        void 하나라도_처리된_인증이면_409() {
            HabitVerify pending = verifyIn(70L, HabitVerifyStatus.PENDING);
            HabitVerify approved = verifyIn(71L, HabitVerifyStatus.APPROVED);
            givenGroupMember(OWNER_ID, ownerGroupMember);
            given(habitVerifyRepository.findAllByIdInAndGroupId(List.of(70L, 71L), GROUP_ID)).willReturn(List.of(pending, approved));

            assertThatThrownBy(() -> habitVerifyService.bulkApprove(OWNER_ID, GROUP_ID, new BulkActionRequest(List.of(70L, 71L))))
                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("조회되지 않은 id(없는 id, 다른 그룹 id)는 오류 없이 건너뛴다 (현재 동작, #186에서 팀 확인 필요)")
        void 조회되지_않은_id는_건너뛴다() {
            HabitVerify pending = verifyIn(70L, HabitVerifyStatus.PENDING);
            givenGroupMember(OWNER_ID, ownerGroupMember);
            given(habitVerifyRepository.findAllByIdInAndGroupId(List.of(70L, 999L), GROUP_ID)).willReturn(List.of(pending));

            List<HabitVerifyResponse> responses =
                habitVerifyService.bulkApprove(OWNER_ID, GROUP_ID, new BulkActionRequest(List.of(70L, 999L)));

            assertThat(responses).extracting(HabitVerifyResponse::id).containsExactly(70L);
        }
    }

    @Nested
    @DisplayName("검토 대기 목록(getPendingByGroup)")
    class Pending {

        @Test
        @DisplayName("방장은 그룹의 검토 대기 목록을 받는다")
        void 방장이_조회() {
            givenGroupMember(OWNER_ID, ownerGroupMember);
            given(habitVerifyRepository.findPendingByGroupId(GROUP_ID)).willReturn(List.of(verifyIn(70L, HabitVerifyStatus.PENDING)));

            List<HabitVerifySummaryResponse> responses = habitVerifyService.getPendingByGroup(OWNER_ID, GROUP_ID);

            assertThat(responses).singleElement().satisfies(response -> {
                assertThat(response.id()).isEqualTo(70L);
                assertThat(response.habitTitle()).isEqualTo("기상 후 스트레칭");
                assertThat(response.memberNickname()).isEqualTo("일반회원");
            });
        }

        @Test
        @DisplayName("그룹 멤버가 아니면 403이고 목록을 조회하지 않는다")
        void 그룹_멤버가_아니면_403() {
            givenGroupMember(OUTSIDER_ID, null);

            assertThatThrownBy(() -> habitVerifyService.getPendingByGroup(OUTSIDER_ID, GROUP_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("해당 그룹의 멤버가 아닙니다.");

            verify(habitVerifyRepository, never()).findPendingByGroupId(anyLong());
        }

        @Test
        @DisplayName("일반 멤버(방장 아님)면 403이고 목록을 조회하지 않는다")
        void 일반_멤버면_403() {
            givenGroupMember(MEMBER_ID, memberGroupMember);

            assertThatThrownBy(() -> habitVerifyService.getPendingByGroup(MEMBER_ID, GROUP_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("방장만 대기 목록을 조회할 수 있습니다.");

            verify(habitVerifyRepository, never()).findPendingByGroupId(anyLong());
        }
    }

    @Nested
    @DisplayName("습관 인증 목록(findAll)")
    class FindAll {

        @Test
        @DisplayName("같은 그룹 멤버는 그 습관의 인증 목록을 받는다")
        void 같은_그룹_멤버가_조회() {
            given(habitRepository.findById(50L)).willReturn(Optional.of(habit));
            given(groupMemberRepository.existsByGroupIdAndMemberId(GROUP_ID, OWNER_ID)).willReturn(true);
            given(habitVerifyRepository.findAllByHabitIdOrderByVerifyDateDesc(50L))
                .willReturn(List.of(verifyIn(71L, HabitVerifyStatus.PENDING), verifyIn(70L, HabitVerifyStatus.APPROVED)));

            List<HabitVerifyResponse> responses = habitVerifyService.findAll(50L, OWNER_ID);

            assertThat(responses).extracting(HabitVerifyResponse::id).containsExactly(71L, 70L);
        }

        @Test
        @DisplayName("없는 습관이면 404다")
        void 없는_습관이면_404() {
            given(habitRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> habitVerifyService.findAll(999L, OWNER_ID))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("존재하지 않는 습관입니다.");
        }

        @Test
        @DisplayName("같은 그룹 멤버가 아니면 403이고 목록을 조회하지 않는다")
        void 같은_그룹이_아니면_403() {
            given(habitRepository.findById(50L)).willReturn(Optional.of(habit));
            given(groupMemberRepository.existsByGroupIdAndMemberId(GROUP_ID, OUTSIDER_ID)).willReturn(false);

            assertThatThrownBy(() -> habitVerifyService.findAll(50L, OUTSIDER_ID))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("같은 그룹의 멤버만 조회할 수 있습니다.");

            verify(habitVerifyRepository, never()).findAllByHabitIdOrderByVerifyDateDesc(anyLong());
        }
    }
}
