
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
import com.back.global.entity.requiredId
import com.back.global.exception.BusinessRuleException
import com.back.global.exception.EntityNotFoundException
import com.back.global.exception.ForbiddenException
import com.back.global.exception.GroupLimitExceededException
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import jakarta.persistence.PersistenceContext
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

@Service
@Transactional(readOnly = true)
class GroupMemberService(
    private val groupMemberRepository: GroupMemberRepository,
    private val groupRepository: GroupRepository,
    private val memberRepository: MemberRepository,
    private val habitRepository: HabitRepository,
    private val habitVerifyRepository: HabitVerifyRepository,
    private val penaltyVerifyRepository: PenaltyVerifyRepository,
    private val passwordEncoder: PasswordEncoder,
    private val transactionTemplate: TransactionTemplate
) {

    @field:PersistenceContext
    private lateinit var entityManager: EntityManager

    // 비밀번호 확인(BCrypt, 한 번에 약 80ms)은 그룹 잠금을 잡기 전에, DB 연결도 쥐지 않은 채로 한다.
    // - 잠금 안에서 하면 참여 요청이 한 명씩 그 시간만큼 줄을 선다. (k6: 300명 동시 참여 시 가장 느린 요청 약 23초)
    // - 트랜잭션 안에서 하면 그동안 DB 연결을 쥐고 있어, 연결을 기다리는 다른 API까지 멈춘다.
    // 그래서 ① 짧은 트랜잭션으로 조회 → ② 트랜잭션 밖에서 비밀번호 확인 → ③ 새 트랜잭션에서 그룹을 잠그고
    // 마감·참여 여부·정원 확인과 저장을 한다.
    // ①에서 정원이 이미 찼으면 비밀번호를 확인하지 않고 바로 거절한다. 몰릴 때 대부분은 정원 초과라,
    // 거절될 요청까지 BCrypt를 하면 CPU가 꽉 차 모두가 느려진다. (k6: 1000명 동시 참여 시 p95 약 12초)
    // 정원은 초대 페이지에도 보이는 정보라 비밀번호보다 먼저 알려도 된다. 정확한 정원 확인은 ③의 잠금 안에서 다시 한다.
    // DB 접근은 반드시 ①·③ 안에서만 한다. 트랜잭션 밖에서 DB를 쓰면 그 연결을 메서드가 끝날 때까지 쥔 채
    // ③이 연결을 하나 더 기다려, 동시 요청이 연결 수만큼 오면 서로 기다리다 멈춘다(연결 풀 교착, k6로 확인).
    // SUPPORTS: 바깥 트랜잭션이 있으면(테스트 등) ①·③도 그 안에서 실행된다.
    // (open-in-view가 켜져 있으면 요청이 끝날 때까지 연결을 쥐므로, 연결을 놓는 효과는 open-in-view를 꺼야 난다)
    @Transactional(propagation = Propagation.SUPPORTS)
    fun joinGroup(
        inviteCode: String,
        memberId: Long,
        request: GroupRequest.Join
    ): Long {
        val target = transactionTemplate.execute {
            val group = findGroupByInviteCode(inviteCode)
            validateNotFinished(group)

            val groupId = group.requiredId

            JoinTarget(
                groupId = groupId,
                encodedPassword = group.password,
                alreadyJoined = groupMemberRepository.existsByGroupIdAndMemberId(groupId, memberId),
                full = isFull(groupId, group.memberLimit)
            )
        } ?: throw IllegalStateException("그룹 조회 트랜잭션이 결과를 돌려주지 않았습니다.")

        if (target.alreadyJoined) {
            return target.groupId
        }

        if (target.full) {
            throw groupLimitExceeded()
        }

        validateGroupPassword(request.password, target.encodedPassword)

        return transactionTemplate.execute {
            joinWithGroupLock(inviteCode, target.groupId, memberId, request.password, target.encodedPassword)
        } ?: throw IllegalStateException("그룹 참여 트랜잭션이 결과를 돌려주지 않았습니다.")
    }

    private data class JoinTarget(
        val groupId: Long,
        val encodedPassword: String,
        val alreadyJoined: Boolean,
        val full: Boolean
    )

    private fun joinWithGroupLock(
        inviteCode: String,
        groupId: Long,
        memberId: Long,
        rawPassword: String,
        verifiedPassword: String
    ): Long {
        // 그룹을 잠그면서 최신 값으로 다시 읽는다. (SELECT ... FOR UPDATE)
        // 잠금 조회 쿼리를 쓰면 이미 영속성 컨텍스트에 있는 옛 값을 그대로 돌려주고(open-in-view가 켜진 경우),
        // detach로 빼면 이 그룹을 참조하던 다른 엔티티가 옛 상태에 묶인다.
        val group = findGroupByInviteCode(inviteCode)
        try {
            entityManager.refresh(group, LockModeType.PESSIMISTIC_WRITE)
        } catch (e: jakarta.persistence.EntityNotFoundException) {
            throw EntityNotFoundException("유효하지 않거나 존재하지 않는 초대 코드입니다.")
        }

        validateNotFinished(group)

        // 같은 회원의 요청이 동시에 들어온 경우, 먼저 잠금을 잡은 요청이 이미 저장했을 수 있다
        if (groupMemberRepository.existsByGroupIdAndMemberId(groupId, memberId)) {
            return groupId
        }

        // 확인한 뒤 잠금을 잡기 전에 방 비밀번호가 바뀐 드문 경우에만 다시 확인한다
        if (group.password != verifiedPassword) {
            validateGroupPassword(rawPassword, group.password)
        }

        if (isFull(groupId, group.memberLimit)) {
            throw groupLimitExceeded()
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

    private fun isFull(groupId: Long, memberLimit: Int): Boolean =
        groupMemberRepository.countByGroupIdAndStatus(groupId, GroupMemberStatus.ACTIVE) >= memberLimit

    private fun groupLimitExceeded() =
        GroupLimitExceededException("그룹의 최대 인원이 초과되어 입장할 수 없습니다.")

    private fun findGroupByInviteCode(inviteCode: String): Group =
        groupRepository.findByInviteCode(inviteCode)
            ?: throw EntityNotFoundException("유효하지 않거나 존재하지 않는 초대 코드입니다.")

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
            val groupMemberId = groupMember.requiredId

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

        val targetId = kickTarget.requiredId

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
        val groupId = group.requiredId

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

    private fun validateNotFinished(group: Group) {
        if (group.isFinished()) {
            throw BusinessRuleException("이미 마감된 그룹입니다.")
        }
    }

    private fun validateGroupPassword(rawPassword: String, encodedPassword: String) {
        if (!passwordEncoder.matches(rawPassword, encodedPassword)) {
            throw ForbiddenException("그룹 비밀번호가 일치하지 않습니다.")
        }
    }
}
