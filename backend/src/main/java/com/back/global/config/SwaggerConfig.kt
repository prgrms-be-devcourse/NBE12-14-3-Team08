package com.back.global.config

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityScheme
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@OpenAPIDefinition(
    security = [SecurityRequirement(name = "bearerAuth")],
    info = Info(
        title = "Habit Challenge API",
        version = "v1",
        description = "습관 챌린지 인증 서비스 API"
    )
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT"
)
class SwaggerConfig {

    @Bean
    fun authAndUserApi(): GroupedOpenApi =
        GroupedOpenApi.builder()
            .group("01. 회원 및 인증 (Auth & Member)")
            .pathsToMatch("/api/auth/**", "/api/members/**")
            .build()

    @Bean
    fun challengeApi(): GroupedOpenApi =
        GroupedOpenApi.builder()
            .group("02. 챌린지 및 인증 (Groups & Verify)")
            .pathsToMatch(
                "/api/groups/**",
                "/api/habits/**",
                "/api/penalties/**"
            )
            .build()
}