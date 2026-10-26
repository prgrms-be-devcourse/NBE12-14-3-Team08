package com.back.domain.settlement.service

import com.back.domain.group.entity.Group
import com.back.domain.group.entity.GroupStatus
import com.back.domain.group.repository.GroupRepository
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.habit.entity.Habit
import com.back.domain.habitVerify.entity.HabitVerify
import com.back.domain.habitVerify.entity.HabitVerifyStatus
import com.back.domain.habitVerify.repository.HabitVerifyRepository
import com.back.domain.member.entity.Member
import com.back.domain.penaltyverify.entity.PenaltyVerify
import com.back.domain.penaltyverify.repository.PenaltyVerifyRepository
import com.back.domain.settlement.dto.SettlementResponse
import com.back.global.exception.BusinessRuleException
import com.back.global.exception.EntityNotFoundException
import com.back.global.exception.ForbiddenException
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional

// 결산 집계 규칙을 DB 없이 확인한다. 저장소는 목이고 엔티티는 직접 만들어 id와 생성 시각만 심는다.
@ExtendWith(MockKExtension::class)
class SettlementServiceTest {

    @InjectMockKs
    private lateinit var settlementService: SettlementService

    @MockK
    private lateinit var groupRepository: GroupRepository

    @MockK
    private lateinit var groupMemberRepository: GroupMemberRepository

    @MockK
    private lateinit var habitVerifyRepository: HabitVerifyRepository

    @MockK
    private lateinit var penaltyVerifyRepository: PenaltyVerifyRepository

    companion object {
        private const val GROUP_ID = 100L

        // 그룹 시작일. 마감일 인증은 이 날부터 7일 단위로 센다.
        private val START: LocalDate = LocalDate.of(2026, 10, 1)
    }

    // --- 픽스처 ---

    private class Participant(val member: Member, val groupMember: GroupMember, val habit: Habit)

    private fun group(status: GroupStatus = GroupStatus.FINISH, createDate: LocalDateTime? = START.atTime(9, 0)): Group {
        val group = Group.builder().title("아침 기상 챌린지").penalty("커피 1잔 쏘기").memberLimit(10).status(status).build()
        ReflectionTestUtils.setField(group, "id", GROUP_ID)
        ReflectionTestUtils.setField(group, "createDate", createDate)
        return group
    }

    private fun participant(group: Group, id: Long, nickname: String = "닉$id"): Participant {
        val member = Member.create(nickname, "user$id", "pw")
        ReflectionTestUtils.setField(member, "id", id)
        val groupMember = GroupMember.create(group, member, GroupMemberRole.MEMBER)
        return Participant(member, groupMember, Habit.create(groupMember, "습관", "설명", 3))
    }

    private fun verify(
        who: Participant,
        date: LocalDate = START.plusDays(1),
        status: HabitVerifyStatus = HabitVerifyStatus.APPROVED,
        description: String? = null,
    ): HabitVerify {
        val verify = HabitVerify.create(who.habit, date, description, "https://example.com/a.png")
        when (status) {
            HabitVerifyStatus.APPROVED -> verify.approve()
            HabitVerifyStatus.REJECTED -> verify.reject()
            HabitVerifyStatus.PENDING -> Unit
        }
        return verify
    }

    // createdAt이 null이면 생성 시각이 비어 있는 벌칙이고, submittedOn이 null이면 아직 제출하지 않은 벌칙이다.
    private fun penalty(who: Participant, createdAt: LocalDateTime? = START.plusDays(7).atTime(0, 5), submittedOn: LocalDate? = null): PenaltyVerify {
        val penalty = PenaltyVerify.create(who.groupMember, who.habit)
        if (createdAt != null) ReflectionTestUtils.setField(penalty, "createDate", createdAt)
        if (submittedOn != null) penalty.submit(submittedOn, "벌칙 수행", "https://example.com/p.png")
        return penalty
    }

