package com.back.domain.penaltyverify.repository

import com.back.domain.penaltyverify.entity.PenaltyVerifyReaction
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface PenaltyVerifyReactionRepository : JpaRepository<PenaltyVerifyReaction, Long> {

    // 같은 사람이 동시에 눌러도 오류 없이 하나만 남도록 DB에 맡긴다. (HabitVerifyReactionRepository와 같은 이유)
    @Modifying(flushAutomatically = true)
    @Query(
        value = """
            insert into penalty_verify_reaction (create_date, modify_date, penalty_verify_id, group_member_id)
            values (:now, :now, :penaltyVerifyId, :groupMemberId)
            on conflict (penalty_verify_id, group_member_id) do nothing
        """,
        nativeQuery = true,
    )
    fun insertIfAbsent(
        @Param("penaltyVerifyId") penaltyVerifyId: Long,
        @Param("groupMemberId") groupMemberId: Long,
        @Param("now") now: LocalDateTime,
    ): Int

    @Modifying(flushAutomatically = true)
    @Query("delete from PenaltyVerifyReaction r where r.penaltyVerify.id = :penaltyVerifyId and r.groupMember.id = :groupMemberId")
    fun deleteByPenaltyVerifyIdAndGroupMemberId(
        @Param("penaltyVerifyId") penaltyVerifyId: Long,
        @Param("groupMemberId") groupMemberId: Long,
    ): Int

    // 벌칙을 REQUIRED로 되돌릴 때 그 인증의 하트를 모두 지운다
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PenaltyVerifyReaction r where r.penaltyVerify.id = :penaltyVerifyId")
    fun deleteAllByPenaltyVerifyId(@Param("penaltyVerifyId") penaltyVerifyId: Long): Int

    fun existsByPenaltyVerifyIdAndGroupMemberId(penaltyVerifyId: Long, groupMemberId: Long): Boolean

    fun countByPenaltyVerifyId(penaltyVerifyId: Long): Long
}
