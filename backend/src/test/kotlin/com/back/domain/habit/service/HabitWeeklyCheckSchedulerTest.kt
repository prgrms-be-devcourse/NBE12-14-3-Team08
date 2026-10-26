package com.back.domain.habit.service

import com.back.domain.habit.entity.HabitStatus
import com.back.domain.habit.repository.HabitRepository
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.scheduling.annotation.Scheduled

// 주간 판정 스케줄러가 습관을 묶음으로 나눠 호출하고, 한 묶음이 실패해도 나머지를 계속 처리하는지 확인한다.
// 판정 규칙 자체는 HabitServiceTest(checkWeeklyChunk)와 통합 테스트가 확인한다.
class HabitWeeklyCheckSchedulerTest {

    private val habitService = mockk<HabitService>()
    private val habitRepository = mockk<HabitRepository>()

    private fun scheduler(chunkSize: Int = 100) = HabitWeeklyCheckScheduler(habitService, habitRepository, chunkSize)

    private fun stubIds(ids: List<Long>) {
        every { habitRepository.findIdsByStatusInProgressGroup(HabitStatus.ACTIVE) } returns ids
    }

    @Test
    @DisplayName("매일 00시 05분에 실행되도록 예약되어 있다 (스케줄러를 옮기면서 예약이 빠지지 않았는지 확인)")
    fun 매일_00시_05분에_예약되어_있다() {
        val scheduled = HabitWeeklyCheckScheduler::class.java.getMethod("run").getAnnotation(Scheduled::class.java)

        assertThat(scheduled).isNotNull()
        assertThat(scheduled.cron).isEqualTo("0 5 0 * * *")
    }

    @Nested
    @DisplayName("대상 조회와 묶음 나누기")
    inner class Chunking {

        @Test
        @DisplayName("진행 중인 그룹의 활성 습관만 조회한다 (종료 그룹 제외 쿼리 사용)")
        fun 종료_그룹_제외_쿼리를_사용한다() {
            stubIds(emptyList())

            scheduler().checkAllActiveHabits()

            verify { habitRepository.findIdsByStatusInProgressGroup(HabitStatus.ACTIVE) }
            verify(exactly = 0) { habitRepository.findIdsByGroupIdAndStatus(any(), any()) }
        }

        @Test
        @DisplayName("습관을 묶음 크기대로 나눠 순서대로 판정한다 (마지막 묶음은 남은 만큼)")
        fun 묶음_크기대로_나눈다() {
            stubIds((1L..7L).toList())
            justRun { habitService.checkWeeklyChunk(any()) }

            val result = scheduler(chunkSize = 3).checkAllActiveHabits()

            verifyOrder {
                habitService.checkWeeklyChunk(listOf(1L, 2L, 3L))
                habitService.checkWeeklyChunk(listOf(4L, 5L, 6L))
                habitService.checkWeeklyChunk(listOf(7L))
            }
            assertThat(result.habits).isEqualTo(7)
            assertThat(result.chunks).isEqualTo(3)
            assertThat(result.skippedHabitIds).isEmpty()
        }

        @Test
        @DisplayName("묶음 크기와 습관 수가 같으면 묶음 하나다")
        fun 정확히_나누어떨어진다() {
            stubIds((1L..4L).toList())
            justRun { habitService.checkWeeklyChunk(any()) }

            val result = scheduler(chunkSize = 4).checkAllActiveHabits()

            verify(exactly = 1) { habitService.checkWeeklyChunk(listOf(1L, 2L, 3L, 4L)) }
            assertThat(result.chunks).isEqualTo(1)
        }

        @Test
        @DisplayName("판정할 습관이 없으면 판정을 호출하지 않는다")
        fun 대상이_없다() {
            stubIds(emptyList())

            val result = scheduler().checkAllActiveHabits()

            verify(exactly = 0) { habitService.checkWeeklyChunk(any()) }
            assertThat(result.habits).isEqualTo(0)
            assertThat(result.chunks).isEqualTo(0)
        }

        @Test
        @DisplayName("예약된 실행(run)은 설정된 묶음 크기를 쓴다")
        fun 예약_실행은_설정값을_쓴다() {
            stubIds((1L..5L).toList())
            justRun { habitService.checkWeeklyChunk(any()) }

            scheduler(chunkSize = 2).run()

            verify(exactly = 1) { habitService.checkWeeklyChunk(listOf(1L, 2L)) }
            verify(exactly = 1) { habitService.checkWeeklyChunk(listOf(3L, 4L)) }
            verify(exactly = 1) { habitService.checkWeeklyChunk(listOf(5L)) }
        }

        @ParameterizedTest(name = "묶음 크기={0}")
        @ValueSource(ints = [0, -1])
        @DisplayName("묶음 크기가 1보다 작으면 거절한다")
        fun 잘못된_묶음_크기(size: Int) {
            assertThatThrownBy { scheduler().checkAllActiveHabits(size) }
                .isInstanceOf(IllegalArgumentException::class.java)

            verify(exactly = 0) { habitRepository.findIdsByStatusInProgressGroup(any()) }
        }
    }

