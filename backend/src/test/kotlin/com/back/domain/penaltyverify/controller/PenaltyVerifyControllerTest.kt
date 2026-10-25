package com.back.domain.penaltyverify.controller

import com.back.domain.group.entity.Group
import com.back.domain.group.repository.GroupRepository
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.habit.entity.Habit
import com.back.domain.habit.repository.HabitRepository
import com.back.domain.member.entity.Member
import com.back.domain.member.repository.MemberRepository
import com.back.domain.member.service.AuthTokenService
import com.back.domain.penaltyverify.dto.BulkActionRequest
import com.back.domain.penaltyverify.dto.SubmitPenaltyVerifyRequest
import com.back.domain.penaltyverify.entity.PenaltyVerify
import com.back.domain.penaltyverify.repository.PenaltyVerifyRepository
import com.back.global.exception.StorageUnavailableException
import com.back.global.storage.StorageService
import com.back.global.storage.UploadUrlRequest
import com.back.global.storage.UploadUrlResponse
import com.back.global.storage.VerificationImageType
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PenaltyVerifyControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jsonMapper: JsonMapper
    @Autowired private lateinit var memberRepository: MemberRepository
    @Autowired private lateinit var authTokenService: AuthTokenService
    @Autowired private lateinit var groupRepository: GroupRepository
    @Autowired private lateinit var groupMemberRepository: GroupMemberRepository
    @Autowired private lateinit var habitRepository: HabitRepository
    @Autowired private lateinit var penaltyVerifyRepository: PenaltyVerifyRepository

    // 인증 사진 URL 검사(Supabase 호출)는 StorageServiceTest에서 확인하므로 여기서는 통과시킨다
    // relaxUnitFun: 정해 두지 않은 Unit 함수(사진 검사 validateImageUrl)는 Mockito처럼 아무것도 하지 않고 넘어간다
    @MockkBean(relaxUnitFun = true) private lateinit var storageService: StorageService

    private var ownerId: Long = 0
    private lateinit var ownerToken: String
    private var memberId: Long = 0
    private lateinit var memberToken: String

    private var groupId: Long = 0
    private var memberGroupMemberId: Long = 0
    private var memberHabitId: Long = 0
    private var pendingPenaltyId: Long = 0
    private var rejectedPenaltyId: Long = 0

    @BeforeEach
    fun setUp() {
        val group = groupRepository.save(
            Group.builder()
                .title("테스트 그룹")
                .penalty("커피 사기")
                .password("1234")
                .inviteCode("TESTCODE")
                .memberLimit(5)
                .build()
        )
        groupId = requireNotNull(group.id)

        val owner = memberRepository.save(Member.create("방장", "owner", "password"))
        ownerId = requireNotNull(owner.id)
        ownerToken = authTokenService.createAccessToken(ownerId)
        groupMemberRepository.save(GroupMember.create(group, owner, GroupMemberRole.OWNER))

        val member = memberRepository.save(Member.create("일반회원", "member", "password"))
        memberId = requireNotNull(member.id)
        memberToken = authTokenService.createAccessToken(memberId)
        val normalGroupMember = groupMemberRepository.save(
            GroupMember.create(group, member, GroupMemberRole.MEMBER)
        )
        memberGroupMemberId = requireNotNull(normalGroupMember.id)

        val habit = habitRepository.save(
            Habit.create(normalGroupMember, "아침 운동", "30분 러닝", 30)
        )
        memberHabitId = requireNotNull(habit.id)
        penaltyVerifyRepository.save(PenaltyVerify.create(normalGroupMember, habit))

        val habitForPending = habitRepository.save(
            Habit.create(normalGroupMember, "책 읽기", "하루 10페이지", 30)
        )
        val pendingPv = PenaltyVerify.create(normalGroupMember, habitForPending)
        pendingPv.submit(LocalDate.now(), "책 읽었습니다.", "https://example.com/book.jpg")
        pendingPenaltyId = requireNotNull(penaltyVerifyRepository.save(pendingPv).id)

        val habitForRejected = habitRepository.save(
            Habit.create(normalGroupMember, "물 마시기", "하루 2L", 30)
        )
        val rejectedPv = PenaltyVerify.create(normalGroupMember, habitForRejected)
        rejectedPv.submit(LocalDate.now(), "물 인증", "https://example.com/water.jpg")
        rejectedPv.reject()
        rejectedPenaltyId = requireNotNull(penaltyVerifyRepository.save(rejectedPv).id)
    }

    @Test
    @DisplayName("벌칙 최초 제출 성공 시 201 Created 및 PENDING 반환")
    fun submit_성공() {
        val request = SubmitPenaltyVerifyRequest("오늘 러닝 완료", "https://example.com/run.jpg")

        mockMvc.post("/api/habits/{habitId}/penalties", memberHabitId) {
            header("Authorization", "Bearer $memberToken")
            contentType = MediaType.APPLICATION_JSON
            content = jsonMapper.writeValueAsString(request)
        }
            .andExpect {
                status { isCreated() }
                jsonPath("$.status") { value("PENDING") }
                jsonPath("$.description") { value("오늘 러닝 완료") }
                jsonPath("$.imageUrl") { value("https://example.com/run.jpg") }
            }
    }

    @Test
    @DisplayName("반려된 벌칙 재제출 성공 시 PENDING 상태로 갱신")
    fun resubmit_성공() {
        val request = SubmitPenaltyVerifyRequest("물 다시 마시고 재인증합니다.", "https://example.com/water2.jpg")

        mockMvc.patch("/api/penalties/{id}/resubmit", rejectedPenaltyId) {
            header("Authorization", "Bearer $memberToken")
            contentType = MediaType.APPLICATION_JSON
            content = jsonMapper.writeValueAsString(request)
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("PENDING") }
                jsonPath("$.description") { value("물 다시 마시고 재인증합니다.") }
            }
    }

    @Test
    @DisplayName("벌칙 단건 상세 조회 성공")
    fun getDetail_성공() {
        mockMvc.get("/api/penalties/{id}", pendingPenaltyId) {
            header("Authorization", "Bearer $memberToken")
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.id") { value(pendingPenaltyId) }
                jsonPath("$.status") { value("PENDING") }
            }
    }

    @Test
    @DisplayName("그룹 멤버별 벌칙 목록 최신순 조회 성공")
    fun getPenaltyList_성공() {
        mockMvc.get("/api/groups/{groupId}/members/{groupMemberId}/penalties", groupId, memberGroupMemberId) {
            header("Authorization", "Bearer $memberToken")
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(3) }
            }
    }

    @Test
    @DisplayName("방장의 벌칙 단건 승인 성공 시 APPROVED 반환")
    fun approve_성공() {
        mockMvc.patch("/api/penalties/{id}/approve", pendingPenaltyId) {
            header("Authorization", "Bearer $ownerToken")
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("APPROVED") }
            }
    }

    @Test
    @DisplayName("방장의 벌칙 단건 거절 성공 시 REJECTED 반환")
    fun reject_성공() {
        mockMvc.patch("/api/penalties/{id}/reject", pendingPenaltyId) {
            header("Authorization", "Bearer $ownerToken")
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("REJECTED") }
            }
    }

    @Test
    @DisplayName("방장의 그룹 내 벌칙 승인 대기 목록 조회 성공")
    fun getPending_성공() {
        mockMvc.get("/api/groups/{groupId}/penalties/pending", groupId) {
            header("Authorization", "Bearer $ownerToken")
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(1) }
                jsonPath("$[0].id") { value(pendingPenaltyId) }
            }
    }

    @Test
    @DisplayName("방장의 벌칙 일괄 승인 성공")
    fun bulkApprove_성공() {
        val request = BulkActionRequest(listOf(pendingPenaltyId))

        mockMvc.patch("/api/groups/{groupId}/penalties/bulk-approve", groupId) {
            header("Authorization", "Bearer $ownerToken")
            contentType = MediaType.APPLICATION_JSON
            content = jsonMapper.writeValueAsString(request)
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(1) }
                jsonPath("$[0].status") { value("APPROVED") }
            }
    }

    @Test
    @DisplayName("벌칙 작성자가 PENDING 벌칙을 제출 취소하면 200 OK 및 REQUIRED 반환, 기록은 남는다")
    fun cancel_성공() {
        mockMvc.patch("/api/penalties/{id}/cancel", pendingPenaltyId) {
            header("Authorization", "Bearer $memberToken")
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("REQUIRED") }
                jsonPath("$.imageUrl") { doesNotExist() }
            }

        assertThat(penaltyVerifyRepository.findById(pendingPenaltyId)).isPresent
    }

    @Nested
    @DisplayName("벌칙 인증 제출 실패 POST /api/habits/{habitId}/penalties")
    inner class SubmitFailure {

        private fun submit(token: String, habitId: Long, request: SubmitPenaltyVerifyRequest): ResultActionsDsl =
            mockMvc.post("/api/habits/{habitId}/penalties", habitId) {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = jsonMapper.writeValueAsString(request)
            }

        @Test
        @DisplayName("사진 없이 제출하면 400이다 (컨트롤러가 @Valid로 사진 필수 검사를 한다)")
        fun 사진_없으면_400() {
            submit(memberToken, memberHabitId, SubmitPenaltyVerifyRequest("오늘 러닝 완료", " "))
                .andExpect {
                    status { isBadRequest() }
                    jsonPath("$.errors[0]") { value("인증 사진은 필수입니다.") }
                }
        }

        @Test
        @DisplayName("같은 방 방장이 남의 벌칙을 제출하면 403이다")
        fun 남의_벌칙_403() {
            submit(ownerToken, memberHabitId, SubmitPenaltyVerifyRequest("대신 제출", "https://example.com/run.jpg"))
                .andExpect {
                    status { isForbidden() }
                }
        }

        @Test
        @DisplayName("벌칙이 없는 습관이면 404다")
        fun 벌칙_없는_습관_404() {
            val memberGroupMember = groupMemberRepository.findById(memberGroupMemberId).orElseThrow()
            val habitWithoutPenaltyId = requireNotNull(
                habitRepository.save(Habit.create(memberGroupMember, "일찍 자기", "11시 전 취침", 30)).id
            )

            submit(memberToken, habitWithoutPenaltyId, SubmitPenaltyVerifyRequest("제출", "https://example.com/run.jpg"))
                .andExpect {
                    status { isNotFound() }
                    jsonPath("$.message") { value("해당 습관에 대한 벌칙 정보가 존재하지 않습니다.") }
                }
        }

        @Test
        @DisplayName("사진을 확인하는 중 Supabase와 통신하지 못하면 502다")
        fun 사진_저장소_장애_502() {
            every { storageService.validateImageUrl(any(), any(), any(), any(), any()) } throws
                StorageUnavailableException("사진 저장소에 연결할 수 없습니다.")

            submit(memberToken, memberHabitId, SubmitPenaltyVerifyRequest("오늘 러닝 완료", "https://example.com/run.jpg"))
                .andExpect {
                    status { isBadGateway() }
                    jsonPath("$.message") { value("사진 저장소에 연결할 수 없습니다.") }
                }
        }
    }

    @Nested
    @DisplayName("벌칙 인증 사진 업로드 URL 발급 POST /api/penalties/{id}/upload-url")
    inner class UploadUrl {

        private fun requestUploadUrl(token: String, penaltyId: Long): ResultActionsDsl =
            mockMvc.post("/api/penalties/{id}/upload-url", penaltyId) {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = jsonMapper.writeValueAsString(UploadUrlRequest("penalty.png"))
            }

        @Test
        @DisplayName("재제출할 수 있는(반려된) 내 벌칙이면 200이고 이 회원·이 벌칙 기준으로 발급한 URL을 돌려준다")
        fun 내_벌칙_200() {
            every { storageService.createUploadUrl(VerificationImageType.PENALTY, memberId, rejectedPenaltyId, "penalty.png") } returns
                UploadUrlResponse("https://storage/upload-signed", "https://storage/public.png")

            requestUploadUrl(memberToken, rejectedPenaltyId)
                .andExpect {
                    status { isOk() }
                    jsonPath("$.uploadUrl") { value("https://storage/upload-signed") }
                    jsonPath("$.publicUrl") { value("https://storage/public.png") }
                }
        }

        @Test
        @DisplayName("이미 승인된 벌칙이면 409다")
        fun 승인된_벌칙_409() {
            penaltyVerifyRepository.findById(pendingPenaltyId).orElseThrow().approve()

            requestUploadUrl(memberToken, pendingPenaltyId)
                .andExpect {
                    status { isConflict() }
                }
        }

        @Test
        @DisplayName("같은 방 방장이 남의 벌칙에 요청하면 403이다")
        fun 남의_벌칙_403() {
            requestUploadUrl(ownerToken, rejectedPenaltyId)
                .andExpect {
                    status { isForbidden() }
                }
        }

        @Test
        @DisplayName("파일명이 비어 있으면 400이다 (UploadUrlRequest의 @NotBlank 검사)")
        fun 파일명_없으면_400() {
            mockMvc.post("/api/penalties/{id}/upload-url", rejectedPenaltyId) {
                header("Authorization", "Bearer $memberToken")
                contentType = MediaType.APPLICATION_JSON
                content = jsonMapper.writeValueAsString(UploadUrlRequest(" "))
            }
                .andExpect {
                    status { isBadRequest() }
                    jsonPath("$.message") { value("입력값 검증에 실패했습니다.") }
                }
        }
    }
}