    private fun settle(
        group: Group,
        viewer: Participant,
        verifies: List<HabitVerify> = emptyList(),
        penalties: List<PenaltyVerify> = emptyList(),
    ): SettlementResponse {
        every { groupRepository.findById(GROUP_ID) } returns Optional.of(group)
        every { groupMemberRepository.findByGroupIdAndMemberId(GROUP_ID, requireNotNull(viewer.member.id)) } returns Optional.of(viewer.groupMember)
        every { habitVerifyRepository.findAllByGroupId(GROUP_ID) } returns verifies
        every { penaltyVerifyRepository.findAllByGroupId(GROUP_ID) } returns penalties
        return settlementService.getSettlement(GROUP_ID, requireNotNull(viewer.member.id))
    }

    private fun winner(memberId: Long, value: Long, nickname: String = "닉$memberId") = SettlementResponse.Winner(memberId, nickname, value)

    // --- 선행 조건 ---

    @Nested
    @DisplayName("조회 조건")
    inner class Preconditions {

        @Test
        @DisplayName("없는 그룹이면 EntityNotFoundException이고 멤버 확인도 집계도 하지 않는다")
        fun 없는_그룹() {
            every { groupRepository.findById(GROUP_ID) } returns Optional.empty()

            assertThatThrownBy { settlementService.getSettlement(GROUP_ID, 1L) }
                .isInstanceOf(EntityNotFoundException::class.java)
                .hasMessage("존재하지 않는 그룹입니다.")

            verify(exactly = 0) { groupMemberRepository.findByGroupIdAndMemberId(any(), any()) }
            verify(exactly = 0) { habitVerifyRepository.findAllByGroupId(any()) }
        }

        @Test
        @DisplayName("그룹 멤버가 아니면 ForbiddenException이고 집계하지 않는다")
        fun 멤버가_아니다() {
            every { groupRepository.findById(GROUP_ID) } returns Optional.of(group())
            every { groupMemberRepository.findByGroupIdAndMemberId(GROUP_ID, 9L) } returns Optional.empty()

            assertThatThrownBy { settlementService.getSettlement(GROUP_ID, 9L) }
                .isInstanceOf(ForbiddenException::class.java)
                .hasMessage("해당 그룹의 결산을 조회할 권한이 없습니다.")

            verify(exactly = 0) { habitVerifyRepository.findAllByGroupId(any()) }
            verify(exactly = 0) { penaltyVerifyRepository.findAllByGroupId(any()) }
        }

        @Test
        @DisplayName("완료(FINISH)되지 않은 그룹이면 BusinessRuleException이고 집계하지 않는다")
        fun 진행_중인_그룹() {
            val group = group(status = GroupStatus.ACTIVE)
            val viewer = participant(group, 1L)
            every { groupRepository.findById(GROUP_ID) } returns Optional.of(group)
            every { groupMemberRepository.findByGroupIdAndMemberId(GROUP_ID, 1L) } returns Optional.of(viewer.groupMember)

            assertThatThrownBy { settlementService.getSettlement(GROUP_ID, 1L) }
                .isInstanceOf(BusinessRuleException::class.java)
                .hasMessage("완료된 그룹만 결산을 조회할 수 있습니다.")

            verify(exactly = 0) { habitVerifyRepository.findAllByGroupId(any()) }
            verify(exactly = 0) { penaltyVerifyRepository.findAllByGroupId(any()) }
        }

        @Test
        @DisplayName("진행 중인 그룹의 멤버가 아니면 상태가 아니라 권한 오류가 먼저다 (멤버 확인이 상태 확인보다 앞선다)")
        fun 검사_순서_멤버가_먼저() {
            every { groupRepository.findById(GROUP_ID) } returns Optional.of(group(status = GroupStatus.ACTIVE))
            every { groupMemberRepository.findByGroupIdAndMemberId(GROUP_ID, 9L) } returns Optional.empty()

            assertThatThrownBy { settlementService.getSettlement(GROUP_ID, 9L) }.isInstanceOf(ForbiddenException::class.java)
        }

        @Test
        @DisplayName("없는 그룹이면 멤버 여부와 상관없이 그룹 없음 오류가 먼저다")
        fun 검사_순서_그룹이_먼저() {
            every { groupRepository.findById(GROUP_ID) } returns Optional.empty()

            assertThatThrownBy { settlementService.getSettlement(GROUP_ID, 9L) }.isInstanceOf(EntityNotFoundException::class.java)
        }

        @Test
        @DisplayName("완료된 그룹의 멤버면 그룹 id와 제목이 응답에 담긴다")
        fun 정상_조회() {
            val group = group()
            val viewer = participant(group, 1L)

            val response = settle(group, viewer)

            assertThat(response.groupId).isEqualTo(GROUP_ID)
            assertThat(response.groupTitle).isEqualTo("아침 기상 챌린지")
        }
    }

