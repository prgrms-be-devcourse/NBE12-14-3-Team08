package com.back.global.exception

import org.springframework.security.access.AccessDeniedException

// require(IllegalArgumentException), check(IllegalStateException)와 같은 모양의 검증 함수.
// 조건이 참이어야 하고, 거짓이면 해당 예외로 응답 상태 코드가 정해진다.

// 비즈니스 규칙 위반 (400)
inline fun requireBusinessRule(value: Boolean, lazyMessage: () -> String) {
    if (!value) throw BusinessRuleException(lazyMessage())
}

// 권한 없음 (403)
inline fun requireAllowed(value: Boolean, lazyMessage: () -> String) {
    if (!value) throw ForbiddenException(lazyMessage())
}

// 접근 거부 (403, Spring Security의 AccessDeniedException: 그룹 멤버·방장 확인 등)
inline fun requireAccess(value: Boolean, lazyMessage: () -> String) {
    if (!value) throw AccessDeniedException(lazyMessage())
}
