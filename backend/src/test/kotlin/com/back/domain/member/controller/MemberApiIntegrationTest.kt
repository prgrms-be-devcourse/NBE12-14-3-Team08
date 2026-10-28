package com.back.domain.member.controller

import com.back.domain.member.entity.MemberStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.http.HttpMethod
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

// 회원 가입, 로그인, 내 정보 조회·수정, 토큰 재발급 API 통합 테스트. (탈퇴는 MemberWithdrawIntegrationTest)
// 응답 JSON 모양과 DB 저장 상태, 인증이 필요한 경로를 실제 요청으로 확인한다.
class MemberApiIntegrationTest : MemberApiTestSupport() {

    // 검증 오류 응답의 errors 배열을 문자열 목록으로 읽는다.
    private fun errorMessages(node: tools.jackson.databind.JsonNode): List<String> {
        val messages = mutableListOf<String>()
        node.get("errors").forEach { messages.add(it.asString()) }
        return messages
    }

    companion object {
        private val MEMBER_KEYS = setOf("memberId", "nickname", "username", "provider", "createDate", "modifyDate")
        private val LOGIN_KEYS = setOf("accessToken", "refreshToken", "tokenType")
        private val ERROR_KEYS = setOf("status", "message", "timestamp")
        private val LOCAL_DATE_TIME = Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{1,9})?$""")
        private const val LOGIN_FAIL_MESSAGE = "아이디 또는 비밀번호가 일치하지 않습니다."
    }

    @Nested
    @DisplayName("응답 JSON 계약")
    inner class ResponseContract {

        @Test
        @DisplayName("가입 응답은 정해진 필드만 가지고 비밀번호와 리프레시 토큰은 담지 않는다")
        fun 가입_응답_계약() {
            val node = json(signUp("contract-user", "계약").andExpect(status().isCreated()))

            assertThat(keys(node)).isEqualTo(MEMBER_KEYS)
            assertThat(node.get("memberId").isNumber).isTrue()
            assertThat(node.get("nickname").asString()).isEqualTo("계약")
            assertThat(node.get("username").asString()).isEqualTo("contract-user")
            assertThat(node.get("provider").asString()).isEqualTo("LOCAL")
            assertThat(node.get("createDate").asString()).matches(LOCAL_DATE_TIME.toPattern())
            assertThat(node.get("modifyDate").asString()).matches(LOCAL_DATE_TIME.toPattern())
        }

        @Test
        @DisplayName("내 정보 응답은 가입 응답과 같은 필드를 가진다")
        fun 내_정보_응답_계약() {
            signUpOk("contract-me")
            val tokens = loginOk("contract-me")

            val node = json(perform(HttpMethod.GET, "/api/members/me", tokens.accessToken).andExpect(status().isOk()))

            assertThat(keys(node)).isEqualTo(MEMBER_KEYS)
            assertThat(node.toString()).doesNotContain("password").doesNotContain("refresh")
        }

        @Test
        @DisplayName("수정 응답도 같은 필드를 가지고 비밀번호는 담지 않는다")
        fun 수정_응답_계약() {
            signUpOk("contract-patch")
            val tokens = loginOk("contract-patch")

            val node = json(
                perform(HttpMethod.PATCH, "/api/members/me", tokens.accessToken, mapOf("nickname" to "새닉네임"))
                    .andExpect(status().isOk()),
            )

            assertThat(keys(node)).isEqualTo(MEMBER_KEYS)
            assertThat(node.toString()).doesNotContain("password")
        }

        @Test
        @DisplayName("로그인 응답은 accessToken, refreshToken, tokenType만 가지고 tokenType은 Bearer다")
        fun 로그인_응답_계약() {
            signUpOk("contract-login")

            val node = json(login("contract-login").andExpect(status().isOk()))

            assertThat(keys(node)).isEqualTo(LOGIN_KEYS)
            assertThat(node.get("tokenType").asString()).isEqualTo("Bearer")
            assertThat(node.get("accessToken").asString()).isNotBlank()
            assertThat(node.get("refreshToken").asString()).isNotBlank()
        }

        @Test
        @DisplayName("오류 응답은 status, message, timestamp만 가진다 (검증 오류는 errors가 더해진다)")
        fun 오류_응답_계약() {
            signUpOk("contract-error")

            val conflict = json(signUp("contract-error").andExpect(status().isConflict()))
            assertThat(keys(conflict)).isEqualTo(ERROR_KEYS)
            assertThat(conflict.get("status").asInt()).isEqualTo(409)
            assertThat(conflict.get("message").asString()).isEqualTo("이미 사용 중인 아이디입니다.")

            val invalid = json(perform(HttpMethod.POST, "/api/members", body = "{}").andExpect(status().isBadRequest()))
            assertThat(keys(invalid)).isEqualTo(ERROR_KEYS + "errors")
        }
    }

    @Nested
    @DisplayName("회원 가입 POST /api/members")
    inner class Join {

        @Test
        @DisplayName("가입하면 ACTIVE 회원이 저장되고 비밀번호는 BCrypt로 암호화되며 리프레시 토큰이 함께 저장된다")
        fun 가입_성공() {
            val memberId = signUpOk("join-user", "가입자")

            val member = memberRepository.findById(memberId).orElseThrow()
            assertThat(member.username).isEqualTo("join-user")
            assertThat(member.nickname).isEqualTo("가입자")
            assertThat(member.status).isEqualTo(MemberStatus.ACTIVE)
            assertThat(member.password).isNotEqualTo(PASSWORD).startsWith("$2")
            assertThat(passwordEncoder.matches(PASSWORD, member.password)).isTrue()
            assertThat(member.refreshToken).isNotBlank()
        }

        @Test
        @DisplayName("가입 때 저장된 리프레시 토큰이 로그인 응답의 리프레시 토큰과 같다")
        fun 가입_때_저장한_리프레시_토큰을_로그인에서_돌려준다() {
            val memberId = signUpOk("join-token")
            val stored = memberRepository.findById(memberId).orElseThrow().refreshToken

            assertThat(loginOk("join-token").refreshToken).isEqualTo(stored)
        }

        @Test
        @DisplayName("이미 쓰는 아이디로 가입하면 409이고 회원이 늘어나지 않는다")
        fun 중복_아이디는_409() {
            signUpOk("dup-user")
            val before = memberRepository.count()

            signUp("dup-user", "다른사람")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 사용 중인 아이디입니다."))

            assertThat(memberRepository.count()).isEqualTo(before)
        }

        @Test
        @DisplayName("닉네임은 중복되어도 가입된다")
        fun 닉네임_중복은_허용된다() {
            signUpOk("nick-a", "같은닉네임")
            signUp("nick-b", "같은닉네임").andExpect(status().isCreated())
        }

        @Test
        @DisplayName("아이디의 대소문자가 다르면 다른 아이디로 가입된다 (현재 동작)")
        fun 아이디_대소문자는_구분된다() {
            signUpOk("CaseUser")
            signUp("caseuser").andExpect(status().isCreated())
        }

        @Test
        @DisplayName("본문이 비어 있으면 세 필드의 필수 메시지가 모두 담긴 400이다")
        fun 빈_본문은_모든_필수_메시지를_돌려준다() {
            val node = json(perform(HttpMethod.POST, "/api/members", body = "{}").andExpect(status().isBadRequest()))

            assertThat(errorMessages(node)).containsExactlyInAnyOrder("이름은 필수입니다.", "아이디는 필수입니다.", "비밀번호는 필수입니다.")
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(
            "닉네임이 공백뿐,'   ',valid-user,password1234,이름은 필수입니다.",
            "아이디가 공백뿐,닉네임,'   ',password1234,아이디는 필수입니다.",
            "비밀번호가 공백뿐,닉네임,valid-user,'   ',비밀번호는 필수입니다.",
            "닉네임이 빈 문자열,'',valid-user,password1234,이름은 필수입니다.",
        )
        @DisplayName("값이 비어 있으면 해당 필드의 메시지로 400이고 회원을 만들지 않는다")
        fun 비어_있는_값은_400이다(case: String, nickname: String, username: String, password: String, message: String) {
            val before = memberRepository.count()

            val node = json(signUp(username, nickname, password).andExpect(status().isBadRequest()))

            assertThat(errorMessages(node)).containsExactly(message)
            assertThat(memberRepository.count()).isEqualTo(before)
        }

        @Test
        @DisplayName("본문이 없으면 400이다")
        fun 본문이_없으면_400이다() {
            perform(HttpMethod.POST, "/api/members").andExpect(status().isBadRequest())
        }
    }

    @Nested
    @DisplayName("로그인 POST /api/auth/login")
    inner class Login {

        @Test
        @DisplayName("올바른 아이디와 비밀번호면 토큰을 돌려주고 액세스 토큰으로 내 정보를 조회할 수 있다")
        fun 로그인_성공() {
            signUpOk("login-user", "로그인")
            val tokens = loginOk("login-user")

            perform(HttpMethod.GET, "/api/members/me", tokens.accessToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("login-user"))
        }

        @Test
        @DisplayName("로그인을 여러 번 해도 리프레시 토큰은 같다 (현재 동작: 로그인으로 갱신되지 않음)")
        fun 리프레시_토큰은_로그인으로_바뀌지_않는다() {
            signUpOk("login-same")

            assertThat(loginOk("login-same").refreshToken).isEqualTo(loginOk("login-same").refreshToken)
        }

        @Test
        @DisplayName("없는 아이디와 틀린 비밀번호는 같은 401 메시지를 돌려준다 (계정 존재 여부를 알려주지 않음)")
        fun 아이디_없음과_비밀번호_틀림은_같은_메시지다() {
            signUpOk("login-fail")

            val unknownUser = json(login("no-such-user").andExpect(status().isUnauthorized()))
            val wrongPassword = json(login("login-fail", "wrong-password").andExpect(status().isUnauthorized()))

            assertThat(unknownUser.get("message").asString()).isEqualTo(LOGIN_FAIL_MESSAGE)
            assertThat(wrongPassword.get("message").asString()).isEqualTo(LOGIN_FAIL_MESSAGE)
        }

        @Test
        @DisplayName("아이디나 비밀번호가 비어 있으면 400이다")
        fun 빈_값은_400이다() {
            val node = json(perform(HttpMethod.POST, "/api/auth/login", body = "{}").andExpect(status().isBadRequest()))

            assertThat(errorMessages(node))
                .containsExactlyInAnyOrder("아이디는 필수입니다.", "비밀번호는 필수입니다.")
        }

        @Test
        @DisplayName("탈퇴한 회원은 올바른 비밀번호로도 로그인할 수 없고, 탈퇴 여부는 알려주지 않는 일반 실패 메시지다")
        fun 탈퇴한_회원은_로그인할_수_없다() {
            val memberId = signUpOk("login-withdrawn")
            memberRepository.findById(memberId).orElseThrow().withdraw()
            memberRepository.flush()

            login("login-withdrawn")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(LOGIN_FAIL_MESSAGE))
        }
    }

    @Nested
    @DisplayName("내 정보 /api/members/me")
    inner class Me {

        @ParameterizedTest(name = "{0} {1}")
        @CsvSource("GET,/api/members/me", "PATCH,/api/members/me", "DELETE,/api/members/me")
        @DisplayName("토큰이 없거나 잘못되면 401이다")
        fun 인증이_필요하다(method: String, path: String) {
            perform(HttpMethod.valueOf(method), path, body = "{}").andExpect(status().isUnauthorized())
            perform(HttpMethod.valueOf(method), path, "invalid.token.value", body = "{}").andExpect(status().isUnauthorized())
        }

        @Test
        @DisplayName("닉네임만 수정하면 닉네임이 바뀌고 비밀번호는 그대로다")
        fun 닉네임만_수정() {
            signUpOk("me-nick", "예전닉네임")
            val tokens = loginOk("me-nick")

            perform(HttpMethod.PATCH, "/api/members/me", tokens.accessToken, mapOf("nickname" to "새닉네임"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("새닉네임"))

            loginOk("me-nick")
            perform(HttpMethod.GET, "/api/members/me", tokens.accessToken).andExpect(jsonPath("$.nickname").value("새닉네임"))
        }

        @Test
        @DisplayName("공백뿐인 닉네임은 무시되어 닉네임이 바뀌지 않는다")
        fun 공백_닉네임은_무시된다() {
            signUpOk("me-blank", "원래닉네임")
            val tokens = loginOk("me-blank")

            perform(HttpMethod.PATCH, "/api/members/me", tokens.accessToken, mapOf("nickname" to "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("원래닉네임"))
        }

        @Test
        @DisplayName("본문이 비어 있는 수정(빈 객체)은 아무것도 바꾸지 않고 200이다")
        fun 빈_객체_수정은_변경이_없다() {
            signUpOk("me-empty", "그대로")
            val tokens = loginOk("me-empty")

            perform(HttpMethod.PATCH, "/api/members/me", tokens.accessToken, "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("그대로"))
            loginOk("me-empty")
        }

        @Test
        @DisplayName("비밀번호를 바꾸면 새 비밀번호로 로그인되고 이전 비밀번호는 거절되며 DB에는 BCrypt로 저장된다")
        fun 비밀번호_변경() {
            val memberId = signUpOk("me-pw")
            val tokens = loginOk("me-pw")

            perform(
                HttpMethod.PATCH,
                "/api/members/me",
                tokens.accessToken,
                mapOf("currentPassword" to PASSWORD, "newPassword" to "newPassword5678"),
            ).andExpect(status().isOk())

            loginOk("me-pw", "newPassword5678")
            login("me-pw", PASSWORD).andExpect(status().isUnauthorized())
            val stored = memberRepository.findById(memberId).orElseThrow().password
            assertThat(stored).startsWith("$2")
            assertThat(passwordEncoder.matches("newPassword5678", stored)).isTrue()
        }

        @Test
        @DisplayName("닉네임과 비밀번호를 함께 바꿀 수 있다")
        fun 닉네임과_비밀번호_동시_수정() {
            signUpOk("me-both", "이전")
            val tokens = loginOk("me-both")

            perform(
                HttpMethod.PATCH,
                "/api/members/me",
                tokens.accessToken,
                mapOf("nickname" to "이후", "currentPassword" to PASSWORD, "newPassword" to "newPassword5678"),
            )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("이후"))

            loginOk("me-both", "newPassword5678")
        }

        @Test
        @DisplayName("새 비밀번호만 보내고 현재 비밀번호가 없으면 400이고 비밀번호는 바뀌지 않는다")
        fun 현재_비밀번호가_없으면_400이다() {
            signUpOk("me-nocurrent")
            val tokens = loginOk("me-nocurrent")

            perform(HttpMethod.PATCH, "/api/members/me", tokens.accessToken, mapOf("newPassword" to "newPassword5678"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("기존 비밀번호를 입력해주세요."))

            loginOk("me-nocurrent")
        }

        @Test
        @DisplayName("현재 비밀번호가 틀리면 400이고 비밀번호는 바뀌지 않는다")
        fun 현재_비밀번호가_틀리면_400이다() {
            signUpOk("me-wrongcurrent")
            val tokens = loginOk("me-wrongcurrent")

            perform(
                HttpMethod.PATCH,
                "/api/members/me",
                tokens.accessToken,
                mapOf("currentPassword" to "wrong-password", "newPassword" to "newPassword5678"),
            )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("기존 비밀번호가 일치하지 않습니다."))

            loginOk("me-wrongcurrent")
            login("me-wrongcurrent", "newPassword5678").andExpect(status().isUnauthorized())
        }
    }

    @Nested
    @DisplayName("토큰 재발급 POST /api/auth/refresh")
    inner class Refresh {

        @Test
        @DisplayName("저장된 리프레시 토큰으로 재발급받으면 새 액세스 토큰으로 내 정보를 조회할 수 있다")
        fun 재발급_성공() {
            signUpOk("refresh-user")
            val tokens = loginOk("refresh-user")

            val node = json(
                perform(HttpMethod.POST, "/api/auth/refresh", body = mapOf("refreshToken" to tokens.refreshToken))
                    .andExpect(status().isOk()),
            )

            assertThat(node.get("refreshToken").asString()).isEqualTo(tokens.refreshToken)
            perform(HttpMethod.GET, "/api/members/me", node.get("accessToken").asString()).andExpect(status().isOk())
        }

        @Test
        @DisplayName("액세스 토큰을 재발급에 쓰면 401이다")
        fun 액세스_토큰으로는_재발급할_수_없다() {
            signUpOk("refresh-access")
            val tokens = loginOk("refresh-access")

            perform(HttpMethod.POST, "/api/auth/refresh", body = mapOf("refreshToken" to tokens.accessToken))
                .andExpect(status().isUnauthorized())
        }

        @Test
        @DisplayName("리프레시 토큰이 형식에 맞지 않으면 401이고 비어 있으면 400이다")
        fun 잘못된_리프레시_토큰() {
            perform(HttpMethod.POST, "/api/auth/refresh", body = mapOf("refreshToken" to "not-a-token"))
                .andExpect(status().isUnauthorized())
            perform(HttpMethod.POST, "/api/auth/refresh", body = "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("리프레시 토큰은 필수입니다."))
        }

        @Test
        @DisplayName("회원 상태가 WITHDRAWN이면 저장된 토큰이 남아 있어도 재발급되지 않는다")
        fun 탈퇴_상태의_회원은_재발급되지_않는다() {
            val memberId = signUpOk("refresh-withdrawn")
            val tokens = loginOk("refresh-withdrawn")
            // 상태만 바꾸고 토큰은 남겨 둔다. (탈퇴 처리가 토큰을 지우는 것과 별개로 상태 확인이 막아야 한다)
            val member = memberRepository.findById(memberId).orElseThrow()
            ReflectionTestUtils.setField(member, "status", MemberStatus.WITHDRAWN)
            memberRepository.flush()

            perform(HttpMethod.POST, "/api/auth/refresh", body = mapOf("refreshToken" to tokens.refreshToken))
                .andExpect(status().isUnauthorized())
        }
    }

    @Nested
    @DisplayName("입력 경계")
    inner class InputBoundary {

        @Test
        @DisplayName("닉네임과 아이디가 255자면 가입된다 (컬럼 길이 한계)")
        fun 닉네임_아이디_255자는_가입된다() {
            signUp("u".repeat(255), "가".repeat(255)).andExpect(status().isCreated())
        }

        // --- 한계를 넘으면 DB 오류(500)가 아니라 400이다. (메시지와 수정 경로까지는 MemberRequestLengthApiTest에서 본다) ---

        @Test
        @DisplayName("닉네임이 256자 이상이면 400이다")
        fun 닉네임_256자는_400이다() {
            signUp("long-nick", "가".repeat(256)).andExpect(status().isBadRequest())
        }

        @Test
        @DisplayName("아이디가 256자 이상이면 400이다")
        fun 아이디_256자는_400이다() {
            signUp("u".repeat(256)).andExpect(status().isBadRequest())
        }
    }
}
