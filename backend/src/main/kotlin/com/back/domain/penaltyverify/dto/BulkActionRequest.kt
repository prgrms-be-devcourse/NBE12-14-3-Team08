package com.back.domain.penaltyverify.dto

// @JvmRecord: Java 쪽(기존 테스트)이 record 접근자 ids()를 그대로 쓸 수 있게 Java record로 컴파일한다
@JvmRecord
data class BulkActionRequest(
    val ids: List<Long>,
)
