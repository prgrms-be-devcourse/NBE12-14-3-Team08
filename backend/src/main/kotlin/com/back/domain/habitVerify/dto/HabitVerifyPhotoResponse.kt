package com.back.domain.habitVerify.dto

import com.back.domain.habitVerify.entity.HabitVerify
import com.back.global.entity.requiredId
import java.time.LocalDate

// 그룹 인증 사진 목록의 항목. 갤러리 화면에 필요한 값만 담는다.
data class HabitVerifyPhotoResponse(
    val id: Long,
    val verifyDate: LocalDate,
    val habitTitle: String,
    val memberId: Long,
    val memberNickname: String,
    val description: String?,
    val imageUrl: String,
) {
    companion object {
        // 목록 쿼리가 사진이 있는 인증만 골라 오므로 imageUrl은 null일 수 없다
        fun from(habitVerify: HabitVerify): HabitVerifyPhotoResponse {
            val habit = habitVerify.habit
            val member = habit.groupMember.member

            return HabitVerifyPhotoResponse(
                id = habitVerify.requiredId,
                verifyDate = habitVerify.verifyDate,
                habitTitle = habit.title,
                memberId = member.requiredId,
                memberNickname = member.nickname,
                description = habitVerify.description,
                imageUrl = requireNotNull(habitVerify.imageUrl) { "사진이 없는 인증은 사진 목록에 포함될 수 없습니다." },
            )
        }
    }
}

// nextCursor가 null이면 마지막 페이지다. 다음 요청에는 이 값을 그대로 cursor로 보낸다.
data class HabitVerifyPhotoPage(
    val items: List<HabitVerifyPhotoResponse>,
    val nextCursor: String?,
)

// 마지막으로 받은 항목의 (인증 날짜, id). 새 인증이 올라와도 페이지 사이에 중복이나 누락이 생기지 않게 한다.
// 클라이언트에는 "2026-10-26:123" 형태의 문자열로 주고받는다.
data class HabitVerifyPhotoCursor(
    val verifyDate: LocalDate,
    val id: Long,
) {
    fun encode(): String = "$verifyDate$SEPARATOR$id"

    companion object {
        private const val SEPARATOR = ":"
        private const val INVALID_MESSAGE = "잘못된 커서입니다."

        fun parse(raw: String): HabitVerifyPhotoCursor {
            val parts = raw.split(SEPARATOR)
            require(parts.size == 2) { INVALID_MESSAGE }

            val verifyDate = runCatching { LocalDate.parse(parts[0]) }.getOrNull()
            val id = parts[1].toLongOrNull()
            require(verifyDate != null && id != null) { INVALID_MESSAGE }

            return HabitVerifyPhotoCursor(verifyDate, id)
        }
    }
}
