package com.back.global.storage

import com.back.global.exception.StorageUnavailableException
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@ExtendWith(MockKExtension::class)
class StorageServiceTest {

    companion object {
        private const val SUPABASE_URL = "https://supabase.test"
        private const val UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
    }

    @MockK
    private lateinit var supabaseStorageClient: SupabaseStorageClient

    private lateinit var storageService: StorageService

    @BeforeEach
    fun setUp() {
        val properties = StorageProperties(
            SUPABASE_URL,
            "service-role-key",
            StorageProperties.Storage("habit-bucket", "penalty-bucket", 300),
        )
        storageService = StorageService(properties, supabaseStorageClient)
    }

    @Test
    @DisplayName("습관 사진은 습관 버킷의 {회원id}/{습관id}/{UUID}.{확장자} 경로로 발급된다")
    fun 습관_사진_업로드_URL() {
        val path = slot<String>()
        every { supabaseStorageClient.createSignedUploadUrl("habit-bucket", capture(path), 300) } returns
            "/object/upload/sign/habit-bucket/signed?token=abc"

        val response = storageService.createUploadUrl(VerificationImageType.HABIT, 7L, 50L, "today.png")

        verify { supabaseStorageClient.createSignedUploadUrl("habit-bucket", any(), 300) }

        assertThat(path.captured).matches("7/50/$UUID_PATTERN\\.png")
        assertThat(response.uploadUrl)
            .isEqualTo("$SUPABASE_URL/storage/v1/object/upload/sign/habit-bucket/signed?token=abc")
        assertThat(response.publicUrl)
            .isEqualTo("$SUPABASE_URL/storage/v1/object/public/habit-bucket/${path.captured}")
    }

    @Test
    @DisplayName("벌칙 사진은 벌칙 버킷의 {회원id}/{벌칙id}/{UUID}.{확장자} 경로로 발급된다")
    fun 벌칙_사진_업로드_URL() {
        val path = slot<String>()
        every { supabaseStorageClient.createSignedUploadUrl("penalty-bucket", capture(path), 300) } returns
            "/object/upload/sign/penalty-bucket/signed?token=abc"

        val response = storageService.createUploadUrl(VerificationImageType.PENALTY, 7L, 3L, "proof.jpg")

        verify { supabaseStorageClient.createSignedUploadUrl("penalty-bucket", any(), 300) }

        assertThat(path.captured).matches("7/3/$UUID_PATTERN\\.jpg")
        assertThat(response.publicUrl).startsWith("$SUPABASE_URL/storage/v1/object/public/penalty-bucket/7/3/")
    }

    @ParameterizedTest(name = "{0} 허용")
    @ValueSource(strings = ["a.jpg", "a.jpeg", "a.png", "a.webp", "a.PNG", "a.Jpeg"])
    @DisplayName("jpg, jpeg, png, webp는 대소문자 상관없이 허용하고 소문자 확장자로 저장한다")
    fun 허용_확장자(filename: String) {
        every { supabaseStorageClient.createSignedUploadUrl(any(), any(), any()) } returns
            "/object/upload/sign/habit-bucket/signed"

        val response = storageService.createUploadUrl(VerificationImageType.HABIT, 7L, 50L, filename)

        val extension = filename.substring(filename.lastIndexOf('.')).lowercase()
        assertThat(response.publicUrl).endsWith(extension)
    }

