package com.back.domain.habit.repository

import com.back.domain.habit.entity.Habit
import com.back.domain.habit.entity.HabitStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface HabitRepository : JpaRepository<Habit, Long> {

    // 아래 3개 메서드(findByIdAndGroupMember_Member_Id, findIdsByGroupMemberId, deleteHabitsByGroupId)는
    // 다른 도메인의 Java 코드·테스트가 호출하므로, 기본형(long)으로 바뀌어 null 인자에서 NPE가 나지 않도록 박싱 타입(Long?)을 유지한다.
    // 해당 도메인을 Kotlin으로 전환할 때 non-null(Long)로 정리한다.
    fun findByIdAndGroupMember_Member_Id(
        habitId: Long?,
        memberId: Long?,
    ): Optional<Habit>

    fun existsByGroupMember_IdAndStatus(
        groupMemberId: Long,
        status: HabitStatus,
    ): Boolean

    fun findByGroupMember_Member_IdAndGroupMember_Group_IdAndStatus(
        memberId: Long,
        groupId: Long,
        status: HabitStatus,
    ): Habit?

    fun findAllByGroupMember_Member_IdAndGroupMember_Group_IdAndStatusOrderByCreateDateDesc(
        memberId: Long,
        groupId: Long,
        status: HabitStatus,
    ): List<Habit>

    // 스케줄러가 주기적으로 순회하며 자동 승인/판정을 적용할 대상 조회용 (이미 종료된 그룹의 습관은 제외)
    @Query(
        "select h from Habit h " +
            "join fetch h.groupMember gm " +
            "join fetch gm.group g " +
            "where h.status = :status " +
            "and g.status <> com.back.domain.group.entity.GroupStatus.FINISH",
    )
    fun findAllByStatusInProgressGroup(@Param("status") status: HabitStatus): List<Habit>

    // 그룹 단위 주간 판정 대상(그룹 내 활성 습관 전체) 조회용
    fun findAllByGroupMember_Group_IdAndStatus(groupId: Long, status: HabitStatus): List<Habit>

    @Query("select h.id from Habit h where h.groupMember.id = :groupMemberId")
    fun findIdsByGroupMemberId(@Param("groupMemberId") groupMemberId: Long?): List<Long>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Habit h where h.id in :habitIds")
    fun deleteByIds(@Param("habitIds") habitIds: List<Long>)

    @Modifying(flushAutomatically = true)
    @Query(
        """
        delete from Habit h
        where h.groupMember.id in (
            select gm.id
            from GroupMember gm
            where gm.group.id = :groupId
        )
        """,
    )
    fun deleteHabitsByGroupId(@Param("groupId") groupId: Long?): Int
}