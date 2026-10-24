package com.back.domain.habitVerify.entity

import com.back.domain.group.entity.Group
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.habit.entity.Habit
import com.back.domain.member.entity.Member
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDate

class HabitVerifyTest {

    private lateinit var habit: Habit

    @BeforeEach
    fun setUp() {
        val member = Member.create("일반회원", "member", "pass1234")
        val group = Group.builder()
            .title("아침 기상 챌린지")
            .penalty("커피 1잔 쏘기")
            .memberLimit(10)
            .build()
        val groupMember = GroupMember.create(group, member, GroupMemberRole.MEMBER)
        habit = Habit.create(groupMember, "기상 후 스트레칭", "10분 스트레칭", 3)
    }

    private fun verifyIn(status: HabitVerifyStatus): HabitVerify {
        val habitVerify = HabitVerify.create(habit, LocalDate.of(2026, 10, 21), "스트레칭 완료", "https://example.com/a.png")
        ReflectionTestUtils.setField(habitVerify, "status", status)
        return habitVerify
    }

    @Test
    @DisplayName("인증을 만들면 검토 대기(PENDING)로 시작한다")
    fun 생성하면_PENDING() {
        val habitVerify = HabitVerify.create(habit, LocalDate.of(2026, 10, 21), "스트레칭 완료", "https://example.com/a.png")

        assertThat(habitVerify.status).isEqualTo(HabitVerifyStatus.PENDING)
        assertThat(habitVerify.habit).isSameAs(habit)
        assertThat(habitVerify.verifyDate).isEqualTo(LocalDate.of(2026, 10, 21))
    }

    @Nested
    @DisplayName("승인(approve)")
    inner class Approve {

        @Test
        @DisplayName("검토 대기 인증을 승인하면 APPROVED가 된다")
        fun 대기_인증_승인() {
            val habitVerify = verifyIn(HabitVerifyStatus.PENDING)

            habitVerify.approve()

            assertThat(habitVerify.status).isEqualTo(HabitVerifyStatus.APPROVED)
        }

        @ParameterizedTest(name = "{0} 상태")
        @EnumSource(value = HabitVerifyStatus::class, names = ["APPROVED", "REJECTED"])
        @DisplayName("이미 처리된 인증은 승인할 수 없고(409) 상태가 그대로다")
        fun 처리된_인증_승인_불가(status: HabitVerifyStatus) {
            val habitVerify = verifyIn(status)

            assertThatThrownBy { habitVerify.approve() }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("검토 대기 상태가 아닙니다")

            assertThat(habitVerify.status).isEqualTo(status)
        }
    }

    @Nested
    @DisplayName("반려(reject)")
    inner class Reject {

        @Test
        @DisplayName("검토 대기 인증을 반려하면 REJECTED가 된다")
        fun 대기_인증_반려() {
            val habitVerify = verifyIn(HabitVerifyStatus.PENDING)

            habitVerify.reject()

            assertThat(habitVerify.status).isEqualTo(HabitVerifyStatus.REJECTED)
        }

        @ParameterizedTest(name = "{0} 상태")
        @EnumSource(value = HabitVerifyStatus::class, names = ["APPROVED", "REJECTED"])
        @DisplayName("이미 처리된 인증은 반려할 수 없고(409) 상태가 그대로다")
        fun 처리된_인증_반려_불가(status: HabitVerifyStatus) {
            val habitVerify = verifyIn(status)

            assertThatThrownBy { habitVerify.reject() }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("검토 대기 상태가 아닙니다")

            assertThat(habitVerify.status).isEqualTo(status)
        }
    }
}
