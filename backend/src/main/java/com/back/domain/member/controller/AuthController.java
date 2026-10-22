package com.back.domain.member.controller;

import com.back.domain.member.dto.LoginRequest;
import com.back.domain.member.dto.LoginResponse;
import com.back.domain.member.dto.RefreshTokenRequest;
import com.back.domain.member.service.AuthTokenService;
import com.back.domain.member.service.MemberService;
import com.back.global.exception.UnauthorizedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증 API", description = "서비스 접근을 위한 사용자 로그인 및 JWT 엑세스 토큰 재발급")
@SecurityRequirements
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final MemberService memberService;
    private final AuthTokenService authTokenService;

    @Operation(
            summary = "사용자 로그인",
            description = "아이디와 비밀번호를 검증하여 서비스 접근용 인증 토큰을 발급합니다.\n\n" +
                    "- 검증 성공 시 엑세스 토큰(Access Token)과 리프레시 토큰(Refresh Token)을 반환합니다."
    )
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        // 아이디와 비밀번호를 검증
        Long memberId = memberService.authenticate(request);

        String accessToken = authTokenService.createAccessToken(memberId);

        String refreshToken = memberService.getRefreshToken(memberId);   // 로그인 시 DB에 저장된 리프레시 토큰 조회
        
        LoginResponse response = LoginResponse.of(
                accessToken,
                refreshToken
        );

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "인증 토큰 재발급",
            description = "만료된 엑세스 토큰을 대신해 리프레시 토큰을 검증하고 새로운 엑세스 토큰을 발급합니다.\n\n" +
                    "- 유효하지 않거나 DB에 저장된 값과 일치하지 않는 리프레시 토큰일 경우 401(Unauthorized) 에러를 반환합니다."
    )
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        String refreshToken = request.getRefreshToken();

        if (!authTokenService.validateToken(refreshToken)
                || !authTokenService.isRefreshToken(refreshToken)) {
            throw new UnauthorizedException(
                    "유효하지 않은 리프레시 토큰입니다."
            );
        }

        Long memberId =
                authTokenService.getMemberId(refreshToken);
        
        // 받은 리프레시 토큰과 DB 저장값 비교
        if (!memberService.matchesRefreshToken(
                memberId,
                refreshToken
        )) {
            throw new UnauthorizedException(
                    "저장된 리프레시 토큰과 일치하지 않습니다."
            );
        }

        String accessToken =
                authTokenService.createAccessToken(memberId);

        LoginResponse response = LoginResponse.of(
                accessToken,
                refreshToken
        );

        return ResponseEntity.ok(response);
    }
}