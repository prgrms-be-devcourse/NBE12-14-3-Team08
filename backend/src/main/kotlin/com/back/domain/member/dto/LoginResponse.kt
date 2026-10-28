package com.back.domain.member.dto

data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
) {

    companion object {

        fun of(accessToken: String, refreshToken: String): LoginResponse =
            LoginResponse(accessToken, refreshToken, "Bearer")
    }
}
