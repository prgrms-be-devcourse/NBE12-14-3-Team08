package com.back.global.security

import com.back.global.exception.ForbiddenException
import com.back.global.exception.GlobalExceptionHandler
import com.back.global.exception.UnauthorizedException
import com.back.global.webMvc.WebMvcConfig
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

// 주의: 이 테스트는 중첩 @SpringBootConfiguration(TestApplication)을 가지고 있어서,
// 같은 패키지(com.back.global.security)에 @SpringBootTest를 두면 컨트롤러 없는 이 최소 설정을 집어 간다.
// 그래서 보안 동작을 확인하는 @SpringBootTest 테스트는 com.back.global.auth 패키지에 둔다.
@WebMvcTest(controllers = [AuthAndExceptionTest.TestController::class])
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(
    classes = [
        AuthAndExceptionTest.TestApplication::class,
        AuthAndExceptionTest.TestController::class,
        WebMvcConfig::class,
        LoginMemberArgumentResolver::class,
        GlobalExceptionHandler::class,
    ],
)
class AuthAndExceptionTest {

    @SpringBootConfiguration
    class TestApplication

    @Autowired
    private lateinit var mockMvc: MockMvc

    // --- 테스트 전용 Dummy Controller & DTO ---
    @RestController
    class TestController {

        @PostMapping("/api/test/validation")
        fun checkValidation(@RequestBody @Valid dto: DummyDto): String = dto.name

        @GetMapping("/api/test/401")
        fun throw401() {
            throw UnauthorizedException("로그인이 필요합니다.")
        }

        @GetMapping("/api/test/403")
        fun throw403() {
            throw ForbiddenException("접근 권한이 없습니다.")
        }

        @GetMapping("/api/test/404")
        fun throw404() {
            throw NoSuchElementException("데이터를 찾을 수 없습니다.")
        }

        @GetMapping("/api/test/409")
        fun throw409() {
            throw IllegalStateException("이미 진행 중인 상태입니다.")
        }

        @GetMapping("/api/test/500")
        fun throw500() {
            throw RuntimeException("DB 연결 문자열이 노출되면 안 되는 내부 오류")
        }
    }

    data class DummyDto(
        @field:NotBlank(message = "이름은 필수입니다.")
        val name: String,
    )

    // --- 2. GlobalExceptionHandler 공통 예외 응답 규격 검증 ---
    @Nested
    @DisplayName("GlobalExceptionHandler 예외 매핑 테스트")
    inner class ExceptionHandlerTests {

        @Test
        @DisplayName("401 UnauthorizedException 처리")
        fun unauthorized() {
            mockMvc.perform(get("/api/test/401"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."))
        }

        @Test
        @DisplayName("403 ForbiddenException 처리")
        fun forbidden() {
            mockMvc.perform(get("/api/test/403"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("접근 권한이 없습니다."))
                .andExpect(jsonPath("$.timestamp").exists())
                // 일반 에러 응답에는 errors 키 자체가 없다 (@Valid 실패일 때만 포함)
                // jsonPath(...).doesNotExist()는 "errors": null도 통과시키므로 본문 문자열로 확인한다
                .andExpect(content().string(not(containsString("\"errors\""))))
        }

        @Test
        @DisplayName("404 NoSuchElementException 처리")
        fun notFound() {
            mockMvc.perform(get("/api/test/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("데이터를 찾을 수 없습니다."))
        }

        @Test
        @DisplayName("409 IllegalStateException 처리")
        fun conflict() {
            mockMvc.perform(get("/api/test/409"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("이미 진행 중인 상태입니다."))
        }

        @Test
        @DisplayName("400 @Valid 유효성 검증 실패 시 errors 리스트 반환")
        fun validationError() {
            mockMvc.perform(
                post("/api/test/validation")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"\"}"),
            )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("입력값 검증에 실패했습니다."))
                .andExpect(jsonPath("$.errors[0]").value("이름은 필수입니다."))
        }

        @Test
        @DisplayName("400 깨진 JSON 본문 전달 시 HttpMessageNotReadableException 처리")
        fun notReadableJson() {
            mockMvc.perform(
                post("/api/test/validation")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{ invalid: json }"),
            )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("요청 본문(JSON) 형식이 올바르지 않습니다."))
        }

        @Test
        @DisplayName("500 처리하지 못한 예외는 내부 메시지를 숨기고 고정 메시지로 응답")
        fun unexpectedException() {
            mockMvc.perform(get("/api/test/500"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."))
        }
    }
}
