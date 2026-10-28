package com.back.domain.habit.repository

import com.back.domain.habit.entity.Habit
import com.back.domain.habit.entity.HabitStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
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

    fun existsByIdAndGroupMember_Member_Id(habitId: Long, memberId: Long): Boolean

    // 같은 습관을 동시에 포기·판정할 때 한 요청만 먼저 처리되도록 행을 잠그고 읽는다.
    // 늦게 온 요청은 잠금이 풀릴 때까지 기다린 뒤 이미 바뀐 상태(FAILED)를 읽는다.
    // 이 습관을 같은 트랜잭션에서 미리 읽어 두었다면 영속성 컨텍스트의 오래된 상태가 그대로 돌아오므로,
    // 호출하기 전에 엔티티가 아니라 id만 조회해야 한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Habit h where h.id = :habitId")
    fun findByIdForUpdate(@Param("habitId") habitId: Long): Habit?

    // 주간 판정 대상 id 조회. 여러 습관을 잠그는 순서를 항상 같게(id 오름차순) 해 교착을 막는다.
    @Query("select h.id from Habit h where h.groupMember.group.id = :groupId and h.status = :status order by h.id")
    fun findIdsByGroupIdAndStatus(@Param("groupId") groupId: Long, @Param("status") status: HabitStatus): List<Long>

    // 스케줄러용: 진행 중인 그룹의 해당 상태 습관 id (이미 종료된 그룹의 습관은 제외)
    @Query(
        "select h.id from Habit h " +
            "join h.groupMember gm " +
            "join gm.group g " +
            "where h.status = :status " +
            "and g.status <> com.back.domain.group.entity.GroupStatus.FINISH " +
            "order by h.id",
    )
    fun findIdsByStatusInProgressGroup(@Param("status") status: HabitStatus): List<Long>

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

    // 메인 화면의 "이번 주 승인" 집계용: 내가 아직 참여 중인 진행 중인 방에서, 아직 포기하지 않은 내 습관.
    // 마감일이 지난 방은 Group.isFinished()와 같은 기준(마감일 다음 날부터 종료)으로 뺀다.
    // 방은 습관마다 달라 요일 계산에 필요하므로 한 번에 같이 읽는다.
    @Query(
        """
        select h
        from Habit h
        join fetch h.groupMember gm
        join fetch gm.group g
        where gm.member.id = :memberId
            and gm.status = com.back.domain.groupMember.entity.GroupMemberStatus.ACTIVE
            and h.status = com.back.domain.habit.entity.HabitStatus.ACTIVE
            and g.status <> com.back.domain.group.entity.GroupStatus.FINISH
            and (g.deadline is null or g.deadline >= :today)
        """,
    )
    fun findActiveHabitsInRunningGroups(
        @Param("memberId") memberId: Long,
        @Param("today") today: java.time.LocalDate,
    ): List<Habit>
}