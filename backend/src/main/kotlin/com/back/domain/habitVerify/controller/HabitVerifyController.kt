package com.back.domain.habitVerify.controller

import com.back.domain.habitVerify.dto.HabitVerifyRequest
import com.back.domain.habitVerify.dto.HabitVerifyResponse
import com.back.domain.habitVerify.service.HabitVerifyService
import com.back.global.security.LoginMemberId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "습관 인증 API", description = "일반 유저의 일일 습관 인증 기록 등록, 목록 조회, 수정, 반려 건 재제출")
@RestController
@RequestMapping("/api/habits/{habitId}/verifications")
class HabitVerifyController(
    private val habitVerifyService: HabitVerifyService,
) {

    @Operation(
        summary = "습관 인증 등록",
        description = "특정 습관에 대한 실천 인증 기록을 새로 등록합니다. (그룹 가입자만 가능)\n\n" +
            "- 오늘 날짜의 실천 내용, 사진 URL 등을 첨부하여 검토 대기(PENDING) 상태로 제출합니다.",
    )
    @PostMapping
    fun create(
        @PathVariable habitId: Long,
        @Valid @RequestBody request: HabitVerifyRequest,
        @LoginMemberId memberId: Long,
    ): ResponseEntity<HabitVerifyResponse> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(habitVerifyService.create(habitId, request, memberId))

    @Operation(
        summary = "습관 인증 목록 조회",
        description = "특정 습관에 누적된 전체 인증 기록 목록을 조회합니다. (그룹 가입자만 조회 가능)",
    )
    @GetMapping
    fun findAll(
        @PathVariable habitId: Long,
        @LoginMemberId memberId: Long,
    ): ResponseEntity<List<HabitVerifyResponse>> =
        ResponseEntity.ok(habitVerifyService.findAll(habitId, memberId))

    @Operation(
        summary = "반려된 습관 인증 재제출",
        description = "방장에게 반려(REJECTED)된 인증을 보완해 다시 제출합니다. (본인만 가능)\n\n" +
            "- 그 인증이 속한 주의 마감 전까지만 재제출할 수 있습니다.\n" +
            "- 사진을 바꾸지 않으면 지금 저장된 사진 URL을 그대로 보내면 됩니다.",
    )
    @PatchMapping("/{verificationId}/resubmit")
    fun resubmit(
        @PathVariable habitId: Long,
        @PathVariable verificationId: Long,
        @Valid @RequestBody request: HabitVerifyRequest,
        @LoginMemberId memberId: Long,
    ): ResponseEntity<HabitVerifyResponse> =
        ResponseEntity.ok(habitVerifyService.resubmit(habitId, verificationId, request, memberId))
}
