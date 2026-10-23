package com.back.domain.habitVerify.controller

import com.back.domain.habitVerify.dto.BulkActionRequest
import com.back.domain.habitVerify.dto.HabitVerifyResponse
import com.back.domain.habitVerify.dto.HabitVerifySummaryResponse
import com.back.domain.habitVerify.service.HabitVerifyService
import com.back.global.security.LoginMemberId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "습관 인증 관리자 API", description = "방장 권한의 멤버 인증 대기 목록 조회, 단건 및 일괄 승인/반려 심사")
@RestController
@RequestMapping("/api")
class HabitVerifyAdminController(
    private val habitVerifyService: HabitVerifyService,
) {

    @Operation(
        summary = "검토 대기 중인 인증 목록 조회",
        description = "특정 그룹에서 멤버들이 제출한 인증 요청 중 검토 대기(PENDING) 상태인 목록을 조회합니다. (그룹 방장만 조회 가능)\n\n" +
            "- 방장이 승인 또는 반려를 결정해야 하는 주간 정산 전의 인증 건들을 리스트로 반환합니다.",
    )
    @GetMapping("/groups/{groupId}/habits/verifications/pending")
    fun getPending(
        @LoginMemberId memberId: Long,
        @PathVariable groupId: Long,
    ): List<HabitVerifySummaryResponse> =
        habitVerifyService.getPendingByGroup(memberId, groupId)

    @Operation(
        summary = "단건 인증 승인",
        description = "멤버가 제출한 특정 습관 인증 요청을 개별 승인합니다. (그룹 방장만 가능)\n\n" +
            "- 승인 완료(APPROVED) 상태로 변경되며, 해당 주차의 주간 정산 시 인정 횟수에 포함됩니다.",
    )
    @PatchMapping("/habits/verifications/{id}/approve")
    fun approve(
        @LoginMemberId memberId: Long,
        @PathVariable id: Long,
    ): HabitVerifyResponse =
        habitVerifyService.approve(memberId, id)

    @Operation(
        summary = "단건 인증 반려",
        description = "멤버가 제출한 특정 습관 인증 요청을 개별 반려(거절)합니다. (그룹 방장만 가능)\n\n" +
            "- 반려 완료(REJECTED) 상태로 변경되며, 해당 인증은 이번 주차 실천 횟수에서 제외됩니다.",
    )
    @PatchMapping("/habits/verifications/{id}/reject")
    fun reject(
        @LoginMemberId memberId: Long,
        @PathVariable id: Long,
    ): HabitVerifyResponse =
        habitVerifyService.reject(memberId, id)

    @Operation(
        summary = "인증 목록 일괄 승인",
        description = "특정 그룹 내 여러 건의 검토 대기 인증 요청을 한 번에 일괄 승인 처리합니다. (그룹 방장만 가능)\n\n" +
            "- 요청 바디(BulkActionRequest)에 포함된 인증 ID 목록 전체를 APPROVED 상태로 일괄 변경합니다.",
    )
    @PatchMapping("/groups/{groupId}/habits/verifications/bulk-approve")
    fun bulkApprove(
        @LoginMemberId memberId: Long,
        @PathVariable groupId: Long,
        @RequestBody request: BulkActionRequest,
    ): List<HabitVerifyResponse> =
        habitVerifyService.bulkApprove(memberId, groupId, request)

    @Operation(
        summary = "인증 목록 일괄 반려",
        description = "특정 그룹 내 여러 건의 검토 대기 인증 요청을 한 번에 일괄 반려 처리합니다. (그룹 방장만 가능)\n\n" +
            "- 요청 바디(BulkActionRequest)에 포함된 인증 ID 목록 전체를 REJECTED 상태로 일괄 변경합니다.",
    )
    @PatchMapping("/groups/{groupId}/habits/verifications/bulk-reject")
    fun bulkReject(
        @LoginMemberId memberId: Long,
        @PathVariable groupId: Long,
        @RequestBody request: BulkActionRequest,
    ): List<HabitVerifyResponse> =
        habitVerifyService.bulkReject(memberId, groupId, request)
}
