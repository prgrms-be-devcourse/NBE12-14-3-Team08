package com.back.domain.penaltyverify.entity

import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.verifyEngagement.service.EngagementRules
import com.back.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction

// 벌칙 인증에 멤버가 남기는 댓글. 구조와 이유는 HabitVerifyComment와 같다. (docs/adr/0002-verify-reaction-comment-tables.md)
@Entity
@Table(
    name = "penalty_verify_comment",
    indexes = [
        Index(name = "idx_penalty_verify_comment_verify", columnList = "penalty_verify_id, create_date"),
        Index(name = "idx_penalty_verify_comment_group_member", columnList = "group_member_id"),
    ],
)
class PenaltyVerifyComment private constructor(
    penaltyVerify: PenaltyVerify,
    groupMember: GroupMember,
    content: String,
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

    @Column(nullable = false, length = CONTENT_MAX_LENGTH)
    var content: String = requireValid(content)
        protected set

    fun edit(content: String) {
        this.content = requireValid(content)
    }

    companion object {
        const val CONTENT_MAX_LENGTH = EngagementRules.COMMENT_MAX_LENGTH

        fun create(penaltyVerify: PenaltyVerify, groupMember: GroupMember, content: String): PenaltyVerifyComment =
            PenaltyVerifyComment(penaltyVerify, groupMember, content)

        private fun requireValid(content: String): String {
            require(content.isNotBlank()) { "댓글 내용을 입력해주세요." }
            require(content.length <= CONTENT_MAX_LENGTH) { "댓글은 ${CONTENT_MAX_LENGTH}자 이하여야 합니다." }
            return content
        }
    }
}
