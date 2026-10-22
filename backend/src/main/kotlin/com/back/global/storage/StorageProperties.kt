package com.back.global.storage

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "supabase")
data class StorageProperties(
    val url: String,
    val serviceRoleKey: String,
    val storage: Storage,
) {
    data class Storage(
        val habitBucket: String,
        val penaltyBucket: String,
        val urlExpirationSeconds: Int,
    )
}
