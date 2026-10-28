package com.back.domain.settlement.repository

import com.back.domain.group.entity.Group
import com.back.domain.group.entity.GroupStatus
import com.back.domain.group.repository.GroupRepository
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.habit.entity.Habit
import com.back.domain.habit.repository.HabitRepository
import com.back.domain.habitVerify.entity.HabitVerify
import com.back.domain.habitVerify.repository.HabitVerifyRepository
import com.back.domain.member.entity.Member
import com.back.domain.member.repository.MemberRepository
import com.back.domain.penaltyverify.entity.PenaltyVerify
import com.back.domain.penaltyverify.repository.PenaltyVerifyRepository
import com.back.global.entity.requiredId
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.Hibernate
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import java.time.LocalDate
import java.util.UUID

// 결산이 쓰는 그룹 단위 조회 쿼리를 PostgreSQL에서 확인한다. (테스트마다 롤백)
// 결산은 이 결과로 인증자와 닉네임을 바로 읽으므로, 연관 엔티티가 한 번에 채워져 있어야 N+1이 생기지 않는다.
@DataJpaTest
class SettlementQueryRepositoryTest @Autowired constructor(
    private val groupRepository: GroupRepository,
    private val memberRepository: MemberRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val habitRepository: HabitRepository,
    private val habitVerifyRepository: HabitVerifyRepository,
    private val penaltyVerifyRepository: PenaltyVerifyRepository,
    private val entityManager: EntityManager
) {

    private class Participant(val groupMember: GroupMember, val habit: Habit)

    private fun saveGroup(title: String): Group =
        groupRepository.save(
            Group(
                title = title,
                description = "설명",
                deadline = LocalDate.now().plusDays(10),
                penalty = "벌칙",
                password = "encoded-password",
                inviteCode = UUID.randomUUID().toString().substring(0, 8),
                memberLimit = 10,
                status = GroupStatus.FINISH,
            ),
        )

    private fun join(group: Group, nickname: String): Participant {
        val member = memberRepository.save(Member.create(nickname, "q-" + UUID.randomUUID().toString().substring(0, 12), "pw"))
        val groupMember = groupMemberRepository.save(GroupMember.create(group, member, GroupMemberRole.MEMBER))
        return Participant(groupMember, habitRepository.save(Habit.create(groupMember, "습관", "설명", 3)))
    }

    private fun verify(who: Participant, day: Long): HabitVerify =
        habitVerifyRepository.save(HabitVerify.create(who.habit, LocalDate.of(2026, 10, 1).plusDays(day), "설명", "https://example.com/a.png"))

    private fun penalty(who: Participant): PenaltyVerify =
        penaltyVerifyRepository.save(PenaltyVerify.create(who.groupMember, who.habit))

    // 영속성 컨텍스트를 비워 쿼리가 실제로 연관 엔티티를 채우는지 확인할 수 있게 한다.
    private fun reload() {
        entityManager.flush()
        entityManager.clear()
    }

    @Nested
    @DisplayName("HabitVerifyRepository.findAllByGroupId")
    inner class HabitVerifies {

        @Test
        @DisplayName("해당 그룹의 모든 회원의 인증만 돌려주고 다른 그룹의 인증은 제외한다")
        fun 그룹의_인증만() {
            val group = saveGroup("그룹")
            val other = saveGroup("다른 그룹")
            val a = join(group, "에이")
            val b = join(group, "비")
            val stranger = join(other, "남")
            val mine = listOf(verify(a, 1), verify(a, 2), verify(b, 1))
            verify(stranger, 1)
            reload()

            val result = habitVerifyRepository.findAllByGroupId(group.requiredId)

            assertThat(result.map { it.requiredId }).containsExactlyInAnyOrderElementsOf(mine.map { it.requiredId })
        }

        @Test
        @DisplayName("인증, 습관, 그룹 멤버, 회원이 한 번에 채워져 있어 닉네임을 읽어도 추가 쿼리가 없다")
        fun 연관_엔티티가_채워진다() {
            val group = saveGroup("그룹")
            verify(join(group, "에이"), 1)
            reload()

            val result = habitVerifyRepository.findAllByGroupId(group.requiredId)

            val verify = result.single()
            assertThat(Hibernate.isInitialized(verify.habit)).isTrue()
            assertThat(Hibernate.isInitialized(verify.habit.groupMember)).isTrue()
            assertThat(Hibernate.isInitialized(verify.habit.groupMember.member)).isTrue()
            assertThat(verify.habit.groupMember.member.nickname).isEqualTo("에이")
        }

        @Test
        @DisplayName("나간(LEFT) 멤버의 인증도 포함한다 (회원 상태나 멤버 상태로 거르지 않는다)")
        fun 나간_멤버의_인증도_포함() {
            val group = saveGroup("그룹")
            val leaver = join(group, "나간 사람")
            leaver.groupMember.leave()
            val saved = verify(leaver, 1)
            reload()

            assertThat(habitVerifyRepository.findAllByGroupId(group.requiredId).map { it.requiredId }).containsExactly(saved.requiredId)
        }

        @Test
        @DisplayName("인증이 없는 그룹과 없는 그룹 id는 빈 목록이다")
        fun 빈_결과() {
            val group = saveGroup("그룹")
            join(group, "에이")
            reload()

            assertThat(habitVerifyRepository.findAllByGroupId(group.requiredId)).isEmpty()
            assertThat(habitVerifyRepository.findAllByGroupId(Long.MAX_VALUE)).isEmpty()
        }
    }

    @Nested
    @DisplayName("PenaltyVerifyRepository.findAllByGroupId")
    inner class PenaltyVerifies {

        @Test
        @DisplayName("해당 그룹의 모든 회원의 벌칙만 돌려주고 다른 그룹의 벌칙은 제외한다")
        fun 그룹의_벌칙만() {
            val group = saveGroup("그룹")
            val other = saveGroup("다른 그룹")
            val a = join(group, "에이")
            val b = join(group, "비")
            val stranger = join(other, "남")
            val mine = listOf(penalty(a), penalty(b))
            penalty(stranger)
            reload()

            val result = penaltyVerifyRepository.findAllByGroupId(group.requiredId)

            assertThat(result.map { it.requiredId }).containsExactlyInAnyOrderElementsOf(mine.map { it.requiredId })
        }

        @Test
        @DisplayName("벌칙, 그룹 멤버, 회원이 한 번에 채워져 있어 닉네임을 읽어도 추가 쿼리가 없다")
        fun 연관_엔티티가_채워진다() {
            val group = saveGroup("그룹")
            penalty(join(group, "에이"))
            reload()

            val penalty = penaltyVerifyRepository.findAllByGroupId(group.requiredId).single()

            assertThat(Hibernate.isInitialized(penalty.groupMember)).isTrue()
            assertThat(Hibernate.isInitialized(penalty.groupMember.member)).isTrue()
            assertThat(penalty.groupMember.member.nickname).isEqualTo("에이")
        }

        @Test
        @DisplayName("벌칙이 없는 그룹과 없는 그룹 id는 빈 목록이다")
        fun 빈_결과() {
            val group = saveGroup("그룹")
            join(group, "에이")
            reload()

            assertThat(penaltyVerifyRepository.findAllByGroupId(group.requiredId)).isEmpty()
            assertThat(penaltyVerifyRepository.findAllByGroupId(Long.MAX_VALUE)).isEmpty()
        }
    }
}
