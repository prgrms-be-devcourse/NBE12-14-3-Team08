package com.back.domain.member.service;

import com.back.domain.group.entity.Group;
import com.back.domain.group.entity.GroupStatus;
import com.back.domain.groupMember.entity.GroupMember;
import com.back.domain.groupMember.entity.GroupMemberRole;
import com.back.domain.groupMember.entity.GroupMemberStatus;
import com.back.domain.groupMember.repository.GroupMemberRepository;
import com.back.domain.member.dto.CreateMemberRequest;
import com.back.domain.member.dto.LoginRequest;
import com.back.domain.member.dto.MemberResponse;
import com.back.domain.member.dto.UpdateMemberRequest;
import com.back.domain.member.entity.Member;
import com.back.domain.member.entity.MemberStatus;
import com.back.domain.member.repository.MemberRepository;
import com.back.global.exception.UnauthorizedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private AuthTokenService authTokenService;

    @InjectMocks
    private MemberService memberService;

    @Test
    @DisplayName("회원가입 성공")
    void joinSuccess() {
        // given
        CreateMemberRequest request = new CreateMemberRequest(
                "habit_user",
                "user@example.com",
                "password123"
        );

        when(memberRepository.existsByUsername(request.getUsername()))
                .thenReturn(false);

        when(passwordEncoder.encode(request.getPassword()))
                .thenReturn("encoded-password");

        when(memberRepository.save(any(Member.class)))
                .thenAnswer(invocation -> {
                    Member member = invocation.getArgument(0);
                    ReflectionTestUtils.setField(member, "id", 1L);
                    return member;
                });

        when(authTokenService.createRefreshToken(1L))
                .thenReturn("refresh-token");

        // when
        MemberResponse response = memberService.join(request);

        // then
        assertThat(response.getMemberId()).isEqualTo(1L);
        assertThat(response.getNickname()).isEqualTo("habit_user");
        assertThat(response.getUsername()).isEqualTo("user@example.com");

        verify(passwordEncoder).encode("password123");
        verify(authTokenService).createRefreshToken(1L);
    }

    @Test
    @DisplayName("중복 아이디 회원가입 실패")
    void joinFailWhenUsernameDuplicated() {
        // given
        CreateMemberRequest request = new CreateMemberRequest(
                "habit_user",
                "user@example.com",
                "password123"
        );

        when(memberRepository.existsByUsername(request.getUsername()))
                .thenReturn(true);

        // when & then
        assertThatThrownBy(() -> memberService.join(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이미 사용 중인 아이디입니다.");

        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    @DisplayName("로그인 성공")
    void authenticateSuccess() {
        // given
        LoginRequest request = new LoginRequest(
                "user@example.com",
                "password123"
        );

        Member member = createMember(
                1L,
                "habit_user",
                "user@example.com",
                "encoded-password"
        );

        when(memberRepository.findByUsername(request.getUsername()))
                .thenReturn(Optional.of(member));

        when(passwordEncoder.matches(
                request.getPassword(),
                member.getPassword()
        )).thenReturn(true);

        // when
        Long memberId = memberService.authenticate(request);

        // then
        assertThat(memberId).isEqualTo(1L);
    }

    @Test
    @DisplayName("존재하지 않는 아이디 로그인 실패")
    void authenticateFailWhenUsernameNotFound() {
        // given
        LoginRequest request = new LoginRequest(
                "none@example.com",
                "password123"
        );

        when(memberRepository.findByUsername(request.getUsername()))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> memberService.authenticate(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("아이디 또는 비밀번호가 일치하지 않습니다.");
    }

    @Test
    @DisplayName("비밀번호 불일치 로그인 실패")
    void authenticateFailWhenPasswordWrong() {
        // given
        LoginRequest request = new LoginRequest(
                "user@example.com",
                "wrong-password"
        );

        Member member = createMember(
                1L,
                "habit_user",
                "user@example.com",
                "encoded-password"
        );

        when(memberRepository.findByUsername(request.getUsername()))
                .thenReturn(Optional.of(member));

        when(passwordEncoder.matches(
                request.getPassword(),
                member.getPassword()
        )).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> memberService.authenticate(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("아이디 또는 비밀번호가 일치하지 않습니다.");
    }

    @Test
    @DisplayName("회원 정보 조회 성공")
    void getMemberSuccess() {
        // given
        Member member = createMember(
                1L,
                "habit_user",
                "user@example.com",
                "encoded-password"
        );

        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));

        // when
        MemberResponse response = memberService.getMember(1L);

        // then
        assertThat(response.getMemberId()).isEqualTo(1L);
        assertThat(response.getNickname()).isEqualTo("habit_user");
        assertThat(response.getUsername()).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("회원 정보 수정 성공")
    void updateMemberSuccess() {
        // given
        Member member = createMember(
                1L,
                "habit_user",
                "user@example.com",
                "old-encoded-password"
        );

        UpdateMemberRequest request = new UpdateMemberRequest(
                "updated_user",
                "oldPassword123",
                "newPassword123"
        );

        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));

        when(passwordEncoder.matches("oldPassword123", "old-encoded-password"))
                .thenReturn(true);

        when(passwordEncoder.encode("newPassword123"))
                .thenReturn("new-encoded-password");

        // when
        MemberResponse response =
                memberService.updateMember(1L, request);

        // then
        assertThat(response.getNickname()).isEqualTo("updated_user");
        assertThat(member.getPassword())
                .isEqualTo("new-encoded-password");
    }

    @Test
    @DisplayName("기존 비밀번호가 일치하지 않으면 회원 정보 수정 실패")
    void updateMemberFailWhenCurrentPasswordDoesNotMatch() {
        Member member = createMember(
                1L,
                "habit_user",
                "user@example.com",
                "old-encoded-password"
        );

        UpdateMemberRequest request = new UpdateMemberRequest(
                "updated_user",
                "wrongPassword",
                "newPassword123"
        );

        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("wrongPassword", "old-encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> memberService.updateMember(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("기존 비밀번호가 일치하지 않습니다.");

        assertThat(member.getPassword()).isEqualTo("old-encoded-password");
        verify(passwordEncoder, never()).encode("newPassword123");
    }

    @Test
    @DisplayName("새 비밀번호 없이 닉네임만 수정 성공")
    void updateNicknameWithoutPassword() {
        Member member = createMember(
                1L,
                "habit_user",
                "user@example.com",
                "old-encoded-password"
        );

        UpdateMemberRequest request = new UpdateMemberRequest(
                "updated_user",
                null,
                null
        );

        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        MemberResponse response = memberService.updateMember(1L, request);

        assertThat(response.getNickname()).isEqualTo("updated_user");
        assertThat(member.getPassword()).isEqualTo("old-encoded-password");
        verify(passwordEncoder, never()).matches(any(), any());
        verify(passwordEncoder, never()).encode(any(CharSequence.class));
    }

    @Test
    @DisplayName("참여 중인 방이 없으면 회원을 WITHDRAWN으로 변경")
    void deleteMemberSuccess() {
        Member member = createMember(1L, "habit_user", "user@example.com", "encoded-password");
        member.updateRefreshToken("refresh-token");

        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(groupMemberRepository.findAllByMemberId(1L)).thenReturn(List.of());

        memberService.deleteMember(1L);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(member.getRefreshToken()).isNull();
        verify(memberRepository, never()).delete(any(Member.class));
    }

    @Test
    @DisplayName("진행 중인 방에 참여 중이면 회원 탈퇴 실패")
    void deleteMemberFailWhenParticipatingInGroup() {
        Member member = createMember(1L, "habit_user", "user@example.com", "encoded-password");
        Group group = Group.builder()
                .title("테스트 방")
                .deadline(LocalDate.now().plusDays(1))
                .status(GroupStatus.ACTIVE)
                .build();
        GroupMember groupMember = GroupMember.create(group, member, GroupMemberRole.MEMBER);

        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(groupMemberRepository.findAllByMemberId(1L))
                .thenReturn(List.of(groupMember));

        assertThatThrownBy(() -> memberService.deleteMember(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("진행 중인 방이 있어 회원 탈퇴를 할 수 없습니다.");

        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(groupMember.getStatus()).isEqualTo(GroupMemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("종료된 방의 기록은 LEFT로 남기고 회원 탈퇴")
    void deleteMemberLeavesFinishedGroup() {
        Member member = createMember(1L, "habit_user", "user@example.com", "encoded-password");
        member.updateRefreshToken("refresh-token");

        Group group = Group.builder()
                .title("종료된 방")
                .deadline(LocalDate.now().minusDays(1))
                .status(GroupStatus.FINISH)
                .build();
        GroupMember groupMember = GroupMember.create(group, member, GroupMemberRole.MEMBER);

        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(groupMemberRepository.findAllByMemberId(1L))
                .thenReturn(List.of(groupMember));

        memberService.deleteMember(1L);

        assertThat(groupMember.getStatus()).isEqualTo(GroupMemberStatus.LEFT);
        assertThat(member.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(member.getRefreshToken()).isNull();
        verify(memberRepository, never()).delete(any(Member.class));
    }

    @Test
    @DisplayName("DB에 저장된 리프레시 토큰 조회 성공")
    void getRefreshTokenSuccess() {
        // given
        Member member = createMember(
                1L,
                "habit_user",
                "user@example.com",
                "encoded-password"
        );

        member.updateRefreshToken("refresh-token");

        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));

        // when
        String refreshToken =
                memberService.getRefreshToken(1L);

        // then
        assertThat(refreshToken).isEqualTo("refresh-token");
    }

    @Test
    @DisplayName("리프레시 토큰과 DB 저장값 비교")
    void matchesRefreshToken() {
        // given
        Member member = createMember(
                1L,
                "habit_user",
                "user@example.com",
                "encoded-password"
        );

        member.updateRefreshToken("refresh-token");

        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(member));

        // when & then
        assertThat(
                memberService.matchesRefreshToken(
                        1L,
                        "refresh-token"
                )
        ).isTrue();

        assertThat(
                memberService.matchesRefreshToken(
                        1L,
                        "wrong-token"
                )
        ).isFalse();
    }

    private Member createMember(
            Long id,
            String nickname,
            String username,
            String encodedPassword
    ) {
        Member member = Member.create(
                nickname,
                username,
                encodedPassword
        );

        ReflectionTestUtils.setField(member, "id", id);

        return member;
    }
}
