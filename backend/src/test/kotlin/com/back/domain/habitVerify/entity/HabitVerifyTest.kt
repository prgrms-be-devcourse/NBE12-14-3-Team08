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
import org.junit.jupiter.params.provider.CsvSource
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
    @DisplayName("사진 크기")
    inner class ImageSize {

        private fun sized(width: Int?, height: Int?, status: HabitVerifyStatus = HabitVerifyStatus.REJECTED): HabitVerify {
            val habitVerify = HabitVerify.create(habit, LocalDate.of(2026, 10, 21), "인증", "https://example.com/a.png", width, height)
            ReflectionTestUtils.setField(habitVerify, "status", status)
            return habitVerify
        }

        @Test
        @DisplayName("크기를 주지 않고 만들면 가로·세로 모두 null이다 (크기 저장 전의 인증과 같은 상태)")
        fun 크기_없이_생성() {
            val habitVerify = HabitVerify.create(habit, LocalDate.of(2026, 10, 21), "인증", "https://example.com/a.png")

            assertThat(habitVerify.imageWidth).isNull()
            assertThat(habitVerify.imageHeight).isNull()
        }

        @Test
        @DisplayName("크기를 주고 만들면 그대로 저장된다")
        fun 크기와_함께_생성() {
            val habitVerify = sized(4032, 3024)

            assertThat(habitVerify.imageWidth).isEqualTo(4032)
            assertThat(habitVerify.imageHeight).isEqualTo(3024)
        }

        @Test
        @DisplayName("가로만 있거나 세로만 있으면 만들 수 없다")
        fun 한쪽만_있으면_생성_불가() {
            assertThatThrownBy { sized(100, null) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("사진의 가로와 세로 크기는 함께 있어야 합니다.")
            assertThatThrownBy { sized(null, 100) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("사진의 가로와 세로 크기는 함께 있어야 합니다.")
        }

        @ParameterizedTest(name = "가로={0}, 세로={1}")
        @CsvSource("0,100", "100,0", "-1,100", "100,-1")
        @DisplayName("0 이하의 크기로는 만들 수 없다")
        fun 양수가_아니면_생성_불가(width: Int, height: Int) {
            assertThatThrownBy { sized(width, height) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("사진의 가로와 세로 크기는 1 이상이어야 합니다.")
        }

        @Test
        @DisplayName("재제출하면서 새 크기를 주면 사진과 함께 크기가 바뀐다")
        fun 재제출하면_크기가_바뀐다() {
            val habitVerify = sized(4032, 3024)

            habitVerify.resubmit("다시", "https://example.com/b.png", 1080, 1920)

            assertThat(habitVerify.imageUrl).isEqualTo("https://example.com/b.png")
            assertThat(habitVerify.imageWidth).isEqualTo(1080)
            assertThat(habitVerify.imageHeight).isEqualTo(1920)
            assertThat(habitVerify.status).isEqualTo(HabitVerifyStatus.PENDING)
        }

        @Test
        @DisplayName("재제출하면서 크기를 주지 않으면 이전 사진의 크기를 비운다 (새 사진과 맞지 않으므로)")
        fun 재제출하며_크기를_안_주면_비운다() {
            val habitVerify = sized(4032, 3024)

            habitVerify.resubmit("다시", "https://example.com/b.png")

            assertThat(habitVerify.imageWidth).isNull()
            assertThat(habitVerify.imageHeight).isNull()
        }

        @Test
        @DisplayName("재제출의 크기가 잘못되면 거절하고 사진, 크기, 상태가 그대로다")
        fun 잘못된_크기로_재제출하면_그대로() {
            val habitVerify = sized(4032, 3024)

            assertThatThrownBy { habitVerify.resubmit("다시", "https://example.com/b.png", 100, null) }
                .isInstanceOf(IllegalArgumentException::class.java)

            assertThat(habitVerify.imageUrl).isEqualTo("https://example.com/a.png")
            assertThat(habitVerify.imageWidth).isEqualTo(4032)
            assertThat(habitVerify.imageHeight).isEqualTo(3024)
            assertThat(habitVerify.status).isEqualTo(HabitVerifyStatus.REJECTED)
        }
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
