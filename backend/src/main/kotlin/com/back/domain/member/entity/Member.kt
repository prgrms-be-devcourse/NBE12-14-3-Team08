package com.back.domain.member.entity

import com.back.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated

@Entity
class Member private constructor(
    nickname: String,
    username: String,
    password: String,
) : BaseEntity() {

    @Column(nullable = false, unique = true)
    var username: String = username
        protected set

    @Column(nullable = false)
    var nickname: String = nickname
        protected set

    @Column(nullable = false)
    var password: String = password
        protected set

    @Column(name = "refresh_token", unique = true, length = 512)
    var refreshToken: String? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: MemberStatus = MemberStatus.ACTIVE
        protected set

    fun updateNickname(nickname: String) {
        this.nickname = nickname
    }

    fun updatePassword(encodedPassword: String) {
        this.password = encodedPassword
    }

    // 회원가입 시 생성된 리프레시 토큰 저장
    fun updateRefreshToken(refreshToken: String?) {
        this.refreshToken = refreshToken
    }

    // 재발급 요청의 리프레시 토큰과 DB 저장값 비교
    fun matchesRefreshToken(refreshToken: String?): Boolean =
        this.refreshToken != null && this.refreshToken == refreshToken

    fun withdraw() {
        status = MemberStatus.WITHDRAWN
        refreshToken = null
    }

    companion object {
        @JvmStatic
        fun create(nickname: String, username: String, encodedPassword: String): Member =
            Member(nickname, username, encodedPassword)
    }
}
