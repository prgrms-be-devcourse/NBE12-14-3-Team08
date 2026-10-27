package com.back.domain.member.controller

import com.back.domain.member.dto.LoginRequest
import com.back.domain.member.dto.LoginResponse
import com.back.domain.member.dto.RefreshTokenRequest
import com.back.domain.member.dto.SocialLoginRequest
import com.back.domain.member.service.AuthTokenService
import com.back.domain.member.service.MemberService
import com.back.domain.member.social.SocialAuthService
import com.back.global.exception.UnauthorizedException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "인증 API", description = "서비스 접근을 위한 사용자 로그인 및 JWT 엑세스 토큰 재발급")
@SecurityRequirements
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val memberService: MemberService,
    private val authTokenService: AuthTokenService,
    private val socialAuthService: SocialAuthService,
) {

    @Operation(
        summary = "사용자 로그인",
        description = "아이디와 비밀번호를 검증하여 서비스 접근용 인증 토큰을 발급합니다.\n\n" +
            "- 검증 성공 시 엑세스 토큰(Access Token)과 리프레시 토큰(Refresh Token)을 반환합니다.",
    )
    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
    ): ResponseEntity<LoginResponse> {
        // 아이디와 비밀번호를 검증
        val memberId = memberService.authenticate(request)

        val accessToken = authTokenService.createAccessToken(memberId)

        // 로그인 시 DB에 저장된 리프레시 토큰 조회
        val refreshToken = memberService.getRefreshToken(memberId)

        return ResponseEntity.ok(LoginResponse.of(accessToken, refreshToken))
    }

    @Operation(
        summary = "소셜 로그인",
        description = "소셜 공급자(현재 google)의 로그인 화면에서 받은 인가 코드로 로그인합니다.\n\n" +
            "- 처음 로그인하는 계정이면 회원이 자동으로 만들어지고, 이미 가입한 계정이면 그 회원으로 로그인합니다.\n" +
            "- 이메일이 같아도 아이디/비밀번호 회원이나 다른 공급자의 회원과 합쳐지지 않습니다.\n" +
            "- 성공 시 일반 로그인과 같은 엑세스 토큰과 리프레시 토큰을 반환합니다.\n" +
            "- 코드가 잘못됐거나 만료됐으면 401, 공급자와 통신하지 못하면 502를 반환합니다.",
    )
    @PostMapping("/social/{provider}")
    fun socialLogin(
        @PathVariable provider: String,
        @Valid @RequestBody request: SocialLoginRequest,
    ): ResponseEntity<LoginResponse> {
        // @NotBlank 검증을 통과한 요청이지만 타입상 nullable이라 한 번 더 확인한다.
        val code = requireNotNull(request.code) { "인가 코드는 필수입니다." }
        val redirectUri = requireNotNull(request.redirectUri) { "리다이렉트 주소는 필수입니다." }

        val memberId = socialAuthService.login(provider, code, redirectUri)

        val accessToken = authTokenService.createAccessToken(memberId)
        val refreshToken = memberService.getRefreshToken(memberId)

        return ResponseEntity.ok(LoginResponse.of(accessToken, refreshToken))
    }

    @Operation(
        summary = "인증 토큰 재발급",
        description = "만료된 엑세스 토큰을 대신해 리프레시 토큰을 검증하고 새로운 엑세스 토큰을 발급합니다.\n\n" +
            "- 유효하지 않거나 DB에 저장된 값과 일치하지 않는 리프레시 토큰일 경우 401(Unauthorized) 에러를 반환합니다.",
    )
    @PostMapping("/refresh")
    fun refresh(
        @Valid @RequestBody request: RefreshTokenRequest,
    ): ResponseEntity<LoginResponse> {
        // @NotBlank 검증을 통과한 요청이지만 타입상 nullable이라 한 번 더 확인한다.
        val refreshToken = request.refreshToken
            ?: throw UnauthorizedException("유효하지 않은 리프레시 토큰입니다.")

        if (!authTokenService.validateToken(refreshToken) ||
            !authTokenService.isRefreshToken(refreshToken)
        ) {
            throw UnauthorizedException("유효하지 않은 리프레시 토큰입니다.")
        }

        val memberId = authTokenService.getMemberId(refreshToken)

        // 받은 리프레시 토큰과 DB 저장값 비교
        if (!memberService.matchesRefreshToken(memberId, refreshToken)) {
            throw UnauthorizedException("저장된 리프레시 토큰과 일치하지 않습니다.")
        }

        val accessToken = authTokenService.createAccessToken(memberId)

        return ResponseEntity.ok(LoginResponse.of(accessToken, refreshToken))
    }
}
