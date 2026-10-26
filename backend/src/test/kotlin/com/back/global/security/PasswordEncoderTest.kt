package com.back.global.security

import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

// SecurityConfig가 등록하는 PasswordEncoder가 BCrypt로 해시하고 검증하는지 확인한다.
class PasswordEncoderTest {

    private val encoder = SecurityConfig(mockk()).passwordEncoder()

    @Test
    @DisplayName("BCrypt 인코더이고 해시는 BCrypt 형식(60자, \$2 접두어)이며 평문을 담지 않는다")
    fun 해시_형식() {
        val hash = encoder.encode("password1234")

        assertThat(encoder).isInstanceOf(BCryptPasswordEncoder::class.java)
        assertThat(hash).hasSize(60).startsWith("\$2").doesNotContain("password1234")
    }

    @Test
    @DisplayName("같은 비밀번호도 솔트가 달라 매번 다른 해시가 나오지만 모두 검증을 통과한다")
    fun 솔트() {
        val first = encoder.encode("password1234")
        val second = encoder.encode("password1234")

        assertThat(first).isNotEqualTo(second)
        assertThat(encoder.matches("password1234", first)).isTrue()
        assertThat(encoder.matches("password1234", second)).isTrue()
    }

    @Test
    @DisplayName("다른 비밀번호, 대소문자가 다른 비밀번호, 앞뒤 공백이 붙은 비밀번호는 일치하지 않는다")
    fun 불일치() {
        val hash = encoder.encode("Password1234")

        assertThat(encoder.matches("password1234", hash)).isFalse()
        assertThat(encoder.matches("Password1234 ", hash)).isFalse()
        assertThat(encoder.matches("", hash)).isFalse()
    }

    @Test
    @DisplayName("평문을 그대로 저장한 값(해시가 아닌 값)과는 일치하지 않는다")
    fun 평문_저장값() {
        assertThat(encoder.matches("password1234", "password1234")).isFalse()
    }

    @Test
    @DisplayName("한글과 특수문자 비밀번호도 해시하고 검증한다")
    fun 유니코드() {
        val hash = encoder.encode("비밀번호!@#1234")

        assertThat(encoder.matches("비밀번호!@#1234", hash)).isTrue()
        assertThat(encoder.matches("비밀번호!@#1235", hash)).isFalse()
    }

    @Test
    @DisplayName("72바이트까지는 해시할 수 있다")
    fun 경계값_72바이트() {
        val password = "a".repeat(72)

        assertThat(encoder.matches(password, encoder.encode(password))).isTrue()
    }

    @Test
    @DisplayName("72바이트를 넘으면 IllegalArgumentException으로 거절한다 (BCrypt 제한. 비밀번호 최대 길이를 정해야 하는 이유)")
    fun 경계값_73바이트() {
        assertThatThrownBy { encoder.encode("a".repeat(73)) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    @DisplayName("한글은 글자당 3바이트라 25자(75바이트)부터 거절된다 (글자 수가 아니라 바이트 수 기준)")
    fun 한글_비밀번호_길이() {
        assertThat(encoder.matches("가".repeat(24), encoder.encode("가".repeat(24)))).isTrue()
        assertThatThrownBy { encoder.encode("가".repeat(25)) }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
