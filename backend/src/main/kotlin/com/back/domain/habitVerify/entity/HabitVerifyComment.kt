package com.back.domain.habitVerify.entity

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

// 습관 인증에 멤버가 남기는 댓글. 작성자만 고치고 지우며, 방장은 지우기만 한다. (docs/adr/0002-verify-reaction-comment-tables.md)
// 인증이나 멤버를 지우면 DB가 함께 지운다. 이유는 HabitVerifyReaction과 같다.
@Entity
@Table(
    name = "habit_verify_comment",
    indexes = [
        Index(name = "idx_habit_verify_comment_verify", columnList = "habit_verify_id, create_date"),
        Index(name = "idx_habit_verify_comment_group_member", columnList = "group_member_id"),
    ],
)
class HabitVerifyComment private constructor(
    habitVerify: HabitVerify,
    groupMember: GroupMember,
    content: String,
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

    // 길이를 쓰지 않으면 255자 컬럼이 만들어져 500자까지 받는 요청 검증과 어긋난다.
    @Column(nullable = false, length = CONTENT_MAX_LENGTH)
    var content: String = requireValid(content)
        protected set

    fun edit(content: String) {
        this.content = requireValid(content)
    }

    companion object {
        const val CONTENT_MAX_LENGTH = EngagementRules.COMMENT_MAX_LENGTH

        fun create(habitVerify: HabitVerify, groupMember: GroupMember, content: String): HabitVerifyComment =
            HabitVerifyComment(habitVerify, groupMember, content)

        private fun requireValid(content: String): String {
            require(content.isNotBlank()) { "댓글 내용을 입력해주세요." }
            require(content.length <= CONTENT_MAX_LENGTH) { "댓글은 ${CONTENT_MAX_LENGTH}자 이하여야 합니다." }
            return content
        }
    }
}
