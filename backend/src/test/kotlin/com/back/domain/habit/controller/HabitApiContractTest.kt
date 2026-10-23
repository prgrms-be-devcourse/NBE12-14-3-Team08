package com.back.domain.habit.controller

import com.back.domain.group.dto.GroupRequest
import com.back.domain.habit.dto.CreateHabitRequest
import com.back.domain.member.dto.CreateMemberRequest
import com.back.domain.member.service.AuthTokenService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
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
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate

// Habit API의 응답 모양(JSON 계약), 인증 필요 여부, 입력 경계를 확인하는 통합 테스트다.
// 프론트가 의존하는 필드 이름·null·날짜 형식이 Kotlin 전환이나 리팩터링 중에 바뀌지 않게 고정한다.
// (Testcontainers PostgreSQL, 테스트 종료 시 롤백)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HabitApiContractTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jsonMapper: JsonMapper

    @Autowired
    private lateinit var authTokenService: AuthTokenService

    private var groupId: Long = 0L
    private lateinit var ownerToken: String // 방장 (습관 없음)
    private lateinit var memberToken: String // 일반 멤버 (활성 습관 "물 마시기" 1개 보유)
    private var habitId: Long = 0L

    companion object {
        private val HABIT_KEYS = setOf(
            "id", "createDate", "modifyDate", "groupMemberId", "title", "description", "days", "status",
        )
        private val WEEKLY_KEYS = setOf("habitId", "result", "habitStatus")
        private val ERROR_KEYS = setOf("status", "message", "timestamp")

        // 서버가 시간대 없이 보내는 날짜+시각 (예: 2026-10-23T12:32:09.9547757)
        private val LOCAL_DATE_TIME = Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{1,9})?$""")

        // 오류 응답의 timestamp는 UTC(Z) 표기다. 위 날짜 형식과 다르다는 점도 현재 계약으로 고정한다.
        private val INSTANT = Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{1,9})?Z$""")
    }

    @BeforeEach
    fun setUp() {
        ownerToken = authTokenService.createAccessToken(createMember("contract-owner", "방장"))

        val groupResponse = mockMvc.perform(
            post("/api/groups")
                .header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    jsonMapper.writeValueAsString(
                        GroupRequest.Create("계약 테스트 그룹", "설명", LocalDate.now().plusMonths(1), "커피 사기", "1234", 5),
                    ),
                ),
        )
            .andExpect(status().isCreated())
            .andReturn().response.contentAsString
        val groupNode = jsonMapper.readTree(groupResponse)
        groupId = groupNode.get("id").asLong()
        val inviteCode = groupNode.get("inviteCode").asText()

        memberToken = authTokenService.createAccessToken(createMember("contract-member", "멤버"))
        mockMvc.perform(
            post("/api/groups/join/{inviteCode}", inviteCode)
                .header("Authorization", "Bearer $memberToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(GroupRequest.Join("1234"))),
        )
            .andExpect(status().isOk())

        val habitResponse = mockMvc.perform(
            post("/api/groups/{groupId}/habits", groupId)
                .header("Authorization", "Bearer $memberToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(CreateHabitRequest("물 마시기", "하루 2L", 3))),
        )
            .andExpect(status().isOk())
            .andReturn().response.contentAsString
        habitId = jsonMapper.readTree(habitResponse).get("id").asLong()
    }

    private fun createMember(username: String, nickname: String): Long {
        val response = mockMvc.perform(
            post("/api/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(CreateMemberRequest(nickname, username, "password1234"))),
        )
            .andExpect(status().isCreated())
            .andReturn().response.contentAsString
        return jsonMapper.readTree(response).get("memberId").asLong()
    }

    private fun json(result: ResultActions): JsonNode =
        jsonMapper.readTree(result.andReturn().response.contentAsString)

    private fun keys(node: JsonNode): Set<String> = node.properties().map { it.key }.toSet()

    private fun createHabitAsOwner(body: String): ResultActions =
        mockMvc.perform(
            post("/api/groups/{groupId}/habits", groupId)
                .header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )

    private fun assertHabitShape(node: JsonNode) {
        assertThat(keys(node)).isEqualTo(HABIT_KEYS)
        assertThat(node.get("id").isNumber).isTrue()
        assertThat(node.get("groupMemberId").isNumber).isTrue()
        assertThat(node.get("days").isNumber).isTrue()
        assertThat(node.get("title").isString).isTrue()
        assertThat(node.get("status").isString).isTrue()
        assertThat(node.get("createDate").asString()).matches(LOCAL_DATE_TIME.toPattern())
        assertThat(node.get("modifyDate").asString()).matches(LOCAL_DATE_TIME.toPattern())
    }

    @Nested
    @DisplayName("응답 JSON 계약")
    inner class ResponseContract {

        @Test
        @DisplayName("습관 생성 응답은 정해진 필드를 모두 가진다")
        fun 생성_응답_계약() {
            val node = json(
                createHabitAsOwner(jsonMapper.writeValueAsString(CreateHabitRequest("독서", "30분", 5)))
                    .andExpect(status().isOk()),
            )

            assertHabitShape(node)
            assertThat(node.get("title").asString()).isEqualTo("독서")
            assertThat(node.get("description").asString()).isEqualTo("30분")
            assertThat(node.get("days").asInt()).isEqualTo(5)
            assertThat(node.get("status").asString()).isEqualTo("ACTIVE")
        }

        @Test
        @DisplayName("설명을 보내지 않으면 description 키는 유지되고 값은 null이다")
        fun 설명이_없으면_null로_내려온다() {
            val node = json(
                createHabitAsOwner("""{"title":"독서","days":5}""").andExpect(status().isOk()),
            )

            assertThat(keys(node)).isEqualTo(HABIT_KEYS)
            assertThat(node.get("description").isNull).isTrue()
        }

        @Test
        @DisplayName("습관 상세 응답은 생성 응답과 같은 필드를 가진다")
        fun 상세_응답_계약() {
            val node = json(
                mockMvc.perform(get("/api/habits/{id}", habitId).header("Authorization", "Bearer $memberToken"))
                    .andExpect(status().isOk()),
            )

            assertHabitShape(node)
            assertThat(node.get("id").asLong()).isEqualTo(habitId)
        }

        @Test
        @DisplayName("활성 습관 조회 응답은 습관 응답과 같은 필드를 가진다")
        fun 활성_습관_응답_계약() {
            val node = json(
                mockMvc.perform(
                    get("/api/groups/{groupId}/habits/active", groupId).header("Authorization", "Bearer $memberToken"),
                ).andExpect(status().isOk()),
            )

            assertHabitShape(node)
            assertThat(node.get("status").asString()).isEqualTo("ACTIVE")
        }

        @Test
        @DisplayName("실패 습관 목록은 습관 응답의 배열이고 status는 FAILED다")
        fun 실패_습관_목록_응답_계약() {
            mockMvc.perform(post("/api/habits/fail/{habitId}", habitId).header("Authorization", "Bearer $memberToken"))
                .andExpect(status().isOk())

            val node = json(
                mockMvc.perform(
                    get("/api/groups/{groupId}/habits/fail", groupId).header("Authorization", "Bearer $memberToken"),
                ).andExpect(status().isOk()),
            )

            assertThat(node.isArray).isTrue()
            assertThat(node.size()).isEqualTo(1)
            assertHabitShape(node.get(0))
            assertThat(node.get(0).get("status").asString()).isEqualTo("FAILED")
        }

        @Test
        @DisplayName("습관이 없으면 실패 습관 목록은 빈 배열이다")
        fun 실패_습관이_없으면_빈_배열이다() {
            val node = json(
                mockMvc.perform(
                    get("/api/groups/{groupId}/habits/fail", groupId).header("Authorization", "Bearer $memberToken"),
                ).andExpect(status().isOk()),
            )

            assertThat(node.isArray).isTrue()
            assertThat(node.size()).isZero()
        }

        @Test
        @DisplayName("주간 판정 응답은 habitId, result, habitStatus만 가진다 (가입 직후라 TOO_EARLY)")
        fun 주간_판정_응답_계약() {
            val node = json(
                mockMvc.perform(
                    post("/api/groups/{groupId}/weekly-check", groupId).header("Authorization", "Bearer $ownerToken"),
                ).andExpect(status().isOk()),
            )

            assertThat(node.isArray).isTrue()
            assertThat(node.size()).isEqualTo(1)
            val item = node.get(0)
            assertThat(keys(item)).isEqualTo(WEEKLY_KEYS)
            assertThat(item.get("habitId").asLong()).isEqualTo(habitId)
            assertThat(item.get("result").asString()).isEqualTo("TOO_EARLY")
            assertThat(item.get("habitStatus").asString()).isEqualTo("ACTIVE")
        }

        @Test
        @DisplayName("Habit API의 오류 응답은 status, message, timestamp만 가진다 (timestamp는 UTC 표기)")
        fun 오류_응답_계약() {
            val node = json(
                createHabitAsOwner(jsonMapper.writeValueAsString(CreateHabitRequest("독서", "30분", 5)))
                    .andExpect(status().isOk()),
            )
            assertThat(node).isNotNull()

            // 같은 방장이 두 번째 습관을 만들면 409
            val error = json(
                createHabitAsOwner(jsonMapper.writeValueAsString(CreateHabitRequest("운동", "1시간", 5)))
                    .andExpect(status().isConflict()),
            )

            assertThat(keys(error)).isEqualTo(ERROR_KEYS)
            assertThat(error.get("status").asInt()).isEqualTo(409)
            assertThat(error.get("message").asString()).isEqualTo("이미 활성화된 습관이 존재합니다.")
            assertThat(error.get("timestamp").asString()).matches(INSTANT.toPattern())
        }
    }

    @Nested
    @DisplayName("인증이 필요한 경로")
    inner class AuthRequired {

        @ParameterizedTest(name = "{0} {1}")
        @CsvSource(
            "POST,/api/groups/1/habits",
            "POST,/api/habits/fail/1",
            "GET,/api/habits/1",
            "GET,/api/groups/1/habits/active",
            "GET,/api/groups/1/habits/fail",
            "POST,/api/groups/1/weekly-check",
        )
        @DisplayName("토큰이 없으면 401이다")
        fun 토큰이_없으면_401이다(method: String, path: String) {
            mockMvc.perform(
                request(HttpMethod.valueOf(method), path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"),
            )
                .andExpect(status().isUnauthorized())
        }

        @ParameterizedTest(name = "{0} {1}")
        @CsvSource(
            "POST,/api/groups/1/habits",
            "POST,/api/habits/fail/1",
            "GET,/api/habits/1",
            "GET,/api/groups/1/habits/active",
            "GET,/api/groups/1/habits/fail",
            "POST,/api/groups/1/weekly-check",
        )
        @DisplayName("잘못된 토큰이면 401이다")
        fun 잘못된_토큰이면_401이다(method: String, path: String) {
            mockMvc.perform(
                request(HttpMethod.valueOf(method), path)
                    .header("Authorization", "Bearer invalid.token.value")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"),
            )
                .andExpect(status().isUnauthorized())
        }
    }

    @Nested
    @DisplayName("입력 경계")
    inner class InputBoundary {

        @Test
        @DisplayName("제목이 255자면 생성된다 (컬럼 길이 한계)")
        fun 제목_255자는_생성된다() {
            createHabitAsOwner(jsonMapper.writeValueAsString(CreateHabitRequest("가".repeat(255), "설명", 3)))
                .andExpect(status().isOk())
        }

        @Test
        @DisplayName("설명이 255자면 생성된다 (컬럼 길이 한계)")
        fun 설명_255자는_생성된다() {
            createHabitAsOwner(jsonMapper.writeValueAsString(CreateHabitRequest("독서", "나".repeat(255), 3)))
                .andExpect(status().isOk())
        }

        @Test
        @DisplayName("본문이 없으면 400이다")
        fun 본문이_없으면_400이다() {
            mockMvc.perform(
                post("/api/groups/{groupId}/habits", groupId)
                    .header("Authorization", "Bearer $ownerToken")
                    .contentType(MediaType.APPLICATION_JSON),
            )
                .andExpect(status().isBadRequest())
        }

        @Test
        @DisplayName("필드 타입이 틀리면(days에 문자) 400이다")
        fun 필드_타입이_틀리면_400이다() {
            createHabitAsOwner("""{"title":"독서","days":"많이"}""")
                .andExpect(status().isBadRequest())
        }

        @ParameterizedTest(name = "id={0}")
        @ValueSource(strings = ["0", "-1", "999999"])
        @DisplayName("존재하지 않는 습관 id로 상세를 조회하면 404다")
        fun 없는_id는_404다(id: String) {
            mockMvc.perform(get("/api/habits/{id}", id).header("Authorization", "Bearer $memberToken"))
                .andExpect(status().isNotFound())
        }

        // --- 이전에 500이던 버그를 기록해 둔 테스트. 길이 검증이 추가되어(#55) 활성화했다. ---

        @Test
        @DisplayName("제목이 256자 이상이면 400이다")
        fun 제목_256자는_400이다() {
            createHabitAsOwner(jsonMapper.writeValueAsString(CreateHabitRequest("가".repeat(256), "설명", 3)))
                .andExpect(status().isBadRequest())
        }

        @Test
        @DisplayName("설명이 256자 이상이면 400이다")
        fun 설명_256자는_400이다() {
            createHabitAsOwner(jsonMapper.writeValueAsString(CreateHabitRequest("독서", "나".repeat(256), 3)))
                .andExpect(status().isBadRequest())
        }

        @ParameterizedTest(name = "{0} {1}")
        @CsvSource(
            "GET,/api/habits/abc",
            "GET,/api/habits/99999999999999999999",
            "POST,/api/habits/fail/abc",
            "GET,/api/groups/abc/habits/active",
            "GET,/api/groups/abc/habits/fail",
            "POST,/api/groups/abc/weekly-check",
        )
        @DisplayName("경로 변수가 숫자가 아니면 400이다")
        fun 경로_변수가_숫자가_아니면_400이다(method: String, path: String) {
            mockMvc.perform(request(HttpMethod.valueOf(method), path).header("Authorization", "Bearer $memberToken"))
                .andExpect(status().isBadRequest())
        }
    }
}
