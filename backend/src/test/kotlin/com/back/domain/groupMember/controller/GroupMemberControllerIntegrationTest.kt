package com.back.domain.groupMember.controller

import com.back.domain.member.entity.Member
import com.back.domain.member.repository.MemberRepository
import com.back.domain.member.service.AuthTokenService
import com.back.global.entity.requiredId
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.util.JsonPathExpectationsHelper
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GroupMemberControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var authTokenService: AuthTokenService

    @Nested
    @DisplayName("초대 코드로 그룹 조회")
    inner class GetGroupByInviteCode {

        @Nested
        @DisplayName("성공")
        inner class Success {

            @Test
            @DisplayName("유효한 초대 코드로 그룹 정보를 조회할 수 있다")
            fun getGroupByInviteCode_WithValidToken() {
                val accessToken =
                    createMemberAndGetToken("test-invite-lookup")

                val groupId = createGroup(accessToken)
                val inviteCode = getInviteCode(groupId, accessToken)

                mockMvc.perform(
                    get("/api/groups/invite/{inviteCode}", inviteCode)
                        .header("Authorization", "Bearer $accessToken")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.id").value(groupId.toInt()))
                    .andExpect(jsonPath("$.inviteCode").value(inviteCode))
            }
        }

        @Nested
        @DisplayName("예외")
        inner class Failure {

            @Test
            @DisplayName("인증 토큰이 없으면 초대 코드로 그룹을 조회할 수 없다")
            fun getGroupByInviteCode_WithoutToken() {
                mockMvc.perform(
                    get("/api/groups/invite/{inviteCode}", "invalid-code")
                )
                    .andExpect(status().isUnauthorized)
            }
        }
    }

    @Nested
    @DisplayName("그룹 가입")
    inner class JoinGroup {

        @Nested
        @DisplayName("성공")
        inner class Success {

            @Test
            @DisplayName("유효한 초대 코드와 비밀번호로 그룹에 가입할 수 있다")
            fun joinGroup_WithValidToken() {
                val ownerToken = createMemberAndGetToken("test-join-owner")
                val groupId = createGroup(ownerToken)
                val inviteCode = getInviteCode(groupId, ownerToken)
                val memberToken = createMemberAndGetToken("test-join-member")

                joinGroup(inviteCode, memberToken)

                mockMvc.perform(
                    get("/api/groups/{groupId}/members", groupId)
                        .header("Authorization", "Bearer $memberToken")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.length()").value(2))
            }
        }

        @Nested
        @DisplayName("예외")
        inner class Failure {

            @Test
            @DisplayName("인증 토큰이 없으면 그룹 가입에 실패한다")
            fun joinGroup_WithoutToken() {
                mockMvc.perform(
                    post("/api/groups/join/{inviteCode}", "invalid-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """
                            {
                                "password": "pass123"
                            }
                            """.trimIndent()
                        )
                )
                    .andExpect(status().isUnauthorized)
            }
        }
    }

    @Nested
    @DisplayName("그룹 멤버 목록 조회")
    inner class GetGroupMembers {

        @Nested
        @DisplayName("성공")
        inner class Success {

            @Test
            @DisplayName("인증된 회원은 가입한 그룹의 멤버 목록을 조회할 수 있다")
            fun getGroupMembers_WithValidToken() {
                val accessToken =
                    createMemberAndGetToken("test-group-member-list")

                val groupId = createGroup(accessToken)

                mockMvc.perform(
                    get("/api/groups/{groupId}/members", groupId)
                        .header("Authorization", "Bearer $accessToken")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$").isArray)
            }
        }

        @Nested
        @DisplayName("예외")
        inner class Failure {

            @Test
            @DisplayName("인증 토큰이 없으면 그룹 멤버 목록 조회에 실패한다")
            fun getGroupMembers_WithoutToken() {
                mockMvc.perform(
                    get("/api/groups/{groupId}/members", 1L)
                )
                    .andExpect(status().isUnauthorized)
            }
        }
    }

    @Nested
    @DisplayName("그룹 멤버 상세 조회")
    inner class GetGroupMemberDetail {

        @Nested
        @DisplayName("성공")
        inner class Success {

            @Test
            @DisplayName("그룹 멤버의 상세 정보를 조회할 수 있다")
            fun getGroupMemberDetail_WithValidToken() {
                val accessToken =
                    createMemberAndGetToken("test-member-detail")

                val groupId = createGroup(accessToken)
                val groupMemberId = getGroupMemberId(groupId, accessToken, 0)

                mockMvc.perform(
                    get(
                        "/api/groups/{groupId}/members/{groupMemberId}",
                        groupId,
                        groupMemberId
                    )
                        .header("Authorization", "Bearer $accessToken")
                )
                    .andExpect(status().isOk)
                    .andExpect(
                        jsonPath("$.groupMemberId")
                            .value(groupMemberId.toInt())
                    )
            }
        }

        @Nested
        @DisplayName("예외")
        inner class Failure {

            @Test
            @DisplayName("인증 토큰이 없으면 멤버 상세 조회에 실패한다")
            fun getGroupMemberDetail_WithoutToken() {
                mockMvc.perform(
                    get(
                        "/api/groups/{groupId}/members/{groupMemberId}",
                        1L,
                        1L
                    )
                )
                    .andExpect(status().isUnauthorized)
            }
        }
    }

    @Nested
    @DisplayName("그룹 탈퇴")
    inner class LeaveGroup {

        @Nested
        @DisplayName("성공")
        inner class Success {

            @Test
            @DisplayName("일반 멤버는 가입한 그룹에서 탈퇴할 수 있다")
            fun leaveGroup_WithValidToken() {
                val ownerToken =
                    createMemberAndGetToken("test-leave-owner")

                val groupId = createGroup(ownerToken)
                val inviteCode = getInviteCode(groupId, ownerToken)
                val memberToken =
                    createMemberAndGetToken("test-leave-member")

                joinGroup(inviteCode, memberToken)

                mockMvc.perform(
                    delete("/api/groups/{groupId}/leave", groupId)
                        .header("Authorization", "Bearer $memberToken")
                )
                    .andExpect(status().isOk)

                mockMvc.perform(
                    get("/api/groups/{groupId}/members", groupId)
                        .header("Authorization", "Bearer $ownerToken")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.length()").value(1))
            }
        }

        @Nested
        @DisplayName("예외")
        inner class Failure {

            @Test
            @DisplayName("인증 토큰이 없으면 그룹 탈퇴에 실패한다")
            fun leaveGroup_WithoutToken() {
                mockMvc.perform(
                    delete("/api/groups/{groupId}/leave", 1L)
                )
                    .andExpect(status().isUnauthorized)
            }
        }
    }

    @Nested
    @DisplayName("그룹 멤버 강퇴")
    inner class KickMember {

        @Nested
        @DisplayName("성공")
        inner class Success {

            @Test
            @DisplayName("방장은 그룹의 일반 멤버를 강퇴할 수 있다")
            fun kickMember_WithOwnerToken() {
                val ownerToken =
                    createMemberAndGetToken("test-kick-owner")

                val groupId = createGroup(ownerToken)
                val inviteCode = getInviteCode(groupId, ownerToken)
                val memberToken =
                    createMemberAndGetToken("test-kick-member")

                joinGroup(inviteCode, memberToken)

                val targetGroupMemberId =
                    getGroupMemberId(groupId, ownerToken, 1)

                mockMvc.perform(
                    delete(
                        "/api/groups/{groupId}/members/{groupMemberId}/kick",
                        groupId,
                        targetGroupMemberId
                    )
                        .header("Authorization", "Bearer $ownerToken")
                )
                    .andExpect(status().isNoContent)

                mockMvc.perform(
                    get("/api/groups/{groupId}/members", groupId)
                        .header("Authorization", "Bearer $ownerToken")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.length()").value(1))
            }
        }

        @Nested
        @DisplayName("예외")
        inner class Failure {

            @Test
            @DisplayName("인증 토큰이 없으면 멤버 강퇴에 실패한다")
            fun kickMember_WithoutToken() {
                mockMvc.perform(
                    delete(
                        "/api/groups/{groupId}/members/{groupMemberId}/kick",
                        1L,
                        2L
                    )
                )
                    .andExpect(status().isUnauthorized)
            }
        }
    }

    @Nested
    @DisplayName("방장 권한 위임")
    inner class TransferOwner {

        @Nested
        @DisplayName("성공")
        inner class Success {

            @Test
            @DisplayName("방장은 다른 멤버에게 방장 권한을 위임할 수 있다")
            fun transferOwner_WithOwnerToken() {
                val ownerToken =
                    createMemberAndGetToken("test-delegate-owner")

                val groupId = createGroup(ownerToken)
                val inviteCode = getInviteCode(groupId, ownerToken)
                val memberToken =
                    createMemberAndGetToken("test-delegate-member")

                joinGroup(inviteCode, memberToken)

                val targetGroupMemberId =
                    getGroupMemberId(groupId, ownerToken, 1)

                mockMvc.perform(
                    patch(
                        "/api/groups/{groupId}/members/{groupMemberId}/delegate",
                        groupId,
                        targetGroupMemberId
                    )
                        .header("Authorization", "Bearer $ownerToken")
                )
                    .andExpect(status().isOk)
            }
        }

        @Nested
        @DisplayName("예외")
        inner class Failure {

            @Test
            @DisplayName("인증 토큰이 없으면 방장 권한 위임에 실패한다")
            fun transferOwner_WithoutToken() {
                mockMvc.perform(
                    patch(
                        "/api/groups/{groupId}/members/{groupMemberId}/delegate",
                        1L,
                        2L
                    )
                )
                    .andExpect(status().isUnauthorized)
            }
        }
    }

    private fun createMemberAndGetToken(username: String): String {
        val member = memberRepository.save(
            Member.create(username, username, "encoded-password")
        )

        return authTokenService.createAccessToken(member.requiredId)
    }

    private fun createGroup(accessToken: String): Long {
        val requestBody = """
            {
                "title": "테스트 그룹",
                "description": "테스트 설명",
                "deadline": "2026-12-31",
                "penalty": "벌칙",
                "password": "pass123",
                "memberLimit": 5
            }
        """.trimIndent()

        val result = mockMvc.perform(
            post("/api/groups")
                .header("Authorization", "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isCreated)
            .andReturn()

        return extractLong(result, "$.id")
    }

    private fun getInviteCode(
        groupId: Long,
        accessToken: String
    ): String {
        val result = mockMvc.perform(
            get("/api/groups/{groupId}", groupId)
                .header("Authorization", "Bearer $accessToken")
        )
            .andExpect(status().isOk)
            .andReturn()

        return extractValue(result, "$.inviteCode")
    }

    private fun getGroupMemberId(
        groupId: Long,
        accessToken: String,
        index: Int
    ): Long {
        val result = mockMvc.perform(
            get("/api/groups/{groupId}/members", groupId)
                .header("Authorization", "Bearer $accessToken")
        )
            .andExpect(status().isOk)
            .andReturn()

        return extractLong(result, "$[$index].groupMemberId")
    }

    private fun joinGroup(
        inviteCode: String,
        accessToken: String
    ) {
        val requestBody = """
            {
                "password": "pass123"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/groups/join/{inviteCode}", inviteCode)
                .header("Authorization", "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isOk)
    }

    private fun extractLong(
        result: MvcResult,
        jsonPath: String
    ): Long = extractValue(result, jsonPath).toLong()

    private fun extractValue(
        result: MvcResult,
        jsonPath: String
    ): String {
        val responseBody = result.response.contentAsString
        val value = JsonPathExpectationsHelper(jsonPath)
            .evaluateJsonPath(responseBody)

        return requireNotNull(value).toString()
    }
}