package com.back.domain.habit.controller

import com.back.domain.group.dto.GroupRequest
import com.back.domain.habit.dto.CreateHabitRequest
import com.back.domain.member.dto.CreateMemberRequest
import com.back.domain.member.service.AuthTokenService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate

// 습관 상세 조회(GET /api/habits/{id})의 접근 권한을 확인한다. (Testcontainers PostgreSQL, 테스트 종료 시 롤백)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HabitDetailControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jsonMapper: JsonMapper

    @Autowired
    private lateinit var authTokenService: AuthTokenService

    private lateinit var ownerToken: String
    private lateinit var memberToken: String
    private lateinit var outsiderToken: String
    private var habitId: Long = 0L

    @BeforeEach
    fun setUp() {
        ownerToken = authTokenService.createAccessToken(createMember("detail-owner", "방장"))

        val groupResponse = mockMvc.perform(
            post("/api/groups")
                .header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    jsonMapper.writeValueAsString(
                        GroupRequest.Create("상세 조회 그룹", "설명", LocalDate.now().plusMonths(1), "커피 사기", "1234", 5),
                    ),
                ),
        )
            .andExpect(status().isCreated())
            .andReturn().response.contentAsString
        val groupNode = jsonMapper.readTree(groupResponse)
        val groupId = groupNode.get("id").asLong()
        val inviteCode = groupNode.get("inviteCode").asText()

        memberToken = authTokenService.createAccessToken(createMember("detail-member", "멤버"))
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
            .andReturn().response.contentAsString
        habitId = jsonMapper.readTree(habitResponse).get("id").asLong()

        // 이 그룹에 가입하지 않은 회원
        outsiderToken = authTokenService.createAccessToken(createMember("detail-outsider", "외부인"))
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

    @Test
    @DisplayName("습관 주인은 상세 조회할 수 있다")
    fun 본인_습관은_조회된다() {
        mockMvc.perform(
            get("/api/habits/{id}", habitId)
                .header("Authorization", "Bearer $memberToken"),
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(habitId))
    }

    @Test
    @DisplayName("같은 그룹의 다른 멤버(방장)도 상세 조회할 수 있다")
    fun 같은_그룹_멤버는_조회된다() {
        mockMvc.perform(
            get("/api/habits/{id}", habitId)
                .header("Authorization", "Bearer $ownerToken"),
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(habitId))
    }

    @Test
    @DisplayName("그룹에 가입하지 않은 회원은 다른 그룹의 습관을 조회할 수 없다 (403)")
    fun 다른_그룹_회원은_조회할_수_없다() {
        mockMvc.perform(
            get("/api/habits/{id}", habitId)
                .header("Authorization", "Bearer $outsiderToken"),
        )
            .andExpect(status().isForbidden())
    }

    @Test
    @DisplayName("존재하지 않는 습관은 404를 반환한다")
    fun 없는_습관은_404() {
        mockMvc.perform(
            get("/api/habits/{id}", 999_999L)
                .header("Authorization", "Bearer $memberToken"),
        )
            .andExpect(status().isNotFound())
    }
}
