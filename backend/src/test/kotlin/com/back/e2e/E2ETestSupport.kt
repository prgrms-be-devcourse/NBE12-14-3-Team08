package com.back.e2e

import com.back.domain.group.dto.GroupRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.http.HttpClient
import java.nio.charset.StandardCharsets
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

// E2E 테스트 공통 도우미: 실제 서버를 임의 포트로 띄우고 RestClient로 HTTP 요청을 보낸다. (MockMvc가 아니다)
// 트랜잭션 롤백이 없어 실제로 커밋되므로, 테스트가 만든 그룹과 회원은 끝난 뒤 직접 지운다.
// 외부 서비스(Supabase Storage)만 E2EStorageConfig의 가짜로 바꾼다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(E2EStorageConfig::class)
abstract class E2ETestSupport {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    protected lateinit var jdbc: JdbcTemplate

    @Autowired
    protected lateinit var jsonMapper: JsonMapper

    @Value("\${jwt.secret}")
    protected lateinit var jwtSecret: String

    protected class Response(val status: Int, val body: String, private val mapper: JsonMapper) {
        val json: JsonNode by lazy { mapper.readTree(body) }
    }

    protected class Tokens(val accessToken: String, val refreshToken: String)

    protected class GroupInfo(val id: Long, val inviteCode: String)

    // HTTP/1.1로 고정한다. (JDK 클라이언트의 h2c 업그레이드 시도가 본문 있는 요청과 충돌하는 것을 피함)
    private val client: RestClient by lazy {
        RestClient.builder()
            .baseUrl("http://localhost:$port")
            .requestFactory(JdkClientHttpRequestFactory(HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()))
            .build()
    }

    private val suffix = UUID.randomUUID().toString().substring(0, 8)
    private val groupIds = mutableListOf<Long>()
    private val memberIds = mutableListOf<Long>()

    companion object {
        const val PASSWORD = "password1234"
        const val GROUP_PASSWORD = "1234"
        const val GROUP_PENALTY = "커피 사기"
    }

    @AfterEach
    fun cleanUp() {
        groupIds.forEach { groupId ->
            val gmIds = "select id from group_member where group_id = ?"
            jdbc.update("delete from penalty_verify where group_member_id in ($gmIds)", groupId)
            jdbc.update("delete from habit_verify where habit_id in (select id from habit where group_member_id in ($gmIds))", groupId)
            jdbc.update("delete from habit where group_member_id in ($gmIds)", groupId)
            jdbc.update("delete from group_member where group_id = ?", groupId)
            jdbc.update("delete from groups where id = ?", groupId)
        }
        memberIds.forEach { jdbc.update("delete from member where id = ?", it) }
        groupIds.clear()
        memberIds.clear()
    }

    // --- HTTP ---

    protected fun call(method: HttpMethod, path: String, token: String? = null, body: Any? = null): Response {
        val spec = client.method(method).uri(path)
        if (token != null) spec.header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        if (body != null) {
            spec.contentType(MediaType.APPLICATION_JSON)
            spec.body(if (body is String) body else jsonMapper.writeValueAsString(body))
        }
        return spec.exchange { _, res ->
            Response(res.statusCode.value(), res.body.use { String(it.readAllBytes(), StandardCharsets.UTF_8) }, jsonMapper)
        }
    }

    protected fun expectStatus(response: Response, expected: Int, what: String): Response {
        assertThat(response.status).describedAs("$what (응답: ${response.body})").isEqualTo(expected)
        return response
    }

    // --- 시나리오 도우미 (모두 실제 API를 호출한다) ---

    // 회원 가입 후 아이디를 돌려준다.
    protected fun signUp(name: String): String {
        val username = "e2e-$name-$suffix"
        val response = expectStatus(
            call(HttpMethod.POST, "/api/members", body = mapOf("nickname" to name, "username" to username, "password" to PASSWORD)),
            201,
            "회원 가입($name)",
        )
        memberIds += response.json.get("memberId").asLong()
        return username
    }

    protected fun login(username: String): Tokens {
        val response = expectStatus(
            call(HttpMethod.POST, "/api/auth/login", body = mapOf("username" to username, "password" to PASSWORD)),
            200,
            "로그인($username)",
        )
        return Tokens(response.json.get("accessToken").asString(), response.json.get("refreshToken").asString())
    }

