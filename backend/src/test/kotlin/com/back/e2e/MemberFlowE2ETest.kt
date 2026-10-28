package com.back.e2e

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import java.sql.Date
import java.time.LocalDate

// Member 중심 E2E 시나리오: 실제 서버에 HTTP로 요청해 가입, 로그인, 정보 수정, 토큰 재발급, 탈퇴를 한 흐름으로 확인한다.
// (회원 상태와 저장된 비밀번호·토큰은 DB에서 직접 읽어 확인한다)
class MemberFlowE2ETest : E2ETestSupport() {

    private fun loginBody(username: String, password: String) = mapOf("username" to username, "password" to password)

    private fun storedValue(column: String, username: String): String? =
        jdbc.queryForObject("select $column from member where username = ?", String::class.java, username)

    // 탈퇴하면 아이디가 바뀌므로 탈퇴 전후를 같은 회원으로 보려면 id로 조회한다.
    private fun storedValueById(column: String, memberId: Long): String? =
        jdbc.queryForObject("select $column from member where id = ?", String::class.java, memberId)

    @Test
    @DisplayName("시나리오 1: 가입, 로그인, 내 정보 조회, 닉네임과 비밀번호 수정, 새 비밀번호로 다시 로그인한다")
    fun 가입부터_정보_수정까지() {
        val username = signUp("수정회원")
        val tokens = login(username)

        // 내 정보에는 비밀번호나 토큰이 노출되지 않는다.
        val me = expectStatus(call(HttpMethod.GET, "/api/members/me", tokens.accessToken), 200, "내 정보")
        assertThat(me.json.get("username").asString()).isEqualTo(username)
        assertThat(me.json.get("nickname").asString()).isEqualTo("수정회원")
        assertThat(me.json.properties().map { it.key }).doesNotContain("password", "refreshToken", "status")

        // 저장된 비밀번호는 평문이 아니라 BCrypt 해시다.
        assertThat(storedValue("password", username)).startsWith("$2").isNotEqualTo(PASSWORD)

        // 닉네임 수정
        val renamed = expectStatus(
            call(HttpMethod.PATCH, "/api/members/me", tokens.accessToken, mapOf("nickname" to "새닉네임")),
            200,
            "닉네임 수정",
        )
        assertThat(renamed.json.get("nickname").asString()).isEqualTo("새닉네임")
        val afterRename = expectStatus(call(HttpMethod.GET, "/api/members/me", tokens.accessToken), 200, "수정 후 내 정보")
        assertThat(afterRename.json.get("nickname").asString()).isEqualTo("새닉네임")

        // 기존 비밀번호가 틀리면 바뀌지 않는다.
        expectStatus(
            call(HttpMethod.PATCH, "/api/members/me", tokens.accessToken, mapOf("currentPassword" to "wrong-password", "newPassword" to "newPassword5678")),
            400,
            "틀린 기존 비밀번호로 변경",
        )
        expectStatus(call(HttpMethod.POST, "/api/auth/login", body = loginBody(username, PASSWORD)), 200, "비밀번호 변경 실패 후 기존 비밀번호 로그인")

        // 기존 비밀번호가 맞으면 바뀌고, 이전 비밀번호로는 로그인할 수 없다.
        expectStatus(
            call(HttpMethod.PATCH, "/api/members/me", tokens.accessToken, mapOf("currentPassword" to PASSWORD, "newPassword" to "newPassword5678")),
            200,
            "비밀번호 변경",
        )
        expectStatus(call(HttpMethod.POST, "/api/auth/login", body = loginBody(username, PASSWORD)), 401, "이전 비밀번호 로그인")
        val relogin = expectStatus(call(HttpMethod.POST, "/api/auth/login", body = loginBody(username, "newPassword5678")), 200, "새 비밀번호 로그인")

        // 새로 받은 토큰으로 내 정보를 조회할 수 있고 닉네임은 유지된다.
        val meAgain = expectStatus(call(HttpMethod.GET, "/api/members/me", relogin.json.get("accessToken").asString()), 200, "새 토큰으로 내 정보")
        assertThat(meAgain.json.get("nickname").asString()).isEqualTo("새닉네임")
    }

