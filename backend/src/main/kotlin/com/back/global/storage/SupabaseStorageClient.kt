package com.back.global.storage

import com.back.global.exception.StorageUnavailableException
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Component
class SupabaseStorageClient(
    private val properties: StorageProperties,
) {

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT)
        .build()

    private val objectMapper = ObjectMapper()

    // Supabase에서 업로드용 Signed URL을 받는다.
    // expiresInSeconds는 요청에 함께 보내지만, Supabase는 업로드용 Signed URL에는 이 값을 적용하지 않는다.
    // (직접 확인: 5분이 지나도 업로드가 거절되지 않음)
    // TODO: 5분이 지나도 업로드가 거절되지 않는 현상 확인 및 추후 수정 필요
    fun createSignedUploadUrl(
        bucket: String,
        path: String,
        expiresInSeconds: Int,
    ): String {
        val endpoint =
            "${properties.url}/storage/v1/object/upload/sign/$bucket/$path?expiresIn=$expiresInSeconds"

        val response = translatingFailures(alsoIllegalArgument = true) {
            val request = authorizedRequest(endpoint)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""{"expiresIn":$expiresInSeconds}"""))
                .build()

            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }

        if (response.statusCode() !in 200..299) {
            throw StorageUnavailableException(STORAGE_UNAVAILABLE_MESSAGE)
        }

        val url = translatingFailures { objectMapper.readTree(response.body()).get("url")?.asText() }

        return url?.takeIf { it.isNotBlank() } ?: throw StorageUnavailableException(STORAGE_UNAVAILABLE_MESSAGE)
    }

    // Supabase Storage에 파일이 실제로 존재하는지 확인한다.
    fun exists(
        bucket: String,
        path: String,
    ): Boolean {
        val endpoint = "${properties.url}/storage/v1/object/info/$bucket/$path"

        return try {
            translatingFailures {
                val request = authorizedRequest(endpoint)
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build()

                isFound(httpClient.send(request, HttpResponse.BodyHandlers.ofString()).statusCode())
            }
        } catch (e: IllegalArgumentException) {
            // URI로 만들 수 없는 경로 = 우리가 발급한 파일일 수 없음
            false
        }
    }

    // 통신 중 생기는 예외를 StorageUnavailableException(502)으로 바꾼다.
    // alsoIllegalArgument: 요청을 만들다 나는 IllegalArgumentException(잘못된 URI 등)도 장애로 볼지 여부
    // 그 외 예외(isFound가 던지는 StorageUnavailableException 등)는 그대로 다시 던진다.
    private inline fun <T> translatingFailures(alsoIllegalArgument: Boolean = false, block: () -> T): T =
        try {
            block()
        } catch (e: Exception) {
            throw when (e) {
                is InterruptedException -> {
                    Thread.currentThread().interrupt()
                    StorageUnavailableException(STORAGE_UNAVAILABLE_MESSAGE, e)
                }
                is IOException -> StorageUnavailableException(STORAGE_UNAVAILABLE_MESSAGE, e)
                is IllegalArgumentException ->
                    if (alsoIllegalArgument) StorageUnavailableException(STORAGE_UNAVAILABLE_MESSAGE, e) else e
                else -> e
            }
        }

    // 두 요청에 공통인 주소, 시간 제한, 인증 헤더
    private fun authorizedRequest(endpoint: String): HttpRequest.Builder =
        HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .timeout(REQUEST_TIMEOUT)
            .header("Authorization", "Bearer ${properties.serviceRoleKey}")
            .header("apikey", properties.serviceRoleKey)

    companion object {
        private const val STORAGE_UNAVAILABLE_MESSAGE =
            "사진 저장소와 통신할 수 없습니다. 잠시 후 다시 시도해 주세요."

        // 인증 제출은 트랜잭션 안에서 Supabase를 부르므로, 응답이 없을 때 DB 커넥션을 오래 쥐지 않도록 끊는다.
        // 시간이 넘으면 IOException(HttpTimeoutException)이 나서 502로 응답한다.
        private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(3)
        private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(5)

        // 200: 있음 / 400·404: 없음 (Supabase는 없는 파일에 400을 주기도 함) / 그 외: 저장소 장애
        fun isFound(statusCode: Int): Boolean =
            when (statusCode) {
                200 -> true
                400, 404 -> false
                else -> throw StorageUnavailableException(STORAGE_UNAVAILABLE_MESSAGE)
            }
    }
}
