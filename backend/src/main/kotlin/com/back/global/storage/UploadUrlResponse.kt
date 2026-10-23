package com.back.global.storage

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "스토리지 업로드 서명 URL 발급 응답")
data class UploadUrlResponse(
    @field:Schema(description = "직접 업로드용 서명 URL (프론트가 파일 바이너리 PUT 전송할 엔드포인트)")
    val uploadUrl: String,

    @field:Schema(description = "업로드 성공 후 DB imageUrl 필드에 저장할 Public 조회 URL")
    val publicUrl: String,
)
