package com.back.global.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
class SecurityConfig(
    private val customAuthenticationFilter: CustomAuthenticationFilter,
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    // 기존 Java 설정을 그대로 옮겼다. (경로 규칙은 위에서 아래로 먼저 맞는 것이 적용되므로 순서를 바꾸지 않는다)
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .cors(Customizer.withDefaults())
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .headers { headers ->
                headers.frameOptions { frame -> frame.sameOrigin() }
            }
            .sessionManagement { session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/members",
                        "/api/auth/login",
                        "/api/auth/refresh",
                    ).permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/groups/invite/**").authenticated()
                    .requestMatchers(
                        "/",
                        "/actuator/health",
                        "/api/connection-test",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                    ).permitAll()
                    .anyRequest().authenticated()
            }
            // 인증 정보가 없거나 토큰이 만료·위조된 경우 401로 응답한다.
            // (기본값은 403이라 프론트가 401을 보고 수행하는 토큰 자동 갱신이 동작하지 않는다)
            .exceptionHandling { exception ->
                exception.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            }
            .addFilterBefore(customAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()
}
