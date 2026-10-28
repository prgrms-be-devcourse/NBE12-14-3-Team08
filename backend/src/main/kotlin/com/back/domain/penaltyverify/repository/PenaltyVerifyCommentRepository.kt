package com.back.domain.penaltyverify.repository

import com.back.domain.penaltyverify.entity.PenaltyVerifyComment
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PenaltyVerifyCommentRepository : JpaRepository<PenaltyVerifyComment, Long> {

    @Query(
        """
        select c
        from PenaltyVerifyComment c
        join fetch c.groupMember gm
        join fetch gm.member m
        where c.penaltyVerify.id = :penaltyVerifyId and c.id > :afterId
        order by c.id asc
        """,
    )
    fun findPage(
        @Param("penaltyVerifyId") penaltyVerifyId: Long,
        @Param("afterId") afterId: Long,
        pageable: Pageable,
    ): List<PenaltyVerifyComment>

    @Query(
        """
        select c
        from PenaltyVerifyComment c
        join fetch c.groupMember gm
        join fetch gm.member m
        where c.id = :id and c.penaltyVerify.id = :penaltyVerifyId
        """,
    )
    fun findByIdAndPenaltyVerifyId(@Param("id") id: Long, @Param("penaltyVerifyId") penaltyVerifyId: Long): PenaltyVerifyComment?

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PenaltyVerifyComment c where c.penaltyVerify.id = :penaltyVerifyId")
    fun deleteAllByPenaltyVerifyId(@Param("penaltyVerifyId") penaltyVerifyId: Long): Int

    fun countByPenaltyVerifyId(penaltyVerifyId: Long): Long
}
