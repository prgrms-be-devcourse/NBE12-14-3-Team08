
package com.back.domain.groupMember.repository

import com.back.domain.groupMember.dto.GroupMemberResponse
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.entity.GroupMemberStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface GroupMemberRepository : JpaRepository<GroupMember, Long> {

    @Query(
        """
        select new com.back.domain.groupMember.dto.GroupMemberResponse${'$'}Simple(
            gm.id,
            m.id,
            m.nickname,
            m.username,
            cast(gm.role as string),
            h.id,
            h.title,
            h.description
        )
        from GroupMember gm
        join gm.member m
        left join Habit h
            on h.groupMember.id = gm.id
            and h.status = com.back.domain.habit.entity.HabitStatus.ACTIVE
        where gm.group.id = :groupId
        """
    )
    fun findByGroupIdWithHabit(
        @Param("groupId") groupId: Long
    ): List<GroupMemberResponse.Simple>

    @Query(
        """
        select new com.back.domain.groupMember.dto.GroupMemberResponse${'$'}Detail(
            gm.id,
            m.id,
            m.nickname,
            m.username,
            cast(gm.role as string),
            count(pv)
        )
        from GroupMember gm
        join gm.member m
        left join PenaltyVerify pv
            on pv.groupMember.id = gm.id
        where gm.group.id = :groupId
            and gm.id = :groupMemberId
        group by
            gm.id,
            m.id,
            m.nickname,
            m.username,
            gm.role
        """
    )
    fun findMemberDetailWithPenaltyCount(
        @Param("groupId") groupId: Long,
        @Param("groupMemberId") groupMemberId: Long
    ): Optional<GroupMemberResponse.Detail>

    fun findByGroupIdAndMemberId(
        groupId: Long,
        memberId: Long
    ): Optional<GroupMember>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select gm
        from GroupMember gm
        where gm.group.id = :groupId
            and gm.member.id = :memberId
        """
    )
    fun findByGroupIdAndMemberIdForUpdate(
        @Param("groupId") groupId: Long,
        @Param("memberId") memberId: Long
    ): Optional<GroupMember>

    fun existsByGroupIdAndMemberId(
        groupId: Long,
        memberId: Long
    ): Boolean

    fun existsByMemberId(memberId: Long): Boolean

    @EntityGraph(attributePaths = ["group"])
    fun findAllByMemberId(memberId: Long): List<GroupMember>

    fun countByGroupId(groupId: Long): Long

    fun existsByIdAndGroupId(
        groupMemberId: Long,
        groupId: Long
    ): Boolean

    fun existsByGroupIdAndMemberIdAndStatus(
        groupId: Long,
        memberId: Long,
        status: GroupMemberStatus
    ): Boolean

    fun countByGroupIdAndStatus(
        groupId: Long,
        status: GroupMemberStatus
    ): Long

    fun findAllByGroupId(groupId: Long): List<GroupMember>

    @Modifying(flushAutomatically = true)
    @Query(
        """
        delete from GroupMember gm
        where gm.group.id = :groupId
        """
    )
    fun deleteGroupMembersByGroupId(
        @Param("groupId") groupId: Long
    ): Int
}