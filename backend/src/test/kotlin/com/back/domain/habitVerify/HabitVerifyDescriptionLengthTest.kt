package com.back.domain.habitVerify

import com.back.domain.group.entity.Group
import com.back.domain.group.repository.GroupRepository
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.habit.entity.Habit
import com.back.domain.habit.repository.HabitRepository
import com.back.domain.habitVerify.entity.HabitVerify
import com.back.domain.habitVerify.repository.HabitVerifyRepository
import com.back.domain.member.entity.Member
import com.back.domain.member.repository.MemberRepository
import com.back.domain.member.service.AuthTokenService
import com.back.global.storage.StorageService
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

// 습관 인증 제출·재제출의 설명(description)이 DB 컬럼 길이(255자)를 넘으면 DB에 닿기 전에 400으로 거절하는지 확인한다.
// 검증이 없으면 저장할 때 DB 오류가 그대로 올라와 500이 된다.
// 재제출은 기존 행을 고치는(UPDATE) 것이라 테스트 트랜잭션 안에서는 DB 검사가 미뤄지므로, 성공 경우는 flush로 DB까지 보낸다.
// 사진 검사(Supabase)는 이 테스트의 관심사가 아니라 바꿔 둔다. (설정을 HabitVerifyControllerTest와 같게 맞춰 컨텍스트를 재사용)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HabitVerifyDescriptionLengthTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var authTokenService: AuthTokenService
    @Autowired lateinit var memberRepository: MemberRepository
    @Autowired lateinit var groupRepository: GroupRepository
    @Autowired lateinit var groupMemberRepository: GroupMemberRepository
    @Autowired lateinit var habitRepository: HabitRepository
    @Autowired lateinit var habitVerifyRepository: HabitVerifyRepository

    @MockitoBean lateinit var storageService: StorageService

    private lateinit var token: String
    private lateinit var habit: Habit
    private var habitId: Long = 0

    @BeforeEach
    fun setUp() {
        val group = groupRepository.save(
            Group.builder()
                .title("아침 기상 챌린지")
                .penalty("커피 1잔 쏘기")
                .password("1234")
                .inviteCode("HVLENGTHCODE")
                .memberLimit(5)
                .build()
        )
        val member = memberRepository.save(Member.create("일반회원", "hv-length-member", "pass1234"))
        val groupMember = groupMemberRepository.save(GroupMember.create(group, member, GroupMemberRole.MEMBER))
        habit = habitRepository.save(Habit.create(groupMember, "기상 후 스트레칭", "10분 스트레칭", 3))

        token = authTokenService.createAccessToken(requireNotNull(member.id))
        habitId = requireNotNull(habit.id)
    }

    private fun body(description: String): String =
        """{"description":"$description","imageUrl":"https://example.com/habit.png"}"""

    private fun submit(description: String): ResultActionsDsl =
        mockMvc.post("/api/habits/{habitId}/verifications", habitId) {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = body(description)
        }

    private fun resubmit(verificationId: Long, description: String): ResultActionsDsl =
        mockMvc.patch("/api/habits/{habitId}/verifications/{verificationId}/resubmit", habitId, verificationId) {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = body(description)
        }

    private fun verifications(): ResultActionsDsl =
        mockMvc.get("/api/habits/{habitId}/verifications", habitId) {
            header("Authorization", "Bearer $token")
        }
            .andExpect {
                status { isOk() }
            }

    // 반려된 인증을 만든다 (재제출은 반려된 인증만 가능)
    private fun rejectedVerification(): Long {
        val habitVerify = habitVerifyRepository.save(
            HabitVerify.create(habit, LocalDate.now(), "처음 제출", "https://example.com/first.png")
        )
        habitVerify.reject()
        habitVerifyRepository.flush()
        return requireNotNull(habitVerify.id)
    }

    @Test
    @DisplayName("제출: 설명이 255자면 제출되고 DB에도 저장된다 (컬럼 길이 한계)")
    fun 제출_255자는_성공한다() {
        submit("가".repeat(255)).andExpect { status { isCreated() } }
        habitVerifyRepository.flush()

        verifications()
            .andExpect {
                jsonPath("$") { value(hasSize<Any>(1)) }
                jsonPath("$[0].status") { value("PENDING") }
            }
    }

    @Test
    @DisplayName("제출: 설명이 256자면 400과 길이 메시지를 돌려주고 인증을 만들지 않는다")
    fun 제출_256자는_400이다() {
        submit("가".repeat(256))
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.errors[0]") { value("습관 인증 설명은 255자 이하여야 합니다.") }
            }

        verifications().andExpect { jsonPath("$") { value(hasSize<Any>(0)) } }
    }

    @Test
    @DisplayName("재제출: 설명이 255자면 재제출되고 DB에도 저장된다 (컬럼 길이 한계)")
    fun 재제출_255자는_성공한다() {
        val verificationId = rejectedVerification()

        resubmit(verificationId, "나".repeat(255)).andExpect { status { isOk() } }
        habitVerifyRepository.flush()

        verifications().andExpect { jsonPath("$[0].status") { value("PENDING") } }
    }

    @Test
    @DisplayName("재제출: 설명이 256자면 400과 길이 메시지를 돌려주고 반려(REJECTED) 그대로다")
    fun 재제출_256자는_400이다() {
        val verificationId = rejectedVerification()

        resubmit(verificationId, "나".repeat(256))
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.errors[0]") { value("습관 인증 설명은 255자 이하여야 합니다.") }
            }

        verifications().andExpect { jsonPath("$[0].status") { value("REJECTED") } }
    }
}
