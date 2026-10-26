package com.back.domain.habit.service

import com.back.domain.group.entity.GroupStatus
import com.back.domain.group.repository.GroupRepository
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.group.entity.Group
import com.back.domain.habit.entity.Habit
import com.back.domain.habit.entity.HabitStatus
import com.back.domain.habit.repository.HabitRepository
import com.back.domain.member.entity.Member
import com.back.domain.member.repository.MemberRepository
import com.back.domain.penaltyverify.repository.PenaltyVerifyRepository
import com.back.global.entity.requiredId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

// 주간 판정 배치(HabitWeeklyCheckScheduler)가 종료된 그룹의 습관을 판정하지 않는지 확인한다. (Testcontainers PostgreSQL, 테스트 종료 시 롤백)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HabitServiceFinishedGroupBatchTest {

    @Autowired
    private lateinit var habitWeeklyCheckScheduler: HabitWeeklyCheckScheduler

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var groupRepository: GroupRepository

    @Autowired
    private lateinit var groupMemberRepository: GroupMemberRepository

    @Autowired
    private lateinit var habitRepository: HabitRepository

    @Autowired
    private lateinit var penaltyVerifyRepository: PenaltyVerifyRepository

    // 그룹 생성일 14일 전(오늘과 같은 요일), 가입일/습관 생성일 10일 전, 인증 기록 없음
    // -> 이번 주 판정 시 주 3회 미달로 실패해야 하는 습관을 만든다.
    private fun createOverdueHabit(status: GroupStatus, deadline: LocalDate): Habit {
        val suffix = UUID.randomUUID().toString().substring(0, 8)
        val now = LocalDateTime.now()

        val owner = memberRepository.save(Member.create("batch-$suffix", "batch-$suffix", "pw1234!"))

        val group = groupRepository.save(
            Group.builder()
                .title("배치 테스트 그룹")
                .penalty("커피 사기")
                .password("1234")
                .inviteCode("B$suffix")
                .memberLimit(5)
                .deadline(deadline)
                .status(status)
                .build(),
        )
        ReflectionTestUtils.setField(group, "createDate", now.minusDays(14))
        groupRepository.saveAndFlush(group)

        val groupMember = groupMemberRepository.save(GroupMember.create(group, owner, GroupMemberRole.OWNER))
        ReflectionTestUtils.setField(groupMember, "createDate", now.minusDays(10))
        groupMemberRepository.saveAndFlush(groupMember)

        val habit = habitRepository.save(Habit.create(groupMember, "물 마시기", "하루 2L", 3))
        ReflectionTestUtils.setField(habit, "createDate", now.minusDays(10))
        return habitRepository.saveAndFlush(habit)
    }

    @Test
    @DisplayName("진행 중인 그룹의 미달 습관은 배치에서 실패 처리되고 벌칙이 생긴다 (대조군)")
    fun 진행중인_그룹은_판정된다() {
        val habit = createOverdueHabit(GroupStatus.ACTIVE, LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(30))

        habitWeeklyCheckScheduler.checkAllActiveHabits()

        assertThat(habit.status).isEqualTo(HabitStatus.FAILED)
        assertThat(penaltyVerifyRepository.findByHabitId(habit.requiredId)).isPresent()
    }

    @Test
    @DisplayName("묶음 크기보다 습관이 많아도 모든 습관이 판정된다 (묶음 경계를 넘어 처리)")
    fun 묶음을_나눠도_모두_판정된다() {
        val habits = List(5) { createOverdueHabit(GroupStatus.ACTIVE, LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(30)) }

        val result = habitWeeklyCheckScheduler.checkAllActiveHabits(chunkSize = 2)

        assertThat(habits.map { it.status }).containsOnly(HabitStatus.FAILED)
        assertThat(habits.map { penaltyVerifyRepository.findByHabitId(it.requiredId).isPresent }).containsOnly(true)
        assertThat(result.chunks).isGreaterThanOrEqualTo(3)
        assertThat(result.skippedHabitIds).isEmpty()
    }

    @Test
    @DisplayName("종료된(FINISH) 그룹의 습관은 배치에서 판정하지 않는다")
    fun 종료된_그룹은_판정하지_않는다() {
        val habit = createOverdueHabit(GroupStatus.FINISH, LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1))

        habitWeeklyCheckScheduler.checkAllActiveHabits()

        assertThat(habit.status).isEqualTo(HabitStatus.ACTIVE)
        assertThat(penaltyVerifyRepository.findByHabitId(habit.requiredId)).isEmpty()
    }

    @Test
    @DisplayName("마감일은 지났지만 아직 FINISH 처리 전인 그룹도 배치에서 판정하지 않는다")
    fun 마감일이_지난_그룹도_판정하지_않는다() {
        val habit = createOverdueHabit(GroupStatus.ACTIVE, LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1))

        habitWeeklyCheckScheduler.checkAllActiveHabits()

        assertThat(habit.status).isEqualTo(HabitStatus.ACTIVE)
        assertThat(penaltyVerifyRepository.findByHabitId(habit.requiredId)).isEmpty()
    }
}