    protected fun createGroup(token: String, title: String = "E2E 그룹"): GroupInfo {
        val response = expectStatus(
            call(
                HttpMethod.POST,
                "/api/groups",
                token,
                GroupRequest.Create(title, "설명", LocalDate.now().plusMonths(1), GROUP_PENALTY, GROUP_PASSWORD, 5),
            ),
            201,
            "그룹 생성",
        )
        val id = response.json.get("id").asLong()
        groupIds += id
        return GroupInfo(id, response.json.get("inviteCode").asString())
    }

    protected fun joinGroup(token: String, group: GroupInfo) {
        expectStatus(
            call(HttpMethod.POST, "/api/groups/join/${group.inviteCode}", token, mapOf("password" to GROUP_PASSWORD)),
            200,
            "그룹 가입",
        )
    }

    protected fun createHabit(token: String, groupId: Long, title: String = "물 마시기", days: Int = 3): Long {
        val response = expectStatus(
            call(HttpMethod.POST, "/api/groups/$groupId/habits", token, mapOf("title" to title, "description" to "하루 2L", "days" to days)),
            200,
            "습관 생성",
        )
        return response.json.get("id").asLong()
    }

    // 업로드 서명 URL을 발급받은 뒤, 그 사진 주소로 인증을 제출한다. (실제 업로드는 가짜 저장소가 항상 성공으로 본다)
    protected fun submitVerification(token: String, habitId: Long): Long {
        val upload = expectStatus(
            call(HttpMethod.POST, "/api/storage/habits/$habitId/upload-url", token, mapOf("filename" to "today.png")),
            200,
            "업로드 URL 발급",
        )
        val publicUrl = upload.json.get("publicUrl").asString()
        val response = expectStatus(
            call(HttpMethod.POST, "/api/habits/$habitId/verifications", token, mapOf("description" to "오늘도 완료", "imageUrl" to publicUrl)),
            201,
            "습관 인증 제출",
        )
        return response.json.get("id").asLong()
    }

    protected fun approveVerification(ownerToken: String, verificationId: Long) {
        expectStatus(
            call(HttpMethod.PATCH, "/api/habits/verifications/$verificationId/approve", ownerToken),
            200,
            "습관 인증 승인",
        )
    }

    protected fun groupMemberId(token: String, groupId: Long, username: String): Long {
        val members = expectStatus(call(HttpMethod.GET, "/api/groups/$groupId/members", token), 200, "멤버 목록").json
        return members.first { it.get("username").asString() == username }.get("groupMemberId").asLong()
    }

    // --- 시간 조작 (DB를 직접 고친다) ---
    // 주간 판정은 "가입한 지 1주 경과 + 이번 주 마감이 지남"이어야 하는데 실제로 일주일을 기다릴 수는 없다.

    private fun ts(time: LocalDateTime) = Timestamp.valueOf(time)

    // 그룹은 14일 전(오늘과 같은 요일), 가입과 습관은 10일 전에 만든 것으로 되돌린다.
    protected fun backdateGroup(groupId: Long, groupDays: Long = 14, memberDays: Long = 10, habitDays: Long = 10) {
        val now = LocalDateTime.now()
        jdbc.update("update groups set create_date = ? where id = ?", ts(now.minusDays(groupDays)), groupId)
        jdbc.update("update group_member set create_date = ? where group_id = ?", ts(now.minusDays(memberDays)), groupId)
        jdbc.update(
            "update habit set create_date = ? where group_member_id in (select id from group_member where group_id = ?)",
            ts(now.minusDays(habitDays)),
            groupId,
        )
    }

    // 인증을 며칠 전에 낸 것으로 되돌린다. 날짜별로 한 번만 낼 수 있어서, 같은 날 여러 번 내려면 이렇게 비워 줘야 한다.
    protected fun backdateVerification(verificationId: Long, daysAgo: Long) {
        jdbc.update(
            "update habit_verify set verify_date = ?, create_date = ? where id = ?",
            java.sql.Date.valueOf(LocalDate.now().minusDays(daysAgo)),
            ts(LocalDateTime.now().minusDays(daysAgo)),
            verificationId,
        )
    }
}
