package com.back.domain.member.controller

import com.back.domain.member.dto.CreateMemberRequest
import com.back.domain.member.dto.MemberResponse
import com.back.domain.member.dto.MemberSummaryResponse
import com.back.domain.member.dto.UpdateMemberRequest
import com.back.domain.member.service.MemberService
import com.back.domain.member.service.MemberSummaryService
import com.back.global.security.LoginMemberId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "회원 API", description = "새로운 회원 가입 및 로그인한 본인의 프로필 정보 조회, 수정, 탈퇴 계정 관리")
@RestController
@RequestMapping("/api/members")
class MemberController(
    private val memberService: MemberService,
    private val memberSummaryService: MemberSummaryService,
) {

    @Operation(
        summary = "회원 가입",
        description = "서비스 이용을 위한 새로운 회원 계정을 등록합니다.\n\n" +
            "- 회원 가입 성공 시 생성된 회원 프로필 정보를 반환합니다.",
    )
    @SecurityRequirements
    @PostMapping
    fun join(
        @Valid @RequestBody request: CreateMemberRequest,
    ): ResponseEntity<MemberResponse> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(memberService.join(request))

    @Operation(
        summary = "내 정보 조회",
        description = "현재 로그인한 사용자의 회원 프로필 정보를 조회합니다. (본인만 가능)",
    )
    @GetMapping("/me")
    fun getMyInfo(
        @LoginMemberId memberId: Long,
    ): ResponseEntity<MemberResponse> =
        ResponseEntity.ok(memberService.getMember(memberId))

    @Operation(
        summary = "내 요약 조회",
        description = "메인(방 목록) 화면 상단에 보여 줄 내 수치를 조회합니다. (본인만 가능)\n\n" +
            "- weeklyApprovedCount / weeklyGoalCount: 참여 중인 진행 중인 방의 활성 습관 전체에서, 이번 주(방을 만든 요일부터 7일) 승인된 인증 수와 목표 횟수의 합입니다.\n" +
            "- penaltyCount: 방과 상태를 가리지 않고 내가 받은 벌칙 인증 수입니다.",
    )
    @GetMapping("/me/summary")
    fun getMySummary(
        @LoginMemberId memberId: Long,
    ): ResponseEntity<MemberSummaryResponse> =
        ResponseEntity.ok(memberSummaryService.getSummary(memberId))

    @Operation(
        summary = "내 정보 수정",
        description = "현재 로그인한 사용자의 닉네임, 비밀번호 등 회원 정보를 수정합니다. (본인만 가능)",
    )
    @PatchMapping("/me")
    fun updateMyInfo(
        @LoginMemberId memberId: Long,
        @Valid @RequestBody request: UpdateMemberRequest,
    ): ResponseEntity<MemberResponse> =
        ResponseEntity.ok(memberService.updateMember(memberId, request))

    @Operation(
        summary = "회원 탈퇴",
        description = "현재 로그인한 사용자의 회원 계정을 삭제합니다. (본인만 가능)",
    )
    @DeleteMapping("/me")
    fun deleteMyAccount(
        @LoginMemberId memberId: Long,
    ): ResponseEntity<Void> {
        memberService.deleteMember(memberId)

        return ResponseEntity.noContent().build()
    }
}
