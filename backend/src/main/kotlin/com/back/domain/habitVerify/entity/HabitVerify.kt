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
    imageWidth: Int?,
    imageHeight: Int?,
) : BaseEntity() {

    init {
        requireImageSize(imageWidth, imageHeight)
    }

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

    // 사진의 가로·세로 픽셀 크기. 갤러리가 사진이 뜨기 전에 자리를 잡는 데 쓴다.
    // 크기를 저장하기 전에 올라온 인증은 null이고, 둘은 항상 함께 있거나 함께 없다.
    var imageWidth: Int? = imageWidth
        protected set

    var imageHeight: Int? = imageHeight
        protected set

    fun validateResubmittable() {
        check(status == HabitVerifyStatus.REJECTED) { "반려된 인증만 재제출할 수 있습니다. 현재 상태: $status" }
    }

    // 사진을 바꾸면 이전 사진의 크기는 맞지 않으므로, 새 크기를 주지 않으면 크기도 비운다.
    fun resubmit(description: String?, imageUrl: String?, imageWidth: Int? = null, imageHeight: Int? = null) {
        validateResubmittable()
        requireImageSize(imageWidth, imageHeight)
        this.description = description
        this.imageUrl = imageUrl
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        status = HabitVerifyStatus.PENDING
    }

    fun approve() = reviewTo(HabitVerifyStatus.APPROVED)

    fun reject() = reviewTo(HabitVerifyStatus.REJECTED)

    // 승인·반려는 검토 대기(PENDING)에서만 할 수 있다
    private fun reviewTo(result: HabitVerifyStatus) {
        check(status == HabitVerifyStatus.PENDING) { "검토 대기 상태가 아닙니다. 현재 상태: $status" }
        status = result
    }

    companion object {
        fun create(
            habit: Habit,
            verifyDate: LocalDate,
            description: String?,
            imageUrl: String?,
            imageWidth: Int? = null,
            imageHeight: Int? = null,
        ): HabitVerify = HabitVerify(habit, verifyDate, description, imageUrl, imageWidth, imageHeight)

        // 가로와 세로는 함께 있거나 함께 없어야 하고, 있으면 양수여야 한다.
        private fun requireImageSize(imageWidth: Int?, imageHeight: Int?) {
            require((imageWidth == null) == (imageHeight == null)) { "사진의 가로와 세로 크기는 함께 있어야 합니다." }
            require(imageWidth == null || (imageWidth > 0 && imageHeight!! > 0)) { "사진의 가로와 세로 크기는 1 이상이어야 합니다." }
        }
    }
}
