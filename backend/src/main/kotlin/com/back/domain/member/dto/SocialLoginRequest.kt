package com.back.domain.member.dto

import jakarta.validation.constraints.NotBlank

// 필드를 nullable로 두는 이유는 CreateMemberRequest와 같다.
data class SocialLoginRequest(
    // 공급자 로그인 화면에서 돌아올 때 받은 인가 코드
    @field:NotBlank(message = "인가 코드는 필수입니다.")
    val code: String? = null,

    // 로그인을 시작할 때 쓴 리다이렉트 주소. 코드를 받을 때와 정확히 같아야 한다.
    @field:NotBlank(message = "리다이렉트 주소는 필수입니다.")
    val redirectUri: String? = null,
)
