package com.back.global.security

import com.back.domain.member.entity.MemberStatus
import com.back.domain.member.repository.MemberRepository
import com.back.domain.member.service.AuthTokenService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class CustomAuthenticationFilter(
    private val authTokenService: AuthTokenService,
    private val memberRepository: MemberRepository,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val accessToken = resolveAccessToken(request)

        if (accessToken != null &&
            authTokenService.validateToken(accessToken) &&
            authTokenService.isAccessToken(accessToken)
        ) {
            val memberId = authTokenService.getMemberId(accessToken)

            if (memberRepository.existsByIdAndStatus(memberId, MemberStatus.ACTIVE)) {
                request.setAttribute("loginMemberId", memberId)

                val authentication = UsernamePasswordAuthenticationToken(memberId, null, emptyList())

                SecurityContextHolder.getContext().authentication = authentication
            }
        }

        filterChain.doFilter(request, response)
    }

    private fun resolveAccessToken(request: HttpServletRequest): String? {
        val authorizationHeader = request.getHeader(AUTHORIZATION_HEADER)

        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return null
        }

        return authorizationHeader.substring(BEARER_PREFIX.length)
    }

    companion object {
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
    }
}
