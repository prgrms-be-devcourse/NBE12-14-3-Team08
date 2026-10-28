package com.back.domain.member.service

import com.back.domain.group.entity.Group
import com.back.domain.group.entity.GroupStatus
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.entity.GroupMemberStatus
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.member.dto.CreateMemberRequest
import com.back.domain.member.dto.LoginRequest
import com.back.domain.member.dto.UpdateMemberRequest
import com.back.domain.member.entity.Member
import com.back.domain.member.entity.MemberStatus
import com.back.domain.member.repository.MemberRepository
import com.back.global.exception.UnauthorizedException
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDate
import java.util.Optional

// 의존성은 모두 목이라 호출되지 않아야 할 메서드를 부르면 바로 실패한다. (MockK 기본이 엄격 모드)
@ExtendWith(MockKExtension::class)
class MemberServiceTest {

    @MockK
    private lateinit var memberRepository: MemberRepository

    @MockK
    private lateinit var passwordEncoder: PasswordEncoder

    @MockK
    private lateinit var groupMemberRepository: GroupMemberRepository

    @MockK
    private lateinit var authTokenService: AuthTokenService

    @InjectMockKs
    private lateinit var memberService: MemberService

    companion object {
        private const val MEMBER_ID = 1L
        private const val LOGIN_FAIL_MESSAGE = "아이디 또는 비밀번호가 일치하지 않습니다."
        private const val NOT_FOUND_MESSAGE = "존재하지 않는 회원입니다."
    }

    private fun member(
        id: Long = MEMBER_ID,
        nickname: String = "닉네임",
        username: String = "user-1",
        password: String = "encoded-password",
    ): Member {
        val member = Member.create(nickname, username, password)
        ReflectionTestUtils.setField(member, "id", id)
        return member
    }

    private fun stubFind(member: Member) {
        every { memberRepository.findById(member.id!!) } returns Optional.of(member)
    }

    private fun group(status: GroupStatus, deadline: LocalDate?): Group =
        Group.builder().title("테스트 방").status(status).deadline(deadline).build()

    private fun groupMember(
        group: Group,
        member: Member,
        status: GroupMemberStatus = GroupMemberStatus.ACTIVE,
    ): GroupMember {
        val groupMember = GroupMember.create(group, member, GroupMemberRole.MEMBER)
        if (status == GroupMemberStatus.LEFT) groupMember.leave()
        return groupMember
    }

