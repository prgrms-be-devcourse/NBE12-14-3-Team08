package com.back.domain.groupMember.repository

import com.back.domain.group.entity.Group
import com.back.domain.group.repository.GroupRepository
import com.back.domain.groupMember.dto.GroupMemberResponse
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.entity.GroupMemberStatus
import com.back.domain.habit.entity.Habit
import com.back.domain.habit.repository.HabitRepository
import com.back.domain.member.entity.Member
import com.back.domain.member.repository.MemberRepository
import com.back.domain.penaltyverify.entity.PenaltyVerify
import com.back.domain.penaltyverify.repository.PenaltyVerifyRepository
import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.transaction.annotation.Transactional

@DataJpaTest
class GroupMemberRepositoryTest {

    @Autowired
    private lateinit var groupMemberRepository: GroupMemberRepository

    @Autowired
    private lateinit var groupRepository: GroupRepository

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var habitRepository: HabitRepository

    @Autowired
    private lateinit var penaltyVerifyRepository: PenaltyVerifyRepository

    private lateinit var group: Group
    private lateinit var member: Member
    private lateinit var groupMember: GroupMember

    @BeforeEach
    fun setUp() {
        member = memberRepository.save(
            Member.create("테스트회원", "test-user", "encoded-password")
        )

        group = groupRepository.save(
            Group(
                title = "테스트 그룹",
                description = "테스트 설명",
                deadline = LocalDate.now().plusDays(7),
                penalty = "벌칙",
                password = "1234",
                inviteCode = "TEST-CODE",
                memberLimit = 5
            )
        )

        groupMember = groupMemberRepository.save(
            GroupMember.create(group, member, GroupMemberRole.MEMBER)
        )

        groupMemberRepository.flush()
    }

    @Test
    @DisplayName("그룹 ID와 회원 ID로 그룹 멤버를 조회한다")
    fun findByGroupIdAndMemberId() {
        val result = groupMemberRepository.findByGroupIdAndMemberId(
            group.id,
            member.id
        )

        assertThat(result).isPresent
        assertThat(result.get().id).isEqualTo(groupMember.id)
    }

    @Test
    @DisplayName("그룹 ID와 회원 ID로 그룹 멤버 존재 여부를 확인한다")
    fun existsByGroupIdAndMemberId() {
        assertThat(
            groupMemberRepository.existsByGroupIdAndMemberId(
                group.id,
                member.id
            )
        ).isTrue

        assertThat(
            groupMemberRepository.existsByGroupIdAndMemberId(
                group.id,
                -1L
            )
        ).isFalse
    }

    @Test
    @DisplayName("회원 ID로 그룹 멤버 존재 여부를 확인한다")
    fun existsByMemberId() {
        assertThat(groupMemberRepository.existsByMemberId(member.id)).isTrue
        assertThat(groupMemberRepository.existsByMemberId(-1L)).isFalse
    }

    @Test
    @DisplayName("그룹 ID로 멤버 수를 조회한다")
    fun countByGroupId() {
        val count = groupMemberRepository.countByGroupId(group.id)

        assertThat(count).isEqualTo(1L)
    }

    @Test
    @DisplayName("그룹 ID와 상태로 멤버 수를 조회한다")
    fun countByGroupIdAndStatus() {
        val activeCount = groupMemberRepository.countByGroupIdAndStatus(
            group.id,
            GroupMemberStatus.ACTIVE
        )

        val leftCount = groupMemberRepository.countByGroupIdAndStatus(
            group.id,
            GroupMemberStatus.LEFT
        )

        assertThat(activeCount).isEqualTo(1L)
        assertThat(leftCount).isZero()
    }

    @Test
    @DisplayName("그룹 ID와 상태로 멤버 존재 여부를 확인한다")
    fun existsByGroupIdAndMemberIdAndStatus() {
        assertThat(
            groupMemberRepository.existsByGroupIdAndMemberIdAndStatus(
                group.id,
                member.id,
                GroupMemberStatus.ACTIVE
            )
        ).isTrue

        assertThat(
            groupMemberRepository.existsByGroupIdAndMemberIdAndStatus(
                group.id,
                member.id,
                GroupMemberStatus.LEFT
            )
        ).isFalse
    }

    @Test
    @DisplayName("그룹 멤버 ID와 그룹 ID로 존재 여부를 확인한다")
    fun existsByIdAndGroupId() {
        assertThat(
            groupMemberRepository.existsByIdAndGroupId(
                groupMember.id,
                group.id
            )
        ).isTrue

        assertThat(
            groupMemberRepository.existsByIdAndGroupId(
                groupMember.id,
                -1L
            )
        ).isFalse
    }

    @Test
    @DisplayName("그룹 ID로 그룹 멤버 목록을 조회한다")
    fun findAllByGroupId() {
        val result = groupMemberRepository.findAllByGroupId(group.id)

        assertThat(result)
            .hasSize(1)
            .extracting<Long> { it.id }
            .containsExactly(groupMember.id)
    }

    @Test
    @DisplayName("회원 ID로 해당 회원의 그룹 멤버 목록을 조회한다")
    fun findAllByMemberId() {
        val result = groupMemberRepository.findAllByMemberId(member.id)

        assertThat(result)
            .hasSize(1)
            .extracting<Long> { it.id }
            .containsExactly(groupMember.id)
    }

