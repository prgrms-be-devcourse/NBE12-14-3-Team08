package com.back.domain.member.social

import com.back.domain.member.entity.MemberProvider

// 소셜 로그인 공급자 한 곳과 통신하는 클라이언트. 공급자를 늘릴 때는 이 인터페이스를 구현한 빈을 추가한다.
interface SocialOAuthClient {

    val provider: MemberProvider

    // 인가 코드를 공급자에게 토큰으로 바꾸고, 그 토큰으로 사용자 정보를 가져온다.
    // 코드가 잘못됐거나 만료됐으면 UnauthorizedException, 공급자와 통신하지 못하면 SocialAuthUnavailableException
    fun fetchProfile(code: String, redirectUri: String): SocialProfile
}