    @Nested
    @DisplayName("회원가입")
    inner class Join {

        @Test
        @DisplayName("비밀번호를 암호화해 저장하고 리프레시 토큰을 발급해 회원에 담는다")
        fun 가입_성공() {
            val saved = slot<Member>()
            every { memberRepository.existsByUsername("user-1") } returns false
            every { passwordEncoder.encode("password1234") } returns "encoded-password"
            every { memberRepository.save(capture(saved)) } answers {
                ReflectionTestUtils.setField(saved.captured, "id", MEMBER_ID)
                saved.captured
            }
            every { authTokenService.createRefreshToken(MEMBER_ID) } returns "refresh-token"

            val response = memberService.join(CreateMemberRequest("닉네임", "user-1", "password1234"))

            assertThat(response.memberId).isEqualTo(MEMBER_ID)
            assertThat(response.nickname).isEqualTo("닉네임")
            assertThat(response.username).isEqualTo("user-1")
            assertThat(saved.captured.password).isEqualTo("encoded-password")
            assertThat(saved.captured.refreshToken).isEqualTo("refresh-token")
            assertThat(saved.captured.status).isEqualTo(MemberStatus.ACTIVE)
        }

        @ParameterizedTest(name = "아이디={0}")
        @ValueSource(strings = ["withdrawn_1", "withdrawn_", "withdrawn_abc"])
        @DisplayName("탈퇴한 회원의 익명 아이디 접두사(withdrawn_)로 시작하면 IllegalArgumentException이고 저장도 암호화도 하지 않는다")
        fun 예약된_접두사_아이디(username: String) {
            assertThatThrownBy { memberService.join(CreateMemberRequest("닉네임", username, "password1234")) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("사용할 수 없는 아이디입니다.")

            verify(exactly = 0) { memberRepository.existsByUsername(any()) }
            verify(exactly = 0) { memberRepository.save(any<Member>()) }
            verify(exactly = 0) { passwordEncoder.encode(any()) }
        }

        @Test
        @DisplayName("응답에 비밀번호나 리프레시 토큰은 담기지 않는다")
        fun 응답에는_민감한_값이_없다() {
            every { memberRepository.existsByUsername(any()) } returns false
            every { passwordEncoder.encode(any()) } returns "encoded-password"
            every { memberRepository.save(any<Member>()) } answers {
                firstArg<Member>().also { ReflectionTestUtils.setField(it, "id", MEMBER_ID) }
            }
            every { authTokenService.createRefreshToken(MEMBER_ID) } returns "refresh-token"

            val response = memberService.join(CreateMemberRequest("닉네임", "user-1", "password1234"))

            assertThat(response.toString()).doesNotContain("encoded-password", "refresh-token", "password1234")
        }

        @Test
        @DisplayName("이미 있는 아이디면 IllegalStateException이고 저장도 암호화도 하지 않는다")
        fun 중복_아이디() {
            every { memberRepository.existsByUsername("user-1") } returns true

            assertThatThrownBy { memberService.join(CreateMemberRequest("닉네임", "user-1", "password1234")) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessage("이미 사용 중인 아이디입니다.")

            verify(exactly = 0) { memberRepository.save(any<Member>()) }
            verify(exactly = 0) { passwordEncoder.encode(any()) }
        }

        @Test
        @DisplayName("암호화 결과가 null이면 IllegalStateException이고 저장하지 않는다")
        fun 암호화_실패() {
            every { memberRepository.existsByUsername(any()) } returns false
            every { passwordEncoder.encode(any()) } returns null

            assertThatThrownBy { memberService.join(CreateMemberRequest("닉네임", "user-1", "password1234")) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessage("비밀번호를 암호화하지 못했습니다.")

            verify(exactly = 0) { memberRepository.save(any<Member>()) }
        }

        @Test
        @DisplayName("닉네임이 없으면 IllegalArgumentException이고 아무 저장소도 부르지 않는다")
        fun 닉네임_누락() {
            assertThatThrownBy { memberService.join(CreateMemberRequest(null, "user-1", "password1234")) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("이름은 필수입니다.")
        }

        @Test
        @DisplayName("아이디가 없으면 IllegalArgumentException이고 아무 저장소도 부르지 않는다")
        fun 아이디_누락() {
            assertThatThrownBy { memberService.join(CreateMemberRequest("닉네임", null, "password1234")) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("아이디는 필수입니다.")
        }

        @Test
        @DisplayName("비밀번호가 없으면 IllegalArgumentException이고 아무 저장소도 부르지 않는다")
        fun 비밀번호_누락() {
            assertThatThrownBy { memberService.join(CreateMemberRequest("닉네임", "user-1", null)) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("비밀번호는 필수입니다.")
        }
    }

    @Nested
    @DisplayName("로그인 인증")
    inner class Authenticate {

        @Test
        @DisplayName("아이디와 비밀번호가 맞으면 회원 id를 돌려준다")
        fun 로그인_성공() {
            every { memberRepository.findByUsername("user-1") } returns Optional.of(member())
            every { passwordEncoder.matches("password1234", "encoded-password") } returns true

            assertThat(memberService.authenticate(LoginRequest("user-1", "password1234"))).isEqualTo(MEMBER_ID)
        }

        @Test
        @DisplayName("없는 아이디면 UnauthorizedException이다")
        fun 없는_아이디() {
            every { memberRepository.findByUsername("none") } returns Optional.empty()

            assertThatThrownBy { memberService.authenticate(LoginRequest("none", "password1234")) }
                .isInstanceOf(UnauthorizedException::class.java)
                .hasMessage(LOGIN_FAIL_MESSAGE)

            verify(exactly = 0) { passwordEncoder.matches(any(), any()) }
        }

        @Test
        @DisplayName("비밀번호가 틀리면 UnauthorizedException이다")
        fun 틀린_비밀번호() {
            every { memberRepository.findByUsername("user-1") } returns Optional.of(member())
            every { passwordEncoder.matches("wrong", "encoded-password") } returns false

            assertThatThrownBy { memberService.authenticate(LoginRequest("user-1", "wrong")) }
                .isInstanceOf(UnauthorizedException::class.java)
                .hasMessage(LOGIN_FAIL_MESSAGE)
        }

        @Test
        @DisplayName("없는 아이디와 틀린 비밀번호는 같은 메시지라 가입 여부를 알 수 없다")
        fun 실패_메시지가_같다() {
            every { memberRepository.findByUsername("none") } returns Optional.empty()
            every { memberRepository.findByUsername("user-1") } returns Optional.of(member())
            every { passwordEncoder.matches(any(), any()) } returns false

            val notFound = runCatching { memberService.authenticate(LoginRequest("none", "pw")) }.exceptionOrNull()
            val wrongPassword = runCatching { memberService.authenticate(LoginRequest("user-1", "pw")) }.exceptionOrNull()

            assertThat(notFound).hasMessage(wrongPassword?.message)
        }

        @Test
        @DisplayName("탈퇴한 회원은 비밀번호를 맞게 입력해도 비밀번호를 비교하지 않고 일반 실패 메시지다 (탈퇴 여부를 알려주지 않는다)")
        fun 탈퇴한_회원() {
            val withdrawn = member().also { it.withdraw() }
            every { memberRepository.findByUsername("withdrawn_1") } returns Optional.of(withdrawn)

            assertThatThrownBy { memberService.authenticate(LoginRequest("withdrawn_1", "password1234")) }
                .isInstanceOf(UnauthorizedException::class.java)
                .hasMessage(LOGIN_FAIL_MESSAGE)

            verify(exactly = 0) { passwordEncoder.matches(any(), any()) }
        }

        @Test
        @DisplayName("아이디가 없으면 IllegalArgumentException이다")
        fun 아이디_누락() {
            assertThatThrownBy { memberService.authenticate(LoginRequest(null, "password1234")) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("아이디는 필수입니다.")
        }

        @Test
        @DisplayName("비밀번호가 없으면 IllegalArgumentException이다")
        fun 비밀번호_누락() {
            assertThatThrownBy { memberService.authenticate(LoginRequest("user-1", null)) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("비밀번호는 필수입니다.")
        }
    }

    @Nested
    @DisplayName("내 정보 조회")
    inner class GetMember {

        @Test
        @DisplayName("회원 정보를 응답으로 바꿔 돌려준다")
        fun 조회_성공() {
            stubFind(member())

            val response = memberService.getMember(MEMBER_ID)

            assertThat(response.memberId).isEqualTo(MEMBER_ID)
            assertThat(response.nickname).isEqualTo("닉네임")
            assertThat(response.username).isEqualTo("user-1")
        }

        @Test
        @DisplayName("없는 회원이면 NoSuchElementException이다")
        fun 없는_회원() {
            every { memberRepository.findById(99L) } returns Optional.empty()

            assertThatThrownBy { memberService.getMember(99L) }
                .isInstanceOf(NoSuchElementException::class.java)
                .hasMessage(NOT_FOUND_MESSAGE)
        }
    }

    @Nested
    @DisplayName("내 정보 수정")
    inner class UpdateMember {

        @Test
        @DisplayName("기존 비밀번호가 맞으면 닉네임과 비밀번호를 함께 바꾼다")
        fun 닉네임과_비밀번호_수정() {
            val member = member(password = "old-encoded")
            stubFind(member)
            every { passwordEncoder.matches("oldPassword1", "old-encoded") } returns true
            every { passwordEncoder.encode("newPassword1") } returns "new-encoded"

            val response = memberService.updateMember(MEMBER_ID, UpdateMemberRequest("새닉네임", "oldPassword1", "newPassword1"))

            assertThat(response.nickname).isEqualTo("새닉네임")
            assertThat(member.nickname).isEqualTo("새닉네임")
            assertThat(member.password).isEqualTo("new-encoded")
        }

        @Test
        @DisplayName("닉네임만 보내면 비밀번호 검증과 암호화를 하지 않는다")
        fun 닉네임만_수정() {
            val member = member()
            stubFind(member)

            val response = memberService.updateMember(MEMBER_ID, UpdateMemberRequest(nickname = "새닉네임"))

            assertThat(response.nickname).isEqualTo("새닉네임")
            assertThat(member.password).isEqualTo("encoded-password")
        }

        @Test
        @DisplayName("새 비밀번호만 바꾸는 요청도 기존 비밀번호가 맞으면 닉네임은 그대로 둔다")
        fun 비밀번호만_수정() {
            val member = member(password = "old-encoded")
            stubFind(member)
            every { passwordEncoder.matches("oldPassword1", "old-encoded") } returns true
            every { passwordEncoder.encode("newPassword1") } returns "new-encoded"

            memberService.updateMember(MEMBER_ID, UpdateMemberRequest(currentPassword = "oldPassword1", newPassword = "newPassword1"))

            assertThat(member.nickname).isEqualTo("닉네임")
            assertThat(member.password).isEqualTo("new-encoded")
        }

        @ParameterizedTest(name = "닉네임=\"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = [" ", "   "])
        @DisplayName("닉네임이 null, 빈 값, 공백이면 바꾸지 않는다")
        fun 비어_있는_닉네임은_무시한다(nickname: String?) {
            val member = member()
            stubFind(member)

            val response = memberService.updateMember(MEMBER_ID, UpdateMemberRequest(nickname = nickname))

            assertThat(response.nickname).isEqualTo("닉네임")
            assertThat(member.nickname).isEqualTo("닉네임")
        }

        @ParameterizedTest(name = "새 비밀번호=\"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = [" "])
        @DisplayName("새 비밀번호가 null, 빈 값, 공백이면 비밀번호를 바꾸지 않고 기존 비밀번호도 확인하지 않는다")
        fun 비어_있는_새_비밀번호는_무시한다(newPassword: String?) {
            val member = member()
            stubFind(member)

            memberService.updateMember(MEMBER_ID, UpdateMemberRequest(currentPassword = "wrong", newPassword = newPassword))

            assertThat(member.password).isEqualTo("encoded-password")
        }

        @ParameterizedTest(name = "기존 비밀번호=\"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = [" "])
        @DisplayName("새 비밀번호를 보내면서 기존 비밀번호가 null, 빈 값, 공백이면 IllegalArgumentException이다")
        fun 기존_비밀번호가_없으면_거절한다(currentPassword: String?) {
            val member = member()
            stubFind(member)

            assertThatThrownBy {
                memberService.updateMember(MEMBER_ID, UpdateMemberRequest(currentPassword = currentPassword, newPassword = "newPassword1"))
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("기존 비밀번호를 입력해주세요.")

            assertThat(member.password).isEqualTo("encoded-password")
        }

        @Test
        @DisplayName("기존 비밀번호가 틀리면 IllegalArgumentException이고 비밀번호를 암호화하거나 바꾸지 않는다")
        fun 기존_비밀번호가_틀리면_거절한다() {
            val member = member(password = "old-encoded")
            stubFind(member)
            every { passwordEncoder.matches("wrong", "old-encoded") } returns false

            assertThatThrownBy {
                memberService.updateMember(MEMBER_ID, UpdateMemberRequest(currentPassword = "wrong", newPassword = "newPassword1"))
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("기존 비밀번호가 일치하지 않습니다.")

            assertThat(member.password).isEqualTo("old-encoded")
            verify(exactly = 0) { passwordEncoder.encode(any()) }
        }

        @Test
        @DisplayName("새 비밀번호 암호화 결과가 null이면 IllegalStateException이고 비밀번호는 그대로다")
        fun 암호화_실패() {
            val member = member(password = "old-encoded")
            stubFind(member)
            every { passwordEncoder.matches("oldPassword1", "old-encoded") } returns true
            every { passwordEncoder.encode("newPassword1") } returns null

            assertThatThrownBy {
                memberService.updateMember(MEMBER_ID, UpdateMemberRequest(currentPassword = "oldPassword1", newPassword = "newPassword1"))
            }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessage("비밀번호를 암호화하지 못했습니다.")

            assertThat(member.password).isEqualTo("old-encoded")
        }

        @Test
        @DisplayName("없는 회원이면 NoSuchElementException이다")
        fun 없는_회원() {
            every { memberRepository.findById(99L) } returns Optional.empty()

            assertThatThrownBy { memberService.updateMember(99L, UpdateMemberRequest(nickname = "새닉네임")) }
                .isInstanceOf(NoSuchElementException::class.java)
                .hasMessage(NOT_FOUND_MESSAGE)
        }
    }

    @Nested
    @DisplayName("리프레시 토큰")
    inner class RefreshTokenTest {

        @Test
        @DisplayName("getRefreshToken: 저장된 토큰을 돌려준다")
        fun 토큰_조회() {
            stubFind(member().also { it.updateRefreshToken("refresh-token") })

            assertThat(memberService.getRefreshToken(MEMBER_ID)).isEqualTo("refresh-token")
        }

        @Test
        @DisplayName("getRefreshToken: 저장된 토큰이 없으면 UnauthorizedException이다")
        fun 토큰_없음() {
            stubFind(member())

            assertThatThrownBy { memberService.getRefreshToken(MEMBER_ID) }
                .isInstanceOf(UnauthorizedException::class.java)
                .hasMessage("저장된 리프레시 토큰이 없습니다.")
        }

        @Test
        @DisplayName("getRefreshToken: 저장된 토큰이 공백뿐이어도 없는 것으로 본다")
        fun 공백_토큰() {
            stubFind(member().also { it.updateRefreshToken("  ") })

            assertThatThrownBy { memberService.getRefreshToken(MEMBER_ID) }
                .isInstanceOf(UnauthorizedException::class.java)
        }

        @Test
        @DisplayName("getRefreshToken: 없는 회원이면 NoSuchElementException이다")
        fun 토큰_조회_없는_회원() {
            every { memberRepository.findById(99L) } returns Optional.empty()

            assertThatThrownBy { memberService.getRefreshToken(99L) }
                .isInstanceOf(NoSuchElementException::class.java)
        }

        @Test
        @DisplayName("matchesRefreshToken: ACTIVE 회원은 저장된 토큰과 같을 때만 true다")
        fun 토큰_비교() {
            stubFind(member().also { it.updateRefreshToken("refresh-token") })

            assertThat(memberService.matchesRefreshToken(MEMBER_ID, "refresh-token")).isTrue()
            assertThat(memberService.matchesRefreshToken(MEMBER_ID, "wrong-token")).isFalse()
        }

        @Test
        @DisplayName("matchesRefreshToken: 저장된 토큰이 없으면 어떤 값도 false다")
        fun 저장된_토큰이_없으면_false() {
            stubFind(member())

            assertThat(memberService.matchesRefreshToken(MEMBER_ID, "refresh-token")).isFalse()
            assertThat(memberService.matchesRefreshToken(MEMBER_ID, "")).isFalse()
        }

        @Test
        @DisplayName("matchesRefreshToken: 탈퇴한 회원은 토큰이 같아도 false다")
        fun 탈퇴한_회원은_false() {
            val withdrawn = member().also { it.withdraw() }
            // 탈퇴하면 토큰이 지워지므로, 상태 검사만 따로 확인하려고 토큰을 직접 되돌려 놓는다.
            ReflectionTestUtils.setField(withdrawn, "refreshToken", "refresh-token")
            stubFind(withdrawn)

            assertThat(memberService.matchesRefreshToken(MEMBER_ID, "refresh-token")).isFalse()
        }

        @Test
        @DisplayName("matchesRefreshToken: 없는 회원이면 NoSuchElementException이다")
        fun 토큰_비교_없는_회원() {
            every { memberRepository.findById(99L) } returns Optional.empty()

            assertThatThrownBy { memberService.matchesRefreshToken(99L, "refresh-token") }
                .isInstanceOf(NoSuchElementException::class.java)
        }
    }

    @Nested
    @DisplayName("회원 탈퇴")
    inner class DeleteMember {

        private val today: LocalDate = LocalDate.now()

        @Test
        @DisplayName("참여한 방이 없으면 WITHDRAWN이 되고 리프레시 토큰이 폐기되며 행은 삭제하지 않는다")
        fun 방이_없으면_탈퇴() {
            val member = member().also { it.updateRefreshToken("refresh-token") }
            stubFind(member)
            every { groupMemberRepository.findAllByMemberId(MEMBER_ID) } returns emptyList()

            memberService.deleteMember(MEMBER_ID)

            assertThat(member.status).isEqualTo(MemberStatus.WITHDRAWN)
            assertThat(member.refreshToken).isNull()
            verify(exactly = 0) { memberRepository.delete(any<Member>()) }
        }

        @Test
        @DisplayName("탈퇴하면 닉네임, 아이디, 비밀번호가 익명 값으로 바뀌어 같은 아이디로 다시 가입할 수 있다")
        fun 탈퇴하면_익명화된다() {
            val member = member(nickname = "홍길동", username = "user-1", password = "encoded-password")
            stubFind(member)
            every { groupMemberRepository.findAllByMemberId(MEMBER_ID) } returns emptyList()

            memberService.deleteMember(MEMBER_ID)

            assertThat(member.username).isEqualTo("withdrawn_$MEMBER_ID")
            assertThat(member.password).isEqualTo(Member.WITHDRAWN_PASSWORD)
            assertThat(member.nickname).isEqualTo(Member.WITHDRAWN_NICKNAME)
            // 원래 아이디는 비어 있게 되어 다시 가입할 수 있다 (존재 여부 검사는 저장소가 한다)
            every { memberRepository.existsByUsername("user-1") } returns false
            every { passwordEncoder.encode("password1234") } returns "encoded-new"
            every { memberRepository.save(any<Member>()) } answers {
                firstArg<Member>().also { ReflectionTestUtils.setField(it, "id", 2L) }
            }
            every { authTokenService.createRefreshToken(2L) } returns "new-refresh"

            val rejoined = memberService.join(CreateMemberRequest("홍길동", "user-1", "password1234"))

            assertThat(rejoined.memberId).isEqualTo(2L)
            assertThat(rejoined.username).isEqualTo("user-1")
        }

        @Test
        @DisplayName("이미 탈퇴한 회원이면 IllegalStateException이고 방 기록은 조회하지 않는다")
        fun 이미_탈퇴한_회원() {
            stubFind(member().also { it.withdraw() })

            assertThatThrownBy { memberService.deleteMember(MEMBER_ID) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessage("이미 탈퇴한 회원입니다.")

            verify(exactly = 0) { groupMemberRepository.findAllByMemberId(any()) }
        }

        @Test
        @DisplayName("마감일이 내일인 진행 중인 방이 있으면 IllegalStateException이고 아무것도 바뀌지 않는다")
        fun 마감일이_남은_진행_중인_방() {
            val member = member()
            val groupMember = groupMember(group(GroupStatus.ACTIVE, today.plusDays(1)), member)
            stubFind(member)
            every { groupMemberRepository.findAllByMemberId(MEMBER_ID) } returns listOf(groupMember)

            assertThatThrownBy { memberService.deleteMember(MEMBER_ID) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessage("진행 중인 방이 있어 회원 탈퇴를 할 수 없습니다.")

            assertThat(member.status).isEqualTo(MemberStatus.ACTIVE)
            assertThat(groupMember.status).isEqualTo(GroupMemberStatus.ACTIVE)
        }

        @Test
        @DisplayName("마감일이 오늘인 방은 아직 진행 중이다")
        fun 마감일이_오늘() {
            val member = member()
            stubFind(member)
            every { groupMemberRepository.findAllByMemberId(MEMBER_ID) } returns
                listOf(groupMember(group(GroupStatus.ACTIVE, today), member))

            assertThatThrownBy { memberService.deleteMember(MEMBER_ID) }.isInstanceOf(IllegalStateException::class.java)
            assertThat(member.status).isEqualTo(MemberStatus.ACTIVE)
        }

        @Test
        @DisplayName("마감일이 없는 ACTIVE 방은 계속 진행 중이다")
        fun 마감일이_없는_방() {
            val member = member()
            stubFind(member)
            every { groupMemberRepository.findAllByMemberId(MEMBER_ID) } returns
                listOf(groupMember(group(GroupStatus.ACTIVE, null), member))

            assertThatThrownBy { memberService.deleteMember(MEMBER_ID) }.isInstanceOf(IllegalStateException::class.java)
            assertThat(member.status).isEqualTo(MemberStatus.ACTIVE)
        }

        @Test
        @DisplayName("마감일이 어제인 방은 아직 ACTIVE여도 끝난 방으로 보고 LEFT로 남기며 탈퇴한다")
        fun 마감일이_어제() {
            val member = member()
            val groupMember = groupMember(group(GroupStatus.ACTIVE, today.minusDays(1)), member)
            stubFind(member)
            every { groupMemberRepository.findAllByMemberId(MEMBER_ID) } returns listOf(groupMember)

            memberService.deleteMember(MEMBER_ID)

            assertThat(groupMember.status).isEqualTo(GroupMemberStatus.LEFT)
            assertThat(member.status).isEqualTo(MemberStatus.WITHDRAWN)
        }

        @Test
        @DisplayName("종료(FINISH)된 방은 마감일이 남아 있어도 LEFT로 남기며 탈퇴한다")
        fun 종료된_방() {
            val member = member().also { it.updateRefreshToken("refresh-token") }
            val groupMember = groupMember(group(GroupStatus.FINISH, today.plusDays(10)), member)
            stubFind(member)
            every { groupMemberRepository.findAllByMemberId(MEMBER_ID) } returns listOf(groupMember)

            memberService.deleteMember(MEMBER_ID)

            assertThat(groupMember.status).isEqualTo(GroupMemberStatus.LEFT)
            assertThat(member.status).isEqualTo(MemberStatus.WITHDRAWN)
            assertThat(member.refreshToken).isNull()
        }

        @Test
        @DisplayName("이미 나간(LEFT) 멤버 기록은 진행 중인 방이어도 탈퇴를 막지 않고 상태도 그대로다")
        fun 이미_나간_진행_중인_방() {
            val member = member()
            val groupMember = groupMember(group(GroupStatus.ACTIVE, today.plusDays(5)), member, GroupMemberStatus.LEFT)
            stubFind(member)
            every { groupMemberRepository.findAllByMemberId(MEMBER_ID) } returns listOf(groupMember)

            memberService.deleteMember(MEMBER_ID)

            assertThat(member.status).isEqualTo(MemberStatus.WITHDRAWN)
            assertThat(groupMember.status).isEqualTo(GroupMemberStatus.LEFT)
        }

        @Test
        @DisplayName("진행 중인 방과 끝난 방이 섞여 있으면 막고, 끝난 방의 멤버 기록도 바꾸지 않는다")
        fun 섞여_있으면_아무것도_바꾸지_않는다() {
            val member = member()
            val active = groupMember(group(GroupStatus.ACTIVE, today.plusDays(3)), member)
            val finished = groupMember(group(GroupStatus.FINISH, today.minusDays(3)), member)
            stubFind(member)
            every { groupMemberRepository.findAllByMemberId(MEMBER_ID) } returns listOf(finished, active)

            assertThatThrownBy { memberService.deleteMember(MEMBER_ID) }.isInstanceOf(IllegalStateException::class.java)

            assertThat(finished.status).isEqualTo(GroupMemberStatus.ACTIVE)
            assertThat(active.status).isEqualTo(GroupMemberStatus.ACTIVE)
            assertThat(member.status).isEqualTo(MemberStatus.ACTIVE)
        }

        @Test
        @DisplayName("끝난 방이 여러 개면 모두 LEFT로 남긴다")
        fun 끝난_방이_여러_개() {
            val member = member()
            val first = groupMember(group(GroupStatus.FINISH, today.minusDays(1)), member)
            val second = groupMember(group(GroupStatus.ACTIVE, today.minusDays(9)), member)
            stubFind(member)
            every { groupMemberRepository.findAllByMemberId(MEMBER_ID) } returns listOf(first, second)

            memberService.deleteMember(MEMBER_ID)

            assertThat(listOf(first, second).map { it.status }).containsOnly(GroupMemberStatus.LEFT)
        }

        @Test
        @DisplayName("없는 회원이면 NoSuchElementException이다")
        fun 없는_회원() {
            every { memberRepository.findById(99L) } returns Optional.empty()

            assertThatThrownBy { memberService.deleteMember(99L) }
                .isInstanceOf(NoSuchElementException::class.java)
                .hasMessage(NOT_FOUND_MESSAGE)
        }
    }
}
