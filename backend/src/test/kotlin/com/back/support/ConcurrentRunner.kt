package com.back.support

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// 여러 작업을 같은 순간에 시작시켜 동시성 문제를 재현하는 도우미다.
// 모든 작업이 준비된 뒤 한꺼번에 시작하고, 각 작업이 던진 예외를 순서대로 돌려준다. (성공이면 null)
object ConcurrentRunner {

    fun runAll(tasks: List<() -> Unit>, timeoutSeconds: Long = 60): List<Throwable?> {
        val executor = Executors.newFixedThreadPool(tasks.size)
        val ready = CountDownLatch(tasks.size)
        val start = CountDownLatch(1)

        try {
            val futures = tasks.map { task ->
                executor.submit(
                    Callable<Throwable?> {
                        ready.countDown()
                        start.await()
                        try {
                            task()
                            null
                        } catch (e: Throwable) {
                            e
                        }
                    },
                )
            }

            ready.await()
            start.countDown()

            return futures.map { it.get(timeoutSeconds, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
    }
}
