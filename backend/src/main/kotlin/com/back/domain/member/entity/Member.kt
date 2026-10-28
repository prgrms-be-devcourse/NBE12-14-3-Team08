package com.back.domain.member.entity

import com.back.global.entity.BaseEntity
import com.back.global.entity.requiredId
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
// 같은 공급자의 같은 계정으로 회원이 둘 생기지 않게 한다. (아이디/비밀번호 회원은 provider_id가 비어 있어 제한받지 않는다)
@Table(uniqueConstraints = [UniqueConstraint(name = "uk_member_provider", columnNames = ["provider", "provider_id"])])
class Member private constructor(
    nickname: String,
    username: String,
    password: String,
    provider: MemberProvider,
    providerId: String?,
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

    // 가입 방식. 이 컬럼이 생기기 전의 기존 회원은 기본값 LOCAL이 채워진다. (ddl-auto: update로 컬럼을 추가해도 기존 행이 깨지지 않게 default를 둔다)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'LOCAL' not null")
    var provider: MemberProvider = provider
        protected set

    // 소셜 공급자 안에서의 사용자 식별자. 아이디/비밀번호 회원은 비어 있다.
    @Column(name = "provider_id", length = 100)
    var providerId: String? = providerId
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

    // 탈퇴한 회원은 행을 지우지 않고 익명화한다. (인증·벌칙 기록이 이 회원을 참조하고 있어 지우면 관계가 깨진다)
    // 닉네임은 방 기록에 그대로 남기고, 계정을 가리키는 값(아이디·비밀번호·소셜 식별자)은 지정한 값으로 바꾼다.
    // 그러면 같은 아이디와 같은 소셜 계정으로 새로 가입할 수 있다. 이미 탈퇴한 회원에게 다시 호출해도 같은 값이 된다.
    fun withdraw() {
        status = MemberStatus.WITHDRAWN
        refreshToken = null
        username = "$WITHDRAWN_USERNAME_PREFIX$requiredId"
        password = WITHDRAWN_PASSWORD
        provider = MemberProvider.LOCAL
        providerId = null
    }

    fun isSocial(): Boolean = provider != MemberProvider.LOCAL

    companion object {
        // 탈퇴한 회원의 아이디는 이 접두사 + 회원 id다. 가입 때 이 접두사로 시작하는 아이디를 막아, 탈퇴할 때 아이디가 겹치지 않게 한다.
        const val WITHDRAWN_USERNAME_PREFIX = "withdrawn_"

        // BCrypt 해시 형식이 아니라 어떤 입력과도 일치하지 않는다. (탈퇴 회원은 비밀번호 비교 전에 로그인을 거절한다)
        const val WITHDRAWN_PASSWORD = "!withdrawn"

        fun create(nickname: String, username: String, encodedPassword: String): Member =
            Member(nickname, username, encodedPassword, MemberProvider.LOCAL, null)

        // 소셜 로그인으로 처음 들어온 회원. 비밀번호로는 로그인할 수 없도록 아무도 모르는 값의 해시를 넣는다.
        @JvmStatic
        fun createSocial(
            provider: MemberProvider,
            providerId: String,
            nickname: String,
            username: String,
            encodedUnusablePassword: String,
        ): Member {
            require(provider != MemberProvider.LOCAL) { "소셜 로그인 공급자가 필요합니다." }
            return Member(nickname, username, encodedUnusablePassword, provider, providerId)
        }
    }
}
