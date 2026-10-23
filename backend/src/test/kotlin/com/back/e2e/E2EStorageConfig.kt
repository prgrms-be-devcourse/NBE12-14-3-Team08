package com.back.e2e

import com.back.global.storage.StorageProperties
import com.back.global.storage.SupabaseStorageClient
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

// E2E 테스트에서 외부 서비스(Supabase Storage)만 가짜로 바꾼다. 서버, 보안 필터, DB는 실제 그대로 쓴다.
@TestConfiguration
class E2EStorageConfig {

    @Bean
    @Primary
    fun fakeSupabaseStorageClient(properties: StorageProperties): SupabaseStorageClient =
        FakeSupabaseStorageClient(properties)
}

// 업로드 서명 URL은 형태만 맞춰 돌려주고, 파일은 항상 올라와 있다고 본다.
class FakeSupabaseStorageClient(properties: StorageProperties) : SupabaseStorageClient(properties) {

    override fun createSignedUploadUrl(bucket: String, path: String, expiresInSeconds: Int): String =
        "/object/upload/sign/$bucket/$path?token=e2e-fake"

    override fun exists(bucket: String, path: String): Boolean = true
}
