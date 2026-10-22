package com.back.domain.habitVerify.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.back.domain.group.entity.Group;
import com.back.domain.groupMember.entity.GroupMember;
import com.back.domain.groupMember.entity.GroupMemberRole;
import com.back.domain.groupMember.repository.GroupMemberRepository;
import com.back.domain.habit.entity.Habit;
import com.back.domain.habit.repository.HabitRepository;
import com.back.domain.habitVerify.dto.HabitVerifyRequest;
import com.back.domain.habitVerify.entity.HabitVerify;
import com.back.domain.habitVerify.entity.HabitVerifyStatus;
import com.back.domain.habitVerify.repository.HabitVerifyRepository;
import com.back.domain.member.entity.Member;
import com.back.global.exception.ForbiddenException;
import com.back.global.storage.StorageService;
import com.back.global.storage.VerificationImageType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class HabitVerifyServiceSubmitTest {

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

    private Habit habit;

    @BeforeEach
    void setUp() {
        Member member = Member.create("일반회원", "member", "pass1234");
        ReflectionTestUtils.setField(member, "id", 2L);

        Group group = Group.builder()
            .title("아침 기상 챌린지")
            .penalty("커피 1잔 쏘기")
            .memberLimit(10)
            .build();
        ReflectionTestUtils.setField(group, "createDate", LocalDateTime.now());

        GroupMember groupMember = GroupMember.create(group, member, GroupMemberRole.MEMBER);
        habit = Habit.create(groupMember, "기상 후 스트레칭", "10분 스트레칭", 3);
        ReflectionTestUtils.setField(habit, "id", 50L);
    }

    @Nested
    @DisplayName("습관 인증 등록(create)")
    class Create {

        @Test
        @DisplayName("남의 습관이면 ForbiddenException(403), 사진 검사는 하지 않는다")
        void 남의_습관_403() {
            given(habitRepository.findByIdAndGroupMember_Member_Id(50L, 999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> habitVerifyService.create(50L,
                new HabitVerifyRequest("인증", "https://storage/a.png"), 999L))
                .isInstanceOf(ForbiddenException.class);

            verify(storageService, never()).validateImageUrl(any(), anyLong(), anyLong(), anyString(), any());
        }

        @Test
        @DisplayName("사진이 있으면 이 회원·이 습관 기준으로 검사한 뒤 저장한다")
        void 사진_검사_후_저장() {
            given(habitRepository.findByIdAndGroupMember_Member_Id(50L, 2L)).willReturn(Optional.of(habit));
            given(habitVerifyRepository.existsByHabitIdAndVerifyDate(50L, LocalDate.now())).willReturn(false);
            given(habitVerifyRepository.save(any(HabitVerify.class))).willAnswer(invocation -> invocation.getArgument(0));

            habitVerifyService.create(50L, new HabitVerifyRequest("인증", "https://storage/a.png"), 2L);

            verify(storageService).validateImageUrl(VerificationImageType.HABIT, 2L, 50L, "https://storage/a.png", null);
            verify(habitVerifyRepository).save(any(HabitVerify.class));
        }

        @Test
        @DisplayName("사진 검사에 실패하면 400이고 저장하지 않는다")
        void 사진_검사_실패() {
            given(habitRepository.findByIdAndGroupMember_Member_Id(50L, 2L)).willReturn(Optional.of(habit));
            given(habitVerifyRepository.existsByHabitIdAndVerifyDate(50L, LocalDate.now())).willReturn(false);
            willThrow(new IllegalArgumentException("업로드한 사진만 사용할 수 있습니다."))
                .given(storageService).validateImageUrl(any(), anyLong(), anyLong(), anyString(), any());

            assertThatThrownBy(() -> habitVerifyService.create(50L,
                new HabitVerifyRequest("인증", "https://evil.com/a.png"), 2L))
                .isInstanceOf(IllegalArgumentException.class);

            verify(habitVerifyRepository, never()).save(any());
        }

        @Test
        @DisplayName("포기·판정으로 실패(FAILED)한 습관이면 409이고, 사진 검사와 저장을 하지 않는다")
        void 실패한_습관_409() {
            habit.fail();
            given(habitRepository.findByIdAndGroupMember_Member_Id(50L, 2L)).willReturn(Optional.of(habit));

            assertThatThrownBy(() -> habitVerifyService.create(50L,
                new HabitVerifyRequest("인증", "https://storage/a.png"), 2L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("진행 중인 습관에만 인증을 등록할 수 있습니다. 현재 상태: FAILED");

            verify(storageService, never()).validateImageUrl(any(), anyLong(), anyLong(), anyString(), any());
            verify(habitVerifyRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("습관 인증 재제출(resubmit)")
    class Resubmit {

        private HabitVerify verifyIn(HabitVerifyStatus status) {
            HabitVerify habitVerify = HabitVerify.create(habit, LocalDate.now(), "처음 인증", "https://storage/old.png");
            ReflectionTestUtils.setField(habitVerify, "id", 70L);
            ReflectionTestUtils.setField(habitVerify, "status", status);
            ReflectionTestUtils.setField(habitVerify, "createDate", LocalDateTime.now());
            return habitVerify;
        }

        @Test
        @DisplayName("남의 습관이면 ForbiddenException(403), 사진 검사는 하지 않는다")
        void 남의_습관_403() {
            given(habitRepository.findByIdAndGroupMember_Member_Id(50L, 999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> habitVerifyService.resubmit(50L, 70L,
                new HabitVerifyRequest("수정", "https://storage/a.png"), 999L))
                .isInstanceOf(ForbiddenException.class);

            verify(storageService, never()).validateImageUrl(any(), anyLong(), anyLong(), anyString(), any());
        }

        @Test
        @DisplayName("반려되지 않은 인증이면 사진 검사 전에 409")
        void 반려_아님_409() {
            HabitVerify habitVerify = verifyIn(HabitVerifyStatus.APPROVED);
            given(habitRepository.findByIdAndGroupMember_Member_Id(50L, 2L)).willReturn(Optional.of(habit));
            given(habitVerifyRepository.findByIdAndHabitId(70L, 50L)).willReturn(Optional.of(habitVerify));

            assertThatThrownBy(() -> habitVerifyService.resubmit(50L, 70L,
                new HabitVerifyRequest("수정", "https://storage/a.png"), 2L))
                .isInstanceOf(IllegalStateException.class);

            verify(storageService, never()).validateImageUrl(any(), anyLong(), anyLong(), anyString(), any());
        }

        @Test
        @DisplayName("지금 붙어 있는 사진을 기존 사진으로 넘겨 검사한 뒤 PENDING이 된다")
        void 재제출_성공() {
            HabitVerify habitVerify = verifyIn(HabitVerifyStatus.REJECTED);
            given(habitRepository.findByIdAndGroupMember_Member_Id(50L, 2L)).willReturn(Optional.of(habit));
            given(habitVerifyRepository.findByIdAndHabitId(70L, 50L)).willReturn(Optional.of(habitVerify));

            habitVerifyService.resubmit(50L, 70L, new HabitVerifyRequest("수정", "https://storage/old.png"), 2L);

            verify(storageService).validateImageUrl(VerificationImageType.HABIT, 2L, 50L,
                "https://storage/old.png", "https://storage/old.png");
            assertThat(habitVerify.getStatus()).isEqualTo(HabitVerifyStatus.PENDING);
        }

        @Test
        @DisplayName("사진 검사에 실패하면 400이고 인증 상태는 그대로다")
        void 사진_검사_실패() {
            HabitVerify habitVerify = verifyIn(HabitVerifyStatus.REJECTED);
            given(habitRepository.findByIdAndGroupMember_Member_Id(50L, 2L)).willReturn(Optional.of(habit));
            given(habitVerifyRepository.findByIdAndHabitId(70L, 50L)).willReturn(Optional.of(habitVerify));
            willThrow(new IllegalArgumentException("본인이 업로드한 사진만 사용할 수 있습니다."))
                .given(storageService).validateImageUrl(any(), anyLong(), anyLong(), anyString(), any());

            assertThatThrownBy(() -> habitVerifyService.resubmit(50L, 70L,
                new HabitVerifyRequest("수정", "https://storage/someone-else.png"), 2L))
                .isInstanceOf(IllegalArgumentException.class);

            assertThat(habitVerify.getStatus()).isEqualTo(HabitVerifyStatus.REJECTED);
            assertThat(habitVerify.getImageUrl()).isEqualTo("https://storage/old.png");
        }
    }
}
