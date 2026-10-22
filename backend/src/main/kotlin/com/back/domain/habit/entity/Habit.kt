package com.back.domain.habit.entity

import com.back.domain.groupMember.entity.GroupMember
import com.back.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(
    name = "habit",
    indexes = [Index(name = "idx_habit_group_member", columnList = "group_member_id")],
)
class Habit private constructor(
    groupMember: GroupMember,
    title: String,
    description: String?,
    days: Int,
) : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_member_id", nullable = false)
    var groupMember: GroupMember = groupMember
        protected set

    @Column(nullable = false)
    var title: String = title
        protected set

    var description: String? = description
        protected set

    @Column(nullable = false)
    var days: Int = days
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: HabitStatus = HabitStatus.ACTIVE
        protected set

    fun fail() {
        check(status == HabitStatus.ACTIVE) { "활성화된 습관만 실패 처리할 수 있습니다." }
        status = HabitStatus.FAILED
    }

    companion object {
        @JvmStatic
        fun create(
            groupMember: GroupMember,
            title: String,
            description: String?,
            days: Int,
        ): Habit = Habit(groupMember, title, description, days)
    }
}