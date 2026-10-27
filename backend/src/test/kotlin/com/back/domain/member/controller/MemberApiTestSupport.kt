package com.back.domain.member.controller

import com.back.domain.member.repository.MemberRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

// Member API 통합 테스트 공통 도우미. 실제 보안 필터와 PostgreSQL(Testcontainers)을 거치고, 테스트가 끝나면 롤백한다.
// 기존 MockMvc 테스트와 같은 설정이라 스프링 컨텍스트를 새로 만들지 않는다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
abstract class MemberApiTestSupport {

    @Autowired protected lateinit var mockMvc: MockMvc
    @Autowired protected lateinit var jsonMapper: JsonMapper
    @Autowired protected lateinit var memberRepository: MemberRepository
    @Autowired protected lateinit var passwordEncoder: PasswordEncoder

    protected class Tokens(val accessToken: String, val refreshToken: String)

    companion object {
        const val PASSWORD = "password1234"
    }

    protected fun perform(method: HttpMethod, path: String, token: String? = null, body: Any? = null): ResultActions {
        val builder = request(method, path)
        if (token != null) builder.header("Authorization", "Bearer $token")
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON)
            builder.content(if (body is String) body else jsonMapper.writeValueAsString(body))
        }
        return mockMvc.perform(builder)
    }

    protected fun json(result: ResultActions): JsonNode =
        jsonMapper.readTree(result.andReturn().response.contentAsString)

    protected fun keys(node: JsonNode): Set<String> = node.properties().map { it.key }.toSet()

    protected fun signUp(username: String, nickname: String = "닉네임", password: String = PASSWORD): ResultActions =
        perform(
            HttpMethod.POST,
            "/api/members",
            body = mapOf("nickname" to nickname, "username" to username, "password" to password),
        )

    // 가입에 성공했는지 확인하고 회원 id를 돌려준다.
    protected fun signUpOk(username: String, nickname: String = "닉네임", password: String = PASSWORD): Long {
        val result = signUp(username, nickname, password)
        assertEquals(201, result.andReturn().response.status, "가입 실패: ${result.andReturn().response.contentAsString}")
        return jsonMapper.readTree(result.andReturn().response.contentAsString).get("memberId").asLong()
    }

    protected fun login(username: String, password: String = PASSWORD): ResultActions =
        perform(HttpMethod.POST, "/api/auth/login", body = mapOf("username" to username, "password" to password))

    protected fun loginOk(username: String, password: String = PASSWORD): Tokens {
        val result = login(username, password)
        assertEquals(200, result.andReturn().response.status, "로그인 실패: ${result.andReturn().response.contentAsString}")
        val node = jsonMapper.readTree(result.andReturn().response.contentAsString)
        return Tokens(node.get("accessToken").asString(), node.get("refreshToken").asString())
    }
}
