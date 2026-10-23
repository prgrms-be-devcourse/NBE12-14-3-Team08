
package com.back.domain.groupMember.service

import com.back.domain.group.dto.GroupRequest
import com.back.domain.group.entity.Group
import com.back.domain.group.repository.GroupRepository
import com.back.domain.groupMember.dto.GroupMemberResponse
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.entity.GroupMemberStatus
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.habit.repository.HabitRepository
import com.back.domain.habitVerify.repository.HabitVerifyRepository
import com.back.domain.member.repository.MemberRepository
import com.back.domain.penaltyverify.repository.PenaltyVerifyRepository
import com.back.global.exception.BusinessRuleException
import com.back.global.exception.EntityNotFoundException
import com.back.global.exception.ForbiddenException
import com.back.global.exception.GroupLimitExceededException
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class GroupMemberService(
    private val groupMemberRepository: GroupMemberRepository,
    private val groupRepository: GroupRepository,
    private val memberRepository: MemberRepository,
    private val habitRepository: HabitRepository,
    private val habitVerifyRepository: HabitVerifyRepository,
    private val penaltyVerifyRepository: PenaltyVerifyRepository,
    private val passwordEncoder: PasswordEncoder
) {

    @field:PersistenceContext
    private lateinit var entityManager: EntityManager

    @Transactional
    fun joinGroup(
        inviteCode: String,
        memberId: Long,
        request: GroupRequest.Join
    ): Long {
        val group = groupRepository.findByInviteCodeForUpdate(inviteCode)
            ?: throw EntityNotFoundException("유효하지 않거나 존재하지 않는 초대 코드입니다.")

        if (group.isFinished()) {
            throw BusinessRuleException("이미 마감된 그룹입니다.")
        }

        val groupId = requireNotNull(group.id) {
            "그룹 ID가 아직 생성되지 않았습니다."
        }

        if (groupMemberRepository.existsByGroupIdAndMemberId(groupId, memberId)) {
            return groupId
        }

        if (!passwordEncoder.matches(request.password, group.password)) {
            throw ForbiddenException("그룹 비밀번호가 일치하지 않습니다.")
        }

        val currentMemberCount = groupMemberRepository.countByGroupIdAndStatus(
            groupId,
            GroupMemberStatus.ACTIVE
        )

        if (currentMemberCount >= group.memberLimit) {
            throw GroupLimitExceededException(
                "그룹의 최대 인원이 초과되어 입장할 수 없습니다."
            )
        }

        val member = memberRepository.findById(memberId)
            .orElseThrow {
                EntityNotFoundException("존재하지 않는 회원입니다.")
            }

        val groupMember = GroupMember.create(
            group = group,
            member = member,
            role = GroupMemberRole.MEMBER
        )

        groupMemberRepository.save(groupMember)

        return groupId
    }

    fun getGroupMembers(
        groupId: Long,
        memberId: Long
    ): List<GroupMemberResponse.Simple> {
        if (
            !groupMemberRepository.existsByGroupIdAndMemberIdAndStatus(
                groupId,
                memberId,
                GroupMemberStatus.ACTIVE
            )
        ) {
            throw ForbiddenException("해당 그룹의 접근 권한이 없습니다.")
        }

        return groupMemberRepository.findByGroupIdWithHabit(groupId)
    }

    fun getGroupMemberDetail(
        groupId: Long,
        groupMemberId: Long,
        memberId: Long
    ): GroupMemberResponse.Detail {
        if (
            !groupMemberRepository.existsByGroupIdAndMemberIdAndStatus(
                groupId,
                memberId,
                GroupMemberStatus.ACTIVE
            )
        ) {
            throw ForbiddenException("해당 그룹의 접근 권한이 없습니다.")
        }

        return groupMemberRepository
            .findMemberDetailWithPenaltyCount(groupId, groupMemberId)
            .orElseThrow {
                EntityNotFoundException(
                    "존재하지 않거나 해당 그룹의 가입 멤버가 아닙니다."
                )
            }
    }

    @Transactional
    fun leaveGroup(groupId: Long, memberId: Long) {
        val group = groupRepository.findByIdForUpdate(groupId)
            ?: throw EntityNotFoundException("존재하지 않는 그룹입니다.")

        val groupMember = groupMemberRepository
            .findByGroupIdAndMemberId(groupId, memberId)
            .orElseThrow {
                EntityNotFoundException("해당 그룹의 멤버가 아닙니다.")
            }

        if (groupMember.status != GroupMemberStatus.ACTIVE) {
            throw BusinessRuleException("이미 탈퇴한 멤버입니다.")
        }

        val isFinished = group.isFinished()

        val memberCount = groupMemberRepository.countByGroupIdAndStatus(
            groupId,
            GroupMemberStatus.ACTIVE
        )

        if (groupMember.role == GroupMemberRole.OWNER) {
            if (!isFinished && memberCount > 1L) {
                throw BusinessRuleException(
                    "방장은 다른 멤버가 남아있는 상태에서 바로 탈퇴할 수 없습니다. " +
                            "방장 권한을 위임해 주세요."
                )
            }
        }

        if (memberCount == 1L) {
            deleteGroupAndAllMembers(group)
            return
        }

        if (!isFinished) {
            val groupMemberId = requireNotNull(groupMember.id) {
                "그룹 멤버 ID가 아직 생성되지 않았습니다."
            }

            deleteGroupMemberDataBulk(groupMemberId)
            groupMemberRepository.delete(groupMember)
        } else {
            groupMember.leave()
        }
    }

    @Transactional
    fun kickMember(
        groupId: Long,
        groupMemberId: Long,
        memberId: Long
    ) {
        val owner = groupMemberRepository
            .findByGroupIdAndMemberId(groupId, memberId)
            .orElseThrow {
                EntityNotFoundException("해당 그룹의 멤버가 아닙니다.")
            }

        if (owner.role != GroupMemberRole.OWNER) {
            throw ForbiddenException("그룹 멤버 추방은 방장만 가능합니다.")
        }

        val kickTarget = groupMemberRepository.findById(groupMemberId)
            .orElseThrow {
                EntityNotFoundException("존재하지 않거나 해당 그룹의 멤버가 아닙니다.")
            }

        if (kickTarget.group.id != groupId) {
            throw BusinessRuleException("해당 그룹에 속한 멤버가 아닙니다.")
        }

        if (kickTarget.id == owner.id) {
            throw BusinessRuleException(
                "방장 스스로를 추방할 수 없습니다. 방 삭제를 이용해 주세요."
            )
        }

        val group = kickTarget.group

        if (group.isFinished()) {
            throw BusinessRuleException(
                "이미 종료된 그룹은 멤버를 추방할 수 없습니다."
            )
        }

        if (kickTarget.status != GroupMemberStatus.ACTIVE) {
            throw BusinessRuleException("현재 활동 중인 멤버만 추방할 수 있습니다.")
        }

        val targetId = requireNotNull(kickTarget.id) {
            "그룹 멤버 ID가 아직 생성되지 않았습니다."
        }

        deleteGroupMemberDataBulk(targetId)
        groupMemberRepository.delete(kickTarget)
    }

    @Transactional
    fun deleteGroupMemberDataBulk(groupMemberId: Long) {
        val habitIds = habitRepository.findIdsByGroupMemberId(groupMemberId)

        if (habitIds.isNotEmpty()) {
            habitVerifyRepository.deleteByHabitIds(habitIds)
        }

        penaltyVerifyRepository.deleteByGroupMemberId(groupMemberId)

        if (habitIds.isNotEmpty()) {
            habitRepository.deleteByIds(habitIds)
        }
    }

    @Transactional
    fun deleteGroupAndAllMembers(group: Group) {
        val groupId = requireNotNull(group.id) {
            "그룹 ID가 아직 생성되지 않았습니다."
        }

        habitVerifyRepository.deleteHabitVerifiesByGroupId(groupId)
        penaltyVerifyRepository.deletePenaltyVerifiesByGroupId(groupId)
        habitRepository.deleteHabitsByGroupId(groupId)
        groupMemberRepository.deleteGroupMembersByGroupId(groupId)

        entityManager.flush()
        entityManager.clear()

        val groupToDelete = groupRepository.findById(groupId)
            .orElseThrow {
                EntityNotFoundException("존재하지 않는 그룹입니다.")
            }

        groupRepository.delete(groupToDelete)
    }

    @Transactional
    fun transferOwner(
        groupId: Long,
        groupMemberId: Long,
        memberId: Long
    ) {
        val currentOwner = groupMemberRepository
            .findByGroupIdAndMemberId(groupId, memberId)
            .orElseThrow {
                EntityNotFoundException("해당 그룹의 멤버가 아닙니다.")
            }

        if (currentOwner.role != GroupMemberRole.OWNER) {
            throw ForbiddenException("방장 권한 위임은 현재 방장만 가능합니다.")
        }

        val nextOwner = groupMemberRepository.findById(groupMemberId)
            .orElseThrow {
                EntityNotFoundException("권한을 위임할 대상 멤버가 존재하지 않습니다.")
            }

        if (nextOwner.group.id != groupId) {
            throw BusinessRuleException("해당 그룹에 속한 멤버가 아닙니다.")
        }

        if (nextOwner.id == currentOwner.id) {
            throw BusinessRuleException("자기 자신에게 방장 권한을 위임할 수 없습니다.")
        }

        if (currentOwner.group.isFinished()) {
            throw BusinessRuleException(
                "이미 종료된 그룹은 방장을 위임할 수 없습니다."
            )
        }

        if (nextOwner.status != GroupMemberStatus.ACTIVE) {
            throw BusinessRuleException(
                "현재 활동 중인 멤버에게만 방장 권한을 위임할 수 있습니다."
            )
        }

        currentOwner.changeRole(GroupMemberRole.MEMBER)
        nextOwner.changeRole(GroupMemberRole.OWNER)
    }
}
