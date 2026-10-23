
package com.back.domain.group.repository

import com.back.domain.group.dto.GroupResponse
import com.back.domain.group.entity.Group
import com.back.domain.group.entity.GroupStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface GroupRepository : JpaRepository<Group, Long> {

    fun findByInviteCode(inviteCode: String): Group?

    fun existsByInviteCode(inviteCode: String): Boolean

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Group g where g.inviteCode = :inviteCode")
    fun findByInviteCodeForUpdate(
        @Param("inviteCode") inviteCode: String
    ): Group?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Group g where g.id = :id")
    fun findByIdForUpdate(
        @Param("id") id: Long
    ): Group?

    @Query(
        """
        select new com.back.domain.group.dto.GroupResponse${'$'}Simple(
            g.id,
            g.title,
            g.description,
            g.memberLimit,
            count(gm),
            cast(g.status as string),
            cast(myGm.role as string)
        )
        from Group g
        left join GroupMember gm
            on gm.group.id = g.id
            and gm.status = com.back.domain.groupMember.entity.GroupMemberStatus.ACTIVE
        join GroupMember myGm
            on myGm.group.id = g.id
            and myGm.member.id = :memberId
            and myGm.status = com.back.domain.groupMember.entity.GroupMemberStatus.ACTIVE
        where g.status = :status
        group by
            g.id,
            g.title,
            g.description,
            g.memberLimit,
            g.status,
            myGm.role
        """
    )
    fun findMyGroupsWithCount(
        @Param("memberId") memberId: Long,
        @Param("status") status: GroupStatus
    ): List<GroupResponse.Simple>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select g
        from Group g
        where g.status = com.back.domain.group.entity.GroupStatus.ACTIVE
          and g.deadline < :today
        """
    )
    fun findExpiredGroupsForUpdate(
        @Param("today") today: LocalDate
    ): List<Group>
}