package com.back.global.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

@Configuration
class SocialClientConfig {

    // 소셜 로그인 공급자 호출용. 공급자가 느려져도 요청 스레드가 오래 묶이지 않도록 제한 시간을 둔다.
    @Bean
    fun socialRestClient(): RestClient {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(5))
            setReadTimeout(Duration.ofSeconds(10))
        }

        return RestClient.builder().requestFactory(requestFactory).build()
    }
}
