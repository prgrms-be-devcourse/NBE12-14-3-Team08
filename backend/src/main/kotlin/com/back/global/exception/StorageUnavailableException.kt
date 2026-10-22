package com.back.global.exception

// 외부 사진 저장소(Supabase Storage)와 통신하지 못했을 때 사용 (502 BAD_GATEWAY)
class StorageUnavailableException @JvmOverloads constructor(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