    @ParameterizedTest(name = "{0} 거절")
    @ValueSource(strings = ["a.gif", "a.exe", "a.svg"])
    @DisplayName("허용되지 않은 확장자는 거절하고 Supabase를 호출하지 않는다")
    fun 허용되지_않은_확장자(filename: String) {
        assertThatThrownBy { storageService.createUploadUrl(VerificationImageType.PENALTY, 7L, 3L, filename) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("허용되지 않는 이미지 파일 형식입니다.")

        verify(exactly = 0) { supabaseStorageClient.createSignedUploadUrl(any(), any(), any()) }
    }

    @Test
    @DisplayName("확장자가 없으면 거절한다 (기본 확장자를 붙이지 않는다)")
    fun 확장자_없음() {
        assertThatThrownBy { storageService.createUploadUrl(VerificationImageType.PENALTY, 7L, 3L, "proof") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("파일 확장자가 없습니다.")

        verify(exactly = 0) { supabaseStorageClient.createSignedUploadUrl(any(), any(), any()) }
    }

    @Test
    @DisplayName("Supabase 통신 실패는 StorageUnavailableException(502)으로 전달된다")
    fun 저장소_통신_실패() {
        every { supabaseStorageClient.createSignedUploadUrl(any(), any(), any()) } throws
            StorageUnavailableException("사진 저장소와 통신할 수 없습니다. 잠시 후 다시 시도해 주세요.")

        assertThatThrownBy { storageService.createUploadUrl(VerificationImageType.HABIT, 7L, 50L, "today.png") }
            .isInstanceOf(StorageUnavailableException::class.java)
    }

    @Nested
    @DisplayName("제출된 인증 사진 URL 검사(validateImageUrl)")
    inner class ValidateImageUrl {

        private val penaltyBucketUrl = "$SUPABASE_URL/storage/v1/object/public/penalty-bucket/"
        private val issuedPath = "7/3/0f8fad5b-d9cb-469f-a165-70867728950e.png"

        @Test
        @DisplayName("우리 버킷 + 내 경로 + 파일 있음이면 통과한다")
        fun 정상() {
            every { supabaseStorageClient.exists("penalty-bucket", issuedPath) } returns true

            assertThatCode {
                storageService.validateImageUrl(
                    VerificationImageType.PENALTY, 7L, 3L, penaltyBucketUrl + issuedPath, null
                )
            }
                .doesNotThrowAnyException()
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(
            strings = [
                "https://evil.com/7/3/0f8fad5b-d9cb-469f-a165-70867728950e.png",
                "https://supabase.test.evil.com/storage/v1/object/public/penalty-bucket/7/3/0f8fad5b-d9cb-469f-a165-70867728950e.png",
                "https://supabase.test/storage/v1/object/public/habit-bucket/7/3/0f8fad5b-d9cb-469f-a165-70867728950e.png",
            ]
        )
        @DisplayName("외부 주소, 비슷하게 생긴 주소, 다른 버킷은 거절하고 Supabase를 호출하지 않는다")
        fun 우리_버킷_아님(imageUrl: String) {
            assertThatThrownBy {
                storageService.validateImageUrl(VerificationImageType.PENALTY, 7L, 3L, imageUrl, null)
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("업로드한 사진만 사용할 수 있습니다.")

            verify(exactly = 0) { supabaseStorageClient.exists(any(), any()) }
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(
            strings = [
                "9/3/0f8fad5b-d9cb-469f-a165-70867728950e.png",
                "7/4/0f8fad5b-d9cb-469f-a165-70867728950e.png",
                "7/3/../../9/3/0f8fad5b-d9cb-469f-a165-70867728950e.png",
                "7/3/0f8fad5b-d9cb-469f-a165-70867728950e.png?x=1",
                "penalties/7/0f8fad5b-d9cb-469f-a165-70867728950e.png",
            ]
        )
        @DisplayName("남의 경로, 다른 대상 경로, 조작된 경로는 거절한다")
        fun 내_경로_아님(path: String) {
            assertThatThrownBy {
                storageService.validateImageUrl(VerificationImageType.PENALTY, 7L, 3L, penaltyBucketUrl + path, null)
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("본인이 업로드한 사진만 사용할 수 있습니다.")

            verify(exactly = 0) { supabaseStorageClient.exists(any(), any()) }
        }

        @Test
        @DisplayName("지금 저장된 사진과 같은 URL이면 예전 경로여도 경로 검사를 건너뛴다")
        fun 기존_사진_재사용() {
            val oldUrl = penaltyBucketUrl + "penalties/7/old-photo.png"
            every { supabaseStorageClient.exists("penalty-bucket", "penalties/7/old-photo.png") } returns true

            assertThatCode {
                storageService.validateImageUrl(VerificationImageType.PENALTY, 7L, 3L, oldUrl, oldUrl)
            }
                .doesNotThrowAnyException()
        }

        @Test
        @DisplayName("기존 사진이어도 우리 버킷 주소가 아니면 거절한다")
        fun 기존_사진_외부주소() {
            val external = "https://evil.com/a.png"

            assertThatThrownBy {
                storageService.validateImageUrl(VerificationImageType.PENALTY, 7L, 3L, external, external)
            }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        @DisplayName("Supabase에 파일이 없으면 거절한다")
        fun 파일_없음() {
            every { supabaseStorageClient.exists("penalty-bucket", issuedPath) } returns false

            assertThatThrownBy {
                storageService.validateImageUrl(
                    VerificationImageType.PENALTY, 7L, 3L, penaltyBucketUrl + issuedPath, null
                )
            }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("업로드된 사진을 확인할 수 없습니다.")
        }

        @Test
        @DisplayName("Supabase 장애는 StorageUnavailableException(502)으로 전달된다")
        fun 저장소_장애() {
            every { supabaseStorageClient.exists("penalty-bucket", issuedPath) } throws
                StorageUnavailableException("사진 저장소와 통신할 수 없습니다. 잠시 후 다시 시도해 주세요.")

            assertThatThrownBy {
                storageService.validateImageUrl(
                    VerificationImageType.PENALTY, 7L, 3L, penaltyBucketUrl + issuedPath, null
                )
            }
                .isInstanceOf(StorageUnavailableException::class.java)
        }
    }
}
