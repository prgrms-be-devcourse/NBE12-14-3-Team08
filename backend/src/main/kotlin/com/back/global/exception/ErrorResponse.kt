package com.back.global.exception

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant

data class ErrorResponse(
    val status: Int,
    val message: String?,
    val timestamp: Instant = Instant.now(),
    // @Valid 검증 실패일 때만 담기고, 비어 있으면 응답에서 키 자체를 뺀다
    @get:JsonInclude(JsonInclude.Include.NON_EMPTY)
    val errors: List<String?>? = null,
)
