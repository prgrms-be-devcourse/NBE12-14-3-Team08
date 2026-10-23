package com.back.global.auth

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

// 주의: 이 패키지에 두는 이유 - com.back.global.security 패키지에는 중첩 @SpringBootConfiguration을 가진
// AuthAndExceptionTest(패키지 선언이 폴더와 다름)가 있어, 거기에 두면 @SpringBootTest가 최소 설정을 집어 간다.
// SecurityConfig의 경로 규칙, 인증 헤더 해석, CORS가 바뀌지 않았는지 확인하는 특성 테스트다.
// (Kotlin 전환 전후로 같은 결과가 나와야 한다. Testcontainers PostgreSQL 사용)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @ParameterizedTest(name = "POST {0}은 토큰 없이 컨트롤러까지 도달한다")
    @ValueSource(strings = ["/api/members", "/api/auth/login", "/api/auth/refresh"])
    @DisplayName("공개 POST 경로는 토큰 없이 접근할 수 있다 (빈 본문이라 검증 오류 400까지 도달)")
    fun 공개_POST_경로는_토큰_없이_접근할_수_있다(path: String) {
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest())
    }

    @ParameterizedTest(name = "GET {0}은 토큰 없이 접근할 수 있다")
    @ValueSource(strings = ["/actuator/health", "/api/connection-test", "/swagger-ui/index.html", "/v3/api-docs"])
    @DisplayName("공개 GET 경로는 401, 403으로 막히지 않는다")
    fun 공개_GET_경로는_막히지_않는다(path: String) {
        val statusCode = mockMvc.perform(get(path)).andReturn().response.status

        assertThat(statusCode).isNotIn(401, 403)
    }

    @Test
    @DisplayName("루트(/)는 토큰 없이 Swagger 문서로 리다이렉트된다")
    fun 루트는_스웨거로_리다이렉트된다() {
        mockMvc.perform(get("/"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", "/swagger-ui/index.html"))
    }

    @ParameterizedTest(name = "{0} {1}은 토큰이 없으면 401")
    @CsvSource(
        "GET,/api/members/me",
        "DELETE,/api/members/me",
        "GET,/api/groups",
        "GET,/api/groups/invite/abc",
        "GET,/api/members",
        "GET,/api/auth/login",
        "GET,/api/없는-경로",
    )
    @DisplayName("그 외 경로와 공개 경로의 다른 메서드는 토큰이 없으면 401이다")
    fun 보호된_경로는_토큰이_없으면_401이다(method: String, path: String) {
        mockMvc.perform(request(HttpMethod.valueOf(method), path))
            .andExpect(status().isUnauthorized())
    }

    @ParameterizedTest(name = "Authorization: \"{0}\"는 401")
    @ValueSource(strings = ["Bearer", "Bearer ", "bearer abc", "Basic abc", "Token abc", "Bearer not-a-jwt"])
    @DisplayName("Bearer 형식이 아니거나 JWT가 아닌 인증 헤더는 401이다")
    fun 잘못된_인증_헤더는_401이다(header: String) {
        mockMvc.perform(get("/api/members/me").header("Authorization", header))
            .andExpect(status().isUnauthorized())
    }

    @ParameterizedTest(name = "Origin {0}에서 온 사전 요청(preflight)은 허용된다")
    @ValueSource(strings = ["http://localhost:3000", "https://my-app.vercel.app", "https://cdpn.io"])
    @DisplayName("허용된 출처의 사전 요청은 토큰 없이 통과하고 CORS 헤더를 돌려준다")
    fun 허용된_출처의_사전_요청은_통과한다(origin: String) {
        mockMvc.perform(
            options("/api/groups")
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type"),
        )
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", origin))
            .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
    }

    @Test
    @DisplayName("허용되지 않은 출처의 사전 요청은 403이다")
    fun 허용되지_않은_출처의_사전_요청은_403이다() {
        mockMvc.perform(
            options("/api/groups")
                .header("Origin", "https://evil.example.com")
                .header("Access-Control-Request-Method", "POST"),
        )
            .andExpect(status().isForbidden())
    }

    @Test
    @DisplayName("X-Frame-Options는 SAMEORIGIN이다 (현재 설정을 고정. 변경 시 이 테스트도 같이 수정)")
    fun 프레임_옵션은_같은_출처만_허용한다() {
        mockMvc.perform(get("/api/connection-test"))
            .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
    }
}
