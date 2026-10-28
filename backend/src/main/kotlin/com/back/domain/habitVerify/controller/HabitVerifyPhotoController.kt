package com.back.domain.habitVerify.controller

import com.back.domain.habitVerify.dto.HabitVerifyPhotoPage
import com.back.domain.habitVerify.service.HabitVerifyEngagementService
import com.back.domain.habitVerify.service.HabitVerifyPhotoService
import com.back.global.security.LoginMemberId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "그룹 인증 사진 API", description = "그룹 메인 페이지의 인증 사진 갤러리 조회")
@RestController
@RequestMapping("/api")
class HabitVerifyPhotoController(
    private val habitVerifyPhotoService: HabitVerifyPhotoService,
    private val habitVerifyEngagementService: HabitVerifyEngagementService,
) {

    @Operation(
        summary = "그룹 인증 사진 목록 조회",
        description = "그룹에서 승인된 습관 인증 중 사진이 있는 것을 최신순으로 조회합니다. (그룹 멤버만 조회 가능)\n\n" +
            "- 인증 날짜 내림차순, 같은 날짜는 id 내림차순입니다.\n" +
            "- 커서 방식입니다. 응답의 nextCursor를 다음 요청의 cursor로 그대로 보내며, null이면 마지막 페이지입니다.\n" +
            "- size는 1~120(기본 60), memberId를 주면 해당 회원의 인증만 조회합니다.\n" +
            "- includeEngagement=true를 주면 각 사진에 하트 개수(reactionCount), 댓글 개수(commentCount), " +
            "내가 하트를 눌렀는지(reactedByMe)가 더해집니다. 주지 않으면 이 필드들은 응답에 없습니다.",
    )
    @GetMapping("/groups/{groupId}/habits/verifications/photos")
    fun getPhotos(
        @LoginMemberId loginMemberId: Long,
        @PathVariable groupId: Long,
        @RequestParam(defaultValue = "${HabitVerifyPhotoService.DEFAULT_SIZE}") size: Int,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(name = "memberId", required = false) filterMemberId: Long?,
        @RequestParam(defaultValue = "false") includeEngagement: Boolean,
    ): HabitVerifyPhotoPage {
        val page = habitVerifyPhotoService.getPhotos(loginMemberId, groupId, size, cursor, filterMemberId)

        return if (includeEngagement) habitVerifyEngagementService.withCounts(loginMemberId, groupId, page) else page
    }
}
