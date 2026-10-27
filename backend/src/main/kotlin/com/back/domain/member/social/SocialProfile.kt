package com.back.domain.member.social

// 소셜 로그인 공급자가 돌려준 사용자 정보. 이메일은 받지도 저장하지도 않는다.
data class SocialProfile(
    // 공급자 안에서 사용자를 구분하는 고정 값 (구글은 sub)
    val providerId: String,
    val nickname: String?,
)
