package com.back.domain.member.social

import com.back.domain.member.entity.Member
import com.back.domain.member.entity.MemberProvider
import com.back.domain.member.entity.MemberStatus
import com.back.domain.member.repository.MemberRepository
import com.back.domain.member.service.AuthTokenService
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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.util.ReflectionTestUtils

// 의존성은 모두 목이라 호출되지 않아야 할 메서드를 부르면 바로 실패한다. (MockK 기본이 엄격 모드)
@ExtendWith(MockKExtension::class)
class SocialMemberServiceTest {

    @MockK
    private lateinit var memberRepository: MemberRepository

    @MockK
    private lateinit var passwordEncoder: PasswordEncoder

    @MockK
    private lateinit var authTokenService: AuthTokenService

    @InjectMockKs
    private lateinit var socialMemberService: SocialMemberService

    companion object {
        private const val SAVED_ID = 7L
        private const val PROVIDER_ID = "1234567890"
    }

    private fun stubSave() {
        every { memberRepository.save(any<Member>()) } answers {
            firstArg<Member>().also { ReflectionTestUtils.setField(it, "id", SAVED_ID) }
        }
    }

    private fun existingMember(id: Long = 3L): Member {
        val member = Member.createSocial(MemberProvider.GOOGLE, PROVIDER_ID, "기존", "google_$PROVIDER_ID", "hash")
        ReflectionTestUtils.setField(member, "id", id)
        member.updateRefreshToken("existing-refresh")
        return member
    }

    @Test
    @DisplayName("처음 로그인하면 소셜 회원을 만들고 리프레시 토큰까지 저장해 id를 돌려준다")
    fun 신규_가입() {
        every { memberRepository.findByProviderAndProviderId(MemberProvider.GOOGLE, PROVIDER_ID) } returns null
        every { memberRepository.existsByUsername("google_$PROVIDER_ID") } returns false
        every { passwordEncoder.encode(any()) } returns "encoded-random"
        every { authTokenService.createRefreshToken(SAVED_ID) } returns "new-refresh"
        stubSave()
        val saved = slot<Member>()

        val id = socialMemberService.findOrCreate(MemberProvider.GOOGLE, SocialProfile(PROVIDER_ID, "홍길동"))

        assertThat(id).isEqualTo(SAVED_ID)
        verify { memberRepository.save(capture(saved)) }
        with(saved.captured) {
            assertThat(provider).isEqualTo(MemberProvider.GOOGLE)
            assertThat(providerId).isEqualTo(PROVIDER_ID)
            assertThat(username).isEqualTo("google_$PROVIDER_ID")
            assertThat(nickname).isEqualTo("홍길동")
            assertThat(password).isEqualTo("encoded-random")
            assertThat(status).isEqualTo(MemberStatus.ACTIVE)
            assertThat(refreshToken).isEqualTo("new-refresh")
        }
    }

    @Test
    @DisplayName("비밀번호는 아무도 모르는 임의 값으로 암호화한다 (같은 값이 두 번 쓰이지 않는다)")
    fun 임의_비밀번호() {
        every { memberRepository.findByProviderAndProviderId(any(), any()) } returns null
        every { memberRepository.existsByUsername(any()) } returns false
        val raw = mutableListOf<String>()
        every { passwordEncoder.encode(capture(raw)) } returns "encoded"
        every { authTokenService.createRefreshToken(any()) } returns "refresh"
        stubSave()

        socialMemberService.findOrCreate(MemberProvider.GOOGLE, SocialProfile("a", "가"))
        socialMemberService.findOrCreate(MemberProvider.GOOGLE, SocialProfile("b", "나"))

        assertThat(raw).hasSize(2)
        assertThat(raw[0]).isNotEqualTo(raw[1])
        assertThat(raw).allSatisfy { assertThat(it.length).isGreaterThan(40) }
    }

