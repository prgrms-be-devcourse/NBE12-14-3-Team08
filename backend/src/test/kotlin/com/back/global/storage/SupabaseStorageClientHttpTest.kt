package com.back.global.storage

import com.back.global.exception.StorageUnavailableException
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.InetSocketAddress

// SupabaseStorageClient의 실제 HTTP 경로를 가짜 서버(JDK HttpServer)로 확인한다.
// 기존 SupabaseStorageClientTest는 상태 코드 번역(isFound)만 보고, StorageServiceTest는 클라이언트를 목으로 바꾸기 때문에
// 요청 만들기, 응답 해석, 통신 예외를 502로 바꾸는 부분(translatingFailures)은 여기서만 확인된다.
class SupabaseStorageClientHttpTest {

    private data class RecordedRequest(
        val method: String,
        val uri: String,
        val authorization: String?,
        val apikey: String?,
        val body: String,
    )

    private lateinit var server: HttpServer
    private lateinit var client: SupabaseStorageClient

    // 가짜 서버가 돌려줄 응답과, 마지막으로 받은 요청
    @Volatile private var responseStatus = 200
    @Volatile private var responseBody = ""
    @Volatile private var lastRequest: RecordedRequest? = null

    @BeforeEach
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            lastRequest = RecordedRequest(
                method = exchange.requestMethod,
                uri = exchange.requestURI.toString(),
                authorization = exchange.requestHeaders.getFirst("Authorization"),
                apikey = exchange.requestHeaders.getFirst("apikey"),
                body = exchange.requestBody.readBytes().toString(Charsets.UTF_8),
            )
            val bytes = responseBody.toByteArray()
            // 본문이 없거나 HEAD 요청이면 길이를 -1로 알려야 한다 (JDK HttpServer 규칙)
            val length = if (bytes.isEmpty() || exchange.requestMethod == "HEAD") -1L else bytes.size.toLong()
            exchange.sendResponseHeaders(responseStatus, length)
            if (length != -1L) exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
        client = clientFor("http://127.0.0.1:${server.address.port}")
    }

    @AfterEach
    fun stopServer() {
        server.stop(0)
    }

    private fun clientFor(url: String) = SupabaseStorageClient(
        StorageProperties(
            url = url,
            serviceRoleKey = "test-service-key",
            storage = StorageProperties.Storage("habit-bucket", "penalty-bucket", 300),
        )
    )

    private val unavailableMessage = "사진 저장소와 통신할 수 없습니다. 잠시 후 다시 시도해 주세요."

    @Test
    @DisplayName("createSignedUploadUrl: 올바른 요청(주소, 인증 헤더, 본문)을 보내고 응답의 url을 돌려준다")
    fun 업로드_url_발급_성공() {
        responseBody = """{"url":"/object/upload/sign/habit-bucket/1/2/a.png?token=abc"}"""

        val result = client.createSignedUploadUrl("habit-bucket", "1/2/a.png", 300)

        assertThat(result).isEqualTo("/object/upload/sign/habit-bucket/1/2/a.png?token=abc")
        val request = requireNotNull(lastRequest)
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.uri).isEqualTo("/storage/v1/object/upload/sign/habit-bucket/1/2/a.png?expiresIn=300")
        assertThat(request.authorization).isEqualTo("Bearer test-service-key")
        assertThat(request.apikey).isEqualTo("test-service-key")
        assertThat(request.body).isEqualTo("""{"expiresIn":300}""")
    }

    @Test
    @DisplayName("createSignedUploadUrl: 2xx가 아니면 저장소 장애로 본다")
    fun 업로드_url_발급_비정상_응답() {
        responseStatus = 500

        assertThatThrownBy { client.createSignedUploadUrl("habit-bucket", "1/2/a.png", 300) }
            .isInstanceOf(StorageUnavailableException::class.java)
            .hasMessage(unavailableMessage)
    }

    @Test
    @DisplayName("createSignedUploadUrl: 응답 본문이 JSON이 아니면 저장소 장애로 본다")
    fun 업로드_url_발급_본문이_json이_아님() {
        responseBody = "this is not json"

        assertThatThrownBy { client.createSignedUploadUrl("habit-bucket", "1/2/a.png", 300) }
            .isInstanceOf(StorageUnavailableException::class.java)
            .hasMessage(unavailableMessage)
    }

    @Test
    @DisplayName("createSignedUploadUrl: url 필드가 없거나 비어 있으면 저장소 장애로 본다")
    fun 업로드_url_발급_url이_없거나_빔() {
        listOf("""{}""", """{"url":""}""", """{"url":"   "}""").forEach { body ->
            responseBody = body

            assertThatThrownBy { client.createSignedUploadUrl("habit-bucket", "1/2/a.png", 300) }
                .describedAs("응답 본문: $body")
                .isInstanceOf(StorageUnavailableException::class.java)
                .hasMessage(unavailableMessage)
        }
    }

    @Test
    @DisplayName("createSignedUploadUrl: 서버에 연결할 수 없으면 원인(IOException)을 담아 저장소 장애로 본다")
    fun 업로드_url_발급_연결_실패() {
        server.stop(0)

        assertThatThrownBy { client.createSignedUploadUrl("habit-bucket", "1/2/a.png", 300) }
            .isInstanceOf(StorageUnavailableException::class.java)
            .hasMessage(unavailableMessage)
            .hasCauseInstanceOf(IOException::class.java)
    }

    @Test
    @DisplayName("createSignedUploadUrl: 요청 주소를 만들 수 없으면(IllegalArgumentException) 저장소 장애로 본다")
    fun 업로드_url_발급_주소가_잘못됨() {
        val badClient = clientFor("http://bad host")

        assertThatThrownBy { badClient.createSignedUploadUrl("habit-bucket", "1/2/a.png", 300) }
            .isInstanceOf(StorageUnavailableException::class.java)
            .hasMessage(unavailableMessage)
            .hasCauseInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    @DisplayName("createSignedUploadUrl: 스레드가 중단(interrupt)되면 저장소 장애로 보고 중단 표시를 되살린다")
    fun 업로드_url_발급_스레드_중단() {
        Thread.currentThread().interrupt()
        try {
            assertThatThrownBy { client.createSignedUploadUrl("habit-bucket", "1/2/a.png", 300) }
                .isInstanceOf(StorageUnavailableException::class.java)
                .hasCauseInstanceOf(InterruptedException::class.java)
            // 중단 표시를 지우지 않고 되살려 둬야 위쪽 코드가 중단을 알 수 있다 (읽으면서 지운다)
            assertThat(Thread.interrupted()).isTrue()
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    @DisplayName("exists: HEAD 요청에 인증 헤더를 붙이고, 200이면 true")
    fun 존재_확인_있음() {
        responseStatus = 200

        assertThat(client.exists("habit-bucket", "1/2/a.png")).isTrue()

        val request = requireNotNull(lastRequest)
        assertThat(request.method).isEqualTo("HEAD")
        assertThat(request.uri).isEqualTo("/storage/v1/object/info/habit-bucket/1/2/a.png")
        assertThat(request.authorization).isEqualTo("Bearer test-service-key")
        assertThat(request.apikey).isEqualTo("test-service-key")
    }

    @Test
    @DisplayName("exists: 400, 404면 false")
    fun 존재_확인_없음() {
        listOf(400, 404).forEach { status ->
            responseStatus = status

            assertThat(client.exists("habit-bucket", "1/2/a.png"))
                .describedAs("응답 코드: $status")
                .isFalse()
        }
    }

    @Test
    @DisplayName("exists: 그 외 응답이면 false가 아니라 저장소 장애")
    fun 존재_확인_장애_응답() {
        responseStatus = 500

        assertThatThrownBy { client.exists("habit-bucket", "1/2/a.png") }
            .isInstanceOf(StorageUnavailableException::class.java)
            .hasMessage(unavailableMessage)
    }

    @Test
    @DisplayName("exists: 서버에 연결할 수 없으면 false가 아니라 저장소 장애")
    fun 존재_확인_연결_실패() {
        server.stop(0)

        assertThatThrownBy { client.exists("habit-bucket", "1/2/a.png") }
            .isInstanceOf(StorageUnavailableException::class.java)
            .hasCauseInstanceOf(IOException::class.java)
    }

    @Test
    @DisplayName("exists: URI로 만들 수 없는 경로(공백 등)는 우리가 발급한 파일일 수 없으므로 서버에 묻지 않고 false")
    fun 존재_확인_잘못된_경로() {
        assertThat(client.exists("habit-bucket", "1/2/not allowed.png")).isFalse()

        assertThat(lastRequest).isNull()
    }

    @Test
    @DisplayName("exists: 스레드가 중단(interrupt)되면 false가 아니라 저장소 장애")
    fun 존재_확인_스레드_중단() {
        Thread.currentThread().interrupt()
        try {
            assertThatThrownBy { client.exists("habit-bucket", "1/2/a.png") }
                .isInstanceOf(StorageUnavailableException::class.java)
                .hasCauseInstanceOf(InterruptedException::class.java)
            assertThat(Thread.interrupted()).isTrue()
        } finally {
            Thread.interrupted()
        }
    }
}
