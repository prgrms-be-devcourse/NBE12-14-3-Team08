package com.back.domain.penaltyverify.repository

import com.back.domain.penaltyverify.entity.PenaltyVerify
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface PenaltyVerifyRepository : JpaRepository<PenaltyVerify, Long> {

    // 서비스는 이 메서드를 직접 부른다. Spring Data의 findByIdOrNull(id)은 일반 findById를 불러 @EntityGraph가 빠진다.
    @EntityGraph(attributePaths = ["groupMember", "groupMember.group", "groupMember.member"])
    override fun findById(id: Long): Optional<PenaltyVerify>

    // 하트·댓글을 쓰는 동안 초기화가 끼어들지 못하게 그 벌칙 행을 잠근다. (초기화가 하트·댓글을 지운 뒤에 새 글이 들어오는 것을 막는다)
    // 엔티티를 읽기 전에 불러서, 잠금이 풀린 뒤의 최신 상태를 읽게 한다.
    @Query(value = "select id from penalty_verify where id = :id for update", nativeQuery = true)
    fun lockRow(@Param("id") id: Long): Long?

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

    // 메인 화면의 "누적 벌칙": 방과 상태를 가리지 않고 내가 받은 벌칙 인증 전체 (멤버 상세의 벌칙 횟수와 같은 기준)
    fun countByGroupMember_Member_Id(memberId: Long): Long

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