    // --- 개인 통계 ---

    @Nested
    @DisplayName("개인 통계")
    inner class PersonalStats {

        @Test
        @DisplayName("인증이 하나도 없으면 회원 정보와 모든 횟수가 0이다")
        fun 기록이_없다() {
            val group = group()
            val viewer = participant(group, 1L, "나")

            val stats = settle(group, viewer).personalStats

            assertThat(stats).isEqualTo(SettlementResponse.PersonalStats(1L, "나", 0, 0, 0, 0))
        }

        @Test
        @DisplayName("전체, 승인, 반려 인증 수를 센다 (대기 중인 인증은 전체에만 들어간다)")
        fun 인증_수() {
            val group = group()
            val me = participant(group, 1L)
            val verifies = listOf(
                verify(me, START.plusDays(1), HabitVerifyStatus.APPROVED),
                verify(me, START.plusDays(2), HabitVerifyStatus.APPROVED),
                verify(me, START.plusDays(3), HabitVerifyStatus.REJECTED),
                verify(me, START.plusDays(4), HabitVerifyStatus.REJECTED),
                verify(me, START.plusDays(5), HabitVerifyStatus.REJECTED),
                verify(me, START.plusDays(6), HabitVerifyStatus.PENDING),
            )

            val stats = settle(group, me, verifies).personalStats

            // 승인 2, 반려 3, 대기 1: 세 값이 서로 달라야 어느 상태를 세는지 구분된다. (대기는 전체에만 들어간다)
            assertThat(stats.totalVerifyCount).isEqualTo(6)
            assertThat(stats.approvedCount).isEqualTo(2)
            assertThat(stats.rejectedCount).isEqualTo(3)
        }

        @Test
        @DisplayName("다른 회원의 인증은 내 통계에 들어가지 않는다")
        fun 다른_회원의_인증은_제외() {
            val group = group()
            val me = participant(group, 1L)
            val other = participant(group, 2L)
            val verifies = listOf(
                verify(me, START.plusDays(1), HabitVerifyStatus.APPROVED),
                verify(other, START.plusDays(1), HabitVerifyStatus.APPROVED),
                verify(other, START.plusDays(2), HabitVerifyStatus.REJECTED),
            )

            val stats = settle(group, me, verifies).personalStats

            assertThat(stats.totalVerifyCount).isEqualTo(1)
            assertThat(stats.approvedCount).isEqualTo(1)
            assertThat(stats.rejectedCount).isEqualTo(0)
        }

        @Test
        @DisplayName("벌칙 수는 상태와 상관없이 내 벌칙을 모두 센다 (제출 전, 제출 후, 초기화된 벌칙 포함)")
        fun 벌칙_수() {
            val group = group()
            val me = participant(group, 1L)
            val other = participant(group, 2L)
            val submitted = penalty(me, submittedOn = START.plusDays(9)).also { it.approve() }
            val reset = penalty(me, submittedOn = START.plusDays(9)).also {
                it.approve()
                it.resetByOwner()
            }
            val penalties = listOf(penalty(me), submitted, reset, penalty(other))

            val stats = settle(group, me, penalties = penalties).personalStats

            assertThat(stats.penaltyCount).isEqualTo(3)
        }

        @Test
        @DisplayName("개인 통계는 조회한 회원 기준이라 같은 데이터도 보는 사람에 따라 다르다")
        fun 조회자_기준() {
            val group = group()
            val a = participant(group, 1L, "에이")
            val b = participant(group, 2L, "비")
            val verifies = listOf(verify(a, START.plusDays(1)), verify(b, START.plusDays(1)), verify(b, START.plusDays(2)))

            assertThat(settle(group, a, verifies).personalStats.totalVerifyCount).isEqualTo(1)
            assertThat(settle(group, b, verifies).personalStats.totalVerifyCount).isEqualTo(2)
            assertThat(settle(group, b, verifies).personalStats.nickname).isEqualTo("비")
        }

        @Test
        @DisplayName("방장이 초기화한 벌칙도 결산의 벌칙 수에 포함되고, 제출일이 없어 지연 집계에서는 빠진다")
        fun 초기화된_벌칙도_벌칙수에_포함() {
            val group = group()
            val me = participant(group, 2L, "일반회원")
            val reset = penalty(me, createdAt = LocalDateTime.of(2026, 10, 8, 0, 5), submittedOn = LocalDate.of(2026, 10, 10)).also {
                it.approve()
                it.resetByOwner()
            }

            val response = settle(group, me, penalties = listOf(reset))

            assertThat(response.personalStats.penaltyCount).isEqualTo(1)
            assertThat(response.groupComparison.longestPenaltyDelay).isNull()
        }
    }

