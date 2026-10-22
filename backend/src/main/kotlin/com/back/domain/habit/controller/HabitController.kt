package com.back.domain.habit.controller

import com.back.domain.habit.dto.CreateHabitRequest
import com.back.domain.habit.dto.HabitResponse
import com.back.domain.habit.dto.HabitWeeklyCheckResponse
import com.back.domain.habit.service.HabitService
import com.back.global.security.LoginMemberId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "습관 API", description = "그룹 내 실천 습관 생성, 상세 조회, 수동 검증 처리 및 주간 정산 판정")
@RestController
@RequestMapping("/api")
@SecurityRequirement(name = "bearerAuth")
class HabitController(
    private val habitService: HabitService,
) {

    @Operation(
        summary = "그룹 습관 생성",
        description = "활성화된 특정 그룹 내에서 실천할 새로운 습관을 생성합니다. (그룹 가입자만 가능)\n\n" +
            "- 한 멤버당 하나의 활성화(ACTIVE)된 습관만 가질 수 있습니다.",
    )
    @PostMapping("/groups/{groupId}/habits")
    fun createHabit(
        @LoginMemberId memberId: Long,
        @PathVariable groupId: Long,
        @Valid @RequestBody request: CreateHabitRequest,
    ): HabitResponse = habitService.createHabit(groupId, memberId, request)

    @Operation(
        summary = "습관 실패 처리",
        description = "진행 중인 특정 습관을 포기 또는 실패 처리합니다. (그룹 가입자만 가능)\n\n" +
            "- 해당 습관의 상태가 즉시 실패(FAIL)로 전환됩니다.",
    )
    @PostMapping("/habits/fail/{habitId}")
    fun failHabit(
        @LoginMemberId memberId: Long,
        @PathVariable habitId: Long,
    ): ResponseEntity<Void> {
        habitService.failHabit(memberId, habitId)

        return ResponseEntity.ok().build()
    }

    @Operation(
        summary = "습관 상세 조회",
        description = "특정 습관의 상세 정보를 조회합니다.\n\n" +
            "- 습관 ID 기준 기본 정보 및 달성 현황을 반환합니다.",
    )
    @GetMapping("/habits/{id}")
    fun detail(@LoginMemberId memberId: Long, @PathVariable id: Long): HabitResponse =
        habitService.getHabit(memberId, id)

    @Operation(
        summary = "그룹 내 활성화된 습관 조회",
        description = "특정 그룹에서 현재 진행 중인 습관을 조회합니다. (그룹 가입자만 조회 가능)",
    )
    @GetMapping("/groups/{groupId}/habits/active")
    fun getActiveHabit(
        @LoginMemberId memberId: Long,
        @PathVariable groupId: Long,
    ): ResponseEntity<HabitResponse> =
        ResponseEntity.ok(habitService.getActiveHabit(memberId, groupId))

    @Operation(
        summary = "그룹 내 실패한 습관 목록 조회",
        description = "특정 그룹에서 실패 처리된 누적 습관 목록을 조회합니다. (그룹 가입자만 조회 가능)",
    )
    @GetMapping("/groups/{groupId}/habits/fail")
    fun getFailedHabits(
        @LoginMemberId memberId: Long,
        @PathVariable groupId: Long,
    ): ResponseEntity<List<HabitResponse>> =
        ResponseEntity.ok(habitService.getFailedHabits(memberId, groupId))

    @Operation(
        summary = "그룹 주간 실패 여부 체크 및 정산",
        description = "특정 그룹의 주간 습관 실천 상태를 검증하고 실패 여부를 시스템에 반영합니다. (그룹 방장만 가능)\n\n" +
            "- 그룹 내 모든 활성화(ACTIVE)된 습관들을 전수 조사하여 주간 목표 달성 여부를 판정합니다.\n" +
            "- 미검토(PENDING) 인증 건이 있더라도 '방장 검토 유예시간(마감 후 24시간)'이 지났다면 시스템이 자동 승인 처리 후 판정을 강제 진행합니다.\n" +
            "- 매일 00시 05분 배치 스케줄러에 의해 자동 정산이 수행되므로, 이 API는 방장이 수동으로 즉시 정산하고자 할 때 호출합니다.",
    )
    @PostMapping("/groups/{groupId}/weekly-check")
    fun checkWeeklyFailureForGroup(
        @LoginMemberId memberId: Long,
        @PathVariable groupId: Long,
    ): ResponseEntity<List<HabitWeeklyCheckResponse>> =
        ResponseEntity.ok(habitService.checkWeeklyFailureForGroup(memberId, groupId))
}