package com.back.global.exception

import com.back.domain.member.dto.CreateMemberRequest
import com.back.domain.member.service.AuthTokenService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import org.hamcrest.Matchers.containsString
import tools.jackson.databind.json.JsonMapper

// Spring MVC가 원래 4xx로 바꿔 주는 오류(없는 경로, 허용되지 않는 메서드, 본문 형식, 경로 변수·쿼리 형식)가
// 전역 예외 처리의 Exception 일괄 처리에 가려 500이 되지 않는지 확인한다. (이슈 #56)
// 응답 본문은 다른 오류와 같은 모양(status, message, timestamp)이어야 한다.
// 습관 API의 경로 변수 400은 HabitApiContractTest가 확인하므로 여기서는 다른 도메인을 본다.
// (Testcontainers PostgreSQL, 테스트 종료 시 롤백)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SpringMvcErrorStatusTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jsonMapper: JsonMapper

    @Autowired
    private lateinit var authTokenService: AuthTokenService

    private lateinit var token: String

    @BeforeEach
    fun setUp() {
        val response = mockMvc.perform(
            post("/api/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(CreateMemberRequest("회원", "mvc-error-member", "password1234"))),
        )
            .andExpect(status().isCreated())
            .andReturn().response.contentAsString
        token = authTokenService.createAccessToken(jsonMapper.readTree(response).get("memberId").asLong())
    }

    // 상태 코드와 본문의 status 숫자, 고정 메시지가 함께 맞는지 확인한다
    private fun ResultActions.andExpectError(status: Int, message: String): ResultActions =
        andExpect(status().`is`(status))
            .andExpect(jsonPath("$.status").value(status))
            .andExpect(jsonPath("$.message").value(message))
            .andExpect(jsonPath("$.timestamp").exists())

    @Test
    @DisplayName("없는 경로는 404다")
    fun 없는_경로는_404다() {
        mockMvc.perform(get("/api/no-such-path").header("Authorization", "Bearer $token"))
            .andExpectError(404, "요청한 주소를 찾을 수 없습니다.")
    }

    @Test
    @DisplayName("허용되지 않는 메서드는 405이고, 가능한 메서드를 Allow 헤더로 알려 준다")
    fun 허용되지_않는_메서드는_405다() {
        mockMvc.perform(delete("/api/habits/1").header("Authorization", "Bearer $token"))
            .andExpectError(405, "허용되지 않는 요청 방식입니다.")
            .andExpect(header().string(HttpHeaders.ALLOW, containsString("GET")))
    }

    @Test
    @DisplayName("지원하지 않는 본문 형식은 415다")
    fun 지원하지_않는_본문_형식은_415다() {
        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.TEXT_PLAIN)
                .content("username=a&password=b"),
        )
            .andExpectError(415, "지원하지 않는 요청 형식입니다.")
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["/api/groups/abc", "/api/penalties/abc", "/api/groups/99999999999999999999"])
    @DisplayName("경로 변수가 숫자가 아니면 400이다 (습관 외 도메인)")
    fun 경로_변수가_숫자가_아니면_400이다(path: String) {
        mockMvc.perform(get(path).header("Authorization", "Bearer $token"))
            .andExpectError(400, "요청 값의 형식이 올바르지 않습니다.")
    }

    @Test
    @DisplayName("쿼리 값이 정해진 값이 아니면 400이다")
    fun 쿼리_값_형식이_틀리면_400이다() {
        mockMvc.perform(get("/api/groups").param("status", "WRONG").header("Authorization", "Bearer $token"))
            .andExpectError(400, "요청 값의 형식이 올바르지 않습니다.")
    }
}