    // --- 그룹 비교: 설명 글자 수 ---

    @Nested
    @DisplayName("그룹 비교: 설명을 가장 길게 쓴 사람")
    inner class DescriptionChars {

        @Test
        @DisplayName("회원별로 설명 글자 수를 모두 더해 가장 많은 사람이 1등이다")
        fun 합산() {
            val group = group()
            val a = participant(group, 1L)
            val b = participant(group, 2L)
            val verifies = listOf(
                verify(a, START.plusDays(1), description = "안녕"),
                verify(a, START.plusDays(2), description = "하세요"),
                verify(b, START.plusDays(1), description = "네글자네글"),
            )

            assertThat(settle(group, a, verifies).groupComparison.mostDescriptionChars).isEqualTo(winner(1L, 5))
        }

        @Test
        @DisplayName("글자 수는 UTF-16 길이가 아니라 코드포인트 기준이라 이모지 하나는 1글자다")
        fun 이모지는_한_글자() {
            val group = group()
            val a = participant(group, 1L)

            val actual = settle(group, a, listOf(verify(a, description = "😀😀😀"))).groupComparison.mostDescriptionChars

            assertThat(actual).isEqualTo(winner(1L, 3))
        }

        @Test
        @DisplayName("공백도 글자로 센다 (문자열 안의 공백 포함)")
        fun 공백도_센다() {
            val group = group()
            val a = participant(group, 1L)

            val actual = settle(group, a, listOf(verify(a, description = "a b c"))).groupComparison.mostDescriptionChars

            assertThat(actual).isEqualTo(winner(1L, 5))
        }

        @Test
        @DisplayName("설명이 null, 빈 값, 공백뿐이면 세지 않아 1등이 없다")
        fun 비어_있는_설명() {
            val group = group()
            val a = participant(group, 1L)
            val verifies = listOf(
                verify(a, START.plusDays(1), description = null),
                verify(a, START.plusDays(2), description = ""),
                verify(a, START.plusDays(3), description = "   "),
                verify(a, START.plusDays(4), description = "\n\t"),
            )

            assertThat(settle(group, a, verifies).groupComparison.mostDescriptionChars).isNull()
        }

        @Test
        @DisplayName("줄바꿈 없는 공백(U+00A0)만 있는 설명은 공백으로 보지 않고 글자로 센다 (현재 동작: Java의 isBlank와 같다)")
        fun 줄바꿈_없는_공백은_글자다() {
            val group = group()
            val a = participant(group, 1L)

            val actual = settle(group, a, listOf(verify(a, description = "  "))).groupComparison.mostDescriptionChars

            assertThat(actual).isEqualTo(winner(1L, 2))
        }

        @Test
        @DisplayName("반려되거나 대기 중인 인증의 설명도 글자 수에 들어간다 (현재 동작)")
        fun 상태와_상관없이_센다() {
            val group = group()
            val a = participant(group, 1L)
            val b = participant(group, 2L)
            val verifies = listOf(
                verify(a, START.plusDays(1), HabitVerifyStatus.REJECTED, "열글자열글자열글자"),
                verify(b, START.plusDays(1), HabitVerifyStatus.APPROVED, "짧음"),
            )

            assertThat(settle(group, a, verifies).groupComparison.mostDescriptionChars).isEqualTo(winner(1L, 9))
        }
    }