    @Test
    @Transactional
    @DisplayName("그룹 ID와 회원 ID로 그룹 멤버를 잠금 조회한다")
    fun findByGroupIdAndMemberIdForUpdate() {
        val result = groupMemberRepository.findByGroupIdAndMemberIdForUpdate(
            group.id,
            member.id
        )

        assertThat(result).isPresent
        assertThat(result.get().id).isEqualTo(groupMember.id)
    }

    @Test
    @DisplayName("그룹 ID로 멤버 목록과 활성 습관 정보를 조회한다")
    fun findByGroupIdWithHabit() {
        val habit = habitRepository.save(
            Habit.create(groupMember, "운동하기", "매일 운동하기", 7)
        )

        val anotherMember = memberRepository.save(
            Member.create("두번째회원", "test-user-2", "encoded-password")
        )

        groupMemberRepository.save(
            GroupMember.create(group, anotherMember, GroupMemberRole.MEMBER)
        )

        groupMemberRepository.flush()

        val result = groupMemberRepository.findByGroupIdWithHabit(group.id)

        assertThat(result).hasSize(2)

        val memberWithHabit = result
            .first { it.groupMemberId == groupMember.id }

        assertThat(memberWithHabit.memberId).isEqualTo(member.id)
        assertThat(memberWithHabit.nickname).isEqualTo("테스트회원")
        assertThat(memberWithHabit.username).isEqualTo("test-user")
        assertThat(memberWithHabit.role).isEqualTo("MEMBER")
        assertThat(memberWithHabit.habitId).isEqualTo(habit.id)
        assertThat(memberWithHabit.habitTitle).isEqualTo("운동하기")
        assertThat(memberWithHabit.habitDescription).isEqualTo("매일 운동하기")

        val memberWithoutHabit = result
            .first { it.memberId == anotherMember.id }

        assertThat(memberWithoutHabit.habitId).isNull()
        assertThat(memberWithoutHabit.habitTitle).isNull()
        assertThat(memberWithoutHabit.habitDescription).isNull()
    }

    @Test
    @DisplayName("그룹 ID와 그룹 멤버 ID로 벌칙 횟수를 포함한 멤버 상세 정보를 조회한다")
    fun findMemberDetailWithPenaltyCount() {
        val result = groupMemberRepository.findMemberDetailWithPenaltyCount(
            group.id,
            groupMember.id
        )

        assertThat(result).isPresent

        val detail = result.get()

        assertThat(detail.groupMemberId).isEqualTo(groupMember.id)
        assertThat(detail.memberId).isEqualTo(member.id)
        assertThat(detail.nickname).isEqualTo("테스트회원")
        assertThat(detail.username).isEqualTo("test-user")
        assertThat(detail.role).isEqualTo("MEMBER")
        assertThat(detail.penaltyCount).isZero()
    }

    @Test
    @DisplayName("멤버의 벌칙 인증 기록이 여러 건이면 해당 개수를 반환한다")
    fun findMemberDetailWithPenaltyCount_multiplePenalties() {
        val habit1 = habitRepository.save(
            Habit.create(groupMember, "운동하기", "매일 운동하기", 7)
        )

        val habit2 = habitRepository.save(
            Habit.create(groupMember, "독서하기", "매일 독서하기", 7)
        )

        penaltyVerifyRepository.save(
            PenaltyVerify.create(groupMember, habit1)
        )

        penaltyVerifyRepository.save(
            PenaltyVerify.create(groupMember, habit2)
        )

        penaltyVerifyRepository.flush()

        val result = groupMemberRepository.findMemberDetailWithPenaltyCount(
            group.id,
            groupMember.id
        )

        assertThat(result).isPresent
        assertThat(result.get().penaltyCount).isEqualTo(2L)
    }

    @Test
    @DisplayName("다른 그룹의 멤버 ID로 상세 정보를 조회하면 결과가 없다")
    fun findMemberDetailWithPenaltyCount_wrongGroup() {
        val anotherGroup = groupRepository.save(
            Group(
                title = "다른 그룹",
                description = "다른 그룹 설명",
                deadline = LocalDate.now().plusDays(7),
                penalty = "다른 벌칙",
                password = "5678",
                inviteCode = "ANOTHER-CODE",
                memberLimit = 5
            )
        )

        val result = groupMemberRepository.findMemberDetailWithPenaltyCount(
            anotherGroup.id,
            groupMember.id
        )

        assertThat(result).isEmpty
    }

    @Test
    @DisplayName("활성 습관이 없는 멤버도 그룹 멤버 목록에 포함된다")
    fun findByGroupIdWithHabit_withoutActiveHabit() {
        val result = groupMemberRepository.findByGroupIdWithHabit(group.id)

        assertThat(result).hasSize(1)

        val simple: GroupMemberResponse.Simple = result[0]

        assertThat(simple.groupMemberId).isEqualTo(groupMember.id)
        assertThat(simple.habitId).isNull()
        assertThat(simple.habitTitle).isNull()
        assertThat(simple.habitDescription).isNull()
    }

    @Test
    @DisplayName("그룹 ID로 해당 그룹의 모든 멤버를 삭제한다")
    fun deleteGroupMembersByGroupId() {
        val deletedCount =
            groupMemberRepository.deleteGroupMembersByGroupId(group.id)

        assertThat(deletedCount).isEqualTo(1)
        assertThat(groupMemberRepository.countByGroupId(group.id)).isZero()

        // 그룹 자체는 삭제되지 않아야 한다.
        assertThat(groupRepository.findById(group.id)).isPresent

        // 회원 계정도 삭제되지 않아야 한다.
        assertThat(memberRepository.findById(member.id)).isPresent
    }
}