    @Test
    @DisplayName("시나리오 2: 리프레시 토큰은 가입 때 발급되어 DB에 저장되고, 로그인과 재발급에서 같은 값이 쓰이며, 변조하면 거절된다")
    fun 토큰_발급과_재발급() {
        val username = signUp("토큰회원")

        // 가입할 때 발급된 리프레시 토큰이 DB에 저장되어 있고, 로그인은 그 값을 그대로 돌려준다. (로그인해도 새로 발급하지 않는다)
        val stored = storedValue("refresh_token", username)
        assertThat(stored).isNotBlank()
        val first = login(username)
        val second = login(username)
        assertThat(first.refreshToken).isEqualTo(stored)
        assertThat(second.refreshToken).isEqualTo(stored)

        // 재발급: 새 액세스 토큰을 주고 리프레시 토큰은 그대로이며, 새 액세스 토큰으로 요청이 성공한다.
        val refreshed = expectStatus(
            call(HttpMethod.POST, "/api/auth/refresh", body = mapOf("refreshToken" to first.refreshToken)),
            200,
            "토큰 재발급",
        )
        assertThat(refreshed.json.get("refreshToken").asString()).isEqualTo(first.refreshToken)
        assertThat(refreshed.json.get("tokenType").asString()).isEqualTo("Bearer")
        expectStatus(call(HttpMethod.GET, "/api/members/me", refreshed.json.get("accessToken").asString()), 200, "재발급 받은 토큰으로 내 정보")

        // 리프레시 토큰은 API 인증에 쓸 수 없다. (액세스 토큰만 통과한다)
        expectStatus(call(HttpMethod.GET, "/api/members/me", first.refreshToken), 401, "리프레시 토큰으로 내 정보")

        // 액세스 토큰, 변조한 토큰, 아무 문자열은 재발급 자리에서 거절된다.
        expectStatus(call(HttpMethod.POST, "/api/auth/refresh", body = mapOf("refreshToken" to first.accessToken)), 401, "액세스 토큰으로 재발급")
        val tampered = first.refreshToken.dropLast(4) + (if (first.refreshToken.endsWith("AAAA")) "BBBB" else "AAAA")
        expectStatus(call(HttpMethod.POST, "/api/auth/refresh", body = mapOf("refreshToken" to tampered)), 401, "변조한 토큰으로 재발급")
        expectStatus(call(HttpMethod.POST, "/api/auth/refresh", body = mapOf("refreshToken" to "garbage")), 401, "아무 문자열로 재발급")
        expectStatus(call(HttpMethod.POST, "/api/auth/refresh", body = mapOf("refreshToken" to "")), 400, "빈 토큰으로 재발급")
    }

    @Test
    @DisplayName("시나리오 3: 진행 중인 방이 있으면 탈퇴가 막히고, 방이 끝난 뒤에는 탈퇴되며 모든 토큰이 거절된다")
    fun 탈퇴_흐름() {
        val username = signUp("탈퇴회원")
        val tokens = login(username)
        val group = createGroup(tokens.accessToken)

        // 진행 중인 방(방장)이 있으면 탈퇴할 수 없고, 계정은 그대로 쓸 수 있다.
        val blocked = expectStatus(call(HttpMethod.DELETE, "/api/members/me", tokens.accessToken), 409, "진행 중인 방이 있을 때 탈퇴")
        assertThat(blocked.json.get("message").asString()).isEqualTo("진행 중인 방이 있어 회원 탈퇴를 할 수 없습니다.")
        expectStatus(call(HttpMethod.GET, "/api/members/me", tokens.accessToken), 200, "탈퇴 실패 후 내 정보")
        assertThat(storedValue("status", username)).isEqualTo("ACTIVE")

        val memberId = jdbc.queryForObject("select id from member where username = ?", Long::class.java, username)!!

        // 마감일이 지나(어제) 방이 끝나면 탈퇴할 수 있다.
        jdbc.update("update groups set deadline = ? where id = ?", Date.valueOf(LocalDate.now().minusDays(1)), group.id)
        expectStatus(call(HttpMethod.DELETE, "/api/members/me", tokens.accessToken), 204, "방이 끝난 뒤 탈퇴")

        // DB: 회원은 WITHDRAWN이고 리프레시 토큰이 폐기되며, 아이디와 비밀번호는 익명 값으로 바뀌고 닉네임은 남는다. 방의 멤버 기록은 LEFT로 남는다.
        assertThat(storedValueById("status", memberId)).isEqualTo("WITHDRAWN")
        assertThat(storedValueById("refresh_token", memberId)).isNull()
        assertThat(storedValueById("username", memberId)).isEqualTo("withdrawn_$memberId")
        assertThat(storedValueById("password", memberId)).isEqualTo("!withdrawn")
        assertThat(storedValueById("nickname", memberId)).isEqualTo("탈퇴회원")
        val memberStatuses = jdbc.queryForList(
            "select gm.status from group_member gm where gm.member_id = ?",
            String::class.java,
            memberId,
        )
        assertThat(memberStatuses).containsExactly("LEFT")

        // 탈퇴한 뒤에는 기존 액세스 토큰, 리프레시 토큰, 로그인이 모두 거절된다.
        expectStatus(call(HttpMethod.GET, "/api/members/me", tokens.accessToken), 401, "탈퇴 후 기존 액세스 토큰")
        expectStatus(call(HttpMethod.POST, "/api/auth/refresh", body = mapOf("refreshToken" to tokens.refreshToken)), 401, "탈퇴 후 기존 리프레시 토큰")
        val login = expectStatus(call(HttpMethod.POST, "/api/auth/login", body = loginBody(username, PASSWORD)), 401, "탈퇴 후 로그인")
        assertThat(login.json.get("message").asString()).isEqualTo("아이디 또는 비밀번호가 일치하지 않습니다.")

        // 탈퇴한 아이디로는 다시 가입할 수 있고, 새 회원은 다른 id이며 이전 기록을 이어받지 않는다.
        val rejoin = expectStatus(
            call(HttpMethod.POST, "/api/members", body = mapOf("nickname" to "다시", "username" to username, "password" to PASSWORD)),
            201,
            "탈퇴한 아이디로 재가입",
        )
        assertThat(rejoin.json.get("memberId").asLong()).isNotEqualTo(memberId)
        val rejoinTokens = login(username)
        expectStatus(call(HttpMethod.GET, "/api/members/me", rejoinTokens.accessToken), 200, "재가입한 회원 내 정보")
        val newMemberships = jdbc.queryForList(
            "select gm.status from group_member gm where gm.member_id = ?",
            String::class.java,
            rejoin.json.get("memberId").asLong(),
        )
        assertThat(newMemberships).isEmpty()
    }

