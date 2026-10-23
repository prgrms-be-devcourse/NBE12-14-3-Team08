package com.back.domain.habitVerify

import com.back.domain.group.entity.Group
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
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException
import java.time.LocalDate

// 습관 하나에 같은 날 습관 인증은 하나만 남는다.
// 서비스의 "같은 날 인증 여부" 조회 외에 DB 유니크 제약(habit_id, verify_date)이 동시 요청까지 막으므로,
// 엔티티를 Kotlin으로 바꾸면서 제약이 빠지지 않는지 고정한다.
// (테스트 DB 스키마는 엔티티에서 만들어져서, 제약이 빠져도 다른 테스트는 조용히 통과한다)
@DataJpaTest
class HabitVerifyOnePerDayTest {

    @Autowired lateinit var habitVerifyRepository: HabitVerifyRepository
    @Autowired lateinit var habitRepository: HabitRepository
    @Autowired lateinit var groupRepository: GroupRepository
    @Autowired lateinit var groupMemberRepository: GroupMemberRepository
    @Autowired lateinit var memberRepository: MemberRepository

    @Test
    @DisplayName("같은 습관에 같은 날짜의 습관 인증을 두 번 저장할 수 없다")
    fun 같은_날_습관_인증은_하나만() {
        val group = groupRepository.save(
            Group.builder()
                .title("아침 기상 챌린지")
                .penalty("커피 1잔 쏘기")
                .password("1234")
                .inviteCode("ONEPERDAY")
                .memberLimit(5)
                .build()
        )
        val member = memberRepository.save(Member.create("회원", "one-per-day", "pass1234"))
        val groupMember = groupMemberRepository.save(GroupMember.create(group, member, GroupMemberRole.MEMBER))
        val habit = habitRepository.save(Habit.create(groupMember, "기상 후 스트레칭", "10분 스트레칭", 3))
        val today = LocalDate.now()

        habitVerifyRepository.saveAndFlush(HabitVerify.create(habit, today, "첫 인증", "https://example.com/a.png"))

        assertThatThrownBy {
            habitVerifyRepository.saveAndFlush(HabitVerify.create(habit, today, "두 번째", "https://example.com/b.png"))
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }
}
