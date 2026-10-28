package com.back.domain.penaltyverify.entity

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

// 벌칙 인증에 멤버가 남기는 하트. 구조와 이유는 HabitVerifyReaction과 같다. (docs/adr/0002-verify-reaction-comment-tables.md)
// 벌칙 인증은 지우지 않고 REQUIRED로 되돌리므로, 되돌릴 때는 PenaltyVerifyEngagementService가 하트와 댓글을 함께 지운다.
@Entity
@Table(
    name = "penalty_verify_reaction",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_penalty_verify_reaction", columnNames = ["penalty_verify_id", "group_member_id"]),
    ],
    indexes = [Index(name = "idx_penalty_verify_reaction_group_member", columnList = "group_member_id")],
)
class PenaltyVerifyReaction private constructor(
    penaltyVerify: PenaltyVerify,
    groupMember: GroupMember,
) : BaseEntity() {

    @OnDelete(action = OnDeleteAction.CASCADE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "penalty_verify_id", nullable = false)
    var penaltyVerify: PenaltyVerify = penaltyVerify
        protected set

    @OnDelete(action = OnDeleteAction.CASCADE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_member_id", nullable = false)
    var groupMember: GroupMember = groupMember
        protected set

    companion object {
        fun create(penaltyVerify: PenaltyVerify, groupMember: GroupMember): PenaltyVerifyReaction =
            PenaltyVerifyReaction(penaltyVerify, groupMember)
    }
}
