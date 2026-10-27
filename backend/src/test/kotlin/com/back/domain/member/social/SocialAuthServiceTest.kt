package com.back.domain.member.social

import com.back.domain.member.entity.MemberProvider
import com.back.global.exception.BusinessRuleException
import com.back.global.exception.UnauthorizedException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException

class SocialAuthServiceTest {

    private val googleClient = mockk<SocialOAuthClient> { every { provider } returns MemberProvider.GOOGLE }
    private val socialMemberService = mockk<SocialMemberService>()
    private val service = SocialAuthService(listOf(googleClient), socialMemberService)

    private val profile = SocialProfile("sub-1", "홍길동")

    @Test
    @DisplayName("공급자 클라이언트로 프로필을 받아 회원 id를 돌려준다 (공급자 이름은 대소문자를 가리지 않는다)")
    fun 로그인_성공() {
        every { googleClient.fetchProfile("code", "http://localhost:3000/cb") } returns profile
        every { socialMemberService.findOrCreate(MemberProvider.GOOGLE, profile) } returns 7L

        assertThat(service.login("google", "code", "http://localhost:3000/cb")).isEqualTo(7L)
        assertThat(service.login("GOOGLE", "code", "http://localhost:3000/cb")).isEqualTo(7L)
    }

    @Test
    @DisplayName("지원하지 않거나 일반(LOCAL)인 공급자 이름은 공급자 호출 없이 거절한다")
    fun 지원하지_않는_공급자() {
        listOf("naver", "kakao", "local", "LOCAL", "").forEach { name ->
            assertThatThrownBy { service.login(name, "code", "uri") }
                .isInstanceOf(BusinessRuleException::class.java)
                .hasMessage("지원하지 않는 로그인 방식입니다.")
        }

        verify(exactly = 0) { googleClient.fetchProfile(any(), any()) }
    }

    @Test
    @DisplayName("공급자 쪽 실패는 회원 조회·생성으로 넘어가지 않고 그대로 전달한다")
    fun 공급자_실패() {
        every { googleClient.fetchProfile(any(), any()) } throws UnauthorizedException("구글 로그인에 실패했습니다.")

        assertThatThrownBy { service.login("google", "bad", "uri") }.isInstanceOf(UnauthorizedException::class.java)
        verify(exactly = 0) { socialMemberService.findOrCreate(any(), any()) }
    }

    @Test
    @DisplayName("같은 계정의 첫 로그인이 동시에 들어와 유니크 제약에 걸리면 한 번 더 조회해 먼저 만들어진 회원으로 로그인한다")
    fun 동시_첫_로그인() {
        every { googleClient.fetchProfile(any(), any()) } returns profile
        every { socialMemberService.findOrCreate(MemberProvider.GOOGLE, profile) } throws
            DataIntegrityViolationException("uk_member_provider") andThen 9L

        assertThat(service.login("google", "code", "uri")).isEqualTo(9L)
        verify(exactly = 2) { socialMemberService.findOrCreate(MemberProvider.GOOGLE, profile) }
    }

    @Test
    @DisplayName("재시도에서도 제약 위반이면 오류를 그대로 던진다 (무한 재시도하지 않는다)")
    fun 재시도_실패() {
        every { googleClient.fetchProfile(any(), any()) } returns profile
        every { socialMemberService.findOrCreate(any(), any()) } throws DataIntegrityViolationException("uk_member_provider")

        assertThatThrownBy { service.login("google", "code", "uri") }
            .isInstanceOf(DataIntegrityViolationException::class.java)
        verify(exactly = 2) { socialMemberService.findOrCreate(any(), any()) }
    }
}
