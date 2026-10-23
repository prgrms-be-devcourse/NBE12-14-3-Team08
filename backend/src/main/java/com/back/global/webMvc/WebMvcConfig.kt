package com.back.global.webMvc

import com.back.global.security.LoginMemberArgumentResolver
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebMvcConfig(
    private val loginMemberArgumentResolver: LoginMemberArgumentResolver
) : WebMvcConfigurer {

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**")
            .allowedOriginPatterns(
                "http://localhost:3000",
                "https://*.vercel.app",
                "https://cdpn.io"
            )
            .allowedMethods(
                "GET",
                "POST",
                "PUT",
                "DELETE",
                "PATCH",
                "OPTIONS"
            )
            .allowedHeaders("*")
            .allowCredentials(true)
    }

    override fun addArgumentResolvers(
        resolvers: MutableList<HandlerMethodArgumentResolver>
    ) {
        resolvers.add(loginMemberArgumentResolver)
    }

    override fun addViewControllers(
        registry: ViewControllerRegistry
    ) {
        registry.addRedirectViewController(
            "/",
            "/swagger-ui/index.html"
        )
    }
}