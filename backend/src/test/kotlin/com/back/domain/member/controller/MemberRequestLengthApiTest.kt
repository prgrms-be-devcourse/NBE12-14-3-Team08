package com.back.domain.member.controller

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

// 가입·수정 API가 닉네임·아이디 길이를 DB에 닿기 전에 400으로 거절하는지 확인한다. (255자까지만 저장 가능한 컬럼)
// 이전에는 256자부터 DB 오류가 그대로 올라와 500이 되었다. (Testcontainers PostgreSQL, 테스트 종료 시 롤백)
class MemberRequestLengthApiTest : MemberApiTestSupport() {

    private fun nicknameOf(username: String): String? = memberRepository.findByUsername(username).orElse(null)?.nickname

    private fun exists(username: String): Boolean = memberRepository.existsByUsername(username)

    @Test
    @DisplayName("닉네임이 256자면 400과 길이 메시지를 돌려주고 회원을 만들지 않는다")
    fun 닉네임_256자는_400이다() {
        signUp("len-nick", "가".repeat(256))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0]").value("이름은 255자 이하여야 합니다."))

        assertThat(exists("len-nick")).isFalse()
    }

    @Test
    @DisplayName("아이디가 256자면 400과 길이 메시지를 돌려주고 회원을 만들지 않는다")
    fun 아이디_256자는_400이다() {
        val username = "u".repeat(256)

        signUp(username)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0]").value("아이디는 255자 이하여야 합니다."))

        assertThat(exists(username)).isFalse()
    }

    @Test
    @DisplayName("닉네임과 아이디가 모두 길면 두 메시지를 모두 돌려준다")
    fun 둘_다_길면_두_메시지다() {
        val node = json(signUp("u".repeat(256), "가".repeat(256)).andExpect(status().isBadRequest()))

        val messages = mutableListOf<String>()
        node.get("errors").forEach { messages.add(it.asString()) }
        assertThat(messages)
            .containsExactlyInAnyOrder("이름은 255자 이하여야 합니다.", "아이디는 255자 이하여야 합니다.")
    }

    @Test
    @DisplayName("아주 긴 입력(10,000자)도 500이 아니라 400이다")
    fun 아주_긴_입력도_400이다() {
        signUp("u".repeat(10_000), "가".repeat(10_000)).andExpect(status().isBadRequest())
    }

    @Test
    @DisplayName("이모지는 두 글자로 세어 128개(256글자)부터 400이다 (현재 동작: 검증은 UTF-16 글자 수 기준이라 DB 한도보다 먼저 막힌다)")
    fun 이모지는_두_글자로_센다() {
        signUp("emoji-ok", "😀".repeat(127)).andExpect(status().isCreated())
        signUp("emoji-long", "😀".repeat(128)).andExpect(status().isBadRequest())
    }

    @Test
    @DisplayName("닉네임을 256자로 수정하면 400이고 기존 닉네임이 그대로다")
    fun 닉네임_수정_256자는_400이다() {
        signUpOk("len-patch", "원래닉네임")
        val tokens = loginOk("len-patch")

        perform(HttpMethod.PATCH, "/api/members/me", tokens.accessToken, mapOf("nickname" to "가".repeat(256)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0]").value("이름은 255자 이하여야 합니다."))

        assertThat(nicknameOf("len-patch")).isEqualTo("원래닉네임")
    }

    @Test
    @DisplayName("닉네임을 255자로 수정하면 바뀐다")
    fun 닉네임_수정_255자는_성공한다() {
        signUpOk("len-patch-ok", "원래닉네임")
        val tokens = loginOk("len-patch-ok")

        perform(HttpMethod.PATCH, "/api/members/me", tokens.accessToken, mapOf("nickname" to "나".repeat(255)))
            .andExpect(status().isOk())

        assertThat(nicknameOf("len-patch-ok")).isEqualTo("나".repeat(255))
    }

    @Test
    @DisplayName("닉네임 없이 수정 요청을 보내도 기존처럼 닉네임은 그대로이고 성공한다 (비밀번호만 바꾸는 경우 등)")
    fun 닉네임_없는_수정은_그대로다() {
        signUpOk("len-patch-none", "원래닉네임")
        val tokens = loginOk("len-patch-none")

        perform(HttpMethod.PATCH, "/api/members/me", tokens.accessToken, mapOf("currentPassword" to PASSWORD, "newPassword" to "newPassword1234"))
            .andExpect(status().isOk())

        assertThat(nicknameOf("len-patch-none")).isEqualTo("원래닉네임")
    }
}
