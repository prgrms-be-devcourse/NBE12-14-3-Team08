package com.back.domain.member.service;

import com.back.domain.group.entity.GroupStatus;
import com.back.domain.groupMember.entity.GroupMember;
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
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.back.domain.group.entity.Group;
import java.util.NoSuchElementException;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final GroupMemberRepository groupMemberRepository;
    private final AuthTokenService authTokenService;

    @Transactional
    public MemberResponse join(CreateMemberRequest request) {
        if (memberRepository.existsByUsername(request.getUsername())) {
            throw new IllegalStateException("이미 사용 중인 아이디입니다.");
        }

        String encodedPassword = passwordEncoder.encode(request.getPassword());

        Member member = Member.create(
                request.getNickname(),
                request.getUsername(),
                encodedPassword
        );

        Member savedMember = memberRepository.save(member);

        String refreshToken =
                authTokenService.createRefreshToken(
                        savedMember.getId()
                );

        savedMember.updateRefreshToken(refreshToken);

        return MemberResponse.from(savedMember);
    }

    public Long authenticate(LoginRequest request) {
        Member member = memberRepository.findByUsername(request.getUsername())
                .orElseThrow(
                        () -> new UnauthorizedException(
                                "아이디 또는 비밀번호가 일치하지 않습니다."
                        )
                );

        if (!passwordEncoder.matches(
                request.getPassword(),
                member.getPassword()
        )) {
            throw new UnauthorizedException(
                    "아이디 또는 비밀번호가 일치하지 않습니다."
            );
        }

        if (member.getStatus() == MemberStatus.WITHDRAWN) {
            throw new UnauthorizedException("탈퇴한 회원은 로그인할 수 없습니다.");
        }

        return member.getId();
    }

    public MemberResponse getMember(Long memberId) {
        Member member = findMember(memberId);
        return MemberResponse.from(member);
    }

    @Transactional
    public MemberResponse updateMember(Long memberId, UpdateMemberRequest request) {
        Member member = findMember(memberId);

        if (request.getNickname() != null && !request.getNickname().isBlank()) {
            member.updateNickname(request.getNickname());
        }

        if (request.getNewPassword() != null && !request.getNewPassword().isBlank()) {
            if (request.getCurrentPassword() == null || request.getCurrentPassword().isBlank()) {
                throw new IllegalArgumentException("기존 비밀번호를 입력해주세요.");
            }

            if (!passwordEncoder.matches(request.getCurrentPassword(), member.getPassword())) {
                throw new IllegalArgumentException("기존 비밀번호가 일치하지 않습니다.");
            }

            String encodedPassword = passwordEncoder.encode(request.getNewPassword());
            member.updatePassword(encodedPassword);
        }

        return MemberResponse.from(member);
    }

    // 로그인 시 DB에 저장된 리프레시 토큰 조회
    public String getRefreshToken(Long memberId) {
        Member member = findMember(memberId);

        String refreshToken = member.getRefreshToken();

        if (refreshToken == null
                || refreshToken.isBlank()) {
            throw new UnauthorizedException(
                    "저장된 리프레시 토큰이 없습니다."
            );
        }

        return refreshToken;
    }

    // 재발급 요청의 리프레시 토큰과 DB 저장값 비교
    public boolean matchesRefreshToken(
            Long memberId,
            String refreshToken
    ) {
        Member member = findMember(memberId);

        return member.getStatus() == MemberStatus.ACTIVE && member.matchesRefreshToken(refreshToken);
    }

    @Transactional
    public void deleteMember(Long memberId) {
        Member member = findMember(memberId);

        if (member.getStatus() == MemberStatus.WITHDRAWN) {
            throw new IllegalStateException("이미 탈퇴한 회원입니다.");
        }

        List<GroupMember> groupMembers =
                groupMemberRepository.findAllByMemberId(memberId);
        LocalDate today = LocalDate.now();

        boolean hasActiveGroup = groupMembers.stream()
                .anyMatch(gm -> gm.getStatus() == GroupMemberStatus.ACTIVE
                        && isActiveGroup(gm.getGroup(), today));

        if (hasActiveGroup) {
            throw new IllegalStateException(
                    "진행 중인 방이 있어 회원 탈퇴를 할 수 없습니다."
            );
        }

        groupMembers.stream()
                .filter(gm -> !isActiveGroup(gm.getGroup(), today))
                .forEach(GroupMember::leave);

        member.withdraw();
    }

    private boolean isActiveGroup(Group group, LocalDate today) {
        return group.getStatus() == GroupStatus.ACTIVE
                && (group.getDeadline() == null
                || !today.isAfter(group.getDeadline()));
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 회원입니다."));
    }
}
