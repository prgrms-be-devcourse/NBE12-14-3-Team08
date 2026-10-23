package com.back.global.storage

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

@Schema(description = "스토리지 업로드 서명 URL 발급 요청")
data class UploadUrlRequest(
    // 검증·문서 어노테이션은 필드에 붙여야 Bean Validation과 Swagger가 읽는다
    @field:NotBlank
    @field:Schema(description = "업로드할 파일명 (확장자 포함)", example = "today_habit.png")
    val filename: String?,
)
