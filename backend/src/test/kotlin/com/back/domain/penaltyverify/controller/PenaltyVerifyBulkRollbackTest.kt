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
import com.back.domain.penaltyverify.entity.PenaltyVerify
import com.back.domain.penaltyverify.entity.PenaltyVerifyStatus
import com.back.domain.penaltyverify.repository.PenaltyVerifyRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.patch
import java.time.LocalDate
import java.util.UUID

// 벌칙 일괄 승인의 "하나라도 실패하면 전부 취소"를 실제 트랜잭션으로 확인한다.
// 테스트 클래스에 @Transactional을 붙이면 서비스가 테스트 트랜잭션에 합류해 롤백 여부를 볼 수 없으므로 붙이지 않고,
// 대신 만든 데이터를 @AfterEach에서 직접 지운다. (메모리 DB, test 프로필)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PenaltyVerifyBulkRollbackTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val authTokenService: AuthTokenService,
    private val memberRepository: MemberRepository,
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val habitRepository: HabitRepository,
    private val penaltyVerifyRepository: PenaltyVerifyRepository
) {

    private lateinit var owner: Member
    private lateinit var member: Member
    private lateinit var group: Group
    private lateinit var ownerGroupMember: GroupMember
    private lateinit var memberGroupMember: GroupMember
    private val habits = mutableListOf<Habit>()
    private val penalties = mutableListOf<PenaltyVerify>()
    private lateinit var ownerToken: String

    @BeforeEach
    fun setUp() {
        owner = memberRepository.save(Member.create("방장", uniqueName("owner"), "pass1234"))
        member = memberRepository.save(Member.create("일반회원", uniqueName("member"), "pass1234"))
        group = groupRepository.save(
            Group(
                title = "아침 기상 챌린지",
                penalty = "커피 1잔 쏘기",
                password = "1234",
                inviteCode = uniqueName("code"),
                memberLimit = 10,
                deadline = LocalDate.now().plusMonths(1),
            )
        )
        ownerGroupMember = groupMemberRepository.save(GroupMember.create(group, owner, GroupMemberRole.OWNER))
        memberGroupMember = groupMemberRepository.save(GroupMember.create(group, member, GroupMemberRole.MEMBER))
        ownerToken = authTokenService.createAccessToken(requireNotNull(owner.id))
    }

    @AfterEach
    fun tearDown() {
        penaltyVerifyRepository.deleteAll(penalties)
        habitRepository.deleteAll(habits)
        groupMemberRepository.deleteAll(listOf(memberGroupMember, ownerGroupMember))
        groupRepository.delete(group)
        memberRepository.deleteAll(listOf(member, owner))
    }

    private fun uniqueName(prefix: String): String = prefix + "-" + UUID.randomUUID().toString().substring(0, 8)

    // 벌칙은 습관 하나에 하나씩 생기므로, 벌칙마다 습관을 새로 만든다
    private fun savePenalty(status: PenaltyVerifyStatus): PenaltyVerify {
        val habit = habitRepository.save(Habit.create(memberGroupMember, "습관-" + uniqueName("h"), "설명", 3))
        habits.add(habit)
        val pv = PenaltyVerify.create(memberGroupMember, habit)
        pv.submit(LocalDate.of(2026, 10, 20), "벌칙 수행", "https://example.com/p.jpg")
        if (status == PenaltyVerifyStatus.APPROVED) {
            pv.approve()
        }
        val saved = penaltyVerifyRepository.save(pv)
        penalties.add(saved)
        return saved
    }

    private fun statusInDb(pv: PenaltyVerify): PenaltyVerifyStatus =
        penaltyVerifyRepository.findById(requireNotNull(pv.id)).orElseThrow().status

    @Test
    @DisplayName("일괄 승인: 모두 검토 대기면 200이고 DB에 모두 APPROVED로 저장된다")
    fun 모두_대기면_전부_승인() {
        val first = savePenalty(PenaltyVerifyStatus.PENDING)
        val second = savePenalty(PenaltyVerifyStatus.PENDING)

        mockMvc.patch("/api/groups/{groupId}/penalties/bulk-approve", group.id) {
            header("Authorization", "Bearer $ownerToken")
            contentType = MediaType.APPLICATION_JSON
            content = """{"ids": [${first.id}, ${second.id}]}"""
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
            }

        assertThat(statusInDb(first)).isEqualTo(PenaltyVerifyStatus.APPROVED)
        assertThat(statusInDb(second)).isEqualTo(PenaltyVerifyStatus.APPROVED)
    }

    @Test
    @DisplayName("일괄 승인: 하나라도 이미 처리된 벌칙이면 409이고, 앞서 승인하던 벌칙도 DB에서 PENDING 그대로다 (전부 취소)")
    fun 하나라도_처리됐으면_전부_취소() {
        val pending = savePenalty(PenaltyVerifyStatus.PENDING)
        val approved = savePenalty(PenaltyVerifyStatus.APPROVED)

        mockMvc.patch("/api/groups/{groupId}/penalties/bulk-approve", group.id) {
            header("Authorization", "Bearer $ownerToken")
            contentType = MediaType.APPLICATION_JSON
            content = """{"ids": [${pending.id}, ${approved.id}]}"""
        }
            .andExpect {
                status { isConflict() }
            }

        assertThat(statusInDb(pending)).isEqualTo(PenaltyVerifyStatus.PENDING)
        assertThat(statusInDb(approved)).isEqualTo(PenaltyVerifyStatus.APPROVED)
    }

    @Test
    @DisplayName("일괄 승인: 일반 멤버가 요청하면 403이고 DB는 그대로다")
    fun 일반_멤버면_403() {
        val pending = savePenalty(PenaltyVerifyStatus.PENDING)
        val memberToken = authTokenService.createAccessToken(requireNotNull(member.id))

        mockMvc.patch("/api/groups/{groupId}/penalties/bulk-approve", group.id) {
            header("Authorization", "Bearer $memberToken")
            contentType = MediaType.APPLICATION_JSON
            content = """{"ids": [${pending.id}]}"""
        }
            .andExpect {
                status { isForbidden() }
            }

        assertThat(statusInDb(pending)).isEqualTo(PenaltyVerifyStatus.PENDING)
    }
}
