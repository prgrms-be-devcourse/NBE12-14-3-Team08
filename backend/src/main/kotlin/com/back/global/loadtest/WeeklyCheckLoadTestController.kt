package com.back.global.loadtest

import com.back.domain.habit.entity.HabitStatus
import com.back.domain.habit.repository.HabitRepository
import com.back.domain.habit.service.HabitService
import com.back.domain.habit.service.HabitWeeklyCheckScheduler
import jakarta.persistence.EntityManagerFactory
import org.hibernate.SessionFactory
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.CompletableFuture

// 주간 판정을 크론을 기다리지 않고 바로 실행해 소요 시간과 부하 지표를 돌려준다.
// loadtest 프로파일에서만 만들어지므로 운영·개발·로컬 실행에는 존재하지 않는다. (인증은 일반 API와 같다)
@Profile("loadtest")
@RestController
@RequestMapping("/loadtest/weekly-check")
class WeeklyCheckLoadTestController(
    private val habitService: HabitService,
    private val habitWeeklyCheckScheduler: HabitWeeklyCheckScheduler,
    private val habitRepository: HabitRepository,
    private val jdbc: JdbcTemplate,
    entityManagerFactory: EntityManagerFactory,
) {

    private val statistics = entityManagerFactory.unwrap(SessionFactory::class.java).statistics

    // 현재 판정 대상과 결과 상태를 센다. checksum은 습관·벌칙·인증 상태 전체의 요약값이라
    // 두 방식으로 판정한 결과가 같은지 비교할 때 쓴다.
    @GetMapping("/state")
    fun state(): Map<String, Any> = counts() + mapOf("checksum" to checksum())

    // mode=single : 개선 전 방식. 활성 습관 전체를 한 트랜잭션으로 순회한다. (비교 기준선)
    // mode=chunked: 실제 스케줄러(HabitWeeklyCheckScheduler)가 도는 방식. 습관 chunk개씩 별도 트랜잭션으로 순회한다.
    @PostMapping("/run")
    fun run(
        @RequestParam(defaultValue = "single") mode: String,
        @RequestParam(defaultValue = "100") chunk: Int,
    ): Map<String, Any> {
        require(mode == "single" || mode == "chunked") { "mode는 single 또는 chunked여야 합니다." }
        require(chunk > 0) { "chunk는 1 이상이어야 합니다." }

        val before = counts()
        val heapBeforeMb = usedHeapMb()
        val statementsBefore = statistics.prepareStatementCount
        val loadsBefore = statistics.entityLoadCount
        val updatesBefore = statistics.entityUpdateCount
        val insertsBefore = statistics.entityInsertCount

        val startedAtMs = System.currentTimeMillis()
        val startedAt = System.nanoTime()
        // 웹 요청 스레드에서 돌리면 Open Session In View 때문에 요청 하나가 영속성 컨텍스트를 끝까지 공유해서
        // 묶음별 트랜잭션을 나눠도 엔티티가 계속 쌓인다. 실제 스케줄러(웹 요청이 아닌 별도 스레드)와 같은 조건이 되도록
        // 별도 스레드에서 실행한다.
        val transactions = CompletableFuture.supplyAsync { execute(mode, chunk) }.get()
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        val finishedAtMs = System.currentTimeMillis()

        val after = counts()
        val habits = before.getValue("activeHabits")
        val statements = statistics.prepareStatementCount - statementsBefore

        return linkedMapOf(
            "mode" to mode,
            "chunk" to (if (mode == "chunked") chunk else 0),
            "transactions" to transactions,
            // 판정 대상 습관 수와 소요 시간
            "habits" to habits,
            "elapsedMs" to elapsedMs,
            "msPerHabit" to if (habits > 0) elapsedMs.toDouble() / habits else 0.0,
            // k6가 이 구간 안의 요청과 밖의 요청을 나눠 볼 수 있도록 시작·종료 시각(epoch ms)을 함께 준다.
            "startedAtMs" to startedAtMs,
            "finishedAtMs" to finishedAtMs,
            // 판정 결과: 실패 처리된 습관, 만들어진 벌칙, 자동 승인된 인증 (같은 시간에 들어온 습관 포기 요청의 결과도 섞인다)
            "failedHabits" to after.getValue("failedHabits") - before.getValue("failedHabits"),
            "penaltiesCreated" to after.getValue("penalties") - before.getValue("penalties"),
            "autoApprovedVerifies" to after.getValue("approvedVerifies") - before.getValue("approvedVerifies"),
            // SQL 수와 엔티티 수. 같은 시간에 다른 요청이 있으면 그 요청의 값도 섞인다.
            "sqlStatements" to statements,
            "sqlPerHabit" to if (habits > 0) statements.toDouble() / habits else 0.0,
            "entityLoads" to statistics.entityLoadCount - loadsBefore,
            "entityUpdates" to statistics.entityUpdateCount - updatesBefore,
            "entityInserts" to statistics.entityInsertCount - insertsBefore,
            "heapUsedMbBefore" to heapBeforeMb,
            "heapUsedMbAfter" to usedHeapMb(),
        )
    }

    // 판정 실행 본체. 만들어진 트랜잭션 수를 돌려준다.
    private fun execute(mode: String, chunk: Int): Int {
        if (mode == "single") {
            // 전체 id를 묶음 하나로 넘기면 개선 전과 같이 한 트랜잭션으로 순회한다. 소요 시간이 곧 습관 행 잠금을 쥐고 있는 시간이다.
            habitService.checkWeeklyChunk(habitRepository.findIdsByStatusInProgressGroup(HabitStatus.ACTIVE))
            return 1
        }
        return habitWeeklyCheckScheduler.checkAllActiveHabits(chunk).chunks
    }

    private fun counts(): Map<String, Long> = linkedMapOf(
        "activeHabits" to count("select count(*) from habit h join group_member gm on gm.id = h.group_member_id join groups g on g.id = gm.group_id where h.status = 'ACTIVE' and g.status <> 'FINISH'"),
        "failedHabits" to count("select count(*) from habit where status = 'FAILED'"),
        "penalties" to count("select count(*) from penalty_verify"),
        "approvedVerifies" to count("select count(*) from habit_verify where status = 'APPROVED'"),
        "pendingVerifies" to count("select count(*) from habit_verify where status = 'PENDING'"),
    )

    private fun checksum(): String = jdbc.queryForObject(
        """
        select md5(
            coalesce((select string_agg(id || ':' || status, ',' order by id) from habit), '') || '|' ||
            coalesce((select string_agg(habit_id || ':' || status, ',' order by habit_id) from penalty_verify), '') || '|' ||
            coalesce((select string_agg(id || ':' || status, ',' order by id) from habit_verify), '')
        )
        """.trimIndent(),
        String::class.java,
    ) ?: ""

    private fun count(sql: String): Long = jdbc.queryForObject(sql, Long::class.java) ?: 0L

    private fun usedHeapMb(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    }
}
