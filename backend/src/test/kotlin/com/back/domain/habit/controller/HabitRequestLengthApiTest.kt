package com.back.domain.habit.controller

import com.back.domain.group.dto.GroupRequest
import com.back.domain.habit.entity.HabitStatus
import com.back.domain.habit.repository.HabitRepository
import com.back.domain.member.dto.CreateMemberRequest
import com.back.domain.member.service.AuthTokenService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.jsonMapper
import java.time.LocalDate

// 습관 생성 API가 제목·설명 길이를 DB에 닿기 전에 400으로 거절하는지 확인한다. (255자까지만 저장 가능한 컬럼)
// 이전에는 256자부터 DB 오류가 그대로 올라와 500이 되었다. (Testcontainers PostgreSQL, 테스트 종료 시 롤백)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HabitRequestLengthApiTes @Autowired constructor(
    private val mockMvc: MockMvc,
    private val jsonMapper: JsonMapper,
    private val authTokenService: AuthTokenService,
    private val habitRepository: HabitRepository
) {

    private var groupId: Long = 0L
    private lateinit var token: String

    @BeforeEach
    fun setUp() {
        val memberResponse = mockMvc.perform(
            post("/api/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(CreateMemberRequest("방장", "length-owner", "password1234"))),
        )
            .andExpect(status().isCreated())
            .andReturn().response.contentAsString
        token = authTokenService.createAccessToken(jsonMapper.readTree(memberResponse).get("memberId").asLong())

        val groupResponse = mockMvc.perform(
            post("/api/groups")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    jsonMapper.writeValueAsString(
                        GroupRequest.Create("길이 테스트 그룹", "설명", LocalDate.now().plusMonths(1), "커피 사기", "1234", 5),
                    ),
                ),
        )
            .andExpect(status().isCreated())
            .andReturn().response.contentAsString
        groupId = jsonMapper.readTree(groupResponse).get("id").asLong()
    }

    private fun createHabit(title: String, description: String): ResultActions =
        mockMvc.perform(
            post("/api/groups/{groupId}/habits", groupId)
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(mapOf("title" to title, "description" to description, "days" to 3))),
        )

    private fun activeHabitCount(): Int = habitRepository.findAllByGroupMember_Group_IdAndStatus(groupId, HabitStatus.ACTIVE).size

    @Test
    @DisplayName("제목이 255자면 생성된다 (컬럼 길이 한계)")
    fun 제목_255자는_생성된다() {
        createHabit("가".repeat(255), "설명").andExpect(status().isOk())

        assertThat(activeHabitCount()).isEqualTo(1)
    }

    @Test
    @DisplayName("설명이 255자면 생성된다 (컬럼 길이 한계)")
    fun 설명_255자는_생성된다() {
        createHabit("독서", "나".repeat(255)).andExpect(status().isOk())

        assertThat(activeHabitCount()).isEqualTo(1)
    }

    @Test
    @DisplayName("제목이 256자면 400과 길이 메시지를 돌려주고 습관을 만들지 않는다")
    fun 제목_256자는_400이다() {
        createHabit("가".repeat(256), "설명")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0]").value("습관 제목은 255자 이하여야 합니다."))

        assertThat(activeHabitCount()).isZero()
    }

    @Test
    @DisplayName("설명이 256자면 400과 길이 메시지를 돌려주고 습관을 만들지 않는다")
    fun 설명_256자는_400이다() {
        createHabit("독서", "나".repeat(256))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0]").value("습관 설명은 255자 이하여야 합니다."))

        assertThat(activeHabitCount()).isZero()
    }

    @Test
    @DisplayName("아주 긴 입력(10,000자)도 500이 아니라 400이다")
    fun 아주_긴_입력도_400이다() {
        createHabit("가".repeat(10_000), "나".repeat(10_000))
            .andExpect(status().isBadRequest())
    }
}
