package com.back.domain.member.dto

// 메인(방 목록) 화면 상단의 내 요약 수치
data class MemberSummaryResponse(
    // 이번 주 승인된 인증 수와 목표 횟수(습관의 주당 횟수)를, 참여 중인 진행 중인 방의 활성 습관 전체에서 합한 값
    val weeklyApprovedCount: Int,
    val weeklyGoalCount: Int,
    // 방과 상태를 가리지 않고 내가 받은 벌칙 인증 수
    val penaltyCount: Long,
)
