package com.back.domain.habitVerify.controller

import com.back.domain.group.entity.Group
import com.back.domain.group.repository.GroupRepository
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.habit.entity.Habit
import com.back.domain.habit.repository.HabitRepository
import com.back.domain.habitVerify.dto.HabitVerifyRequest
import com.back.domain.habitVerify.entity.HabitVerify
import com.back.domain.habitVerify.entity.HabitVerifyStatus
import com.back.domain.habitVerify.repository.HabitVerifyRepository
import com.back.domain.member.entity.Member
import com.back.domain.member.repository.MemberRepository
import com.back.domain.member.service.AuthTokenService
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
class HabitVerifyControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jsonMapper: JsonMapper
    @Autowired private lateinit var authTokenService: AuthTokenService
    @Autowired private lateinit var memberRepository: MemberRepository
    @Autowired private lateinit var groupRepository: GroupRepository
    @Autowired private lateinit var groupMemberRepository: GroupMemberRepository
    @Autowired private lateinit var habitRepository: HabitRepository
    @Autowired private lateinit var habitVerifyRepository: HabitVerifyRepository

    // 인증 사진 URL 검사·업로드 URL 발급(Supabase 호출)은 StorageServiceTest에서 확인하므로 여기서는 통과시킨다
    // relaxUnitFun: 정해 두지 않은 Unit 함수(사진 검사 validateImageUrl)는 Mockito처럼 아무것도 하지 않고 넘어간다
    @MockkBean(relaxUnitFun = true) private lateinit var storageService: StorageService

    private lateinit var ownerToken: String
    private var memberId: Long = 0
    private lateinit var memberToken: String
    private lateinit var outsiderToken: String
    private lateinit var memberHabit: Habit
    private var memberHabitId: Long = 0

    @BeforeEach
    fun setUp() {
        val group = groupRepository.save(
            Group.builder()
                .title("아침 기상 챌린지")
                .penalty("커피 1잔 쏘기")
                .password("1234")
                .inviteCode("HABITCODE")
                .memberLimit(10)
                .build()
        )

        val owner = memberRepository.save(Member.create("방장", "owner", "pass1234"))
        groupMemberRepository.save(GroupMember.create(group, owner, GroupMemberRole.OWNER))
        ownerToken = authTokenService.createAccessToken(requireNotNull(owner.id))

        val member = memberRepository.save(Member.create("일반회원", "member", "pass1234"))
        val memberGroupMember = groupMemberRepository.save(
            GroupMember.create(group, member, GroupMemberRole.MEMBER)
        )
        memberId = requireNotNull(member.id)
        memberToken = authTokenService.createAccessToken(memberId)

        memberHabit = habitRepository.save(
            Habit.create(memberGroupMember, "기상 후 스트레칭", "10분 스트레칭", 3)
        )
        memberHabitId = requireNotNull(memberHabit.id)

        val outsider = memberRepository.save(Member.create("다른방회원", "outsider", "pass1234"))
        outsiderToken = authTokenService.createAccessToken(requireNotNull(outsider.id))
    }

    private fun postVerification(token: String, habitId: Long, request: HabitVerifyRequest): ResultActionsDsl =
        mockMvc.post("/api/habits/{habitId}/verifications", habitId) {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = jsonMapper.writeValueAsString(request)
        }

    @Nested
    @DisplayName("습관 인증 등록 POST /api/habits/{habitId}/verifications")
    inner class Create {

        @Test
        @DisplayName("내 습관에 사진과 함께 인증하면 201이고 검토 대기(PENDING)로 등록된다")
        fun 등록_성공_201() {
            postVerification(
                memberToken, memberHabitId,
                HabitVerifyRequest("스트레칭 완료", "https://example.com/stretch.png"),
            )
                .andExpect {
                    status { isCreated() }
                    jsonPath("$.habitId") { value(memberHabitId) }
                    jsonPath("$.status") { value("PENDING") }
                    jsonPath("$.description") { value("스트레칭 완료") }
                    jsonPath("$.imageUrl") { value("https://example.com/stretch.png") }
                }
        }

        @Test
        @DisplayName("사진 없이 인증하면 400이다 (컨트롤러가 @Valid로 사진 필수 검사를 한다)")
        fun 사진_없으면_400() {
            postVerification(memberToken, memberHabitId, HabitVerifyRequest("스트레칭 완료", " "))
                .andExpect {
                    status { isBadRequest() }
                    jsonPath("$.errors[0]") { value("인증 사진은 필수입니다.") }
                }
        }

        @Test
        @DisplayName("사진 크기와 함께 인증하면 크기가 저장된다 (응답 모양은 그대로)")
        fun 크기와_함께_등록() {
            postVerification(
                memberToken, memberHabitId,
                HabitVerifyRequest("스트레칭 완료", "https://example.com/stretch.png", 4032, 3024),
            )
                .andExpect {
                    status { isCreated() }
                    jsonPath("$.imageWidth") { doesNotExist() }
                }

            val saved = habitVerifyRepository.findAllByHabitIdOrderByVerifyDateDesc(memberHabitId).single()
            assertThat(saved.imageWidth).isEqualTo(4032)
            assertThat(saved.imageHeight).isEqualTo(3024)
        }

        @Test
        @DisplayName("사진 크기 없이 인증해도 201이고 크기는 null로 저장된다")
        fun 크기_없이_등록() {
            postVerification(memberToken, memberHabitId, HabitVerifyRequest("스트레칭 완료", "https://example.com/stretch.png"))
                .andExpect { status { isCreated() } }

            val saved = habitVerifyRepository.findAllByHabitIdOrderByVerifyDateDesc(memberHabitId).single()
            assertThat(saved.imageWidth).isNull()
            assertThat(saved.imageHeight).isNull()
        }

        @Test
        @DisplayName("가로만 보내면 400이고 인증은 저장되지 않는다")
        fun 가로만_보내면_400() {
            postVerification(memberToken, memberHabitId, HabitVerifyRequest("스트레칭 완료", "https://example.com/stretch.png", 4032, null))
                .andExpect {
                    status { isBadRequest() }
                    jsonPath("$.errors[0]") { value("사진의 가로와 세로 크기는 함께 보내야 합니다.") }
                }

            assertThat(habitVerifyRepository.findAllByHabitIdOrderByVerifyDateDesc(memberHabitId)).isEmpty()
        }

        @Test
        @DisplayName("크기가 0이거나 20000을 넘으면 400이고 인증은 저장되지 않는다")
        fun 크기_범위_밖_400() {
            postVerification(memberToken, memberHabitId, HabitVerifyRequest("스트레칭 완료", "https://example.com/stretch.png", 0, 3024))
                .andExpect {
                    status { isBadRequest() }
                    jsonPath("$.errors[0]") { value("사진 가로 크기는 1 이상이어야 합니다.") }
                }
            postVerification(memberToken, memberHabitId, HabitVerifyRequest("스트레칭 완료", "https://example.com/stretch.png", 4032, 20001))
                .andExpect {
                    status { isBadRequest() }
                    jsonPath("$.errors[0]") { value("사진 세로 크기는 20000 이하여야 합니다.") }
                }

            assertThat(habitVerifyRepository.findAllByHabitIdOrderByVerifyDateDesc(memberHabitId)).isEmpty()
        }

        @Test
        @DisplayName("같은 방이어도 남의 습관에 인증하면 403이다")
        fun 남의_습관_403() {
            postVerification(
                ownerToken, memberHabitId,
                HabitVerifyRequest("대신 인증", "https://example.com/stretch.png"),
            )
                .andExpect {
                    status { isForbidden() }
                }
        }

        @Test
        @DisplayName("같은 습관을 같은 날 두 번 인증하면 두 번째는 400이다")
        fun 같은_날_두_번_400() {
            val request = HabitVerifyRequest("스트레칭 완료", "https://example.com/stretch.png")
            postVerification(memberToken, memberHabitId, request).andExpect { status { isCreated() } }

            postVerification(memberToken, memberHabitId, request)
                .andExpect {
                    status { isBadRequest() }
                    jsonPath("$.message") { value("해당 날짜에 이미 인증 기록이 있습니다.") }
                }
        }

        @Test
        @DisplayName("포기해서 실패(FAILED)한 습관에 인증하면 409다")
        fun 실패한_습관_409() {
            memberHabit.fail()

            postVerification(
                memberToken, memberHabitId,
                HabitVerifyRequest("스트레칭 완료", "https://example.com/stretch.png"),
            )
                .andExpect {
                    status { isConflict() }
                    jsonPath("$.message") { value("진행 중인 습관에만 인증을 등록할 수 있습니다. 현재 상태: FAILED") }
                }
        }
    }

    @Nested
    @DisplayName("습관 인증 목록 GET /api/habits/{habitId}/verifications")
    inner class FindAll {

        private fun getVerifications(token: String, habitId: Long): ResultActionsDsl =
            mockMvc.get("/api/habits/{habitId}/verifications", habitId) {
                header("Authorization", "Bearer $token")
            }

        @Test
        @DisplayName("같은 방 멤버는 남의 습관 인증 목록도 200으로 받는다")
        fun 같은_방_멤버_200() {
            habitVerifyRepository.save(
                HabitVerify.create(memberHabit, LocalDate.now(), "스트레칭 완료", "https://example.com/stretch.png")
            )

            getVerifications(ownerToken, memberHabitId)
                .andExpect {
                    status { isOk() }
                    jsonPath("$.length()") { value(1) }
                    jsonPath("$[0].description") { value("스트레칭 완료") }
                }
        }

        @Test
        @DisplayName("다른 방 사람이 조회하면 403이다")
        fun 다른_방_사람_403() {
            getVerifications(outsiderToken, memberHabitId)
                .andExpect {
                    status { isForbidden() }
                }
        }

        @Test
        @DisplayName("없는 습관이면 404다")
        fun 없는_습관_404() {
            getVerifications(memberToken, Long.MAX_VALUE)
                .andExpect {
                    status { isNotFound() }
                    jsonPath("$.message") { value("존재하지 않는 습관입니다.") }
                }
        }
    }

    @Nested
    @DisplayName("반려된 습관 인증 재제출 PATCH /api/habits/{habitId}/verifications/{verificationId}/resubmit")
    inner class Resubmit {

        private fun saveVerify(rejected: Boolean): Long {
            val habitVerify = HabitVerify.create(
                memberHabit, LocalDate.now(), "처음 인증", "https://example.com/old.png"
            )
            if (rejected) {
                habitVerify.reject()
            }
            return requireNotNull(habitVerifyRepository.save(habitVerify).id)
        }

        private fun resubmit(verificationId: Long, request: HabitVerifyRequest): ResultActionsDsl =
            mockMvc.patch("/api/habits/{habitId}/verifications/{verificationId}/resubmit", memberHabitId, verificationId) {
                header("Authorization", "Bearer $memberToken")
                contentType = MediaType.APPLICATION_JSON
                content = jsonMapper.writeValueAsString(request)
            }

        @Test
        @DisplayName("반려된 인증을 다시 내면 200이고 검토 대기(PENDING)로 돌아간다")
        fun 반려_재제출_200() {
            val verificationId = saveVerify(true)

            resubmit(verificationId, HabitVerifyRequest("다시 인증", "https://example.com/new.png"))
                .andExpect {
                    status { isOk() }
                    jsonPath("$.status") { value("PENDING") }
                    jsonPath("$.description") { value("다시 인증") }
                    jsonPath("$.imageUrl") { value("https://example.com/new.png") }
                }
        }

        @Test
        @DisplayName("재제출에 새 크기를 주면 크기가 바뀌고, 크기 없이 다시 내면 크기가 비워진다")
        fun 재제출_크기_갱신() {
            val habitVerify = HabitVerify.create(memberHabit, LocalDate.now(), "처음 인증", "https://example.com/old.png", 100, 50)
            habitVerify.reject()
            val verificationId = requireNotNull(habitVerifyRepository.save(habitVerify).id)

            resubmit(verificationId, HabitVerifyRequest("다시 인증", "https://example.com/new.png", 1080, 1920))
                .andExpect { status { isOk() } }
            val renewed = habitVerifyRepository.findById(verificationId).orElseThrow()
            assertThat(renewed.imageWidth).isEqualTo(1080)
            assertThat(renewed.imageHeight).isEqualTo(1920)

            renewed.reject()
            resubmit(verificationId, HabitVerifyRequest("또 다시", "https://example.com/newer.png"))
                .andExpect { status { isOk() } }
            val cleared = habitVerifyRepository.findById(verificationId).orElseThrow()
            assertThat(cleared.imageWidth).isNull()
            assertThat(cleared.imageHeight).isNull()
        }

        @Test
        @DisplayName("재제출에서 가로만 보내면 400이고 인증은 그대로 반려 상태다")
        fun 재제출_가로만_400() {
            val verificationId = saveVerify(true)

            resubmit(verificationId, HabitVerifyRequest("다시 인증", "https://example.com/new.png", 1080, null))
                .andExpect {
                    status { isBadRequest() }
                    jsonPath("$.errors[0]") { value("사진의 가로와 세로 크기는 함께 보내야 합니다.") }
                }

            val unchanged = habitVerifyRepository.findById(verificationId).orElseThrow()
            assertThat(unchanged.status).isEqualTo(HabitVerifyStatus.REJECTED)
            assertThat(unchanged.imageUrl).isEqualTo("https://example.com/old.png")
        }

        @Test
        @DisplayName("반려되지 않은 인증(검토 대기)을 다시 내면 409다")
        fun 반려_아니면_409() {
            val verificationId = saveVerify(false)

            resubmit(verificationId, HabitVerifyRequest("다시 인증", "https://example.com/new.png"))
                .andExpect {
                    status { isConflict() }
                }
        }

        @Test
        @DisplayName("사진 없이 다시 내면 400이다 (컨트롤러가 @Valid로 사진 필수 검사를 한다)")
        fun 사진_없으면_400() {
            val verificationId = saveVerify(true)

            resubmit(verificationId, HabitVerifyRequest("다시 인증", ""))
                .andExpect {
                    status { isBadRequest() }
                    jsonPath("$.errors[0]") { value("인증 사진은 필수입니다.") }
                }
        }

        @Test
        @DisplayName("없는 인증이면 404다")
        fun 없는_인증_404() {
            resubmit(Long.MAX_VALUE, HabitVerifyRequest("다시 인증", "https://example.com/new.png"))
                .andExpect {
                    status { isNotFound() }
                    jsonPath("$.message") { value("존재하지 않는 인증 기록입니다.") }
                }
        }
    }

    @Nested
    @DisplayName("습관 인증 사진 업로드 URL 발급 POST /api/storage/habits/{habitId}/upload-url")
    inner class UploadUrl {

        private fun requestUploadUrl(token: String, habitId: Long): ResultActionsDsl =
            mockMvc.post("/api/storage/habits/{habitId}/upload-url", habitId) {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = jsonMapper.writeValueAsString(UploadUrlRequest("today.png"))
            }

        @Test
        @DisplayName("내 습관이면 200이고 이 회원·이 습관 기준으로 발급한 URL을 돌려준다")
        fun 내_습관_200() {
            every { storageService.createUploadUrl(VerificationImageType.HABIT, memberId, memberHabitId, "today.png") } returns
                UploadUrlResponse("https://storage/upload-signed", "https://storage/public.png")

            requestUploadUrl(memberToken, memberHabitId)
                .andExpect {
                    status { isOk() }
                    jsonPath("$.uploadUrl") { value("https://storage/upload-signed") }
                    jsonPath("$.publicUrl") { value("https://storage/public.png") }
                }
        }

        @Test
        @DisplayName("같은 방이어도 남의 습관이면 403이다")
        fun 남의_습관_403() {
            requestUploadUrl(ownerToken, memberHabitId)
                .andExpect {
                    status { isForbidden() }
                }
        }
    }
}
