package com.back.global.storage

// 인증 사진 종류: 습관 인증 사진 / 벌칙 인증 사진. 종류마다 저장 버킷이 다르다.
enum class VerificationImageType {
    HABIT,
    PENALTY,
    ;

    fun bucket(storage: StorageProperties.Storage): String =
        when (this) {
            HABIT -> storage.habitBucket
            PENALTY -> storage.penaltyBucket
        }
}
