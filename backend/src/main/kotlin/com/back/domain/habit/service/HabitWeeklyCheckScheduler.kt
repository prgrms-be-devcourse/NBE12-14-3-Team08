package com.back.domain.habit.service

import com.back.domain.habit.entity.HabitStatus
import com.back.domain.habit.repository.HabitRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

// 매일 00시 05분에 진행 중인 그룹의 모든 활성 습관을 판정한다.
//
// 습관을 묶음(기본 100개)으로 나눠 묶음마다 별도 트랜잭션(HabitService.checkWeeklyChunk)으로 판정한다.
// 전체를 한 트랜잭션으로 돌리면 습관 수에 따라 시간이 제곱으로 늘고(1만 개에서 6분 넘게), 그동안 DB 연결과
// 습관 행 잠금을 쥐어 다른 요청까지 막힌다. (부하 테스트 결과: docs/loadtest/weekly-check.md)
//
// 이 클래스는 트랜잭션 밖에서 호출해야 한다. 바깥 트랜잭션이 있으면 묶음이 그 안에 합쳐져 분리의 효과가 없다.
@Component
class HabitWeeklyCheckScheduler(
    private val habitService: HabitService,
    private val habitRepository: HabitRepository,
    @param:Value("\${app.habit.weekly-check.chunk-size:100}") private val chunkSize: Int,
) {

    class Result(val habits: Int, val chunks: Int, val skippedHabitIds: List<Long>)

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 5 0 * * *")
    fun run() {
        checkAllActiveHabits(chunkSize)
    }

    fun checkAllActiveHabits(chunkSize: Int = this.chunkSize): Result {
        require(chunkSize > 0) { "묶음 크기는 1 이상이어야 합니다: $chunkSize" }

        val startedAt = System.nanoTime()
        val chunks = habitRepository.findIdsByStatusInProgressGroup(HabitStatus.ACTIVE).chunked(chunkSize)
        val skipped = mutableListOf<Long>()

        for (chunk in chunks) {
            try {
                habitService.checkWeeklyChunk(chunk)
            } catch (e: Exception) {
                // 묶음 안의 한 습관 때문에 나머지까지 롤백되었을 수 있으니, 습관별로 다시 판정해 문제가 된 습관만 건너뛴다.
                // 판정은 다시 실행해도 결과가 같다. (이미 실패한 습관은 ALREADY_FAILED로 넘어간다)
                log.error("주간 판정 묶음이 실패했습니다. 습관별로 다시 판정합니다. (습관 {}개)", chunk.size, e)
                skipped += retryOneByOne(chunk)
            }
        }

        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        val habits = chunks.sumOf { it.size }
        if (skipped.isEmpty()) {
            log.info("주간 판정을 마쳤습니다. 습관 {}개, 묶음 {}개, {}ms", habits, chunks.size, elapsedMs)
        } else {
            log.warn("주간 판정을 마쳤지만 습관 {}개를 건너뛰었습니다: {} (전체 {}개, {}ms)", skipped.size, skipped, habits, elapsedMs)
        }
        return Result(habits, chunks.size, skipped)
    }

    private fun retryOneByOne(chunk: List<Long>): List<Long> {
        val skipped = mutableListOf<Long>()
        for (habitId in chunk) {
            try {
                habitService.checkWeeklyChunk(listOf(habitId))
            } catch (e: Exception) {
                log.error("습관 {}의 주간 판정에 실패해 건너뜁니다.", habitId, e)
                skipped += habitId
            }
        }
        return skipped
    }
}
