package com.back.domain.member.dto

data class UpdateMemberRequest(
    val nickname: String? = null,
    val currentPassword: String? = null,
    val newPassword: String? = null,
)