    // --- 그룹 비교: 마감일 인증 ---

    @Nested
    @DisplayName("그룹 비교: 마감일에 인증한 사람")
    inner class DeadlineVerifications {

        @ParameterizedTest(name = "시작일 + {0}일 인증 → 마감일 인증 {1}")
        @CsvSource(
            "-1,false", "0,false", "1,false", "5,false", "6,true", "7,false",
            "12,false", "13,true", "14,false", "20,true", "27,true", "28,false",
        )
        @DisplayName("시작일부터 센 경과일이 6, 13, 20처럼 (7의 배수 - 1)인 날만 마감일 인증이다")
        fun 경과일_경계(elapsed: Long, counted: Boolean) {
            val group = group()
            val a = participant(group, 1L)

            val actual = settle(group, a, listOf(verify(a, START.plusDays(elapsed)))).groupComparison.mostDeadlineVerifications

            if (counted) assertThat(actual).isEqualTo(winner(1L, 1)) else assertThat(actual).isNull()
        }

        @Test
        @DisplayName("승인된 인증만 세고 반려, 대기 중인 인증은 마감일이어도 세지 않는다")
        fun 승인된_인증만() {
            val group = group()
            val a = participant(group, 1L)
            val verifies = listOf(
                verify(a, START.plusDays(6), HabitVerifyStatus.REJECTED),
                verify(a, START.plusDays(13), HabitVerifyStatus.PENDING),
            )

            assertThat(settle(group, a, verifies).groupComparison.mostDeadlineVerifications).isNull()
        }

        @Test
        @DisplayName("회원별로 마감일 인증을 세어 가장 많은 사람이 1등이다")
        fun 많은_사람이_1등() {
            val group = group()
            val a = participant(group, 1L)
            val b = participant(group, 2L)
            val verifies = listOf(
                verify(a, START.plusDays(6)),
                verify(b, START.plusDays(6)),
                verify(b, START.plusDays(13)),
                verify(b, START.plusDays(20)),
            )

            assertThat(settle(group, a, verifies).groupComparison.mostDeadlineVerifications).isEqualTo(winner(2L, 3))
        }

        @Test
        @DisplayName("시작일은 생성 시각의 시간과 상관없이 날짜만 쓴다 (23시 59분에 만든 그룹도 그날이 시작일)")
        fun 시작일은_날짜만_쓴다() {
            val group = group(createDate = START.atTime(23, 59))
            val a = participant(group, 1L)

            val actual = settle(group, a, listOf(verify(a, START.plusDays(6)))).groupComparison.mostDeadlineVerifications

            assertThat(actual).isEqualTo(winner(1L, 1))
        }
    }

    // --- 그룹 비교: 반려 ---

    @Nested
    @DisplayName("그룹 비교: 반려를 가장 많이 받은 사람")
    inner class RejectedVerifications {

        @Test
        @DisplayName("반려된 인증만 세고 승인, 대기 인증은 세지 않는다")
        fun 반려만_센다() {
            val group = group()
            val a = participant(group, 1L)
            val b = participant(group, 2L)
            val verifies = listOf(
                verify(a, START.plusDays(1), HabitVerifyStatus.REJECTED),
                verify(a, START.plusDays(2), HabitVerifyStatus.REJECTED),
                verify(a, START.plusDays(3), HabitVerifyStatus.APPROVED),
                verify(b, START.plusDays(1), HabitVerifyStatus.PENDING),
                verify(b, START.plusDays(2), HabitVerifyStatus.APPROVED),
            )

            assertThat(settle(group, a, verifies).groupComparison.mostRejectedVerifications).isEqualTo(winner(1L, 2))
        }

        @Test
        @DisplayName("반려가 한 번도 없으면 1등이 없다")
        fun 반려가_없다() {
            val group = group()
            val a = participant(group, 1L)

            assertThat(settle(group, a, listOf(verify(a))).groupComparison.mostRejectedVerifications).isNull()
        }
    }

    // --- 그룹 비교: 벌칙 지연 ---

