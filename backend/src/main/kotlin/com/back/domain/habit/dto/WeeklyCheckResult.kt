package com.back.domain.habit.dto

enum class WeeklyCheckResult {
    TOO_EARLY,                        // 가입 1주 미만 - 판정 대상 아님
    ALREADY_FAILED,                   // 이미 실패 처리된 습관 - 재판정 없음
    GROUP_FINISHED,                   // 이미 종료된 그룹 - 판정하지 않음
    BEFORE_DEADLINE,                  // 습관이 이번 판정 구간의 마감 이후에 생성됨 - 아직 판정 대상 아님
    FIRST_WEEK_EXEMPT,                // 습관을 만든 뒤 처음 맞는 부분 주 - 판정 면제, 다음 온전한 주부터 판정
    PASSED,                          // 검토 완료 + 승인된 인증 횟수가 days 이상 - 실패 아님
    PENDING_REVIEW,                   // 미검토(PENDING) 건이 남아있고 검토 유예시간도 아직 안 지남 - 방장 검토 대기
    FAILED_INSUFFICIENT_SUBMISSION,   // 검토 완료 후에도 승인 횟수가 days 미달 - 자동 실패 및 벌칙 확정
}