package com.back.global.storage

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource

// application.yaml의 supabase.* 설정(케밥 케이스)이 StorageProperties에 그대로 들어오는지 확인한다.
// 설정 바인딩이 깨지면 컴파일·다른 테스트는 통과하고 운영에서 버킷 이름이 비어 업로드가 실패하므로 따로 고정해 둔다.
// Kotlin 테스트라 StorageProperties가 Java record든 Kotlin data class든 같은 코드(properties.storage)로 접근한다.
class StoragePropertiesBindingTest {

    @Test
    @DisplayName("supabase.* 설정이 url, service-role-key, storage 하위 값까지 StorageProperties에 바인딩된다")
    fun 설정_바인딩() {
        val source = MapConfigurationPropertySource(
            mapOf(
                "supabase.url" to "https://example.supabase.co",
                "supabase.service-role-key" to "test-key",
                "supabase.storage.habit-bucket" to "habit-verifications",
                "supabase.storage.penalty-bucket" to "penalty-verifications",
                "supabase.storage.url-expiration-seconds" to "300",
            )
        )

        val properties = Binder(source).bind("supabase", StorageProperties::class.java).get()

        assertThat(properties.url).isEqualTo("https://example.supabase.co")
        assertThat(properties.serviceRoleKey).isEqualTo("test-key")
        assertThat(properties.storage.habitBucket).isEqualTo("habit-verifications")
        assertThat(properties.storage.penaltyBucket).isEqualTo("penalty-verifications")
        assertThat(properties.storage.urlExpirationSeconds).isEqualTo(300)
    }
}
