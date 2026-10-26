package com.back.domain.member.entity

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.test.util.ReflectionTestUtils

// Member 엔티티의 상태 변경 규칙을 DB 없이 확인한다.
class MemberTest {

    private fun member(): Member = Member.create("닉네임", "user-1", "encoded-password")

    @Nested
    @DisplayName("생성")
    inner class Create {

        @Test
        @DisplayName("생성하면 입력값 그대로 담기고 ACTIVE이며 리프레시 토큰이 없다")
        fun 생성_기본값() {
            val member = Member.create("닉네임", "user-1", "encoded-password")

            assertThat(member.nickname).isEqualTo("닉네임")
            assertThat(member.username).isEqualTo("user-1")
            assertThat(member.password).isEqualTo("encoded-password")
            assertThat(member.status).isEqualTo(MemberStatus.ACTIVE)
            assertThat(member.refreshToken).isNull()
        }

        @Test
        @DisplayName("저장 전에는 id와 생성·수정 시각이 비어 있다")
        fun 저장_전에는_식별자와_시각이_없다() {
            val member = member()

            // BaseEntity getter가 null 불가로 추론되어 저장 전에 읽으면 NPE라서 필드를 직접 확인한다.
            assertThat(ReflectionTestUtils.getField(member, "id")).isNull()
            assertThat(ReflectionTestUtils.getField(member, "createDate")).isNull()
            assertThat(ReflectionTestUtils.getField(member, "modifyDate")).isNull()
        }
    }

    @Nested
    @DisplayName("정보 변경")
    inner class Update {

        @Test
        @DisplayName("닉네임을 바꿔도 아이디와 비밀번호는 그대로다")
        fun 닉네임_변경() {
            val member = member()

            member.updateNickname("새닉네임")

            assertThat(member.nickname).isEqualTo("새닉네임")
            assertThat(member.username).isEqualTo("user-1")
            assertThat(member.password).isEqualTo("encoded-password")
        }

        @Test
        @DisplayName("비밀번호를 바꿔도 닉네임은 그대로다")
        fun 비밀번호_변경() {
            val member = member()

            member.updatePassword("new-encoded-password")

            assertThat(member.password).isEqualTo("new-encoded-password")
            assertThat(member.nickname).isEqualTo("닉네임")
        }
    }

    @Nested
    @DisplayName("리프레시 토큰")
    inner class RefreshToken {

        @Test
        @DisplayName("저장한 토큰과 같은 값만 일치한다")
        fun 같은_토큰만_일치한다() {
            val member = member()
            member.updateRefreshToken("refresh-token")

            assertThat(member.matchesRefreshToken("refresh-token")).isTrue()
            assertThat(member.matchesRefreshToken("other-token")).isFalse()
        }

        @Test
        @DisplayName("대소문자나 앞뒤 공백이 다르면 일치하지 않는다")
        fun 정확히_같아야_일치한다() {
            val member = member()
            member.updateRefreshToken("Refresh-Token")

            assertThat(member.matchesRefreshToken("refresh-token")).isFalse()
            assertThat(member.matchesRefreshToken(" Refresh-Token")).isFalse()
        }

        @Test
        @DisplayName("저장된 토큰이 없으면 null을 보내도 일치하지 않는다 (null끼리 같다고 보면 인증이 뚫린다)")
        fun 저장된_토큰이_없으면_null도_일치하지_않는다() {
            val member = member()

            assertThat(member.matchesRefreshToken(null)).isFalse()
            assertThat(member.matchesRefreshToken("refresh-token")).isFalse()
        }

        @ParameterizedTest(name = "저장된 토큰이 있을 때 보낸 값=\"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = [" "])
        @DisplayName("저장된 토큰이 있어도 null, 빈 값, 공백은 일치하지 않는다")
        fun 비어_있는_값은_일치하지_않는다(sent: String?) {
            val member = member()
            member.updateRefreshToken("refresh-token")

            assertThat(member.matchesRefreshToken(sent)).isFalse()
        }

        @Test
        @DisplayName("토큰을 null로 바꾸면 폐기되어 이전 토큰이 더 이상 일치하지 않는다")
        fun 토큰_폐기() {
            val member = member()
            member.updateRefreshToken("refresh-token")

            member.updateRefreshToken(null)

            assertThat(member.refreshToken).isNull()
            assertThat(member.matchesRefreshToken("refresh-token")).isFalse()
        }

        @Test
        @DisplayName("새 토큰을 저장하면 이전 토큰은 더 이상 일치하지 않는다")
        fun 토큰_교체() {
            val member = member()
            member.updateRefreshToken("old-token")

            member.updateRefreshToken("new-token")

            assertThat(member.matchesRefreshToken("new-token")).isTrue()
            assertThat(member.matchesRefreshToken("old-token")).isFalse()
        }
    }

    @Nested
    @DisplayName("탈퇴")
    inner class Withdraw {

        @Test
        @DisplayName("탈퇴하면 WITHDRAWN이 되고 리프레시 토큰이 폐기된다")
        fun 탈퇴() {
            val member = member()
            member.updateRefreshToken("refresh-token")

            member.withdraw()

            assertThat(member.status).isEqualTo(MemberStatus.WITHDRAWN)
            assertThat(member.refreshToken).isNull()
            assertThat(member.matchesRefreshToken("refresh-token")).isFalse()
        }

        @Test
        @DisplayName("탈퇴해도 아이디, 닉네임, 비밀번호는 지워지지 않는다")
        fun 탈퇴해도_정보는_남는다() {
            val member = member()

            member.withdraw()

            assertThat(member.username).isEqualTo("user-1")
            assertThat(member.nickname).isEqualTo("닉네임")
            assertThat(member.password).isEqualTo("encoded-password")
        }

        @Test
        @DisplayName("두 번 호출해도 WITHDRAWN 그대로다 (엔티티는 막지 않고 서비스가 막는다)")
        fun 두_번_탈퇴해도_예외가_없다() {
            val member = member()

            member.withdraw()
            member.withdraw()

            assertThat(member.status).isEqualTo(MemberStatus.WITHDRAWN)
        }
    }
}
