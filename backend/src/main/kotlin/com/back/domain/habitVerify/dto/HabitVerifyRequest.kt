package com.back.domain.habitVerify.dto

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// imageUrl이 nullable인 이유: null이어도 객체는 만들어지고 @NotBlank 검증 오류(400 "입력값 검증에 실패했습니다.")로 응답해야 한다
// 설명 길이는 DB 컬럼 길이(HabitVerify.description: 255자)와 맞춘다.
// 검증이 없으면 저장할 때 DB 오류가 그대로 올라와 400이 아니라 500이 된다.
// @Size는 UTF-16 글자 수 기준이라 이모지처럼 두 글자로 세는 문자는 DB보다 먼저 막힐 수 있다. (DB 한도를 넘는 일은 없다)
// 사진 크기(imageWidth, imageHeight)는 클라이언트가 업로드 전에 읽어 보내는 값이고 선택이다.
// 서버가 이미지를 직접 읽어 확인하지 않으며, 값이 틀려도 갤러리의 칸 비율만 어긋난다.
// 보낼 때는 가로와 세로를 함께 보내야 하고, 크기를 보내지 않으면 null로 저장되어 갤러리가 정사각형으로 보여 준다.
data class HabitVerifyRequest(
    @field:Size(max = DESCRIPTION_MAX_LENGTH, message = "습관 인증 설명은 255자 이하여야 합니다.")
    val description: String?,

    @field:NotBlank(message = "인증 사진은 필수입니다.")
    val imageUrl: String?,

    @field:Min(value = 1, message = "사진 가로 크기는 1 이상이어야 합니다.")
    @field:Max(value = IMAGE_SIZE_MAX, message = "사진 가로 크기는 20000 이하여야 합니다.")
    val imageWidth: Int? = null,

    @field:Min(value = 1, message = "사진 세로 크기는 1 이상이어야 합니다.")
    @field:Max(value = IMAGE_SIZE_MAX, message = "사진 세로 크기는 20000 이하여야 합니다.")
    val imageHeight: Int? = null,
) {
    @get:JsonIgnore
    @get:AssertTrue(message = "사진의 가로와 세로 크기는 함께 보내야 합니다.")
    val isImageSizeComplete: Boolean
        get() = (imageWidth == null) == (imageHeight == null)

    companion object {
        const val DESCRIPTION_MAX_LENGTH = 255
        const val IMAGE_SIZE_MAX = 20_000L
    }
}
