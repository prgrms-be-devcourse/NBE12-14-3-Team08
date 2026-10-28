package com.back.domain.habit.service

import com.back.domain.group.entity.Group
import com.back.domain.group.repository.GroupRepository
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.habit.entity.Habit
import com.back.domain.habit.repository.HabitRepository
import com.back.domain.member.entity.Member
import com.back.domain.member.repository.MemberRepository
import com.back.support.ConcurrentRunner
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

// 매일 00시 05분 스케줄러(HabitWeeklyCheckScheduler)와 방장의 수동 주간 판정(checkWeeklyFailureForGroup)이
// 같은 습관을 동시에 판정할 때 실패 처리와 벌칙이 한 번만 일어나고 어느 쪽도 예외 없이 끝나는지 확인한다.
// 실제로 커밋되는 트랜잭션이 필요해서 @Transactional 없이 실행하고, 끝나면 만든 데이터를 직접 지운다.
@SpringBootTest
@ActiveProfiles("test")
class HabitServiceJudgementConcurrencyTest @Autowired constructor(
    private val habitService: HabitService,
    private val habitWeeklyCheckScheduler: HabitWeeklyCheckScheduler,
    private val memberRepository: MemberRepository,
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val habitRepository: HabitRepository,
    private val jdbc: JdbcTemplate
) {

    private class OverdueGroup(val groupId: Long, val ownerId: Long, val habitCount: Int)

    // 수동 판정과 스케줄러가 함께 다루는 그룹, 스케줄러만 다루는 다른 그룹
    private lateinit var main: OverdueGroup
    private lateinit var other: OverdueGroup
    private val memberIds = mutableListOf<Long>()

    companion object {
        // 판정 대상 습관 수. 습관이 많을수록 두 판정이 같은 습관에서 겹칠 기회가 늘어난다.
        private const val HABIT_COUNT = 10
        private const val OTHER_HABIT_COUNT = 5
        private const val MANUAL_CALLS = 4
        private const val SCHEDULER_CALLS = 4
    }

    @BeforeEach
    fun setUp() {
        main = createOverdueGroup("main", HABIT_COUNT)
        other = createOverdueGroup("other", OTHER_HABIT_COUNT)
    }

    @AfterEach
    fun tearDown() {
        listOf(main, other).forEach { group ->
            val gmIds = "select id from group_member where group_id = ?"
            jdbc.update("delete from penalty_verify where group_member_id in ($gmIds)", group.groupId)
            jdbc.update("delete from habit where group_member_id in ($gmIds)", group.groupId)
            jdbc.update("delete from group_member where group_id = ?", group.groupId)
            jdbc.update("delete from groups where id = ?", group.groupId)
        }
        memberIds.forEach { jdbc.update("delete from member where id = ?", it) }
    }

    // 모든 습관이 "가입 1주 경과 + 이번 주 마감 지남 + 인증 0건"이라 판정하면 실패 처리되어야 하는 상태의 그룹을 만든다.
    private fun createOverdueGroup(label: String, habitCount: Int): OverdueGroup {
        val suffix = UUID.randomUUID().toString().substring(0, 8)

        val group = groupRepository.save(
            Group(
                title = "판정 동시성 그룹 $label",
                penalty = "커피 사기",
                password = "1234",
                inviteCode = "J$label$suffix".take(20),
                memberLimit = habitCount + 5,
                deadline = LocalDate.now().plusDays(30),
            ),
        )
        val groupId = requireNotNull(group.id)

        val owner = memberRepository.save(Member.create("judge-$label-owner-$suffix", "judge-$label-owner-$suffix", "pw1234!"))
        val ownerId = requireNotNull(owner.id)
        memberIds += ownerId
        groupMemberRepository.save(GroupMember.create(group, owner, GroupMemberRole.OWNER))

        repeat(habitCount) { i ->
            val member = memberRepository.save(Member.create("judge-$label-$i-$suffix", "judge-$label-$i-$suffix", "pw1234!"))
            memberIds += requireNotNull(member.id)
            val groupMember = groupMemberRepository.save(GroupMember.create(group, member, GroupMemberRole.MEMBER))
            habitRepository.save(Habit.create(groupMember, "습관 $i", "설명", 3))
        }

        // 생성 시각은 DB에서 직접 되돌린다. (엔티티의 createDate는 수정되지 않는 컬럼이다)
        val now = LocalDateTime.now()
        jdbc.update("update groups set create_date = ? where id = ?", Timestamp.valueOf(now.minusDays(14)), groupId)
        jdbc.update("update group_member set create_date = ? where group_id = ?", Timestamp.valueOf(now.minusDays(10)), groupId)
        jdbc.update(
            "update habit set create_date = ? where group_member_id in (select id from group_member where group_id = ?)",
            Timestamp.valueOf(now.minusDays(10)),
            groupId,
        )

        return OverdueGroup(groupId, ownerId, habitCount)
    }

    private fun runConcurrently(): List<Throwable?> {
        val manual: List<() -> Unit> =
            List(MANUAL_CALLS) { { habitService.checkWeeklyFailureForGroup(main.ownerId, main.groupId) } }
        val scheduler: List<() -> Unit> = List(SCHEDULER_CALLS) { { habitWeeklyCheckScheduler.checkAllActiveHabits() } }
        return ConcurrentRunner.runAll(manual + scheduler)
    }

    private fun failedHabitCount(group: OverdueGroup): Int =
        jdbc.queryForObject(
            "select count(*) from habit where status = 'FAILED' and group_member_id in (select id from group_member where group_id = ?)",
            Int::class.java,
            group.groupId,
        ) ?: 0

    private fun penaltyCount(group: OverdueGroup): Int =
        jdbc.queryForObject(
            "select count(*) from penalty_verify where group_member_id in (select id from group_member where group_id = ?)",
            Int::class.java,
            group.groupId,
        ) ?: 0

    @Test
    @DisplayName("스케줄러와 수동 판정이 동시에 돌아도 모든 습관은 실패 처리되고 벌칙은 습관마다 하나뿐이다 (데이터 무결성)")
    fun 동시에_판정해도_데이터는_어긋나지_않는다() {
        runConcurrently()

        // 일부 호출이 예외로 끝나더라도, 마지막에는 아직 처리되지 않은 습관이 없도록 한 번 더 판정한다.
        habitService.checkWeeklyFailureForGroup(main.ownerId, main.groupId)
        habitWeeklyCheckScheduler.checkAllActiveHabits()

        assertThat(failedHabitCount(main)).describedAs("실패 처리된 습관").isEqualTo(HABIT_COUNT)
        assertThat(penaltyCount(main)).describedAs("벌칙 개수").isEqualTo(HABIT_COUNT)
    }

    @Test
    @DisplayName("스케줄러와 수동 판정이 동시에 돌아도 어느 쪽도 예외 없이 끝난다")
    fun 동시에_판정해도_예외가_없다() {
        val results = runConcurrently()

        val failures = results.filterNotNull()
        assertThat(failures)
            .describedAs("예외로 끝난 호출: ${failures.map { it.javaClass.simpleName }}")
            .isEmpty()
        assertThat(failedHabitCount(main)).describedAs("실패 처리된 습관").isEqualTo(HABIT_COUNT)
        assertThat(penaltyCount(main)).describedAs("벌칙 개수").isEqualTo(HABIT_COUNT)
    }

    @Test
    @DisplayName("한 그룹에서 판정이 겹쳐도 같은 배치에서 처리하는 다른 그룹의 습관은 모두 정상 처리된다")
    fun 다른_그룹_판정은_영향받지_않는다() {
        val results = runConcurrently()

        // 동시 실행 직후 상태를 그대로 확인한다. (추가 판정 없음)
        assertThat(results.filterNotNull()).describedAs("예외로 끝난 호출").isEmpty()
        assertThat(failedHabitCount(other)).describedAs("다른 그룹에서 실패 처리된 습관").isEqualTo(OTHER_HABIT_COUNT)
        assertThat(penaltyCount(other)).describedAs("다른 그룹의 벌칙 개수").isEqualTo(OTHER_HABIT_COUNT)
    }
}
