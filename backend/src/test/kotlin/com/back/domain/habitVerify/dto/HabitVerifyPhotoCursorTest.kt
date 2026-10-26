package com.back.domain.habitVerify.dto

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDate

class HabitVerifyPhotoCursorTest {

    @Test
    @DisplayName("날짜와 id를 '날짜:id' 문자열로 만든다")
    fun 문자열로_만든다() {
        assertThat(HabitVerifyPhotoCursor(LocalDate.of(2026, 10, 26), 123L).encode()).isEqualTo("2026-10-26:123")
    }

    @Test
    @DisplayName("만든 문자열을 다시 읽으면 같은 값이다")
    fun 왕복하면_같다() {
        val cursor = HabitVerifyPhotoCursor(LocalDate.of(2025, 1, 5), 9_000_000_000L)

        assertThat(HabitVerifyPhotoCursor.parse(cursor.encode())).isEqualTo(cursor)
    }

    @ParameterizedTest(name = "커서=\"{0}\"")
    @ValueSource(strings = ["", "abc", ":", "2026-10-26", "2026-10-26:", ":123", "2026-10-26:abc", "2026-13-40:1", "26-10-26:1", "2026-10-26:1:2", "2026-10-26:1.5"])
    @DisplayName("형식이 맞지 않는 커서는 '잘못된 커서입니다.'로 거절한다")
    fun 잘못된_커서(raw: String) {
        assertThatThrownBy { HabitVerifyPhotoCursor.parse(raw) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("잘못된 커서입니다.")
    }
}
