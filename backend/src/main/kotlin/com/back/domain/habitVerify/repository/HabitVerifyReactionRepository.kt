package com.back.domain.habitVerify.repository

import com.back.domain.habitVerify.entity.HabitVerifyReaction
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface HabitVerifyReactionRepository : JpaRepository<HabitVerifyReaction, Long> {

    // 같은 사람이 동시에 눌러도 오류 없이 하나만 남도록 DB에 맡긴다. (확인 후 저장은 그 사이에 끼어들면 유니크 제약 오류가 난다)
    // 생성일은 다른 행과 같은 기준(애플리케이션 시각)으로 직접 넣는다.
    @Modifying(flushAutomatically = true)
    @Query(
        value = """
            insert into habit_verify_reaction (create_date, modify_date, habit_verify_id, group_member_id)
            values (:now, :now, :habitVerifyId, :groupMemberId)
            on conflict (habit_verify_id, group_member_id) do nothing
        """,
        nativeQuery = true,
    )
    fun insertIfAbsent(
        @Param("habitVerifyId") habitVerifyId: Long,
        @Param("groupMemberId") groupMemberId: Long,
        @Param("now") now: LocalDateTime,
    ): Int

    @Modifying(flushAutomatically = true)
    @Query("delete from HabitVerifyReaction r where r.habitVerify.id = :habitVerifyId and r.groupMember.id = :groupMemberId")
    fun deleteByHabitVerifyIdAndGroupMemberId(
        @Param("habitVerifyId") habitVerifyId: Long,
        @Param("groupMemberId") groupMemberId: Long,
    ): Int

    fun existsByHabitVerifyIdAndGroupMemberId(habitVerifyId: Long, groupMemberId: Long): Boolean

    fun countByHabitVerifyId(habitVerifyId: Long): Long

    // 갤러리 한 페이지의 인증들을 한 번에 센다. 반응이 없는 인증은 결과에 없다.
    @Query(
        """
        select r.habitVerify.id as targetId, count(r) as total
        from HabitVerifyReaction r
        where r.habitVerify.id in :habitVerifyIds
        group by r.habitVerify.id
        """,
    )
    fun countByHabitVerifyIds(@Param("habitVerifyIds") habitVerifyIds: Collection<Long>): List<VerifyCount>

    @Query(
        """
        select r.habitVerify.id
        from HabitVerifyReaction r
        where r.habitVerify.id in :habitVerifyIds and r.groupMember.id = :groupMemberId
        """,
    )
    fun findReactedHabitVerifyIds(
        @Param("habitVerifyIds") habitVerifyIds: Collection<Long>,
        @Param("groupMemberId") groupMemberId: Long,
    ): List<Long>
}