    @Nested
    @DisplayName("묶음 실패 처리")
    inner class Failure {

        @Test
        @DisplayName("한 묶음이 실패하면 그 묶음을 습관별로 다시 판정하고, 문제가 된 습관만 건너뛴다")
        fun 실패한_묶음은_습관별로_다시_판정한다() {
            stubIds((1L..6L).toList())
            justRun { habitService.checkWeeklyChunk(any()) }
            every { habitService.checkWeeklyChunk(listOf(3L, 4L)) } throws IllegalStateException("묶음 실패")
            every { habitService.checkWeeklyChunk(listOf(4L)) } throws IllegalStateException("문제가 된 습관")

            val result = scheduler(chunkSize = 2).checkAllActiveHabits()

            // 3번은 다시 판정되어 처리되고, 4번만 건너뛴다. 앞뒤 묶음은 영향이 없다.
            verify(exactly = 1) { habitService.checkWeeklyChunk(listOf(1L, 2L)) }
            verify(exactly = 1) { habitService.checkWeeklyChunk(listOf(3L)) }
            verify(exactly = 1) { habitService.checkWeeklyChunk(listOf(4L)) }
            verify(exactly = 1) { habitService.checkWeeklyChunk(listOf(5L, 6L)) }
            assertThat(result.skippedHabitIds).containsExactly(4L)
            assertThat(result.habits).isEqualTo(6)
        }

        @Test
        @DisplayName("묶음이 일시적으로 실패했다면 습관별 재판정에서 모두 성공하고 건너뛰는 습관이 없다")
        fun 일시적인_실패는_건너뛰지_않는다() {
            stubIds(listOf(1L, 2L, 3L))
            justRun { habitService.checkWeeklyChunk(any()) }
            every { habitService.checkWeeklyChunk(listOf(1L, 2L, 3L)) } throws IllegalStateException("일시적 오류")

            val result = scheduler(chunkSize = 3).checkAllActiveHabits()

            verify(exactly = 1) { habitService.checkWeeklyChunk(listOf(1L)) }
            verify(exactly = 1) { habitService.checkWeeklyChunk(listOf(2L)) }
            verify(exactly = 1) { habitService.checkWeeklyChunk(listOf(3L)) }
            assertThat(result.skippedHabitIds).isEmpty()
        }

        @Test
        @DisplayName("모든 습관이 실패해도 예외를 밖으로 던지지 않고 건너뛴 습관을 기록한다 (스케줄러가 멈추지 않는다)")
        fun 전부_실패해도_예외를_던지지_않는다() {
            stubIds(listOf(1L, 2L))
            every { habitService.checkWeeklyChunk(any()) } throws IllegalStateException("모두 실패")

            val result = scheduler(chunkSize = 2).checkAllActiveHabits()

            assertThat(result.skippedHabitIds).containsExactly(1L, 2L)
        }

        @Test
        @DisplayName("대상 조회 자체가 실패하면 예외가 그대로 올라간다 (판정을 시작하지 못한 상태)")
        fun 조회_실패는_올라간다() {
            every { habitRepository.findIdsByStatusInProgressGroup(any()) } throws IllegalStateException("DB 오류")

            assertThatThrownBy { scheduler().checkAllActiveHabits() }.isInstanceOf(IllegalStateException::class.java)
            verify(exactly = 0) { habitService.checkWeeklyChunk(any()) }
        }
    }
}
