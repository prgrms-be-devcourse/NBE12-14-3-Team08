package com.back.domain.settlement.service

import com.back.domain.group.entity.Group
import com.back.domain.group.entity.GroupStatus
import com.back.domain.group.repository.GroupRepository
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.habitVerify.entity.HabitVerify
import com.back.domain.habitVerify.entity.HabitVerifyStatus
import com.back.domain.habitVerify.repository.HabitVerifyRepository
import com.back.domain.penaltyverify.entity.PenaltyVerify
import com.back.domain.penaltyverify.repository.PenaltyVerifyRepository
import com.back.domain.settlement.dto.SettlementResponse
import com.back.global.exception.BusinessRuleException
import com.back.global.exception.EntityNotFoundException
import com.back.global.exception.ForbiddenException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
@Transactional(readOnly = true)
class SettlementService(
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val habitVerifyRepository: HabitVerifyRepository,
    private val penaltyVerifyRepository: PenaltyVerifyRepository,
) {

    fun getSettlement(groupId: Long, memberId: Long): SettlementResponse {
        val group = groupRepository.findById(groupId)
            .orElseThrow { EntityNotFoundException("존재하지 않는 그룹입니다.") }

        val currentGroupMember = groupMemberRepository.findByGroupIdAndMemberId(groupId, memberId)
            .orElseThrow { ForbiddenException("해당 그룹의 결산을 조회할 권한이 없습니다.") }

        if (group.status != GroupStatus.FINISH) {
            throw BusinessRuleException("완료된 그룹만 결산을 조회할 수 있습니다.")
        }

        val habitVerifies = habitVerifyRepository.findAllByGroupId(groupId)
        val penaltyVerifies = penaltyVerifyRepository.findAllByGroupId(groupId)

        return SettlementResponse(
            groupId = group.id,
            groupTitle = group.title,
            personalStats = createPersonalStats(currentGroupMember, habitVerifies, penaltyVerifies),
            groupComparison = createGroupComparison(group, habitVerifies, penaltyVerifies),
        )
    }

    private fun createPersonalStats(
        currentGroupMember: GroupMember,
        habitVerifies: List<HabitVerify>,
        penaltyVerifies: List<PenaltyVerify>,
    ): SettlementResponse.PersonalStats {
        val memberId = requireNotNull(currentGroupMember.member.id) { "조회된 그룹 멤버의 회원 ID가 없습니다." }

        val myVerifies = habitVerifies.filter { it.habit.groupMember.member.id == memberId }

        return SettlementResponse.PersonalStats(
            memberId = memberId,
            nickname = currentGroupMember.member.nickname,
            approvedCount = myVerifies.count { it.status == HabitVerifyStatus.APPROVED }.toLong(),
            totalVerifyCount = myVerifies.size.toLong(),
            rejectedCount = myVerifies.count { it.status == HabitVerifyStatus.REJECTED }.toLong(),
            penaltyCount = penaltyVerifies.count { it.groupMember.member.id == memberId }.toLong(),
        )
    }

    private fun createGroupComparison(
        group: Group,
        habitVerifies: List<HabitVerify>,
        penaltyVerifies: List<PenaltyVerify>,
    ): SettlementResponse.GroupComparison {
        val nicknames = mutableMapOf<Long, String>()
        val descriptionChars = mutableMapOf<Long, Long>()
        val deadlineVerifications = mutableMapOf<Long, Long>()
        val rejectedVerifications = mutableMapOf<Long, Long>()
        val penaltyDelays = mutableMapOf<Long, Long>()

        val groupStartDate = requireNotNull(group.createDate) { "그룹 생성일이 없습니다." }.toLocalDate()

        for (verify in habitVerifies) {
            val member = verify.habit.groupMember.member
            val memberId = requireNotNull(member.id) { "인증을 올린 회원의 ID가 없습니다." }

            nicknames.putIfAbsent(memberId, member.nickname)

            val description = verify.description
            // 공백 판정은 기존 Java의 String.isBlank와 같게 둔다. (Kotlin의 isBlank는 줄바꿈 없는 공백(U+00A0) 등도 공백으로 본다)
            if (description != null && !description.all { Character.isWhitespace(it) }) {
                descriptionChars.addTo(memberId, description.codePointCount(0, description.length).toLong())
            }

            if (verify.status == HabitVerifyStatus.REJECTED) {
                rejectedVerifications.addTo(memberId, 1L)
            }

            if (verify.status == HabitVerifyStatus.APPROVED && isWeeklyDeadline(groupStartDate, verify.verifyDate)) {
                deadlineVerifications.addTo(memberId, 1L)
            }
        }

        for (penalty in penaltyVerifies) {
            val member = penalty.groupMember.member
            val memberId = requireNotNull(member.id) { "벌칙 대상 회원의 ID가 없습니다." }

            nicknames.putIfAbsent(memberId, member.nickname)

            val createDate = penalty.createDate
            val verifyDate = penalty.verifyDate
            if (createDate != null && verifyDate != null) {
                val delayDays = ChronoUnit.DAYS.between(createDate.toLocalDate(), verifyDate)

                penaltyDelays.merge(memberId, maxOf(delayDays, 0L)) { current, delay -> maxOf(current, delay) }
            }
        }

        return SettlementResponse.GroupComparison(
            mostDescriptionChars = findWinner(descriptionChars, nicknames),
            mostDeadlineVerifications = findWinner(deadlineVerifications, nicknames),
            mostRejectedVerifications = findWinner(rejectedVerifications, nicknames),
            longestPenaltyDelay = findWinner(penaltyDelays, nicknames),
        )
    }

    private fun MutableMap<Long, Long>.addTo(memberId: Long, amount: Long) {
        merge(memberId, amount) { current, added -> current + added }
    }

    // 그룹 시작일부터 7일 단위로 세어 각 주의 마지막 날(경과일 % 7 == 6)이면 마감일 인증으로 본다.
    private fun isWeeklyDeadline(groupStartDate: LocalDate, verifyDate: LocalDate?): Boolean {
        if (verifyDate == null || verifyDate.isBefore(groupStartDate)) {
            return false
        }

        val elapsedDays = ChronoUnit.DAYS.between(groupStartDate, verifyDate)

        return elapsedDays % 7 == 6L
    }

    // 값이 0보다 큰 회원 중 값이 가장 큰 회원. 동률이면 회원 id가 작은 쪽이 1등이다.
    private fun findWinner(values: Map<Long, Long>, nicknames: Map<Long, String>): SettlementResponse.Winner? =
        values.entries
            .filter { it.value > 0 }
            .minWithOrNull(compareByDescending<Map.Entry<Long, Long>> { it.value }.thenBy { it.key })
            ?.let { SettlementResponse.Winner(it.key, nicknames.getValue(it.key), it.value) }
}
