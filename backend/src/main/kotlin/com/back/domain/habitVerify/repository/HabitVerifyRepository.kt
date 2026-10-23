package com.back.domain.habitVerify.repository

import com.back.domain.habitVerify.entity.HabitVerify
import com.back.domain.habitVerify.entity.HabitVerifyStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional

interface HabitVerifyRepository : JpaRepository<HabitVerify, Long> {

    fun findAllByHabitIdOrderByVerifyDateDesc(habitId: Long): List<HabitVerify>

    fun findByIdAndHabitId(id: Long, habitId: Long): Optional<HabitVerify>

    fun existsByHabitIdAndVerifyDate(habitId: Long, verifyDate: LocalDate): Boolean

    fun existsByHabitIdAndVerifyDateAndIdNot(habitId: Long, verifyDate: LocalDate, id: Long): Boolean

    // 이번 주 정산 구간 내 특정 상태의 제출 건수 조회 (주간 인증 횟수 판정용)
    fun countByHabit_IdAndCreateDateBetweenAndStatus(
        habitId: Long,
        weekStart: LocalDateTime,
        weekEnd: LocalDateTime,
        status: HabitVerifyStatus,
    ): Long

    // 이번 주 정산 구간 내 특정 상태의 제출 건 목록 조회 (그레이스 기간 만료 후 자동 승인 처리용)
    // habitId가 Long?인 이유: HabitService.autoApprovePendingVerifies(habitId: Long?)가 그대로 넘긴다
    fun findAllByHabit_IdAndCreateDateBetweenAndStatus(
        habitId: Long?,
        weekStart: LocalDateTime,
        weekEnd: LocalDateTime,
        status: HabitVerifyStatus,
    ): List<HabitVerify>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from HabitVerify hv where hv.habit.id in :habitIds")
    fun deleteByHabitIds(@Param("habitIds") habitIds: List<Long>)

    @Query(
        """
        SELECT hv
        FROM HabitVerify hv
        JOIN FETCH hv.habit h
        JOIN FETCH h.groupMember gm
        JOIN FETCH gm.member m
        WHERE gm.group.id = :groupId
          AND hv.status = 'PENDING'
        ORDER BY hv.verifyDate DESC
        """,
    )
    fun findPendingByGroupId(@Param("groupId") groupId: Long): List<HabitVerify>

    @Query(
        """
        SELECT hv
        FROM HabitVerify hv
        JOIN hv.habit h
        JOIN h.groupMember gm
        WHERE hv.id IN :ids
          AND gm.group.id = :groupId
        """,
    )
    // groupId가 Long?인 이유: 기존 Java 테스트가 verify(...).findAllByIdInAndGroupId(any(), any())로 null을 넘긴다
    // (Long이면 JVM 원시 long이 되어 NPE)
    fun findAllByIdInAndGroupId(@Param("ids") ids: List<Long>, @Param("groupId") groupId: Long?): List<HabitVerify>

    @Query(
        """
        SELECT hv
        FROM HabitVerify hv
        JOIN FETCH hv.habit h
        JOIN FETCH h.groupMember gm
        JOIN FETCH gm.member
        WHERE gm.group.id = :groupId
        """,
    )
    fun findAllByGroupId(@Param("groupId") groupId: Long): List<HabitVerify>

    @Modifying(flushAutomatically = true)
    @Query(
        """
        delete from HabitVerify hv
        where hv.habit.id in (
            select h.id
            from Habit h
            where h.groupMember.id in (
                select gm.id
                from GroupMember gm
                where gm.group.id = :groupId
            )
        )
        """,
    )
    fun deleteHabitVerifiesByGroupId(@Param("groupId") groupId: Long): Int
}
