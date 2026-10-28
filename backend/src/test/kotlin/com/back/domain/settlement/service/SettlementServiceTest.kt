package com.back.domain.settlement.service

import com.back.domain.group.entity.Group
import com.back.domain.group.repository.GroupRepository
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.habit.entity.Habit
import com.back.domain.habitVerify.repository.HabitVerifyRepository
import com.back.domain.member.entity.Member
import com.back.domain.penaltyverify.entity.PenaltyVerify
import com.back.domain.penaltyverify.repository.PenaltyVerifyRepository
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional

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

    @Test
    @DisplayName("방장이 초기화한 벌칙도 결산의 벌칙 수에 포함되고, 제출일이 없어 지연 집계에서는 빠진다")
    fun 초기화된_벌칙도_벌칙수에_포함() {
        val member = Member.create("일반회원", "member", "pass1234")
        ReflectionTestUtils.setField(member, "id", 2L)

        val group = Group(
            title = "아침 기상 챌린지",
            penalty = "커피 1잔 쏘기",
            memberLimit = 10,
        )
        ReflectionTestUtils.setField(group, "id", 100L)
        ReflectionTestUtils.setField(group, "createDate", LocalDateTime.of(2026, 10, 1, 9, 0))
        group.finish()

        val groupMember = GroupMember.create(group, member, GroupMemberRole.MEMBER)
        val habit = Habit.create(groupMember, "기상 후 스트레칭", "10분 스트레칭", 3)

        val penalty = PenaltyVerify.create(groupMember, habit)
        ReflectionTestUtils.setField(penalty, "createDate", LocalDateTime.of(2026, 10, 8, 0, 5))
        penalty.submit(LocalDate.of(2026, 10, 10), "벌칙 수행했습니다.", "https://example.com/penalty.jpg")
        penalty.approve()
        penalty.resetByOwner()

        every { groupRepository.findById(100L) } returns Optional.of(group)
        every { groupMemberRepository.findByGroupIdAndMemberId(100L, 2L) } returns Optional.of(groupMember)
        every { habitVerifyRepository.findAllByGroupId(100L) } returns emptyList()
        every { penaltyVerifyRepository.findAllByGroupId(100L) } returns listOf(penalty)

        val response = settlementService.getSettlement(100L, 2L)

        assertThat(response.personalStats.penaltyCount).isEqualTo(1)
        assertThat(response.groupComparison.longestPenaltyDelay).isNull()
    }
}
