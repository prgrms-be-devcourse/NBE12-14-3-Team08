package com.back.domain.habitVerify.entity

import com.back.domain.groupMember.entity.GroupMember
import com.back.global.entity.BaseEntity
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction

// 습관 인증에 멤버가 남기는 하트. 한 인증에 멤버당 하나이고, 취소하면 행을 지운다. (docs/adr/0002-verify-reaction-comment-tables.md)
// 인증이나 멤버를 지우면 DB가 함께 지우도록 FK에 ON DELETE CASCADE를 둔다.
// 방 삭제·탈퇴·추방이 모두 직접 쿼리로 행을 지워서, 정리 코드를 경로마다 따로 두면 하나만 빠져도 삭제가 막힌다.
@Entity
@Table(
    name = "habit_verify_reaction",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_habit_verify_reaction", columnNames = ["habit_verify_id", "group_member_id"]),
    ],
    indexes = [Index(name = "idx_habit_verify_reaction_group_member", columnList = "group_member_id")],
)
class HabitVerifyReaction private constructor(
    habitVerify: HabitVerify,
    groupMember: GroupMember,
) : BaseEntity() {

    @OnDelete(action = OnDeleteAction.CASCADE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "habit_verify_id", nullable = false)
    var habitVerify: HabitVerify = habitVerify
        protected set

    @OnDelete(action = OnDeleteAction.CASCADE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_member_id", nullable = false)
    var groupMember: GroupMember = groupMember
        protected set

    companion object {
        fun create(habitVerify: HabitVerify, groupMember: GroupMember): HabitVerifyReaction =
            HabitVerifyReaction(habitVerify, groupMember)
    }
}
