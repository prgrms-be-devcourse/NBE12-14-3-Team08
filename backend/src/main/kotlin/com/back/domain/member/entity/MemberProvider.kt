package com.back.domain.member.entity

// 회원이 가입·로그인한 방식. LOCAL은 아이디/비밀번호, 나머지는 소셜 로그인이다.
enum class MemberProvider {
    LOCAL,
    GOOGLE,
}
