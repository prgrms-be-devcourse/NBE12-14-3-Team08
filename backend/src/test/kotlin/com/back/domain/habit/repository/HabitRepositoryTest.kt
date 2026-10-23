package com.back.domain.habit.repository

import com.back.domain.group.entity.Group
import com.back.domain.group.entity.GroupStatus
import com.back.domain.group.repository.GroupRepository
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.habit.entity.Habit
import com.back.domain.habit.entity.HabitStatus
import com.back.domain.member.entity.Member
import com.back.domain.member.repository.MemberRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDateTime
import java.util.UUID

// Testcontainers PostgreSQL에서 HabitRepository의 커스텀 쿼리를 검증한다. (테스트마다 롤백)
@DataJpaTest
class HabitRepositoryTest {

    @Autowired
    private lateinit var habitRepository: HabitRepository

    @Autowired
    private lateinit var groupRepository: GroupRepository

    @Autowired
    private lateinit var groupMemberRepository: GroupMemberRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    private lateinit var member: Member
    private lateinit var group: Group
    private lateinit var groupMember: GroupMember

    @BeforeEach
    fun setUp() {
        member = memberRepository.save(Member.create("일반회원", uniqueName("member"), "pass1234"))
        group = saveGroup(GroupStatus.ACTIVE)
        groupMember = groupMemberRepository.save(GroupMember.create(group, member, GroupMemberRole.MEMBER))
    }

    private fun uniqueName(prefix: String): String = prefix + "-" + UUID.randomUUID().toString().substring(0, 8)

    private fun saveGroup(status: GroupStatus): Group =
        groupRepository.save(
            Group.builder()
                .title("아침 기상 챌린지")
                .penalty("커피 1잔 쏘기")
                .password("1234")
                .inviteCode(uniqueName("code"))
                .memberLimit(10)
                .status(status)
                .build(),
        )

    private fun saveHabit(owner: GroupMember, title: String, status: HabitStatus): Habit {
        val habit = Habit.create(owner, title, "설명", 3)
        if (status == HabitStatus.FAILED) {
            habit.fail()
        }
        return habitRepository.saveAndFlush(habit)
    }

    @Nested
    @DisplayName("활성 습관 존재/조회")
    inner class ActiveHabit {

        @Test
        @DisplayName("existsByGroupMember_IdAndStatus: 해당 멤버의 해당 상태 습관이 있을 때만 true다")
        fun 상태별_존재_여부() {
            saveHabit(groupMember, "물 마시기", HabitStatus.FAILED)

            assertThat(habitRepository.existsByGroupMember_IdAndStatus(groupMember.id, HabitStatus.FAILED))
                .isTrue()
            assertThat(habitRepository.existsByGroupMember_IdAndStatus(groupMember.id, HabitStatus.ACTIVE))
                .isFalse()
        }

        @Test
        @DisplayName("existsByGroupMember_IdAndStatus: 다른 멤버의 습관은 포함하지 않는다")
        fun 다른_멤버의_습관은_포함하지_않는다() {
            val other = memberRepository.save(Member.create("다른회원", uniqueName("other"), "pass1234"))
            val otherGroupMember =
                groupMemberRepository.save(GroupMember.create(group, other, GroupMemberRole.MEMBER))
            saveHabit(otherGroupMember, "독서", HabitStatus.ACTIVE)

            assertThat(habitRepository.existsByGroupMember_IdAndStatus(groupMember.id, HabitStatus.ACTIVE))
                .isFalse()
        }

        @Test
        @DisplayName("회원과 그룹으로 활성 습관을 조회한다")
        fun 회원과_그룹으로_활성_습관_조회() {
            val active = saveHabit(groupMember, "물 마시기", HabitStatus.ACTIVE)
            saveHabit(groupMember, "예전 습관", HabitStatus.FAILED)

            val found = habitRepository.findByGroupMember_Member_IdAndGroupMember_Group_IdAndStatus(
                member.id, group.id, HabitStatus.ACTIVE,
            )

            assertThat(found).isNotNull()
            assertThat(found?.id).isEqualTo(active.id)
        }

        @Test
        @DisplayName("다른 그룹의 활성 습관은 조회되지 않는다")
        fun 다른_그룹의_활성_습관은_조회되지_않는다() {
            val otherGroup = saveGroup(GroupStatus.ACTIVE)
            val inOtherGroup =
                groupMemberRepository.save(GroupMember.create(otherGroup, member, GroupMemberRole.MEMBER))
            saveHabit(inOtherGroup, "다른 그룹 습관", HabitStatus.ACTIVE)

            assertThat(
                habitRepository.findByGroupMember_Member_IdAndGroupMember_Group_IdAndStatus(
                    member.id, group.id, HabitStatus.ACTIVE,
                ),
            ).isNull()
        }

        @Test
        @DisplayName("본인 소유일 때만 id로 습관을 조회한다")
        fun 본인_습관만_id로_조회() {
            val habit = saveHabit(groupMember, "물 마시기", HabitStatus.ACTIVE)

            assertThat(habitRepository.findByIdAndGroupMember_Member_Id(habit.id, member.id)).isPresent()
            assertThat(habitRepository.findByIdAndGroupMember_Member_Id(habit.id, member.id + 999)).isEmpty()
        }
    }