    @Test
    @DisplayName("이름이 없거나 공백이면 기본 닉네임을 쓰고, 너무 길면 50자까지만 쓴다")
    fun 닉네임_보정() {
        every { memberRepository.findByProviderAndProviderId(any(), any()) } returns null
        every { memberRepository.existsByUsername(any()) } returns false
        every { passwordEncoder.encode(any()) } returns "encoded"
        every { authTokenService.createRefreshToken(any()) } returns "refresh"
        stubSave()
        val saved = mutableListOf<Member>()
        every { memberRepository.save(capture(saved)) } answers {
            firstArg<Member>().also { ReflectionTestUtils.setField(it, "id", SAVED_ID) }
        }

        socialMemberService.findOrCreate(MemberProvider.GOOGLE, SocialProfile("a", null))
        socialMemberService.findOrCreate(MemberProvider.GOOGLE, SocialProfile("b", "   "))
        socialMemberService.findOrCreate(MemberProvider.GOOGLE, SocialProfile("c", "가".repeat(80)))
        socialMemberService.findOrCreate(MemberProvider.GOOGLE, SocialProfile("d", "  홍길동  "))

        assertThat(saved.map { it.nickname })
            .containsExactly("구글 사용자", "구글 사용자", "가".repeat(50), "홍길동")
    }

    @Test
    @DisplayName("같은 아이디를 쓰는 회원이 이미 있으면 뒤에 임의 값을 붙여 아이디 중복을 피한다")
    fun 아이디_충돌() {
        every { memberRepository.findByProviderAndProviderId(any(), any()) } returns null
        every { memberRepository.existsByUsername("google_$PROVIDER_ID") } returns true
        every { passwordEncoder.encode(any()) } returns "encoded"
        every { authTokenService.createRefreshToken(any()) } returns "refresh"
        stubSave()
        val saved = slot<Member>()
        every { memberRepository.save(capture(saved)) } answers {
            firstArg<Member>().also { ReflectionTestUtils.setField(it, "id", SAVED_ID) }
        }

        socialMemberService.findOrCreate(MemberProvider.GOOGLE, SocialProfile(PROVIDER_ID, "홍길동"))

        assertThat(saved.captured.username).startsWith("google_${PROVIDER_ID}_").hasSize("google_${PROVIDER_ID}_".length + 6)
    }

    @Test
    @DisplayName("이미 가입한 계정이면 새로 만들지 않고 기존 회원 id를 돌려준다")
    fun 기존_회원() {
        every { memberRepository.findByProviderAndProviderId(MemberProvider.GOOGLE, PROVIDER_ID) } returns existingMember(3L)

        val id = socialMemberService.findOrCreate(MemberProvider.GOOGLE, SocialProfile(PROVIDER_ID, "바뀐이름"))

        assertThat(id).isEqualTo(3L)
        verify(exactly = 0) { memberRepository.save(any<Member>()) }
        verify(exactly = 0) { passwordEncoder.encode(any()) }
    }

    @Test
    @DisplayName("기존 회원의 리프레시 토큰이 비어 있으면 새로 만들어 저장한다")
    fun 리프레시_토큰_보충() {
        val member = existingMember(3L).also { it.updateRefreshToken(null) }
        every { memberRepository.findByProviderAndProviderId(any(), any()) } returns member
        every { authTokenService.createRefreshToken(3L) } returns "regenerated"

        socialMemberService.findOrCreate(MemberProvider.GOOGLE, SocialProfile(PROVIDER_ID, "x"))

        assertThat(member.refreshToken).isEqualTo("regenerated")
    }

    @Test
    @DisplayName("탈퇴한 회원은 소셜 계정으로도 로그인할 수 없고 새 회원도 만들지 않는다")
    fun 탈퇴_회원() {
        val withdrawn = existingMember(3L).also { it.withdraw() }
        every { memberRepository.findByProviderAndProviderId(any(), any()) } returns withdrawn

        assertThatThrownBy { socialMemberService.findOrCreate(MemberProvider.GOOGLE, SocialProfile(PROVIDER_ID, "x")) }
            .isInstanceOf(UnauthorizedException::class.java)
            .hasMessage("탈퇴한 회원은 로그인할 수 없습니다.")
        verify(exactly = 0) { memberRepository.save(any<Member>()) }
    }

    @Test
    @DisplayName("공급자 식별자가 컬럼 길이(100자)를 넘으면 저장하지 않고 거절한다")
    fun 식별자_길이_초과() {
        assertThatThrownBy {
            socialMemberService.findOrCreate(MemberProvider.GOOGLE, SocialProfile("1".repeat(101), "x"))
        }.isInstanceOf(UnauthorizedException::class.java)
    }
}