    @Test
    @DisplayName("시나리오 4: 한 회원이 탈퇴해도 같은 방의 다른 회원은 계속 쓸 수 있다")
    fun 다른_회원은_영향을_받지_않는다() {
        val ownerName = signUp("방장")
        val memberName = signUp("나가는멤버")
        val owner = login(ownerName)
        val member = login(memberName)
        val group = createGroup(owner.accessToken)
        joinGroup(member.accessToken, group)

        // 방이 끝나면 멤버가 먼저 탈퇴한다.
        jdbc.update("update groups set deadline = ? where id = ?", Date.valueOf(LocalDate.now().minusDays(1)), group.id)
        expectStatus(call(HttpMethod.DELETE, "/api/members/me", member.accessToken), 204, "멤버 탈퇴")

        expectStatus(call(HttpMethod.GET, "/api/members/me", member.accessToken), 401, "탈퇴한 멤버의 토큰")
        val ownerMe = expectStatus(call(HttpMethod.GET, "/api/members/me", owner.accessToken), 200, "방장의 내 정보")
        assertThat(ownerMe.json.get("username").asString()).isEqualTo(ownerName)
        assertThat(storedValue("status", ownerName)).isEqualTo("ACTIVE")
        expectStatus(call(HttpMethod.GET, "/api/groups/${group.id}", owner.accessToken), 200, "방장의 그룹 조회")
    }

    @Test
    @DisplayName("시나리오 5: 잘못된 가입과 로그인은 알맞은 상태 코드로 거절되고, 인증 헤더가 없거나 잘못되면 401이다")
    fun 실패_응답() {
        val username = signUp("기존회원")

        // 가입
        val duplicated = expectStatus(
            call(HttpMethod.POST, "/api/members", body = mapOf("nickname" to "중복", "username" to username, "password" to PASSWORD)),
            409,
            "중복 아이디 가입",
        )
        assertThat(duplicated.json.get("message").asString()).isEqualTo("이미 사용 중인 아이디입니다.")
        val invalid = expectStatus(call(HttpMethod.POST, "/api/members", body = mapOf("nickname" to " ")), 400, "필수값 누락 가입")
        val errors: List<String> = invalid.json.get("errors").toList().map { it.asString() }
        assertThat(errors).containsExactlyInAnyOrder("이름은 필수입니다.", "아이디는 필수입니다.", "비밀번호는 필수입니다.")

        // 로그인: 없는 아이디와 틀린 비밀번호는 같은 메시지라 가입 여부를 알 수 없다.
        val unknown = expectStatus(call(HttpMethod.POST, "/api/auth/login", body = loginBody("e2e-nobody-zzzz", PASSWORD)), 401, "없는 아이디 로그인")
        val wrong = expectStatus(call(HttpMethod.POST, "/api/auth/login", body = loginBody(username, "wrong-password")), 401, "틀린 비밀번호 로그인")
        assertThat(unknown.json.get("message").asString()).isEqualTo("아이디 또는 비밀번호가 일치하지 않습니다.")
        assertThat(wrong.json.get("message").asString()).isEqualTo(unknown.json.get("message").asString())
        expectStatus(call(HttpMethod.POST, "/api/auth/login", body = mapOf("username" to username)), 400, "비밀번호 누락 로그인")

        // 인증이 필요한 API
        expectStatus(call(HttpMethod.GET, "/api/members/me"), 401, "토큰 없이 내 정보")
        expectStatus(call(HttpMethod.GET, "/api/members/me", "garbage"), 401, "잘못된 토큰으로 내 정보")
        expectStatus(call(HttpMethod.DELETE, "/api/members/me"), 401, "토큰 없이 탈퇴")
    }
}
