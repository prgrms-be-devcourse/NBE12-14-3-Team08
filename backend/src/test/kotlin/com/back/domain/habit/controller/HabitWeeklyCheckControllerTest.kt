package com.back.domain.habit.controller

import com.back.domain.group.entity.Group
import com.back.domain.group.repository.GroupRepository
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.habit.entity.Habit
import com.back.domain.habit.entity.HabitStatus
import com.back.domain.habit.repository.HabitRepository
import com.back.domain.habit.service.HabitService
import com.back.domain.habit.service.HabitWeeklyCheckScheduler
import com.back.domain.habitVerify.entity.HabitVerify
import com.back.domain.habitVerify.entity.HabitVerifyStatus
import com.back.domain.habitVerify.repository.HabitVerifyRepository
import com.back.domain.member.entity.Member
import com.back.domain.member.repository.MemberRepository
import com.back.domain.member.service.AuthTokenService
import com.back.domain.penaltyverify.repository.PenaltyVerifyRepository
import com.back.global.entity.requiredId
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * [HabitControllerTest]와 달리 회원가입/그룹생성/그룹가입을 실제 API로 거치지 않고,
 * weekly-check 판정에 필요한 최소한의 데이터만 리포지토리로 직접 insert해서 검증한다.
 * API 호출 왕복이 없어 더 빠르고, 각 시나리오에 필요한 createDate를 바로 세팅할 수 있다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HabitWeeklyCheckControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var habitService: HabitService

    @Autowired
    private lateinit var habitWeeklyCheckScheduler: HabitWeeklyCheckScheduler

    @Autowired
    private lateinit var authTokenService: AuthTokenService

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var groupRepository: GroupRepository

    @Autowired
    private lateinit var groupMemberRepository: GroupMemberRepository

    @Autowired
    private lateinit var habitRepository: HabitRepository

    @Autowired
    private lateinit var habitVerifyRepository: HabitVerifyRepository

    @Autowired
    private lateinit var penaltyVerifyRepository: PenaltyVerifyRepository

    private var groupId: Long = 0L
    private var memberId: Long = 0L
    private lateinit var ownerToken: String
    private var habitId: Long = 0L

    // 기본값: 그룹 생성일 14일 전(오늘과 같은 요일) / 가입일·습관 생성일 10일 전
    // -> 별도 조작 없이 바로 "가입 1주 경과 + 이번 주 마감 지남" 상태에서 시작한다.
    // 방장(OWNER)이 습관을 갖고 있고, 주간 판정도 방장 권한으로 호출한다.
    @BeforeEach
    fun setUp() {
        val now = LocalDateTime.now()

        val owner = memberRepository.save(Member.create("방장", "weekly-owner01", "password1234"))
        val ownerId: Long = owner.requiredId
        memberId = ownerId
        ownerToken = authTokenService.createAccessToken(ownerId)

        val group = groupRepository.save(
            Group.builder()
                .title("테스트 그룹")
                .penalty("팔굽혀펴기 30개")
                .password("1234")
                .inviteCode("WEEKLYCHECK01")
                .memberLimit(5)
                .build(),
        )
        ReflectionTestUtils.setField(group, "createDate", now.minusDays(14))
        groupRepository.saveAndFlush(group)
        groupId = group.requiredId

        val groupMember = groupMemberRepository.save(GroupMember.create(group, owner, GroupMemberRole.OWNER))
        ReflectionTestUtils.setField(groupMember, "createDate", now.minusDays(10))
        groupMemberRepository.saveAndFlush(groupMember)

        val habit = habitRepository.save(Habit.create(groupMember, "물 마시기", "하루 2L 마시기", 3))
        ReflectionTestUtils.setField(habit, "createDate", now.minusDays(10))
        habitRepository.saveAndFlush(habit)
        habitId = requireNotNull(habit.id)
    }

    // 이번 주 판정 구간(오늘 00시 이전) 안에 들어오도록 제출 시각을 3일 전으로 되돌린 인증 기록 생성
    // submittedDaysAgo: 제출 시각(createDate)을 며칠 전으로 되돌릴지 (0이면 방금 제출)
    private fun createVerify(verifyDate: LocalDate, status: HabitVerifyStatus, submittedDaysAgo: Long = 3): Long {
        val habit = habitRepository.findById(habitId).orElseThrow()

        // HabitVerify.create는 항상 PENDING으로 생성하므로, APPROVED/REJECTED가 필요하면 심사 처리까지 이어서 한다.
        val verify = HabitVerify.create(habit, verifyDate, "테스트 인증", "https://x.com/a.jpg")
        if (status == HabitVerifyStatus.APPROVED) {
            verify.approve()
        } else if (status == HabitVerifyStatus.REJECTED) {
            verify.reject()
        }
        habitVerifyRepository.save(verify)

        ReflectionTestUtils.setField(verify, "createDate", LocalDateTime.now().minusDays(submittedDaysAgo))
        habitVerifyRepository.saveAndFlush(verify)

        return requireNotNull(verify.id)
    }

    // 방장 권한으로 주간 판정을 호출한다.
    private fun weeklyCheck(token: String = ownerToken) =
        mockMvc.perform(
            post("/api/groups/{groupId}/weekly-check", groupId)
                .header("Authorization", "Bearer $token"),
        )

    @Test
    @DisplayName("가입한 지 1주가 안 되면 TOO_EARLY를 반환한다")
    fun 가입_1주_미만이면_TOO_EARLY() {
        val groupMember = groupMemberRepository
            .findByGroupIdAndMemberId(groupId, memberId)
            .orElseThrow()
        ReflectionTestUtils.setField(groupMember, "createDate", LocalDateTime.now())
        groupMemberRepository.saveAndFlush(groupMember)

        weeklyCheck()
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].habitId").value(habitId))
            .andExpect(jsonPath("$[0].result").value("TOO_EARLY"))
            .andExpect(jsonPath("$[0].habitStatus").value("ACTIVE"))
    }

    @Test
    @DisplayName("가입 1주는 지났지만 습관이 이번 판정 구간 마감 이후에 생성됐다면 BEFORE_DEADLINE을 반환한다")
    fun 습관_생성이_마감_이후면_BEFORE_DEADLINE() {
        val habit = habitRepository.findById(habitId).orElseThrow()
        ReflectionTestUtils.setField(habit, "createDate", LocalDateTime.now())
        habitRepository.saveAndFlush(habit)

        weeklyCheck()
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].result").value("BEFORE_DEADLINE"))
            .andExpect(jsonPath("$[0].habitStatus").value("ACTIVE"))
    }

    @Test
    @DisplayName("이미 실패 처리된 습관은 판정 대상에서 제외된다 (그룹 내 활성 습관만 조회)")
    fun 이미_실패한_습관은_판정_대상에서_제외() {
        val habit = habitRepository.findById(habitId).orElseThrow()
        habit.fail()
        habitRepository.saveAndFlush(habit)

        weeklyCheck()
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    @DisplayName("승인된 인증 횟수가 days 이상이면 PASSED를 반환한다")
    fun 승인_횟수가_충분하면_PASSED() {
        createVerify(LocalDate.now().minusDays(1), HabitVerifyStatus.APPROVED)
        createVerify(LocalDate.now().minusDays(2), HabitVerifyStatus.APPROVED)
        createVerify(LocalDate.now().minusDays(3), HabitVerifyStatus.APPROVED)

        weeklyCheck()
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].result").value("PASSED"))
            .andExpect(jsonPath("$[0].habitStatus").value("ACTIVE"))
    }

    @Test
    @DisplayName("미검토(PENDING) 건이 남아있고 검토 유예시간도 안 지났으면 PENDING_REVIEW로 보류된다")
    fun 미검토_건이_있으면_PENDING_REVIEW() {
        createVerify(LocalDate.now().minusDays(1), HabitVerifyStatus.APPROVED)
        createVerify(LocalDate.now().minusDays(2), HabitVerifyStatus.APPROVED)
        createVerify(LocalDate.now().minusDays(3), HabitVerifyStatus.APPROVED)
        createVerify(LocalDate.now().minusDays(4), HabitVerifyStatus.PENDING)

        weeklyCheck()
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].result").value("PENDING_REVIEW"))
            .andExpect(jsonPath("$[0].habitStatus").value("ACTIVE"))
    }

    @Test
    @DisplayName("검토 유예시간(마감 다음날 24시)이 지나면 미검토 건이 자동 승인되어 판정이 이어서 진행된다")
    fun 유예시간이_지나면_미검토_건_자동_승인_후_판정() {
        // 그룹 생성 요일을 "어제"로 맞춰서 이번 주 마감(currentDeadline)이 어제 00시,
        // 검토 유예시간(마감 + 24시간)이 오늘 00시로 이미 지난 상태를 만든다.
        val now = LocalDateTime.now()

        val group = groupRepository.findById(groupId).orElseThrow()
        ReflectionTestUtils.setField(group, "createDate", now.minusDays(15))
        groupRepository.saveAndFlush(group)

        createVerify(LocalDate.now().minusDays(2), HabitVerifyStatus.APPROVED)
        createVerify(LocalDate.now().minusDays(3), HabitVerifyStatus.APPROVED)
        val pendingVerifyId = createVerify(LocalDate.now().minusDays(4), HabitVerifyStatus.PENDING)

        weeklyCheck()
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].result").value("PASSED"))
            .andExpect(jsonPath("$[0].habitStatus").value("ACTIVE"))

        val autoApproved = habitVerifyRepository.findById(pendingVerifyId).orElseThrow()
        Assertions.assertEquals(HabitVerifyStatus.APPROVED, autoApproved.status)
    }

    @Test
    @DisplayName("weekly-check를 아무도 호출하지 않아도 스케줄러가 유예시간 지난 미검토 건을 자동 승인하고 확정한다")
    fun 스케줄러가_전체_활성_습관을_순회하며_자동_승인_및_판정() {
        val now = LocalDateTime.now()

        val group = groupRepository.findById(groupId).orElseThrow()
        ReflectionTestUtils.setField(group, "createDate", now.minusDays(15))
        groupRepository.saveAndFlush(group)

        createVerify(LocalDate.now().minusDays(2), HabitVerifyStatus.APPROVED)
        createVerify(LocalDate.now().minusDays(3), HabitVerifyStatus.APPROVED)
        val pendingVerifyId = createVerify(LocalDate.now().minusDays(4), HabitVerifyStatus.PENDING)

        // 컨트롤러/멤버 호출 없이 스케줄러가 하는 일만 직접 실행
        habitWeeklyCheckScheduler.checkAllActiveHabits()

        val autoApproved = habitVerifyRepository.findById(pendingVerifyId).orElseThrow()
        Assertions.assertEquals(HabitVerifyStatus.APPROVED, autoApproved.status)

        val habit = habitRepository.findById(habitId).orElseThrow()
        Assertions.assertEquals(HabitStatus.ACTIVE, habit.status)
    }

    @Test
    @DisplayName("검토가 끝났는데도 days 미달이면 실패 처리되고 벌칙이 확정된다")
    fun 승인_횟수가_부족하면_실패_및_벌칙_확정() {
        createVerify(LocalDate.now().minusDays(1), HabitVerifyStatus.APPROVED)

        weeklyCheck()
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].result").value("FAILED_INSUFFICIENT_SUBMISSION"))
            .andExpect(jsonPath("$[0].habitStatus").value("FAILED"))

        Assertions.assertNotNull(penaltyVerifyRepository.findByHabitId(habitId))
    }

    @Test
    @DisplayName("방장이 아닌 일반 멤버가 호출하면 403을 반환한다")
    fun 방장이_아니면_403() {
        val regularMember = memberRepository.save(Member.create("일반멤버", "weekly-member02", "password1234"))
        val group = groupRepository.findById(groupId).orElseThrow()
        groupMemberRepository.save(GroupMember.create(group, regularMember, GroupMemberRole.MEMBER))
        val regularMemberId: Long = regularMember.requiredId
        val memberToken = authTokenService.createAccessToken(regularMemberId)

        weeklyCheck(memberToken)
            .andExpect(status().isForbidden())
    }

    @Test
    @DisplayName("그룹 멤버가 아닌 사람이 호출하면 400을 반환한다")
    fun 그룹_멤버가_아니면_400() {
        val other = memberRepository.save(Member.create("다른멤버", "weekly-other01", "password1234"))
        val otherId: Long = other.requiredId
        val otherToken = authTokenService.createAccessToken(otherId)

        weeklyCheck(otherToken)
            .andExpect(status().isBadRequest())
    }

    @Test
    @DisplayName("승인된 인증이 days보다 1개 모자라면 실패 처리되고 벌칙이 확정된다 (경계값)")
    fun 승인_횟수가_days보다_하나_적으면_실패() {
        createVerify(LocalDate.now().minusDays(1), HabitVerifyStatus.APPROVED)
        createVerify(LocalDate.now().minusDays(2), HabitVerifyStatus.APPROVED)

        weeklyCheck()
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].result").value("FAILED_INSUFFICIENT_SUBMISSION"))
            .andExpect(jsonPath("$[0].habitStatus").value("FAILED"))

        Assertions.assertNotNull(penaltyVerifyRepository.findByHabitId(habitId))
    }

    @Test
    @DisplayName("승인된 인증이 days보다 많아도 PASSED다")
    fun 승인_횟수가_days를_넘어도_PASSED() {
        createVerify(LocalDate.now().minusDays(1), HabitVerifyStatus.APPROVED)
        createVerify(LocalDate.now().minusDays(2), HabitVerifyStatus.APPROVED)
        createVerify(LocalDate.now().minusDays(3), HabitVerifyStatus.APPROVED)
        createVerify(LocalDate.now().minusDays(4), HabitVerifyStatus.APPROVED)

        weeklyCheck()
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].result").value("PASSED"))
            .andExpect(jsonPath("$[0].habitStatus").value("ACTIVE"))
    }

    @Test
    @DisplayName("반려된 인증은 승인 횟수에 포함되지 않는다")
    fun 반려된_인증은_횟수에_포함되지_않는다() {
        createVerify(LocalDate.now().minusDays(1), HabitVerifyStatus.APPROVED)
        createVerify(LocalDate.now().minusDays(2), HabitVerifyStatus.APPROVED)
        createVerify(LocalDate.now().minusDays(3), HabitVerifyStatus.REJECTED)

        weeklyCheck()
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].result").value("FAILED_INSUFFICIENT_SUBMISSION"))
            .andExpect(jsonPath("$[0].habitStatus").value("FAILED"))
    }

    @Test
    @DisplayName("이번 판정 구간보다 이전에 제출된 인증은 승인 횟수에 포함되지 않는다")
    fun 지난_구간에_제출된_인증은_횟수에_포함되지_않는다() {
        createVerify(LocalDate.now().minusDays(9), HabitVerifyStatus.APPROVED, 9)
        createVerify(LocalDate.now().minusDays(10), HabitVerifyStatus.APPROVED, 9)
        createVerify(LocalDate.now().minusDays(11), HabitVerifyStatus.APPROVED, 9)

        weeklyCheck()
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].result").value("FAILED_INSUFFICIENT_SUBMISSION"))
            .andExpect(jsonPath("$[0].habitStatus").value("FAILED"))
    }

    @Test
    @DisplayName("이번 주 마감 이후에 제출된 인증은 이번 판정의 승인 횟수에 포함되지 않는다")
    fun 마감_이후_제출된_인증은_횟수에_포함되지_않는다() {
        createVerify(LocalDate.now().minusDays(1), HabitVerifyStatus.APPROVED)
        createVerify(LocalDate.now().minusDays(2), HabitVerifyStatus.APPROVED)
        createVerify(LocalDate.now(), HabitVerifyStatus.APPROVED, 0)

        weeklyCheck()
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].result").value("FAILED_INSUFFICIENT_SUBMISSION"))
            .andExpect(jsonPath("$[0].habitStatus").value("FAILED"))
    }
}
