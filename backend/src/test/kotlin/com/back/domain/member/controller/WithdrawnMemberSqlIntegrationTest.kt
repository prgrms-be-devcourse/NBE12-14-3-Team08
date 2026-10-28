package com.back.domain.member.controller

import com.back.domain.member.entity.Member
import com.back.domain.member.entity.MemberProvider
import com.back.domain.member.entity.MemberStatus
import org.assertj.core.api.Assertions.assertThat
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.nio.file.Files
import java.nio.file.Path

// docs/sql/anonymize-withdrawn-members.sql을 실제 PostgreSQL(Testcontainers)에 적용해 본다.
// 정책 변경 전에 탈퇴한 회원(아이디·비밀번호·소셜 식별자가 그대로 남은 행)이 새 정책과 같은 값이 되고,
// 활성 회원은 건드리지 않으며, 풀린 아이디로 다시 가입할 수 있는지 확인한다.
class WithdrawnMemberSqlIntegrationTest : MemberApiTestSupport() {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var entityManager: EntityManager

    // JDBC로 직접 바꾸는 동안 JPA가 들고 있는 예전 값이 나중에 덮어쓰지 않도록, 먼저 반영하고 끝난 뒤 비운다.
    private fun withJdbc(block: () -> Unit) {
        entityManager.flush()
        block()
        entityManager.clear()
    }

    // Gradle 테스트의 작업 디렉터리는 backend/이고, 스크립트는 저장소 루트의 docs/에 있다.
    private fun runScript() = withJdbc {
        val script = Files.readString(Path.of("..", "docs", "sql", "anonymize-withdrawn-members.sql"))
        jdbcTemplate.execute(script)
    }

    // 정책 변경 전의 탈퇴 상태를 만든다: 상태만 WITHDRAWN이고 나머지 값은 그대로다.
    private fun oldStyleWithdraw(memberId: Long) = withJdbc {
        jdbcTemplate.update("update member set status = 'WITHDRAWN', refresh_token = null where id = ?", memberId)
    }

    private fun row(memberId: Long): Map<String, Any?> =
        jdbcTemplate.queryForMap(
            "select username, nickname, password, provider, provider_id, refresh_token, status from member where id = ?",
            memberId,
        )

    @Test
    @DisplayName("예전 방식으로 탈퇴한 회원은 새 정책과 같은 값(익명 닉네임 포함)이 되고, 활성 회원은 그대로다")
    fun 예전_탈퇴_회원이_익명화된다() {
        val oldId = signUpOk("old-withdrawn", "예전탈퇴")
        val activeId = signUpOk("still-active", "활성회원")
        oldStyleWithdraw(oldId)
        val activeBefore = row(activeId)

        runScript()

        val old = row(oldId)
        assertThat(old["username"]).isEqualTo("withdrawn_$oldId")
        assertThat(old["password"]).isEqualTo(Member.WITHDRAWN_PASSWORD)
        assertThat(old["nickname"]).isEqualTo(Member.WITHDRAWN_NICKNAME)
        assertThat(old["provider"]).isEqualTo(MemberProvider.LOCAL.name)
        assertThat(old["provider_id"]).isNull()
        assertThat(old["refresh_token"]).isNull()
        assertThat(old["status"]).isEqualTo(MemberStatus.WITHDRAWN.name)
        assertThat(row(activeId)).isEqualTo(activeBefore)
    }

    @Test
    @DisplayName("예전 방식으로 탈퇴한 소셜 회원도 소셜 식별자가 비워져 같은 구글 계정으로 다시 가입할 수 있다")
    fun 예전_탈퇴_소셜_회원() {
        val member = memberRepository.saveAndFlush(
            Member.createSocial(MemberProvider.GOOGLE, "google-sub-1", "구글회원", "google_google-sub-1", "hash"),
        )
        val memberId = requireNotNull(member.id)
        oldStyleWithdraw(memberId)

        runScript()

        val row = row(memberId)
        assertThat(row["provider"]).isEqualTo(MemberProvider.LOCAL.name)
        assertThat(row["provider_id"]).isNull()
        assertThat(row["username"]).isEqualTo("withdrawn_$memberId")
        assertThat(row["nickname"]).isEqualTo(Member.WITHDRAWN_NICKNAME)
    }

    @Test
    @DisplayName("스크립트를 실행하면 예전 탈퇴 회원의 아이디가 풀려 같은 아이디로 다시 가입할 수 있다")
    fun 아이디가_풀려_재가입할_수_있다() {
        val oldId = signUpOk("rejoin-user", "다시올사람")
        oldStyleWithdraw(oldId)
        // 스크립트 실행 전에는 아이디가 점유되어 있다.
        signUp("rejoin-user").andExpect(status().isConflict())

        runScript()

        val newId = signUpOk("rejoin-user", "다시올사람")
        assertThat(newId).isNotEqualTo(oldId)
    }

    @Test
    @DisplayName("여러 번 실행해도 결과가 같다")
    fun 여러_번_실행해도_같다() {
        val oldId = signUpOk("twice-user", "두번")
        oldStyleWithdraw(oldId)

        runScript()
        val afterFirst = row(oldId)
        runScript()

        assertThat(row(oldId)).isEqualTo(afterFirst)
    }
}
