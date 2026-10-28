package com.back.domain.penaltyverify.entity

import com.back.domain.groupMember.entity.GroupMember
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
    name = "penalty_verify",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_penalty_verify", columnNames = ["group_member_id", "habit_id"]),
    ],
)
class PenaltyVerify private constructor(
    groupMember: GroupMember,
    habit: Habit,
    habitTitle: String,
    penaltyText: String,
) : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_member_id", nullable = false)
    var groupMember: GroupMember = groupMember
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "habit_id", nullable = false)
    var habit: Habit = habit
        protected set

    @Column(name = "verify_date")
    var verifyDate: LocalDate? = null
        protected set

    @Column(nullable = false)
    var habitTitle: String = habitTitle
        protected set

    // 이전 벌칙 스냅샷
    @Column(nullable = false)
    var penaltyText: String = penaltyText
        protected set

    var imageUrl: String? = null
        protected set

    var description: String? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: PenaltyVerifyStatus = PenaltyVerifyStatus.REQUIRED
        protected set

    fun validateSubmittable() {
        check(status == PenaltyVerifyStatus.REQUIRED) { "벌칙 제출 대상(REQUIRED)가 아닙니다. 현재상태$status" }
    }

    fun validateResubmittable() {
        check(status == PenaltyVerifyStatus.REJECTED) { "반려된 벌칙만 재제출할 수 있습니다. 현재상태 $status" }
    }

    fun submit(verifyDate: LocalDate?, description: String?, imageUrl: String?) {
        validateSubmittable()
        fill(verifyDate, description, imageUrl)
    }

    fun resubmit(verifyDate: LocalDate?, description: String?, imageUrl: String?) {
        validateResubmittable()
        fill(verifyDate, description, imageUrl)
    }

    fun approve() = reviewTo(PenaltyVerifyStatus.APPROVED)

    fun reject() = reviewTo(PenaltyVerifyStatus.REJECTED)

    // 승인·반려는 승인 대기(PENDING)에서만 할 수 있다
    private fun reviewTo(result: PenaltyVerifyStatus) {
        check(status == PenaltyVerifyStatus.PENDING) { "승인 대기 상태가 아닙니다. 현재 상태 : $status" }
        status = result
    }

    // 처음 제출(REQUIRED) 또는 반려 후 재제출(REJECTED)이 가능한 상태인지
    val isSubmittable: Boolean
        get() = status == PenaltyVerifyStatus.REQUIRED || status == PenaltyVerifyStatus.REJECTED

    // 작성자의 제출 취소: 방장 검토 전(PENDING)에만 가능
    fun cancelSubmission() {
        check(status == PenaltyVerifyStatus.PENDING) {
            "승인 대기(PENDING) 중인 벌칙만 제출을 취소할 수 있습니다. 현재 상태 : $status"
        }
        clearSubmission()
    }

    // 방장의 벌칙 초기화: 제출된 적 있는 벌칙(PENDING / APPROVED / REJECTED)을 재수행 대상으로 되돌림
    fun resetByOwner() {
        check(status != PenaltyVerifyStatus.REQUIRED) { "이미 미제출(REQUIRED) 상태인 벌칙입니다." }
        clearSubmission()
    }

    // 제출·재제출 공통: 제출 내용을 채우고 검토 대기로
    private fun fill(verifyDate: LocalDate?, description: String?, imageUrl: String?) {
        this.verifyDate = verifyDate
        this.description = description
        this.imageUrl = imageUrl
        status = PenaltyVerifyStatus.PENDING
    }

    // 결산 집계를 위해 row는 남기고 제출 내용만 비운다 (docs/adr/0001-penalty-verify-no-hard-delete.md)
    private fun clearSubmission() {
        verifyDate = null
        description = null
        imageUrl = null
        status = PenaltyVerifyStatus.REQUIRED
    }

    companion object {
        // 습관 이름과 방의 벌칙 문구는 실패 시점 값으로 남긴다 (이후 바뀌어도 이 벌칙 기록은 그대로)
        fun create(groupMember: GroupMember, habit: Habit): PenaltyVerify =
            PenaltyVerify(
                groupMember = groupMember,
                habit = habit,
                habitTitle = habit.title,
                penaltyText = groupMember.group.penalty,
            )
    }
}
