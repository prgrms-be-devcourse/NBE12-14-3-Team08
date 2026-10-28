package com.back.global.auth

import com.back.domain.group.entity.Group
import com.back.domain.group.repository.GroupRepository
import com.back.domain.member.entity.Member
import com.back.domain.member.repository.MemberRepository
import com.back.domain.member.service.AuthTokenService
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

// 실제 보안 필터 체인을 거쳐서, 인증 실패는 401 / 권한 부족은 403으로 구분되는지 확인한다.
// 프론트의 토큰 자동 갱신은 401일 때만 동작한다. (Testcontainers PostgreSQL, 테스트 종료 시 롤백)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthenticationFailureStatusTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val authTokenService: AuthTokenService,
    private val memberRepository: MemberRepository,
    private val groupRepository: GroupRepository
) {

    @Value("\${jwt.secret}")
    private lateinit var jwtSecret: String

    private lateinit var member: Member

    // 저장된 회원의 id. 저장 전에는 null이라 Long? 로 받아 둔다.
    private val memberId: Long
        get() = requireNotNull(member.id) { "저장된 회원이어야 합니다." }

    companion object {
        private const val PROTECTED_URL = "/api/members/me"
    }

    @BeforeEach
    fun setUp() {
        val suffix = UUID.randomUUID().toString().substring(0, 8)
        member = memberRepository.save(Member.create("sec-$suffix", "sec-$suffix", "pw1234!"))
    }

    private fun bearer(token: String): String = "Bearer $token"

    private fun secretKey(): SecretKey = Keys.hmacShaKeyFor(jwtSecret.toByteArray(StandardCharsets.UTF_8))

    @Test
    @DisplayName("유효한 액세스 토큰이면 보호된 API에 접근할 수 있다 (대조군)")
    fun 유효한_토큰은_통과한다() {
        mockMvc.perform(
            get(PROTECTED_URL)
                .header("Authorization", bearer(authTokenService.createAccessToken(memberId))),
        )
            .andExpect(status().isOk())
    }

    @Test
    @DisplayName("토큰이 없으면 401")
    fun 토큰이_없으면_401() {
        mockMvc.perform(get(PROTECTED_URL))
            .andExpect(status().isUnauthorized())
    }

    @Test
    @DisplayName("형식이 잘못된 토큰이면 401")
    fun 잘못된_토큰이면_401() {
        mockMvc.perform(get(PROTECTED_URL).header("Authorization", bearer("abc.def.ghi")))
            .andExpect(status().isUnauthorized())
    }

    @Test
    @DisplayName("서명이 다른(위조된) 토큰이면 401")
    fun 위조된_토큰이면_401() {
        val otherKey = Keys.hmacShaKeyFor(
            "another-secret-key-another-secret-key-1234567890".toByteArray(StandardCharsets.UTF_8),
        )
        val forged = Jwts.builder()
            .subject(memberId.toString())
            .claim("tokenType", "access")
            .expiration(Date(System.currentTimeMillis() + 60_000))
            .signWith(otherKey)
            .compact()

        mockMvc.perform(get(PROTECTED_URL).header("Authorization", bearer(forged)))
            .andExpect(status().isUnauthorized())
    }

    @Test
    @DisplayName("만료된 액세스 토큰이면 401 (프론트가 이 응답으로 토큰을 갱신한다)")
    fun 만료된_토큰이면_401() {
        val expired = Jwts.builder()
            .subject(memberId.toString())
            .claim("tokenType", "access")
            .issuedAt(Date(System.currentTimeMillis() - 3_600_000))
            .expiration(Date(System.currentTimeMillis() - 1_800_000))
            .signWith(secretKey())
            .compact()

        mockMvc.perform(get(PROTECTED_URL).header("Authorization", bearer(expired)))
            .andExpect(status().isUnauthorized())
    }

    @Test
    @DisplayName("리프레시 토큰을 액세스 토큰 자리에 쓰면 401")
    fun 리프레시_토큰으로는_접근할_수_없다() {
        val refreshToken = authTokenService.createRefreshToken(memberId)

        mockMvc.perform(get(PROTECTED_URL).header("Authorization", bearer(refreshToken)))
            .andExpect(status().isUnauthorized())
    }

    @Test
    @DisplayName("탈퇴한 회원의 토큰이면 401")
    fun 탈퇴한_회원의_토큰이면_401() {
        val token = authTokenService.createAccessToken(memberId)
        member.withdraw()
        memberRepository.saveAndFlush(member)

        mockMvc.perform(get(PROTECTED_URL).header("Authorization", bearer(token)))
            .andExpect(status().isUnauthorized())
    }

    @Test
    @DisplayName("로그인은 했지만 권한이 없으면(다른 그룹 조회) 그대로 403")
    fun 권한이_없으면_403() {
        val group = groupRepository.save(
            Group(
                title = "권한 테스트 그룹",
                penalty = "커피 사기",
                password = "1234",
                inviteCode = "S" + UUID.randomUUID().toString().substring(0, 8),
                memberLimit = 5,
                deadline = LocalDate.now().plusDays(7),
            ),
        )

        mockMvc.perform(
            get("/api/groups/{id}", group.id)
                .header("Authorization", bearer(authTokenService.createAccessToken(memberId))),
        )
            .andExpect(status().isForbidden())
    }
}