    @Nested
    @DisplayName("그룹 비교: 벌칙을 가장 늦게 낸 사람")
    inner class PenaltyDelays {

        @Test
        @DisplayName("벌칙이 생긴 날부터 제출일까지의 일수가 지연 일수다")
        fun 지연_일수() {
            val group = group()
            val a = participant(group, 1L)
            val p = penalty(a, createdAt = START.plusDays(7).atTime(0, 5), submittedOn = START.plusDays(10))

            assertThat(settle(group, a, penalties = listOf(p)).groupComparison.longestPenaltyDelay).isEqualTo(winner(1L, 3))
        }

        @Test
        @DisplayName("시각은 무시하고 날짜만 비교한다 (23시 59분에 생긴 벌칙을 다음 날 내면 1일)")
        fun 날짜만_비교() {
            val group = group()
            val a = participant(group, 1L)
            val p = penalty(a, createdAt = START.plusDays(7).atTime(23, 59), submittedOn = START.plusDays(8))

            assertThat(settle(group, a, penalties = listOf(p)).groupComparison.longestPenaltyDelay).isEqualTo(winner(1L, 1))
        }

        @Test
        @DisplayName("한 회원에게 벌칙이 여러 개면 합이 아니라 가장 긴 지연을 쓴다")
        fun 합이_아니라_최댓값() {
            val group = group()
            val a = participant(group, 1L)
            val penalties = listOf(
                penalty(a, createdAt = START.atTime(1, 0), submittedOn = START.plusDays(3)),
                penalty(a, createdAt = START.atTime(1, 0), submittedOn = START.plusDays(5)),
                penalty(a, createdAt = START.atTime(1, 0), submittedOn = START.plusDays(2)),
            )

            assertThat(settle(group, a, penalties = penalties).groupComparison.longestPenaltyDelay).isEqualTo(winner(1L, 5))
        }

        @Test
        @DisplayName("제출일이 생성일보다 빠르면 음수가 아니라 0일이라 1등이 되지 못한다")
        fun 음수는_영일() {
            val group = group()
            val a = participant(group, 1L)
            val p = penalty(a, createdAt = START.plusDays(5).atTime(9, 0), submittedOn = START.plusDays(2))

            assertThat(settle(group, a, penalties = listOf(p)).groupComparison.longestPenaltyDelay).isNull()
        }

        @Test
        @DisplayName("생성 당일에 낸 벌칙은 0일이라 1등이 되지 못한다")
        fun 당일_제출() {
            val group = group()
            val a = participant(group, 1L)
            val p = penalty(a, createdAt = START.plusDays(5).atTime(9, 0), submittedOn = START.plusDays(5))

            assertThat(settle(group, a, penalties = listOf(p)).groupComparison.longestPenaltyDelay).isNull()
        }

        @Test
        @DisplayName("아직 제출하지 않은 벌칙(제출일 없음)은 지연으로 세지 않는다")
        fun 제출하지_않은_벌칙() {
            val group = group()
            val a = participant(group, 1L)

            assertThat(settle(group, a, penalties = listOf(penalty(a))).groupComparison.longestPenaltyDelay).isNull()
        }

        @Test
        @DisplayName("생성 시각이 없는 벌칙은 제출일이 있어도 지연으로 세지 않는다")
        fun 생성_시각이_없는_벌칙() {
            val group = group()
            val a = participant(group, 1L)
            val p = penalty(a, createdAt = null, submittedOn = START.plusDays(5))

            assertThat(settle(group, a, penalties = listOf(p)).groupComparison.longestPenaltyDelay).isNull()
        }

        @Test
        @DisplayName("벌칙만 있는 회원도 닉네임이 매핑되어 1등이 될 수 있다 (인증 기록이 없어도)")
        fun 인증이_없는_회원도_닉네임이_나온다() {
            val group = group()
            val a = participant(group, 1L, "인증왕")
            val b = participant(group, 2L, "벌칙러")
            val verifies = listOf(verify(a))
            val p = penalty(b, createdAt = START.atTime(1, 0), submittedOn = START.plusDays(4))

            assertThat(settle(group, a, verifies, listOf(p)).groupComparison.longestPenaltyDelay).isEqualTo(winner(2L, 4, "벌칙러"))
        }
    }

    // --- 그룹 비교: 1등 결정 ---

