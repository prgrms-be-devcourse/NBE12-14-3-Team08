package com.back.domain.group.entity

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate

class GroupTest {

    private fun createGroup(
        deadline: LocalDate?,
        status: GroupStatus
    ): Group =
        Group(
            title = "테스트 그룹",
            description = "그룹 테스트",
            deadline = deadline,
            penalty = "벌금 1000원",
            password = "encoded-password",
            inviteCode = "TEST1234",
            memberLimit = 10,
            status = status
        )

    @Test
    @DisplayName("finish()를 호출하면 그룹 상태가 FINISH로 변경된다")
    fun finishChangesStatusToFinish() {
        // given
        val group = createGroup(
            LocalDate.now().plusDays(7),
            GroupStatus.ACTIVE
        )

        // when
        group.finish()

        // then
        assertThat(group.status).isEqualTo(GroupStatus.FINISH)
        assertThat(group.isFinished()).isTrue()
    }

    @Test
    @DisplayName("상태가 FINISH이면 마감일과 관계없이 종료된 그룹이다")
    fun isFinishedWhenStatusIsFinishReturnsTrue() {
        // given
        val group = createGroup(
            LocalDate.now().plusDays(7),
            GroupStatus.FINISH
        )

        // when & then
        assertThat(group.isFinished()).isTrue()
    }

    @Test
    @DisplayName("상태가 ACTIVE이고 마감일이 미래이면 종료되지 않은 그룹이다")
    fun isFinishedWhenDeadlineIsFutureReturnsFalse() {
        // given
        val group = createGroup(
            LocalDate.now().plusDays(1),
            GroupStatus.ACTIVE
        )

        // when & then
        assertThat(group.isFinished()).isFalse()
    }

    @Test
    @DisplayName("상태가 ACTIVE이고 마감일이 오늘이면 종료되지 않은 그룹이다")
    fun isFinishedWhenDeadlineIsTodayReturnsFalse() {
        // given
        val group = createGroup(
            LocalDate.now(),
            GroupStatus.ACTIVE
        )

        // when & then
        assertThat(group.isFinished()).isFalse()
    }

    @Test
    @DisplayName("상태가 ACTIVE여도 마감일이 지났으면 종료된 그룹이다")
    fun isFinishedWhenDeadlineHasPassedReturnsTrue() {
        // given
        val group = createGroup(
            LocalDate.now().minusDays(1),
            GroupStatus.ACTIVE
        )

        // when & then
        assertThat(group.isFinished()).isTrue()
    }

    @Test
    @DisplayName("상태가 ACTIVE이고 마감일이 없으면 종료되지 않은 그룹이다")
    fun isFinishedWhenDeadlineIsNullReturnsFalse() {
        // given
        val group = createGroup(
            null,
            GroupStatus.ACTIVE
        )

        // when & then
        assertThat(group.isFinished()).isFalse()
    }
}