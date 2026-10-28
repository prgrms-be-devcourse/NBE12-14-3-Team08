package com.back.domain.verifyEngagement.dto

import com.back.domain.verifyEngagement.service.EngagementRules
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

// 습관 인증과 벌칙 인증의 반응·댓글이 함께 쓰는 요청·응답. (docs/adr/0002-verify-reaction-comment-tables.md)

// 내가 이 인증에 하트를 눌렀는지와 하트 개수
data class ReactionResponse(
    val reacted: Boolean,
    val reactionCount: Long,
)

// content가 nullable인 이유: null이어도 객체는 만들어지고 @NotBlank 검증 오류(400)로 응답해야 한다
// 길이는 DB 컬럼 길이(댓글 content: 500자)와 맞춘다. 검증이 없으면 저장할 때 DB 오류가 그대로 올라와 500이 된다.
data class CommentRequest(
    @field:NotBlank(message = "댓글 내용을 입력해주세요.")
    @field:Size(max = EngagementRules.COMMENT_MAX_LENGTH, message = "댓글은 500자 이하여야 합니다.")
    val content: String?,
)

data class CommentResponse(
    val id: Long,
    // 방을 나간 멤버의 댓글은 작성자를 알려 주지 않는다. (authorMemberId는 null, 닉네임은 "탈퇴한 멤버")
    val authorMemberId: Long?,
    val authorNickname: String,
    val content: String,
    val createDate: LocalDateTime,
    // 내용을 고친 적이 있으면 true
    val edited: Boolean,
    // 내가 쓴 댓글인지. 수정은 작성자만, 삭제는 작성자와 방장만 할 수 있다. (방이 종료되었거나 쓰기 권한이 없으면 서버가 거절한다)
    val mine: Boolean,
    val canEdit: Boolean,
    val canDelete: Boolean,
)

// nextCursor가 null이면 마지막 페이지다. 다음 요청에는 이 값을 그대로 cursor로 보낸다.
data class CommentPage(
    val items: List<CommentResponse>,
    val nextCursor: Long?,
    val totalCount: Long,
)