    @Nested
    @DisplayName("그룹 비교: 1등 결정")
    inner class Winners {

        @Test
        @DisplayName("값이 같으면 회원 id가 작은 쪽이 1등이다 (현재 동작)")
        fun 동률이면_id가_작은_쪽() {
            val group = group()
            val low = participant(group, 3L)
            val high = participant(group, 7L)
            // 일부러 id가 큰 쪽의 인증을 먼저 둔다.
            val verifies = listOf(verify(high, START.plusDays(1), HabitVerifyStatus.REJECTED), verify(low, START.plusDays(1), HabitVerifyStatus.REJECTED))

            assertThat(settle(group, low, verifies).groupComparison.mostRejectedVerifications).isEqualTo(winner(3L, 1))
        }

        @Test
        @DisplayName("동률이어도 값이 더 큰 쪽이 id와 상관없이 이긴다")
        fun 값이_큰_쪽이_이긴다() {
            val group = group()
            val low = participant(group, 3L)
            val high = participant(group, 7L)
            val verifies = listOf(
                verify(low, START.plusDays(1), HabitVerifyStatus.REJECTED),
                verify(high, START.plusDays(1), HabitVerifyStatus.REJECTED),
                verify(high, START.plusDays(2), HabitVerifyStatus.REJECTED),
            )

            assertThat(settle(group, low, verifies).groupComparison.mostRejectedVerifications).isEqualTo(winner(7L, 2))
        }

        @Test
        @DisplayName("네 부문의 1등은 서로 다른 회원일 수 있다")
        fun 부문별로_독립() {
            val group = group()
            val a = participant(group, 1L, "수다쟁이")
            val b = participant(group, 2L, "마감러")
            val c = participant(group, 3L, "반려왕")
            val d = participant(group, 4L, "지각생")
            val verifies = listOf(
                verify(a, START.plusDays(1), description = "아주아주긴설명입니다"),
                verify(b, START.plusDays(6)),
                verify(c, START.plusDays(2), HabitVerifyStatus.REJECTED),
                verify(c, START.plusDays(3), HabitVerifyStatus.REJECTED),
            )
            val penalties = listOf(penalty(d, createdAt = START.atTime(1, 0), submittedOn = START.plusDays(6)))

            val comparison = settle(group, a, verifies, penalties).groupComparison

            assertThat(comparison.mostDescriptionChars).isEqualTo(winner(1L, 10, "수다쟁이"))
            assertThat(comparison.mostDeadlineVerifications).isEqualTo(winner(2L, 1, "마감러"))
            assertThat(comparison.mostRejectedVerifications).isEqualTo(winner(3L, 2, "반려왕"))
            assertThat(comparison.longestPenaltyDelay).isEqualTo(winner(4L, 6, "지각생"))
        }

        @Test
        @DisplayName("인증과 벌칙이 모두 없으면 네 부문 모두 1등이 없다")
        fun 기록이_없으면_모두_null() {
            val group = group()
            val a = participant(group, 1L)

            val comparison = settle(group, a).groupComparison

            assertThat(comparison).isEqualTo(SettlementResponse.GroupComparison(null, null, null, null))
        }

        @Test
        @DisplayName("그룹 비교에는 조회한 회원이 아닌 다른 회원도 1등으로 나온다")
        fun 조회자가_아니어도_1등이_된다() {
            val group = group()
            val viewer = participant(group, 1L)
            val other = participant(group, 2L, "다른회원")

            val actual = settle(group, viewer, listOf(verify(other, START.plusDays(1), HabitVerifyStatus.REJECTED))).groupComparison.mostRejectedVerifications

            assertThat(actual).isEqualTo(winner(2L, 1, "다른회원"))
        }
    }

    // --- 데이터 이상 ---

    @Nested
    @DisplayName("데이터 이상")
    inner class BrokenData {

        @Test
        @DisplayName("그룹 생성 시각이 없으면 IllegalArgumentException이다 (저장된 그룹에는 항상 있어야 하는 값)")
        fun 생성_시각이_없는_그룹() {
            val group = group(createDate = null)
            val viewer = participant(group, 1L)

            assertThatThrownBy { settle(group, viewer) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("그룹 생성일이 없습니다.")
        }
    }
}
