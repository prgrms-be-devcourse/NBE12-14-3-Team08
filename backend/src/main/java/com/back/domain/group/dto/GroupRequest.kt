
package com.back.domain.group.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import java.time.LocalDate

interface GroupRequest {

    data class Create(
        @field:NotBlank(message = "그룹 이름은 필수입니다.")
        val title: String,

        val description: String?,
        val deadline: LocalDate?,

        @field:NotBlank(message = "벌칙은 필수입니다.")
        val penalty: String,

        @field:NotBlank(message = "비밀번호는 필수입니다.")
        val password: String,

        @field:Min(
            value = 1,
            message = "최대 인원은 1명 이상이어야 합니다."
        )
        val memberLimit: Int?
    )

    data class Update(
        val title: String?,
        val description: String?,
        val deadline: LocalDate?,
        val penalty: String?,
        val password: String?,

        @field:Min(
            value = 1,
            message = "최대 인원은 1명 이상이어야 합니다."
        )
        val memberLimit: Int?
    )

    data class Join(
        @field:NotBlank(message = "비밀번호는 필수입니다.")
        val password: String
    )
}