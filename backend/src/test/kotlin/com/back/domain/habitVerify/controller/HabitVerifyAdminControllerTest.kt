package com.back.domain.habitVerify.controller

import com.back.domain.group.entity.Group
import com.back.domain.group.repository.GroupRepository
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.habit.entity.Habit
import com.back.domain.habit.repository.HabitRepository
import com.back.domain.habitVerify.entity.HabitVerify
import com.back.domain.habitVerify.entity.HabitVerifyStatus
import com.back.domain.habitVerify.repository.HabitVerifyRepository
import com.back.domain.member.entity.Member
import com.back.domain.member.repository.MemberRepository
import com.back.domain.member.service.AuthTokenService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import java.time.LocalDate
import java.util.UUID

// 일괄 승인의 "하나라도 실패하면 전부 취소"를 실제 트랜잭션으로 확인한다.
// 테스트 클래스에 @Transactional을 붙이면 서비스가 테스트 트랜잭션에 합류해 롤백 여부를 볼 수 없으므로 붙이지 않고,
// 대신 만든 데이터를 @AfterEach에서 직접 지운다. (Testcontainers PostgreSQL, test 프로필)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HabitVerifyAdminControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val authTokenService: AuthTokenService,
    private val memberRepository: MemberRepository,
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val habitRepository: HabitRepository,
    private val habitVerifyRepository: HabitVerifyRepository
) {

    private lateinit var owner: Member
    private lateinit var member: Member
    private lateinit var group: Group
    private lateinit var ownerGroupMember: GroupMember
    private lateinit var memberGroupMember: GroupMember
    private lateinit var habit: Habit
    private lateinit var ownerToken: String
    private lateinit var memberToken: String

    @BeforeEach
    fun setUp() {
        owner = memberRepository.save(Member.create("방장", uniqueName("owner"), "pass1234"))
        member = memberRepository.save(Member.create("일반회원", uniqueName("member"), "pass1234"))
        group = groupRepository.save(
            Group.builder()
                .title("아침 기상 챌린지")
                .penalty("커피 1잔 쏘기")
                .password("1234")
                .inviteCode(uniqueName("code"))
                .memberLimit(10)
                .deadline(LocalDate.now().plusMonths(1))
                .build()
        )
        ownerGroupMember = groupMemberRepository.save(GroupMember.create(group, owner, GroupMemberRole.OWNER))
        memberGroupMember = groupMemberRepository.save(GroupMember.create(group, member, GroupMemberRole.MEMBER))
        habit = habitRepository.save(Habit.create(memberGroupMember, "기상 후 스트레칭", "10분 스트레칭", 3))
        ownerToken = authTokenService.createAccessToken(requireNotNull(owner.id))
        memberToken = authTokenService.createAccessToken(requireNotNull(member.id))
    }

    @AfterEach
    fun tearDown() {
        habitVerifyRepository.deleteAll(habitVerifyRepository.findAllByHabitIdOrderByVerifyDateDesc(requireNotNull(habit.id)))
        habitRepository.delete(habit)
        groupMemberRepository.deleteAll(listOf(memberGroupMember, ownerGroupMember))
        groupRepository.delete(group)
        memberRepository.deleteAll(listOf(member, owner))
    }

    private fun uniqueName(prefix: String): String = prefix + "-" + UUID.randomUUID().toString().substring(0, 8)

    private fun saveVerify(verifyDate: LocalDate, status: HabitVerifyStatus): HabitVerify {
        val habitVerify = HabitVerify.create(habit, verifyDate, "인증", "https://example.com/a.png")
        if (status == HabitVerifyStatus.APPROVED) {
            habitVerify.approve()
        }
        return habitVerifyRepository.save(habitVerify)
    }

    private fun statusInDb(habitVerify: HabitVerify): HabitVerifyStatus =
        habitVerifyRepository.findById(requireNotNull(habitVerify.id)).orElseThrow().status

    @Test
    @DisplayName("일괄 승인: 모두 검토 대기면 200이고 DB에 모두 APPROVED로 저장된다")
    fun 모두_대기면_전부_승인() {
        val first = saveVerify(LocalDate.of(2026, 10, 20), HabitVerifyStatus.PENDING)
        val second = saveVerify(LocalDate.of(2026, 10, 21), HabitVerifyStatus.PENDING)

        mockMvc.patch("/api/groups/{groupId}/habits/verifications/bulk-approve", group.id) {
            header("Authorization", "Bearer $ownerToken")
            contentType = MediaType.APPLICATION_JSON
            content = """{"ids": [${first.id}, ${second.id}]}"""
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
            }

        assertThat(statusInDb(first)).isEqualTo(HabitVerifyStatus.APPROVED)
        assertThat(statusInDb(second)).isEqualTo(HabitVerifyStatus.APPROVED)
    }

    @Test
    @DisplayName("일괄 승인: 하나라도 이미 처리된 인증이면 409이고, 앞서 승인하던 인증도 DB에서 PENDING 그대로다 (전부 취소)")
    fun 하나라도_처리됐으면_전부_취소() {
        val pending = saveVerify(LocalDate.of(2026, 10, 20), HabitVerifyStatus.PENDING)
        val approved = saveVerify(LocalDate.of(2026, 10, 21), HabitVerifyStatus.APPROVED)

        mockMvc.patch("/api/groups/{groupId}/habits/verifications/bulk-approve", group.id) {
            header("Authorization", "Bearer $ownerToken")
            contentType = MediaType.APPLICATION_JSON
            content = """{"ids": [${pending.id}, ${approved.id}]}"""
        }
            .andExpect {
                status { isConflict() }
            }

        assertThat(statusInDb(pending)).isEqualTo(HabitVerifyStatus.PENDING)
        assertThat(statusInDb(approved)).isEqualTo(HabitVerifyStatus.APPROVED)
    }

    @Nested
    @DisplayName("단건 승인 PATCH /api/habits/verifications/{id}/approve")
    inner class Approve {

        private fun approve(token: String, verificationId: Long?): ResultActionsDsl =
            mockMvc.patch("/api/habits/verifications/{id}/approve", verificationId) {
                header("Authorization", "Bearer $token")
            }

        @Test
        @DisplayName("방장이 검토 대기 인증을 승인하면 200이고 DB에 APPROVED로 저장된다")
        fun 방장_승인_200() {
            val pending = saveVerify(LocalDate.of(2026, 10, 20), HabitVerifyStatus.PENDING)

            approve(ownerToken, pending.id)
                .andExpect {
                    status { isOk() }
                    jsonPath("$.status") { value("APPROVED") }
                }

            assertThat(statusInDb(pending)).isEqualTo(HabitVerifyStatus.APPROVED)
        }

        @Test
        @DisplayName("일반 멤버가 승인하면 403이고 DB는 PENDING 그대로다")
        fun 일반_멤버_403() {
            val pending = saveVerify(LocalDate.of(2026, 10, 20), HabitVerifyStatus.PENDING)

            approve(memberToken, pending.id)
                .andExpect {
                    status { isForbidden() }
                }

            assertThat(statusInDb(pending)).isEqualTo(HabitVerifyStatus.PENDING)
        }

        @Test
        @DisplayName("없는 인증이면 404다")
        fun 없는_인증_404() {
            approve(ownerToken, Long.MAX_VALUE)
                .andExpect {
                    status { isNotFound() }
                    jsonPath("$.message") { value("존재하지 않는 인증 기록입니다.") }
                }
        }
    }

    @Nested
    @DisplayName("단건 반려 PATCH /api/habits/verifications/{id}/reject")
    inner class Reject {

        private fun reject(token: String, verificationId: Long?): ResultActionsDsl =
            mockMvc.patch("/api/habits/verifications/{id}/reject", verificationId) {
                header("Authorization", "Bearer $token")
            }

        @Test
        @DisplayName("방장이 검토 대기 인증을 반려하면 200이고 DB에 REJECTED로 저장된다")
        fun 방장_반려_200() {
            val pending = saveVerify(LocalDate.of(2026, 10, 20), HabitVerifyStatus.PENDING)

            reject(ownerToken, pending.id)
                .andExpect {
                    status { isOk() }
                    jsonPath("$.status") { value("REJECTED") }
                }

            assertThat(statusInDb(pending)).isEqualTo(HabitVerifyStatus.REJECTED)
        }

        @Test
        @DisplayName("일반 멤버가 반려하면 403이고 DB는 PENDING 그대로다")
        fun 일반_멤버_403() {
            val pending = saveVerify(LocalDate.of(2026, 10, 20), HabitVerifyStatus.PENDING)

            reject(memberToken, pending.id)
                .andExpect {
                    status { isForbidden() }
                }

            assertThat(statusInDb(pending)).isEqualTo(HabitVerifyStatus.PENDING)
        }

        @Test
        @DisplayName("없는 인증이면 404다")
        fun 없는_인증_404() {
            reject(ownerToken, Long.MAX_VALUE)
                .andExpect {
                    status { isNotFound() }
                    jsonPath("$.message") { value("존재하지 않는 인증 기록입니다.") }
                }
        }
    }

    @Nested
    @DisplayName("검토 대기 목록 GET /api/groups/{groupId}/habits/verifications/pending")
    inner class Pending {

        private fun getPending(token: String): ResultActionsDsl =
            mockMvc.get("/api/groups/{groupId}/habits/verifications/pending", group.id) {
                header("Authorization", "Bearer $token")
            }

        @Test
        @DisplayName("방장이 조회하면 200이고 검토 대기(PENDING) 인증만 받는다")
        fun 방장_조회_200() {
            val pending = saveVerify(LocalDate.of(2026, 10, 20), HabitVerifyStatus.PENDING)
            saveVerify(LocalDate.of(2026, 10, 21), HabitVerifyStatus.APPROVED)

            getPending(ownerToken)
                .andExpect {
                    status { isOk() }
                    jsonPath("$.length()") { value(1) }
                    jsonPath("$[0].id") { value(pending.id) }
                    jsonPath("$[0].status") { value("PENDING") }
                }
        }

        @Test
        @DisplayName("일반 멤버가 조회하면 403이다")
        fun 일반_멤버_403() {
            saveVerify(LocalDate.of(2026, 10, 20), HabitVerifyStatus.PENDING)

            getPending(memberToken)
                .andExpect {
                    status { isForbidden() }
                }
        }
    }
}