    @Nested
    @DisplayName("실패 습관 목록")
    inner class FailedHabits {

        @Test
        @DisplayName("해당 멤버와 그룹의 실패 습관만 생성일 최신순으로 반환한다")
        fun 최신순_실패_습관_목록() {
            val older = saveHabit(groupMember, "오래된 습관", HabitStatus.FAILED)
            ReflectionTestUtils.setField(older, "createDate", LocalDateTime.now().minusDays(3))
            val newer = saveHabit(groupMember, "최근 습관", HabitStatus.FAILED)
            ReflectionTestUtils.setField(newer, "createDate", LocalDateTime.now().minusDays(1))
            saveHabit(groupMember, "진행 중 습관", HabitStatus.ACTIVE)
            habitRepository.flush()
            entityManager.clear()

            val result = habitRepository
                .findAllByGroupMember_Member_IdAndGroupMember_Group_IdAndStatusOrderByCreateDateDesc(
                    member.id, group.id, HabitStatus.FAILED,
                )

            assertThat(result).extracting<String> { it.title }.containsExactly("최근 습관", "오래된 습관")
        }
    }

    @Nested
    @DisplayName("주간 판정 대상 조회")
    inner class WeeklyCheckTargets {

        @Test
        @DisplayName("findAllByStatusInProgressGroup: 종료(FINISH)된 그룹의 습관은 제외하고 진행 중 그룹의 활성 습관만 반환한다")
        fun 종료된_그룹은_제외() {
            val inProgress = saveHabit(groupMember, "진행 중 그룹 습관", HabitStatus.ACTIVE)

            val finishedGroup = saveGroup(GroupStatus.FINISH)
            val inFinished =
                groupMemberRepository.save(GroupMember.create(finishedGroup, member, GroupMemberRole.MEMBER))
            saveHabit(inFinished, "종료된 그룹 습관", HabitStatus.ACTIVE)

            saveHabit(
                groupMemberRepository.save(GroupMember.create(saveGroup(GroupStatus.ACTIVE), member, GroupMemberRole.MEMBER)),
                "실패한 습관",
                HabitStatus.FAILED,
            )

            val result = habitRepository.findAllByStatusInProgressGroup(HabitStatus.ACTIVE)

            assertThat(result).extracting<Long?> { it.id }.contains(inProgress.id)
            assertThat(result).extracting<String> { it.title }
                .doesNotContain("종료된 그룹 습관", "실패한 습관")
        }

        @Test
        @DisplayName("findAllByGroupMember_Group_IdAndStatus: 해당 그룹의 활성 습관만 반환한다")
        fun 그룹의_활성_습관만_반환() {
            val active = saveHabit(groupMember, "활성 습관", HabitStatus.ACTIVE)
            val other = memberRepository.save(Member.create("다른회원", uniqueName("other"), "pass1234"))
            val otherInSameGroup =
                groupMemberRepository.save(GroupMember.create(group, other, GroupMemberRole.MEMBER))
            saveHabit(otherInSameGroup, "실패 습관", HabitStatus.FAILED)
            val inOtherGroup = groupMemberRepository.save(
                GroupMember.create(saveGroup(GroupStatus.ACTIVE), member, GroupMemberRole.MEMBER),
            )
            saveHabit(inOtherGroup, "다른 그룹 활성 습관", HabitStatus.ACTIVE)

            val result = habitRepository.findAllByGroupMember_Group_IdAndStatus(group.id, HabitStatus.ACTIVE)

            assertThat(result).extracting<Long?> { it.id }.containsExactly(active.id)
        }
    }

    @Nested
    @DisplayName("삭제 쿼리")
    inner class DeleteQueries {

        @Test
        @DisplayName("findIdsByGroupMemberId: 멤버의 습관 id를 상태와 무관하게 모두 반환한다")
        fun 멤버의_습관_id_조회() {
            val active = saveHabit(groupMember, "활성", HabitStatus.ACTIVE)
            val failed = saveHabit(groupMember, "실패", HabitStatus.FAILED)

            assertThat(habitRepository.findIdsByGroupMemberId(groupMember.id))
                .containsExactlyInAnyOrder(active.id, failed.id)
        }

        @Test
        @DisplayName("deleteByIds: 지정한 습관만 삭제한다")
        fun 지정한_습관만_삭제() {
            val target = saveHabit(groupMember, "삭제 대상", HabitStatus.FAILED)
            val keep = saveHabit(groupMember, "유지", HabitStatus.ACTIVE)

            habitRepository.deleteByIds(listOf(target.id))

            assertThat(habitRepository.findById(target.id)).isEmpty()
            assertThat(habitRepository.findById(keep.id)).isPresent()
        }

        @Test
        @DisplayName("deleteHabitsByGroupId: 해당 그룹 멤버들의 습관만 삭제하고 삭제 건수를 반환한다")
        fun 그룹의_습관만_삭제() {
            val inGroup1 = saveHabit(groupMember, "그룹 습관 1", HabitStatus.ACTIVE)
            val inGroup2 = saveHabit(groupMember, "그룹 습관 2", HabitStatus.FAILED)
            val inOtherGroup = groupMemberRepository.save(
                GroupMember.create(saveGroup(GroupStatus.ACTIVE), member, GroupMemberRole.MEMBER),
            )
            val otherGroupHabit = saveHabit(inOtherGroup, "다른 그룹 습관", HabitStatus.ACTIVE)
            entityManager.clear()

            val deleted = habitRepository.deleteHabitsByGroupId(group.id)
            entityManager.clear()

            assertThat(deleted).isEqualTo(2)
            assertThat(habitRepository.findById(inGroup1.id)).isEmpty()
            assertThat(habitRepository.findById(inGroup2.id)).isEmpty()
            assertThat(habitRepository.findById(otherGroupHabit.id)).isPresent()
        }
    }
}
