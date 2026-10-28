package com.back.domain.member.service

import com.back.domain.member.dto.CreateMemberRequest
import com.back.support.ConcurrentRunner
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

// 같은 아이디로 여러 요청이 동시에 가입할 때(더블클릭, 재시도) 회원이 한 명만 만들어지는지 확인한다.
// 실제로 커밋되는 트랜잭션이 필요해서 @Transactional 없이 실행하고, 끝나면 만든 데이터를 직접 지운다.
@SpringBootTest
@ActiveProfiles("test")
class MemberJoinConcurrencyTest @Autowired constructor(
    private val memberService: MemberService,
    private val jdbc: JdbcTemplate
) {

    private val username = "join-race-" + UUID.randomUUID().toString().substring(0, 8)

    companion object {
        private const val THREAD_COUNT = 8
    }

    @AfterEach
    fun tearDown() {
        jdbc.update("delete from member where username = ?", username)
    }

    private fun joinConcurrently(): List<Throwable?> =
        ConcurrentRunner.runAll(
            List(THREAD_COUNT) { { memberService.join(CreateMemberRequest("닉네임", username, "password1234")) } },
        )

    private fun memberCount(): Int =
        jdbc.queryForObject("select count(*) from member where username = ?", Int::class.java, username) ?: 0

    @Test
    @DisplayName("같은 아이디로 동시에 가입해도 성공은 한 번이고 회원은 한 명만 만들어진다 (데이터 무결성)")
    fun 동시에_가입해도_회원은_한_명이다() {
        val results = joinConcurrently()

        assertThat(results.count { it == null }).describedAs("성공 횟수").isEqualTo(1)
        assertThat(memberCount()).describedAs("회원 수").isEqualTo(1)
    }

    @Test
    @DisplayName("동시에 가입했을 때 진 요청은 충돌(IllegalStateException, 409)로 끝난다")
    fun 동시에_가입하면_진_요청은_충돌로_끝난다() {
        val results = joinConcurrently()

        val unexpected = results.filterNotNull().filter { it !is IllegalStateException }
        assertThat(unexpected)
            .describedAs("충돌(IllegalStateException) 외의 예외: ${unexpected.map { it.javaClass.simpleName }}")
            .isEmpty()
        assertThat(results.count { it is IllegalStateException }).describedAs("충돌 횟수").isEqualTo(THREAD_COUNT - 1)
    }
}
