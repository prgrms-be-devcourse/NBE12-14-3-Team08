package com.back.domain.penaltyverify.repository

import com.back.domain.penaltyverify.entity.PenaltyVerify
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface PenaltyVerifyRepository : JpaRepository<PenaltyVerify, Long> {

    // 기본 findById는 Optional을 돌려줘서 재정의로 null 반환형을 쓸 수 없다. 연관 엔티티를 함께 읽는 전용 조회를 따로 둔다.
    @EntityGraph(attributePaths = ["groupMember", "groupMember.group", "groupMember.member"])
    fun findDetailById(id: Long): PenaltyVerify?

    @EntityGraph(attributePaths = ["groupMember", "groupMember.member"])
    fun findByHabitId(habitId: Long): PenaltyVerify?

    @Query(
        """
        SELECT pv FROM PenaltyVerify pv
        JOIN FETCH pv.groupMember gm
        JOIN FETCH gm.member
        WHERE gm.group.id = :groupId AND pv.status = 'PENDING'
        """,
    )
    fun findPendingByGroupId(@Param("groupId") groupId: Long): List<PenaltyVerify>

    fun countByGroupMember_Group_IdAndGroupMember_Member_Id(groupId: Long, memberId: Long): Long

    @Query(
        """
        SELECT pv FROM PenaltyVerify pv
        WHERE pv.id IN :ids AND pv.groupMember.group.id = :groupId
        """,
    )
    fun findAllByIdInAndGroupId(@Param("ids") ids: List<Long>, @Param("groupId") groupId: Long): List<PenaltyVerify>

    @EntityGraph(attributePaths = ["groupMember", "groupMember.member", "habit"])
    fun findByGroupMemberIdOrderByIdDesc(groupMemberId: Long): List<PenaltyVerify>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from PenaltyVerify pv where pv.groupMember.id = :groupMemberId")
    fun deleteByGroupMemberId(@Param("groupMemberId") groupMemberId: Long)

    @Query(
        """
        SELECT pv
        FROM PenaltyVerify pv
        JOIN FETCH pv.groupMember gm
        JOIN FETCH gm.member
        WHERE gm.group.id = :groupId
        """,
    )
    fun findAllByGroupId(@Param("groupId") groupId: Long): List<PenaltyVerify>

    @Modifying(flushAutomatically = true)
    @Query(
        """
        delete from PenaltyVerify pv
        where pv.groupMember.id in (
            select gm.id
            from GroupMember gm
            where gm.group.id = :groupId
        )
        """,
    )
    fun deletePenaltyVerifiesByGroupId(@Param("groupId") groupId: Long): Int
}
