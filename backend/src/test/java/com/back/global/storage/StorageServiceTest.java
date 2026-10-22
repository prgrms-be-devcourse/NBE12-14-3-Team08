package com.back.global.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.back.global.exception.StorageUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    private static final String SUPABASE_URL = "https://supabase.test";
    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Mock
    private SupabaseStorageClient supabaseStorageClient;

    private StorageService storageService;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties(
            SUPABASE_URL,
            "service-role-key",
            new StorageProperties.Storage("habit-bucket", "penalty-bucket", 300)
        );
        storageService = new StorageService(properties, supabaseStorageClient);
    }

    @Test
    @DisplayName("습관 사진은 습관 버킷의 {회원id}/{습관id}/{UUID}.{확장자} 경로로 발급된다")
    void 습관_사진_업로드_URL() {
        given(supabaseStorageClient.createSignedUploadUrl(eq("habit-bucket"), anyString(), eq(300)))
            .willReturn("/object/upload/sign/habit-bucket/signed?token=abc");

        UploadUrlResponse response = storageService.createUploadUrl(VerificationImageType.HABIT, 7L, 50L, "today.png");

        ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
        verify(supabaseStorageClient).createSignedUploadUrl(eq("habit-bucket"), path.capture(), eq(300));

        assertThat(path.getValue()).matches("7/50/" + UUID_PATTERN + "\\.png");
        assertThat(response.getUploadUrl())
            .isEqualTo(SUPABASE_URL + "/storage/v1/object/upload/sign/habit-bucket/signed?token=abc");
        assertThat(response.getPublicUrl())
            .isEqualTo(SUPABASE_URL + "/storage/v1/object/public/habit-bucket/" + path.getValue());
    }

    @Test
    @DisplayName("벌칙 사진은 벌칙 버킷의 {회원id}/{벌칙id}/{UUID}.{확장자} 경로로 발급된다")
    void 벌칙_사진_업로드_URL() {
        given(supabaseStorageClient.createSignedUploadUrl(eq("penalty-bucket"), anyString(), eq(300)))
            .willReturn("/object/upload/sign/penalty-bucket/signed?token=abc");

        UploadUrlResponse response = storageService.createUploadUrl(VerificationImageType.PENALTY, 7L, 3L, "proof.jpg");

        ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
        verify(supabaseStorageClient).createSignedUploadUrl(eq("penalty-bucket"), path.capture(), eq(300));

        assertThat(path.getValue()).matches("7/3/" + UUID_PATTERN + "\\.jpg");
        assertThat(response.getPublicUrl()).startsWith(SUPABASE_URL + "/storage/v1/object/public/penalty-bucket/7/3/");
    }

    @ParameterizedTest(name = "{0} 허용")
    @ValueSource(strings = {"a.jpg", "a.jpeg", "a.png", "a.webp", "a.PNG", "a.Jpeg"})
    @DisplayName("jpg, jpeg, png, webp는 대소문자 상관없이 허용하고 소문자 확장자로 저장한다")
    void 허용_확장자(String filename) {
        given(supabaseStorageClient.createSignedUploadUrl(anyString(), anyString(), anyInt()))
            .willReturn("/object/upload/sign/habit-bucket/signed");

        UploadUrlResponse response = storageService.createUploadUrl(VerificationImageType.HABIT, 7L, 50L, filename);

        String extension = filename.substring(filename.lastIndexOf('.')).toLowerCase();
        assertThat(response.getPublicUrl()).endsWith(extension);
    }

    @ParameterizedTest(name = "{0} 거절")
    @ValueSource(strings = {"a.gif", "a.exe", "a.svg"})
    @DisplayName("허용되지 않은 확장자는 거절하고 Supabase를 호출하지 않는다")
    void 허용되지_않은_확장자(String filename) {
        assertThatThrownBy(() -> storageService.createUploadUrl(VerificationImageType.PENALTY, 7L, 3L, filename))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("허용되지 않는 이미지 파일 형식입니다.");

        verify(supabaseStorageClient, never()).createSignedUploadUrl(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("확장자가 없으면 거절한다 (기본 확장자를 붙이지 않는다)")
    void 확장자_없음() {
        assertThatThrownBy(() -> storageService.createUploadUrl(VerificationImageType.PENALTY, 7L, 3L, "proof"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("파일 확장자가 없습니다.");

        verify(supabaseStorageClient, never()).createSignedUploadUrl(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("Supabase 통신 실패는 StorageUnavailableException(502)으로 전달된다")
    void 저장소_통신_실패() {
        given(supabaseStorageClient.createSignedUploadUrl(anyString(), anyString(), anyInt()))
            .willThrow(new StorageUnavailableException("사진 저장소와 통신할 수 없습니다. 잠시 후 다시 시도해 주세요."));

        assertThatThrownBy(() -> storageService.createUploadUrl(VerificationImageType.HABIT, 7L, 50L, "today.png"))
            .isInstanceOf(StorageUnavailableException.class);
    }

    @Nested
    @DisplayName("제출된 인증 사진 URL 검사(validateImageUrl)")
    class ValidateImageUrl {

        private static final String PENALTY_BUCKET_URL = SUPABASE_URL + "/storage/v1/object/public/penalty-bucket/";
        private static final String ISSUED_PATH = "7/3/0f8fad5b-d9cb-469f-a165-70867728950e.png";

        @Test
        @DisplayName("우리 버킷 + 내 경로 + 파일 있음이면 통과한다")
        void 정상() {
            given(supabaseStorageClient.exists("penalty-bucket", ISSUED_PATH)).willReturn(true);

            assertThatCode(() -> storageService.validateImageUrl(
                VerificationImageType.PENALTY, 7L, 3L, PENALTY_BUCKET_URL + ISSUED_PATH, null))
                .doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
            "https://evil.com/7/3/0f8fad5b-d9cb-469f-a165-70867728950e.png",
            "https://supabase.test.evil.com/storage/v1/object/public/penalty-bucket/7/3/0f8fad5b-d9cb-469f-a165-70867728950e.png",
            "https://supabase.test/storage/v1/object/public/habit-bucket/7/3/0f8fad5b-d9cb-469f-a165-70867728950e.png"
        })
        @DisplayName("외부 주소, 비슷하게 생긴 주소, 다른 버킷은 거절하고 Supabase를 호출하지 않는다")
        void 우리_버킷_아님(String imageUrl) {
            assertThatThrownBy(() -> storageService.validateImageUrl(
                VerificationImageType.PENALTY, 7L, 3L, imageUrl, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("업로드한 사진만 사용할 수 있습니다.");

            verify(supabaseStorageClient, never()).exists(anyString(), anyString());
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
            "9/3/0f8fad5b-d9cb-469f-a165-70867728950e.png",
            "7/4/0f8fad5b-d9cb-469f-a165-70867728950e.png",
            "7/3/../../9/3/0f8fad5b-d9cb-469f-a165-70867728950e.png",
            "7/3/0f8fad5b-d9cb-469f-a165-70867728950e.png?x=1",
            "penalties/7/0f8fad5b-d9cb-469f-a165-70867728950e.png"
        })
        @DisplayName("남의 경로, 다른 대상 경로, 조작된 경로는 거절한다")
        void 내_경로_아님(String path) {
            assertThatThrownBy(() -> storageService.validateImageUrl(
                VerificationImageType.PENALTY, 7L, 3L, PENALTY_BUCKET_URL + path, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("본인이 업로드한 사진만 사용할 수 있습니다.");

            verify(supabaseStorageClient, never()).exists(anyString(), anyString());
        }

        @Test
        @DisplayName("지금 저장된 사진과 같은 URL이면 예전 경로여도 경로 검사를 건너뛴다")
        void 기존_사진_재사용() {
            String oldUrl = PENALTY_BUCKET_URL + "penalties/7/old-photo.png";
            given(supabaseStorageClient.exists("penalty-bucket", "penalties/7/old-photo.png")).willReturn(true);

            assertThatCode(() -> storageService.validateImageUrl(
                VerificationImageType.PENALTY, 7L, 3L, oldUrl, oldUrl))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("기존 사진이어도 우리 버킷 주소가 아니면 거절한다")
        void 기존_사진_외부주소() {
            String external = "https://evil.com/a.png";

            assertThatThrownBy(() -> storageService.validateImageUrl(
                VerificationImageType.PENALTY, 7L, 3L, external, external))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Supabase에 파일이 없으면 거절한다")
        void 파일_없음() {
            given(supabaseStorageClient.exists("penalty-bucket", ISSUED_PATH)).willReturn(false);

            assertThatThrownBy(() -> storageService.validateImageUrl(
                VerificationImageType.PENALTY, 7L, 3L, PENALTY_BUCKET_URL + ISSUED_PATH, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("업로드된 사진을 확인할 수 없습니다.");
        }

        @Test
        @DisplayName("Supabase 장애는 StorageUnavailableException(502)으로 전달된다")
        void 저장소_장애() {
            given(supabaseStorageClient.exists("penalty-bucket", ISSUED_PATH))
                .willThrow(new StorageUnavailableException("사진 저장소와 통신할 수 없습니다. 잠시 후 다시 시도해 주세요."));

            assertThatThrownBy(() -> storageService.validateImageUrl(
                VerificationImageType.PENALTY, 7L, 3L, PENALTY_BUCKET_URL + ISSUED_PATH, null))
                .isInstanceOf(StorageUnavailableException.class);
        }
    }
}
