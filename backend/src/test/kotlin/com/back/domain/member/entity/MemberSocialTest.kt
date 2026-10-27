package com.back.domain.member.entity

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

// 가입 방식(provider)에 관한 Member 규칙을 DB 없이 확인한다.
class MemberSocialTest {

    @Test
    @DisplayName("아이디/비밀번호 회원은 LOCAL이고 공급자 식별자가 없다")
    fun 일반_회원() {
        val member = Member.create("닉네임", "user-1", "encoded")

        assertThat(member.provider).isEqualTo(MemberProvider.LOCAL)
        assertThat(member.providerId).isNull()
        assertThat(member.isSocial()).isFalse()
    }

    @Test
    @DisplayName("소셜 회원은 공급자와 식별자를 가지고 소셜 회원으로 판별된다")
    fun 소셜_회원() {
        val member = Member.createSocial(MemberProvider.GOOGLE, "1234", "홍길동", "google_1234", "encoded-random")

        assertThat(member.provider).isEqualTo(MemberProvider.GOOGLE)
        assertThat(member.providerId).isEqualTo("1234")
        assertThat(member.username).isEqualTo("google_1234")
        assertThat(member.nickname).isEqualTo("홍길동")
        assertThat(member.status).isEqualTo(MemberStatus.ACTIVE)
        assertThat(member.refreshToken).isNull()
        assertThat(member.isSocial()).isTrue()
    }

    @Test
    @DisplayName("소셜 회원을 LOCAL 공급자로 만들 수는 없다")
    fun 소셜_회원은_LOCAL이_될_수_없다() {
        assertThatThrownBy { Member.createSocial(MemberProvider.LOCAL, "1", "닉네임", "local_1", "encoded") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    @DisplayName("탈퇴해도 가입 방식과 식별자는 남는다 (같은 구글 계정으로 새 회원이 만들어지지 않게 한다)")
    fun 탈퇴해도_식별자는_남는다() {
        val member = Member.createSocial(MemberProvider.GOOGLE, "1234", "홍길동", "google_1234", "encoded")
        member.updateRefreshToken("refresh")

        member.withdraw()

        assertThat(member.status).isEqualTo(MemberStatus.WITHDRAWN)
        assertThat(member.refreshToken).isNull()
        assertThat(member.provider).isEqualTo(MemberProvider.GOOGLE)
        assertThat(member.providerId).isEqualTo("1234")
    }
}
