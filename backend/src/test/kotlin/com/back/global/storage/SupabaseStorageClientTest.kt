package com.back.global.storage

import com.back.global.exception.StorageUnavailableException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class SupabaseStorageClientTest {

    @Test
    @DisplayName("200이면 파일이 있다")
    fun 파일_있음() {
        assertThat(SupabaseStorageClient.isFound(200)).isTrue()
    }

    @ParameterizedTest(name = "{0}이면 파일 없음")
    @ValueSource(ints = [400, 404])
    @DisplayName("400, 404면 파일이 없다 (Supabase는 없는 파일에 400을 주기도 함)")
    fun 파일_없음(statusCode: Int) {
        assertThat(SupabaseStorageClient.isFound(statusCode)).isFalse()
    }

    @ParameterizedTest(name = "{0}이면 저장소 장애")
    @ValueSource(ints = [401, 403, 500, 502, 503])
    @DisplayName("그 외 응답은 파일 없음이 아니라 저장소 장애(502)로 본다")
    fun 저장소_장애(statusCode: Int) {
        assertThatThrownBy { SupabaseStorageClient.isFound(statusCode) }
            .isInstanceOf(StorageUnavailableException::class.java)
    }
}
