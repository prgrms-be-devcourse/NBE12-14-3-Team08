package com.back.domain.habitVerify.controller

import com.back.domain.habitVerify.service.HabitVerifyCommentService
import com.back.domain.habitVerify.service.HabitVerifyReactionService
import com.back.domain.verifyEngagement.dto.CommentPage
import com.back.domain.verifyEngagement.dto.CommentRequest
import com.back.domain.verifyEngagement.dto.CommentResponse
import com.back.domain.verifyEngagement.dto.ReactionResponse
import com.back.global.security.LoginMemberId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "습관 인증 반응·댓글 API", description = "습관 인증에 하트를 누르거나 댓글을 남기고 읽기")
@RestController
@RequestMapping("/api/habits/verifications/{verificationId}")
class HabitVerifyEngagementController(
    private val reactionService: HabitVerifyReactionService,
    private val commentService: HabitVerifyCommentService,
) {

    @Operation(
        summary = "하트 상태 조회",
        description = "이 인증의 하트 개수와 내가 눌렀는지를 조회합니다. (인증이 속한 그룹의 멤버만 가능)",
    )
    @GetMapping("/reactions")
    fun getReaction(
        @PathVariable verificationId: Long,
        @LoginMemberId memberId: Long,
    ): ResponseEntity<ReactionResponse> =
        ResponseEntity.ok(reactionService.get(verificationId, memberId))

    @Operation(
        summary = "하트 누르기",
        description = "인증에 하트를 눌러 둔 상태로 만듭니다. 이미 눌렀거나 동시에 여러 번 보내도 결과가 같습니다.\n\n" +
            "- 활동 중인 멤버가 진행 중인 그룹에서 승인된 인증에만 누를 수 있습니다.\n" +
            "- 본인 인증에는 누를 수 없습니다.",
    )
    @PutMapping("/reactions")
    fun react(
        @PathVariable verificationId: Long,
        @LoginMemberId memberId: Long,
    ): ResponseEntity<ReactionResponse> =
        ResponseEntity.ok(reactionService.react(verificationId, memberId))

    @Operation(
        summary = "하트 취소",
        description = "내가 누른 하트를 취소합니다. 누른 적이 없어도 성공으로 봅니다. (활동 중인 멤버가 진행 중인 그룹에서만 가능)",
    )
    @DeleteMapping("/reactions")
    fun cancelReaction(
        @PathVariable verificationId: Long,
        @LoginMemberId memberId: Long,
    ): ResponseEntity<ReactionResponse> =
        ResponseEntity.ok(reactionService.cancel(verificationId, memberId))

    @Operation(
        summary = "댓글 목록 조회",
        description = "인증의 댓글을 오래된 순으로 조회합니다. (인증이 속한 그룹의 멤버만 가능, 나간 멤버도 읽을 수 있습니다)\n\n" +
            "- 커서 방식입니다. 응답의 nextCursor를 다음 요청의 cursor로 그대로 보내며, null이면 마지막 페이지입니다.\n" +
            "- size는 1~100(기본 30)입니다. 방을 나간 멤버의 댓글은 작성자가 \"탈퇴한 멤버\"로 내려옵니다.",
    )
    @GetMapping("/comments")
    fun listComments(
        @PathVariable verificationId: Long,
        @RequestParam(required = false) cursor: Long?,
        @RequestParam(defaultValue = "${HabitVerifyCommentService.DEFAULT_SIZE}") size: Int,
        @LoginMemberId memberId: Long,
    ): ResponseEntity<CommentPage> =
        ResponseEntity.ok(commentService.list(verificationId, memberId, cursor, size))

    @Operation(
        summary = "댓글 작성",
        description = "인증에 댓글을 남깁니다. 최대 500자입니다.\n\n" +
            "- 활동 중인 멤버가 진행 중인 그룹에서 승인된 인증에만 남길 수 있습니다. 본인 인증에도 남길 수 있습니다.",
    )
    @PostMapping("/comments")
    fun createComment(
        @PathVariable verificationId: Long,
        @Valid @RequestBody request: CommentRequest,
        @LoginMemberId memberId: Long,
    ): ResponseEntity<CommentResponse> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(commentService.create(verificationId, memberId, request))

    @Operation(summary = "댓글 수정", description = "내가 쓴 댓글의 내용을 고칩니다. (작성자만 가능, 진행 중인 그룹에서만)")
    @PatchMapping("/comments/{commentId}")
    fun editComment(
        @PathVariable verificationId: Long,
        @PathVariable commentId: Long,
        @Valid @RequestBody request: CommentRequest,
        @LoginMemberId memberId: Long,
    ): ResponseEntity<CommentResponse> =
        ResponseEntity.ok(commentService.edit(verificationId, commentId, memberId, request))

    @Operation(summary = "댓글 삭제", description = "댓글을 지웁니다. (작성자와 방장만 가능, 진행 중인 그룹에서만)")
    @DeleteMapping("/comments/{commentId}")
    fun deleteComment(
        @PathVariable verificationId: Long,
        @PathVariable commentId: Long,
        @LoginMemberId memberId: Long,
    ): ResponseEntity<Void> {
        commentService.delete(verificationId, commentId, memberId)
        return ResponseEntity.noContent().build()
    }
}
