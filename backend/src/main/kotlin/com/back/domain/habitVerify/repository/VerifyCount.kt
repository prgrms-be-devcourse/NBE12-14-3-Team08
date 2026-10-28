package com.back.domain.habitVerify.repository

// 인증 여러 건의 반응·댓글 개수를 한 번에 세는 쿼리의 결과 한 줄. 습관 인증과 벌칙 인증이 함께 쓴다.
interface VerifyCount {
    val targetId: Long
    val total: Long
}
