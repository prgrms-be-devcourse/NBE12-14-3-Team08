package com.back.domain.member.service

import com.back.domain.group.entity.Group
import com.back.domain.group.entity.GroupStatus
import com.back.domain.groupMember.entity.GroupMemberStatus
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.member.dto.CreateMemberRequest
import com.back.domain.member.dto.LoginRequest
import com.back.domain.member.dto.MemberResponse
import com.back.domain.member.dto.UpdateMemberRequest
import com.back.domain.member.entity.Member
import com.back.domain.member.entity.MemberStatus
import com.back.domain.member.repository.MemberRepository
import com.back.global.exception.UnauthorizedException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class MemberService(
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
    private val groupMemberRepository: GroupMemberRepository,
    private val authTokenService: AuthTokenService,
) {

    @Transactional
    fun join(request: CreateMemberRequest): MemberResponse {
        // 요청 검증을 거치지 않은 직접 호출에서도 값 누락을 같은 메시지로 거절한다.
        val nickname = requireNotNull(request.nickname) { "이름은 필수입니다." }
        val username = requireNotNull(request.username) { "아이디는 필수입니다." }
        val password = requireNotNull(request.password) { "비밀번호는 필수입니다." }

        if (memberRepository.existsByUsername(username)) {
            throw IllegalStateException("이미 사용 중인 아이디입니다.")
        }

        val member = Member.create(nickname, username, encode(password))

        val savedMember = memberRepository.save(member)

        val refreshToken = authTokenService.createRefreshToken(savedMember.id)

        savedMember.updateRefreshToken(refreshToken)

        return MemberResponse.from(savedMember)
    }

    fun authenticate(request: LoginRequest): Long {
        val username = requireNotNull(request.username) { "아이디는 필수입니다." }
        val password = requireNotNull(request.password) { "비밀번호는 필수입니다." }

        val member = memberRepository.findByUsername(username)
            .orElseThrow { UnauthorizedException("아이디 또는 비밀번호가 일치하지 않습니다.") }

        if (!passwordEncoder.matches(password, member.password)) {
            throw UnauthorizedException("아이디 또는 비밀번호가 일치하지 않습니다.")
        }

        if (member.status == MemberStatus.WITHDRAWN) {
            throw UnauthorizedException("탈퇴한 회원은 로그인할 수 없습니다.")
        }

        return member.id
    }

    fun getMember(memberId: Long): MemberResponse = MemberResponse.from(findMember(memberId))

    @Transactional
    fun updateMember(memberId: Long, request: UpdateMemberRequest): MemberResponse {
        val member = findMember(memberId)

        val nickname = request.nickname
        if (!nickname.isNullOrBlank()) {
            member.updateNickname(nickname)
        }

        val newPassword = request.newPassword
        if (!newPassword.isNullOrBlank()) {
            val currentPassword = request.currentPassword

            if (currentPassword.isNullOrBlank()) {
                throw IllegalArgumentException("기존 비밀번호를 입력해주세요.")
            }

            if (!passwordEncoder.matches(currentPassword, member.password)) {
                throw IllegalArgumentException("기존 비밀번호가 일치하지 않습니다.")
            }

            member.updatePassword(encode(newPassword))
        }

        return MemberResponse.from(member)
    }

    // 로그인 시 DB에 저장된 리프레시 토큰 조회
    fun getRefreshToken(memberId: Long): String {
        val refreshToken = findMember(memberId).refreshToken

        if (refreshToken.isNullOrBlank()) {
            throw UnauthorizedException("저장된 리프레시 토큰이 없습니다.")
        }

        return refreshToken
    }

    // 재발급 요청의 리프레시 토큰과 DB 저장값 비교
    fun matchesRefreshToken(memberId: Long, refreshToken: String): Boolean {
        val member = findMember(memberId)

        return member.status == MemberStatus.ACTIVE && member.matchesRefreshToken(refreshToken)
    }

    @Transactional
    fun deleteMember(memberId: Long) {
        val member = findMember(memberId)

        if (member.status == MemberStatus.WITHDRAWN) {
            throw IllegalStateException("이미 탈퇴한 회원입니다.")
        }

        val groupMembers = groupMemberRepository.findAllByMemberId(memberId)
        val today = LocalDate.now()

        val hasActiveGroup = groupMembers.any {
            it.status == GroupMemberStatus.ACTIVE && isActiveGroup(it.group, today)
        }

        if (hasActiveGroup) {
            throw IllegalStateException("진행 중인 방이 있어 회원 탈퇴를 할 수 없습니다.")
        }

        groupMembers
            .filter { !isActiveGroup(it.group, today) }
            .forEach { it.leave() }

        member.withdraw()
    }

    private fun isActiveGroup(group: Group, today: LocalDate): Boolean {
        val deadline = group.deadline

        return group.status == GroupStatus.ACTIVE && (deadline == null || !today.isAfter(deadline))
    }

    // PasswordEncoder.encode는 null을 돌려줄 수 있다고 선언되어 있어(null 입력일 때) 여기서 걸러낸다.
    private fun encode(rawPassword: String): String =
        checkNotNull(passwordEncoder.encode(rawPassword)) { "비밀번호를 암호화하지 못했습니다." }

    private fun findMember(memberId: Long): Member =
        memberRepository.findByIdOrNull(memberId)
            ?: throw NoSuchElementException("존재하지 않는 회원입니다.")
}
