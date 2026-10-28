
package com.back.domain.group.service

import com.back.domain.group.dto.GroupRequest
import com.back.domain.group.dto.GroupResponse
import com.back.domain.group.entity.Group
import com.back.domain.group.entity.GroupStatus
import com.back.domain.group.repository.GroupRepository
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.entity.GroupMemberStatus
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.groupMember.service.GroupMemberService
import com.back.domain.habitVerify.entity.HabitVerifyStatus
import com.back.domain.habitVerify.repository.HabitVerifyRepository
import com.back.domain.member.repository.MemberRepository
import com.back.domain.penaltyverify.entity.PenaltyVerifyStatus
import com.back.domain.penaltyverify.repository.PenaltyVerifyRepository
import com.back.global.entity.requiredId
import com.back.global.exception.BusinessRuleException
import com.back.global.exception.EntityNotFoundException
import com.back.global.exception.ForbiddenException
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

@Service
@Transactional(readOnly = true)
class GroupService(
    private val groupRepository: GroupRepository,
    private val passwordEncoder: PasswordEncoder,
    private val groupMemberRepository: GroupMemberRepository,
    private val memberRepository: MemberRepository,
    private val groupMemberService: GroupMemberService,
    @Value("\${app.invite-base-url}")
    private val baseInviteUrl: String,
    private val habitVerifyRepository: HabitVerifyRepository,
    private val penaltyVerifyRepository: PenaltyVerifyRepository
) {

    @Transactional
    fun createGroup(
        memberId: Long,
        request: GroupRequest.Create
    ): GroupResponse.Detail {
        val inviteCode = generateUniqueInviteCode()
        val encodedPassword = requireNotNull(
            passwordEncoder.encode(request.password)
        ) {
            "비밀번호 인코딩에 실패했습니다."
        }
        val memberLimit = request.memberLimit ?: 10

        val group = Group(
            title = request.title,
            description = request.description,
            deadline = request.deadline,
            penalty = request.penalty,
            password = encodedPassword,
            inviteCode = inviteCode,
            memberLimit = memberLimit
        )

        val savedGroup = groupRepository.save(group)

        val member = memberRepository.findById(memberId)
            .orElseThrow {
                EntityNotFoundException("존재하지 않는 회원입니다.")
            }

        val owner = GroupMember.create(
            savedGroup,
            member,
            GroupMemberRole.OWNER
        )
        groupMemberRepository.save(owner)

        return GroupResponse.Detail.from(
            savedGroup,
            baseInviteUrl,
            true,
            1L
        )
    }

    fun getGroupSimpleList(
        memberId: Long,
        status: GroupStatus
    ): List<GroupResponse.Simple> =
        groupRepository.findMyGroupsWithCount(memberId, status)

    fun getGroupDetail(
        groupId: Long,
        memberId: Long
    ): GroupResponse.Detail {
        val group = groupRepository.findById(groupId)
            .orElseThrow {
                EntityNotFoundException("존재하지 않는 그룹입니다.")
            }

        if (
            !groupMemberRepository.existsByGroupIdAndMemberIdAndStatus(
                groupId,
                memberId,
                GroupMemberStatus.ACTIVE
            )
        ) {
            throw ForbiddenException("해당 그룹의 접근 권한이 없습니다.")
        }

        val currentMemberCount =
            groupMemberRepository.countByGroupIdAndStatus(
                groupId,
                GroupMemberStatus.ACTIVE
            )

        val rankings = if (group.status == GroupStatus.FINISH) {
            createRankings(groupId)
        } else {
            null
        }

        return GroupResponse.Detail.from(
            group = group,
            baseInviteUrl = baseInviteUrl,
            isJoined = true,
            currentMemberCount = currentMemberCount,
            rankings = rankings
        )
    }

    @Transactional
    fun updateGroup(
        groupId: Long,
        memberId: Long,
        request: GroupRequest.Update
    ) {
        val group = groupRepository.findByIdForUpdate(groupId)
            ?: throw EntityNotFoundException("존재하지 않는 그룹입니다.")

        if (group.isFinished()) {
            throw BusinessRuleException("이미 종료된 그룹은 수정할 수 없습니다.")
        }

        val groupMember = groupMemberRepository
            .findByGroupIdAndMemberId(groupId, memberId)
            .orElseThrow {
                ForbiddenException("해당 그룹의 접근 권한이 없습니다.")
            }

        if (groupMember.role != GroupMemberRole.OWNER) {
            throw ForbiddenException("그룹 수정은 방장만 가능합니다.")
        }

        val currentMemberCount =
            groupMemberRepository.countByGroupIdAndStatus(
                groupId,
                GroupMemberStatus.ACTIVE
            )

        val requestedMemberLimit = request.memberLimit

        if (requestedMemberLimit != null) {
            if (requestedMemberLimit < 1) {
                throw BusinessRuleException(
                    "최대 인원은 1명 이상이어야 합니다."
                )
            }

            if (requestedMemberLimit < currentMemberCount) {
                throw BusinessRuleException(
                    "최대 제한 인원은 현재 참여 중인 멤버 수(" +
                            "${currentMemberCount}명)보다 적게 설정할 수 없습니다."
                )
            }
        }

        val encodedPassword = request.password
            ?.takeIf { it.isNotBlank() }
            ?.let { passwordEncoder.encode(it) }

        val memberLimit =
            requestedMemberLimit ?: group.memberLimit

        group.updateGroup(
            request.title,
            request.description,
            request.deadline,
            request.penalty,
            encodedPassword,
            memberLimit
        )
    }

    @Transactional
    fun deleteGroup(
        groupId: Long,
        memberId: Long
    ) {
        val group = groupRepository.findByIdForUpdate(groupId)
            ?: throw EntityNotFoundException("존재하지 않는 그룹입니다.")

        if (group.isFinished()) {
            throw BusinessRuleException("이미 종료된 그룹은 삭제할 수 없습니다.")
        }

        val groupMember = groupMemberRepository
            .findByGroupIdAndMemberId(groupId, memberId)
            .orElseThrow {
                ForbiddenException("해당 그룹의 권한이 없습니다.")
            }

        if (groupMember.role != GroupMemberRole.OWNER) {
            throw ForbiddenException("그룹 삭제는 방장만 가능합니다.")
        }

        val totalMemberCount =
            groupMemberRepository.countByGroupIdAndStatus(
                groupId,
                GroupMemberStatus.ACTIVE
            )

        if (totalMemberCount > 1) {
            throw BusinessRuleException(
                "그룹을 삭제하려면 방장을 제외한 모든 멤버가 퇴장해야 합니다."
            )
        }

        groupMemberService.deleteGroupAndAllMembers(group)
    }

    @Transactional
    @Scheduled(cron = "0 0 0 * * *")
    fun autoCloseExpiredGroups() {
        val today = LocalDate.now()
        val expiredGroups =
            groupRepository.findExpiredGroupsForUpdate(today)

        expiredGroups.forEach { group ->
            group.finish()
        }
    }

    private fun generateUniqueInviteCode(): String {
        while (true) {
            val inviteCode = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8)

            if (!groupRepository.existsByInviteCode(inviteCode)) {
                return inviteCode
            }
        }
    }

    fun getGroupByInviteCode(
        inviteCode: String,
        memberId: Long?
    ): GroupResponse.Detail {
        if (memberId == null || !memberRepository.existsById(memberId)) {
            throw EntityNotFoundException(
                "존재하지 않거나 로그인이 필요한 회원입니다."
            )
        }

        val group = groupRepository.findByInviteCode(inviteCode)
            ?: throw EntityNotFoundException(
                "유효하지 않거나 존재하지 않는 초대 코드입니다."
            )

        if (group.isFinished()) {
            throw BusinessRuleException("이미 마감된 그룹입니다.")
        }

        val groupId = group.requiredId

        val currentMemberCount =
            groupMemberRepository.countByGroupIdAndStatus(
                groupId,
                GroupMemberStatus.ACTIVE
            )

        val isJoined = groupMemberRepository
            .existsByGroupIdAndMemberId(groupId, memberId)

        return GroupResponse.Detail.from(
            group,
            baseInviteUrl,
            isJoined,
            currentMemberCount
        )
    }

    private data class MemberRankingStats(
        val memberId: Long,
        val nickname: String?,
        var habitVerifyCount: Int = 0,
        var penaltyVerifyCount: Int = 0,
        var habitSuccessCount: Int = 0
    )

    private fun createRankings(groupId: Long): GroupResponse.Rankings {
        val groupMembers = groupMemberRepository.findAllByGroupId(groupId)

        // LEFT 멤버도 포함하여 모든 그룹 멤버를 먼저 등록한다.
        val statsByMemberId = groupMembers.associate { groupMember ->
            val memberId = groupMember.member.requiredId

            memberId to MemberRankingStats(
                memberId = memberId,
                nickname = groupMember.member.nickname
            )
        }

        // 모든 상태의 습관 인증을 집계하고,
        // APPROVED만 성공 횟수에 추가한다.
        habitVerifyRepository.findAllByGroupId(groupId).forEach { verify ->
            val memberId = verify.habit.groupMember.member.id
                ?: return@forEach

            val stats = statsByMemberId[memberId]
                ?: return@forEach

            stats.habitVerifyCount++

            if (verify.status == HabitVerifyStatus.APPROVED) {
                stats.habitSuccessCount++
            }
        }

        // REQUIRED는 아직 인증이 제출되지 않은 상태이므로 제외한다.
        penaltyVerifyRepository.findAllByGroupId(groupId).forEach { verify ->
            if (verify.status == PenaltyVerifyStatus.REQUIRED) {
                return@forEach
            }

            val memberId = verify.groupMember.member.id
                ?: return@forEach

            val stats = statsByMemberId[memberId]
                ?: return@forEach

            stats.penaltyVerifyCount++
        }

        val allStats = statsByMemberId.values

        return GroupResponse.Rankings(
            habitVerifyTop3 = createTop3(allStats) { it.habitVerifyCount },
            penaltyVerifyTop3 = createTop3(allStats) { it.penaltyVerifyCount },
            habitSuccessTop3 = createTop3(allStats) { it.habitSuccessCount }
        )
    }

    private fun createTop3(
        stats: Collection<MemberRankingStats>,
        countSelector: (MemberRankingStats) -> Int
    ): List<GroupResponse.Ranking> {
        return stats
            .sortedWith(
                compareByDescending<MemberRankingStats> { countSelector(it) }
                    .thenBy { it.memberId }
            )
            .take(3)
            .mapIndexed { index, member ->
                GroupResponse.Ranking(
                    rank = index + 1,
                    memberId = member.memberId,
                    nickname = member.nickname,
                    count = countSelector(member)
                )
            }
    }
}