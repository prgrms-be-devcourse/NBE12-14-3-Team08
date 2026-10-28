package com.back.domain.member.repository

import com.back.domain.member.entity.Member
import com.back.domain.member.entity.MemberStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException

// Testcontainers PostgreSQL에서 MemberRepository의 조회 쿼리와 유니크 제약을 검증한다. (테스트마다 롤백)
@DataJpaTest
class MemberRepositoryTest @Autowired constructor(
    private val memberRepository: MemberRepository
) {

    private fun save(username: String, refreshToken: String? = null): Member {
        val member = Member.create("닉네임", username, "encoded-password")
        member.updateRefreshToken(refreshToken)
        return memberRepository.saveAndFlush(member)
    }

    @Nested
    @DisplayName("저장")
    inner class Persist {

        @Test
        @DisplayName("저장하면 ACTIVE 상태이고 생성·수정 시각이 채워진다")
        fun 저장_기본값() {
            val saved = save("persist-user")

            assertThat(saved.id).isNotNull()
            assertThat(saved.status).isEqualTo(MemberStatus.ACTIVE)
            assertThat(saved.createDate).isNotNull()
            assertThat(saved.modifyDate).isNotNull()
            assertThat(saved.refreshToken).isNull()
        }
    }

    @Nested
    @DisplayName("아이디 조회")
    inner class ByUsername {

        @Test
        @DisplayName("existsByUsername: 저장된 아이디만 true다")
        fun 아이디_존재_여부() {
            save("exists-user")

            assertThat(memberRepository.existsByUsername("exists-user")).isTrue()
            assertThat(memberRepository.existsByUsername("other-user")).isFalse()
        }

        @Test
        @DisplayName("existsByUsername: 대소문자가 다르면 다른 아이디다 (현재 동작)")
        fun 대소문자를_구분한다() {
            save("CaseUser")

            assertThat(memberRepository.existsByUsername("caseuser")).isFalse()
            assertThat(memberRepository.existsByUsername("CaseUser")).isTrue()
        }

        @Test
        @DisplayName("findByUsername: 아이디로 회원을 찾고 없으면 비어 있다")
        fun 아이디로_조회() {
            val saved = save("find-user")

            assertThat(memberRepository.findByUsername("find-user").orElseThrow().id).isEqualTo(saved.id)
            assertThat(memberRepository.findByUsername("missing-user")).isEmpty
        }
    }

    @Nested
    @DisplayName("상태 조회")
    inner class ByStatus {

        @Test
        @DisplayName("existsByIdAndStatus: 현재 상태와 같을 때만 true다")
        fun 상태별_존재_여부() {
            val saved = save("status-user")
            val id = requireNotNull(saved.id)

            assertThat(memberRepository.existsByIdAndStatus(id, MemberStatus.ACTIVE)).isTrue()
            assertThat(memberRepository.existsByIdAndStatus(id, MemberStatus.WITHDRAWN)).isFalse()
        }

        @Test
        @DisplayName("existsByIdAndStatus: 탈퇴하면 ACTIVE가 false가 된다 (인증 필터가 이 쿼리로 탈퇴 회원을 거른다)")
        fun 탈퇴하면_활성이_아니다() {
            val saved = save("withdraw-user")
            val id = requireNotNull(saved.id)
            saved.withdraw()
            memberRepository.flush()

            assertThat(memberRepository.existsByIdAndStatus(id, MemberStatus.ACTIVE)).isFalse()
            assertThat(memberRepository.existsByIdAndStatus(id, MemberStatus.WITHDRAWN)).isTrue()
        }

        @Test
        @DisplayName("existsByIdAndStatus: 없는 id는 false다")
        fun 없는_id() {
            assertThat(memberRepository.existsByIdAndStatus(Long.MAX_VALUE, MemberStatus.ACTIVE)).isFalse()
        }
    }

    @Nested
    @DisplayName("유니크 제약")
    inner class UniqueConstraint {

        @Test
        @DisplayName("같은 아이디로 저장하면 DB 제약 위반이다")
        fun 아이디는_유일하다() {
            save("unique-user")

            assertThatThrownBy { save("unique-user") }.isInstanceOf(DataIntegrityViolationException::class.java)
        }

        @Test
        @DisplayName("같은 리프레시 토큰을 두 회원이 가질 수 없다")
        fun 리프레시_토큰은_유일하다() {
            save("token-a", refreshToken = "same-refresh-token")

            assertThatThrownBy { save("token-b", refreshToken = "same-refresh-token") }
                .isInstanceOf(DataIntegrityViolationException::class.java)
        }

        @Test
        @DisplayName("리프레시 토큰이 없는(null) 회원은 여러 명이어도 된다")
        fun 토큰이_없는_회원은_여러_명_가능하다() {
            save("null-a")
            save("null-b")

            assertThat(memberRepository.existsByUsername("null-a")).isTrue()
            assertThat(memberRepository.existsByUsername("null-b")).isTrue()
        }

        @Test
        @DisplayName("닉네임은 같아도 저장된다")
        fun 닉네임은_유일하지_않다() {
            save("nick-a")
            save("nick-b")

            assertThat(memberRepository.count()).isGreaterThanOrEqualTo(2)
        }
    }
}
