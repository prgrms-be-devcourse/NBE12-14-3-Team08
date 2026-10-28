package com.back.domain.penaltyverify

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
import com.back.domain.penaltyverify.entity.PenaltyVerify
import com.back.domain.penaltyverify.repository.PenaltyVerifyRepository
import com.back.global.storage.StorageService
import com.ninjasquad.springmockk.MockkBean
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional

// 가이드 규칙 "완료된 방은 조회만 가능" 중 벌칙 제출(POST /api/habits/{habitId}/penalties)을 API로 확인한다.
// 사진 검사(Supabase)는 종료 검사 뒤에 오므로 원래는 호출되지 않지만, 종료 검사가 빠졌을 때 실제 Supabase를 부르지 않도록 바꿔 둔다.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PenaltyVerifySubmitFinishedGroupTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val authTokenService: AuthTokenService,
    private val memberRepository: MemberRepository,
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val habitRepository: HabitRepository,
    private val penaltyVerifyRepository: PenaltyVerifyRepository
) {

    // relaxUnitFun: 정해 두지 않은 Unit 함수(사진 검사 validateImageUrl)는 Mockito처럼 아무것도 하지 않고 넘어간다
    @MockkBean(relaxUnitFun = true) lateinit var storageService: StorageService

    @Test
    @DisplayName("종료된 방에서 벌칙을 제출하면 400이고, 벌칙은 미제출(REQUIRED) 그대로다")
    fun 종료된_방_제출_400() {
        val group = Group(
            title = "아침 기상 챌린지",
            penalty = "커피 1잔 쏘기",
            password = "1234",
            inviteCode = "FINISHEDCODE",
            memberLimit = 5,
        )
        group.finish()
        groupRepository.save(group)

        val member = memberRepository.save(Member.create("일반회원", "finished-member", "pass1234"))
        val groupMember = groupMemberRepository.save(GroupMember.create(group, member, GroupMemberRole.MEMBER))
        val habit = habitRepository.save(Habit.create(groupMember, "기상 후 스트레칭", "10분 스트레칭", 3))
        val penalty = penaltyVerifyRepository.save(PenaltyVerify.create(groupMember, habit))
        val token = authTokenService.createAccessToken(requireNotNull(member.id))

        mockMvc.post("/api/habits/{habitId}/penalties", habit.id) {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"description":"벌칙 완료","imageUrl":"https://example.com/penalty.png"}"""
        }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { value("이미 종료된 그룹에서는 벌칙을 제출할 수 없습니다.") }
            }

        mockMvc.get("/api/penalties/{id}", penalty.id) {
            header("Authorization", "Bearer $token")
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("REQUIRED") }
            }
    }
}
