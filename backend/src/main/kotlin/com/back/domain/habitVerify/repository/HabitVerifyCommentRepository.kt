package com.back.domain.habitVerify.repository

import com.back.domain.habitVerify.entity.HabitVerifyComment
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface HabitVerifyCommentRepository : JpaRepository<HabitVerifyComment, Long> {

    // 오래된 댓글부터 보여주는 커서 방식. 작성자 닉네임까지 함께 읽어 목록에서 건건이 조회하지 않게 한다.
    @Query(
        """
        select c
        from HabitVerifyComment c
        join fetch c.groupMember gm
        join fetch gm.member m
        where c.habitVerify.id = :habitVerifyId and c.id > :afterId
        order by c.id asc
        """,
    )
    fun findPage(
        @Param("habitVerifyId") habitVerifyId: Long,
        @Param("afterId") afterId: Long,
        pageable: Pageable,
    ): List<HabitVerifyComment>

    @Query(
        """
        select c
        from HabitVerifyComment c
        join fetch c.groupMember gm
        join fetch gm.member m
        where c.id = :id and c.habitVerify.id = :habitVerifyId
        """,
    )
    fun findByIdAndHabitVerifyId(@Param("id") id: Long, @Param("habitVerifyId") habitVerifyId: Long): HabitVerifyComment?

    fun countByHabitVerifyId(habitVerifyId: Long): Long

    @Query(
        """
        select c.habitVerify.id as targetId, count(c) as total
        from HabitVerifyComment c
        where c.habitVerify.id in :habitVerifyIds
        group by c.habitVerify.id
        """,
    )
    fun countByHabitVerifyIds(@Param("habitVerifyIds") habitVerifyIds: Collection<Long>): List<VerifyCount>
}
