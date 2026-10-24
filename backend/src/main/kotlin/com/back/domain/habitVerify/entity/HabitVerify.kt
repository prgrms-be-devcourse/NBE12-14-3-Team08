package com.back.domain.habitVerify.entity

import com.back.domain.habit.entity.Habit
import com.back.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate

@Entity
@Table(
    name = "habit_verify",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_habit_verify_date", columnNames = ["habit_id", "verify_date"]),
    ],
)
class HabitVerify private constructor(
    habit: Habit,
    verifyDate: LocalDate,
    description: String?,
    imageUrl: String?,
) : BaseEntity() {

    @Column(nullable = false)
    var verifyDate: LocalDate = verifyDate
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "habit_id", nullable = false)
    var habit: Habit = habit
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: HabitVerifyStatus = HabitVerifyStatus.PENDING
        protected set

    var description: String? = description
        protected set

    var imageUrl: String? = imageUrl
        protected set

    fun validateResubmittable() {
        check(status == HabitVerifyStatus.REJECTED) { "반려된 인증만 재제출할 수 있습니다. 현재 상태: $status" }
    }

    fun resubmit(description: String?, imageUrl: String?) {
        validateResubmittable()
        this.description = description
        this.imageUrl = imageUrl
        status = HabitVerifyStatus.PENDING
    }

    fun approve() {
        check(status == HabitVerifyStatus.PENDING) { "검토 대기 상태가 아닙니다. 현재 상태: $status" }
        status = HabitVerifyStatus.APPROVED
    }

    fun reject() {
        check(status == HabitVerifyStatus.PENDING) { "검토 대기 상태가 아닙니다. 현재 상태: $status" }
        status = HabitVerifyStatus.REJECTED
    }

    companion object {
        fun create(
            habit: Habit,
            verifyDate: LocalDate,
            description: String?,
            imageUrl: String?,
        ): HabitVerify = HabitVerify(habit, verifyDate, description, imageUrl)
    }
}
