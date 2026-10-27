package com.back.global.exception

// 소셜 로그인 공급자와 통신하지 못했거나 서버에 소셜 로그인 설정이 없을 때 사용 (502 BAD_GATEWAY)
class SocialAuthUnavailableException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
