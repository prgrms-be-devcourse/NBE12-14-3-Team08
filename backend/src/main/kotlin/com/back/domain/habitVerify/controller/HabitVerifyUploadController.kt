package com.back.domain.habitVerify.controller

import com.back.domain.habitVerify.service.HabitVerifyService
import com.back.global.security.LoginMemberId
import com.back.global.storage.UploadUrlRequest
import com.back.global.storage.UploadUrlResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "습관 인증 API", description = "일반 유저의 일일 습관 인증 기록 등록, 목록 조회, 수정, 반려 건 재제출 및 삭제")
@RestController
@RequestMapping("/api/storage/habits")
class HabitVerifyUploadController(
    private val habitVerifyService: HabitVerifyService,
) {

    @Operation(
        summary = "습관 인증 사진 업로드용 서명 URL 발급",
        description = "습관 인증 사진을 스토리지(supabase)에 바로 업로드할 수 있는 Signed URL을 발급합니다.\n\n" +
            "- 본인의 진행 중인(ACTIVE) 습관에만 발급하며, 종료된 방에서는 발급할 수 없습니다.\n" +
            "- 허용 확장자: jpg, jpeg, png, webp",
    )
    @PostMapping("/{habitId}/upload-url")
    fun createUploadUrl(
        @LoginMemberId memberId: Long,
        @PathVariable habitId: Long,
        @Valid @RequestBody request: UploadUrlRequest,
    ): UploadUrlResponse =
        habitVerifyService.createUploadUrl(memberId, habitId, request.filename)
}
