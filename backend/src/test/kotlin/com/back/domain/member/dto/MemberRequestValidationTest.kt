package com.back.domain.member.dto

import com.back.domain.member.entity.Member
import jakarta.validation.Validation
import jakarta.validation.Validator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.test.util.ReflectionTestUtils

// 요청 DTO의 Bean Validation과 응답 DTO 변환을 확인한다. (통합 테스트는 대표 실패 케이스만 보므로 경계는 여기서 본다)
class MemberRequestValidationTest {

    private val validator: Validator = Validation.buildDefaultValidatorFactory().validator

    private fun messages(target: Any): List<String> = validator.validate(target).map { it.message }

    @Nested
    @DisplayName("가입 요청 CreateMemberRequest")
    inner class Create {

        @Test
        @DisplayName("세 값이 모두 있으면 통과한다")
        fun 정상() {
            assertThat(messages(CreateMemberRequest("닉네임", "user-1", "password1234"))).isEmpty()
        }

        @ParameterizedTest(name = "nickname=\"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = [" ", "   ", "\t", "\n"])
        @DisplayName("닉네임이 null, 빈 값, 공백이면 이름 필수 메시지로 거절한다")
        fun 닉네임(nickname: String?) {
            assertThat(messages(CreateMemberRequest(nickname, "user-1", "password1234")))
                .containsExactly("이름은 필수입니다.")
        }

        @ParameterizedTest(name = "username=\"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = [" ", "   ", "\t", "\n"])
        @DisplayName("아이디가 null, 빈 값, 공백이면 아이디 필수 메시지로 거절한다")
        fun 아이디(username: String?) {
            assertThat(messages(CreateMemberRequest("닉네임", username, "password1234")))
                .containsExactly("아이디는 필수입니다.")
        }

        @ParameterizedTest(name = "password=\"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = [" ", "   ", "\t", "\n"])
        @DisplayName("비밀번호가 null, 빈 값, 공백이면 비밀번호 필수 메시지로 거절한다")
        fun 비밀번호(password: String?) {
            assertThat(messages(CreateMemberRequest("닉네임", "user-1", password)))
                .containsExactly("비밀번호는 필수입니다.")
        }

        @Test
        @DisplayName("아무것도 보내지 않으면 세 항목 메시지를 모두 돌려준다")
        fun 전부_누락() {
            assertThat(messages(CreateMemberRequest()))
                .containsExactlyInAnyOrder("이름은 필수입니다.", "아이디는 필수입니다.", "비밀번호는 필수입니다.")
        }

        @Test
        @DisplayName("값 앞뒤에 공백이 있어도 비어 있지 않으면 통과한다 (현재 동작: 잘라내지 않는다)")
        fun 앞뒤_공백은_통과한다() {
            assertThat(messages(CreateMemberRequest(" 닉네임 ", " user-1 ", " password1234 "))).isEmpty()
        }

        @Test
        @DisplayName("길이와 형식은 검증하지 않는다 (현재 동작: 1글자 비밀번호도 통과)")
        fun 길이는_검증하지_않는다() {
            assertThat(messages(CreateMemberRequest("a", "b", "c"))).isEmpty()
        }
    }

    @Nested
    @DisplayName("로그인 요청 LoginRequest")
    inner class Login {

        @Test
        @DisplayName("아이디와 비밀번호가 있으면 통과한다")
        fun 정상() {
            assertThat(messages(LoginRequest("user-1", "password1234"))).isEmpty()
        }

        @ParameterizedTest(name = "username=\"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = [" ", "\t"])
        @DisplayName("아이디가 비어 있으면 아이디 필수 메시지로 거절한다")
        fun 아이디(username: String?) {
            assertThat(messages(LoginRequest(username, "password1234"))).containsExactly("아이디는 필수입니다.")
        }

        @ParameterizedTest(name = "password=\"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = [" ", "\t"])
        @DisplayName("비밀번호가 비어 있으면 비밀번호 필수 메시지로 거절한다")
        fun 비밀번호(password: String?) {
            assertThat(messages(LoginRequest("user-1", password))).containsExactly("비밀번호는 필수입니다.")
        }

        @Test
        @DisplayName("둘 다 없으면 두 메시지를 모두 돌려준다")
        fun 전부_누락() {
            assertThat(messages(LoginRequest()))
                .containsExactlyInAnyOrder("아이디는 필수입니다.", "비밀번호는 필수입니다.")
        }
    }

    @Nested
    @DisplayName("재발급 요청 RefreshTokenRequest")
    inner class Refresh {

        @Test
        @DisplayName("토큰이 있으면 통과한다")
        fun 정상() {
            assertThat(messages(RefreshTokenRequest("some-token"))).isEmpty()
        }

        @ParameterizedTest(name = "refreshToken=\"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = [" ", "\t"])
        @DisplayName("토큰이 비어 있으면 리프레시 토큰 필수 메시지로 거절한다")
        fun 토큰(token: String?) {
            assertThat(messages(RefreshTokenRequest(token))).containsExactly("리프레시 토큰은 필수입니다.")
        }

        @Test
        @DisplayName("보내지 않아도 객체는 만들어지고 검증에서 거절된다")
        fun 기본값() {
            assertThat(messages(RefreshTokenRequest())).containsExactly("리프레시 토큰은 필수입니다.")
        }
    }

    @Nested
    @DisplayName("수정 요청 UpdateMemberRequest")
    inner class Update {

        @Test
        @DisplayName("검증 제약이 없어 모두 비어 있어도 통과한다 (비어 있으면 서비스가 바꾸지 않고 넘어간다)")
        fun 제약이_없다() {
            assertThat(messages(UpdateMemberRequest())).isEmpty()
            assertThat(messages(UpdateMemberRequest("", "", ""))).isEmpty()
        }
    }

    @Nested
    @DisplayName("응답 DTO")
    inner class Response {

        @Test
        @DisplayName("MemberResponse.from: 비밀번호와 토큰 없이 id, 닉네임, 아이디, 시각만 옮긴다")
        fun 회원_응답() {
            val member = Member.create("닉네임", "user-1", "encoded-password")
            ReflectionTestUtils.setField(member, "id", 7L)

            val response = MemberResponse.from(member)

            assertThat(response.memberId).isEqualTo(7L)
            assertThat(response.nickname).isEqualTo("닉네임")
            assertThat(response.username).isEqualTo("user-1")
            assertThat(response.createDate).isNull()
            assertThat(response.modifyDate).isNull()
            assertThat(MemberResponse::class.java.declaredFields.map { it.name })
                .doesNotContain("password", "refreshToken", "status")
        }

        @Test
        @DisplayName("LoginResponse.of: 토큰 타입은 항상 Bearer다")
        fun 로그인_응답() {
            val response = LoginResponse.of("access", "refresh")

            assertThat(response.accessToken).isEqualTo("access")
            assertThat(response.refreshToken).isEqualTo("refresh")
            assertThat(response.tokenType).isEqualTo("Bearer")
        }
    }
}
