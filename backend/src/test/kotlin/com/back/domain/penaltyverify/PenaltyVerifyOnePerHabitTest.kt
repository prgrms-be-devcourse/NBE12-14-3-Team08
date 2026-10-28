package com.back.domain.penaltyverify

import com.back.domain.group.entity.Group
import com.back.domain.group.repository.GroupRepository
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.habit.entity.Habit
import com.back.domain.habit.repository.HabitRepository
import com.back.domain.member.entity.Member
import com.back.domain.member.repository.MemberRepository
import com.back.domain.penaltyverify.entity.PenaltyVerify
import com.back.domain.penaltyverify.repository.PenaltyVerifyRepository
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException

// GLOSSARY: 벌칙 인증은 "습관 실패 하나에 하나씩" 생긴다.
// DB 유니크 제약(group_member_id, habit_id)이 이 규칙을 지키므로, 엔티티를 Kotlin으로 바꾸면서 제약이 빠지지 않는지 고정한다.
// (테스트 DB 스키마는 엔티티에서 만들어져서, 제약이 빠져도 다른 테스트는 조용히 통과한다)
@DataJpaTest
class PenaltyVerifyOnePerHabitTest @Autowired constructor(
    private val penaltyVerifyRepository: PenaltyVerifyRepository,
    private val habitRepository: HabitRepository,
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val memberRepository: MemberRepository
) {

    @Test
    @DisplayName("같은 멤버의 같은 습관에 벌칙 인증을 두 번 만들 수 없다")
    fun 같은_습관에_벌칙_인증은_하나만() {
        val group = groupRepository.save(
            Group(
                title = "아침 기상 챌린지",
                penalty = "커피 1잔 쏘기",
                password = "1234",
                inviteCode = "ONEPERHABIT",
                memberLimit = 5,
            )
        )
        val member = memberRepository.save(Member.create("회원", "one-per-habit", "pass1234"))
        val groupMember = groupMemberRepository.save(GroupMember.create(group, member, GroupMemberRole.MEMBER))
        val habit = habitRepository.save(Habit.create(groupMember, "기상 후 스트레칭", "10분 스트레칭", 3))

        penaltyVerifyRepository.saveAndFlush(PenaltyVerify.create(groupMember, habit))

        assertThatThrownBy { penaltyVerifyRepository.saveAndFlush(PenaltyVerify.create(groupMember, habit)) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }
}
