package com.back.e2e

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import java.nio.charset.StandardCharsets
import java.util.Date

// Habit 중심 E2E 시나리오: 실제 서버에 HTTP로 요청해 가입부터 판정, 벌칙까지 한 흐름으로 확인한다.
// (외부 저장소만 가짜. 일주일이 지난 상태는 DB의 생성 시각을 되돌려 만든다)
class HabitFlowE2ETest : E2ETestSupport() {

    @Test
    @DisplayName("시나리오 1: 인증을 채우면 방장의 주간 판정 결과가 PASSED이고 습관은 계속 진행된다")
    fun 정상_흐름_인증을_채우면_PASSED() {
        val ownerName = signUp("방장")
        val memberName = signUp("멤버")
        val owner = login(ownerName)
        val member = login(memberName)
        val group = createGroup(owner.accessToken)
        joinGroup(member.accessToken, group)
        val habitId = createHabit(member.accessToken, group.id, days = 3)

        // 사흘 동안 인증을 내고 방장이 승인한다. (같은 날 한 번만 낼 수 있어 낼 때마다 날짜를 과거로 옮긴다)
        repeat(3) { i ->
            val verificationId = submitVerification(member.accessToken, habitId)
            approveVerification(owner.accessToken, verificationId)
            backdateVerification(verificationId, daysAgo = i + 1L)
        }
        backdateGroup(group.id)

        val check = expectStatus(
            call(HttpMethod.POST, "/api/groups/${group.id}/weekly-check", owner.accessToken),
            200,
            "주간 판정",
        )

        assertThat(check.json.size()).isEqualTo(1)
        assertThat(check.json.get(0).get("habitId").asLong()).isEqualTo(habitId)
        assertThat(check.json.get(0).get("result").asString()).isEqualTo("PASSED")
        assertThat(check.json.get(0).get("habitStatus").asString()).isEqualTo("ACTIVE")

        val detail = expectStatus(call(HttpMethod.GET, "/api/habits/$habitId", member.accessToken), 200, "습관 상세")
        assertThat(detail.json.get("status").asString()).isEqualTo("ACTIVE")
    }

    @Test
    @DisplayName("시나리오 2: 인증이 모자라면 주간 판정에서 습관이 실패 처리되고 벌칙이 REQUIRED로 생긴다")
    fun 실패_흐름_인증이_부족하면_FAILED와_벌칙() {
        val ownerName = signUp("방장")
        val memberName = signUp("멤버")
        val owner = login(ownerName)
        val member = login(memberName)
        val group = createGroup(owner.accessToken)
        joinGroup(member.accessToken, group)
        val habitId = createHabit(member.accessToken, group.id, title = "아침 운동", days = 3)

        // 주 3회가 목표인데 한 번만 인증하고 승인받는다.
        val verificationId = submitVerification(member.accessToken, habitId)
        approveVerification(owner.accessToken, verificationId)
        backdateVerification(verificationId, daysAgo = 1)
        backdateGroup(group.id)

        val check = expectStatus(
            call(HttpMethod.POST, "/api/groups/${group.id}/weekly-check", owner.accessToken),
            200,
            "주간 판정",
        )

        assertThat(check.json.get(0).get("result").asString()).isEqualTo("FAILED_INSUFFICIENT_SUBMISSION")
        assertThat(check.json.get(0).get("habitStatus").asString()).isEqualTo("FAILED")

        val detail = expectStatus(call(HttpMethod.GET, "/api/habits/$habitId", member.accessToken), 200, "습관 상세")
        assertThat(detail.json.get("status").asString()).isEqualTo("FAILED")

        val groupMemberId = groupMemberId(member.accessToken, group.id, memberName)
        val penalties = expectStatus(
            call(HttpMethod.GET, "/api/groups/${group.id}/members/$groupMemberId/penalties", member.accessToken),
            200,
            "벌칙 목록",
        )
        assertThat(penalties.json.size()).isEqualTo(1)
        assertThat(penalties.json.get(0).get("status").asString()).isEqualTo("REQUIRED")
        assertThat(penalties.json.get(0).get("penaltyText").asString()).isEqualTo(GROUP_PENALTY)
        assertThat(penalties.json.get(0).get("habitTitle").asString()).isEqualTo("아침 운동")
    }

    @Test
    @DisplayName("시나리오 3: 습관을 포기하면 벌칙이 생기고, 바로 새 습관을 만들 수 있다")
    fun 포기_흐름_포기하면_벌칙이_생기고_새_습관을_만들_수_있다() {
        val ownerName = signUp("방장")
        val memberName = signUp("멤버")
        val owner = login(ownerName)
        val member = login(memberName)
        val group = createGroup(owner.accessToken)
        joinGroup(member.accessToken, group)
        val habitId = createHabit(member.accessToken, group.id, title = "독서")

        // 이미 활성 습관이 있으면 새 습관은 만들 수 없다.
        expectStatus(
            call(HttpMethod.POST, "/api/groups/${group.id}/habits", member.accessToken, mapOf("title" to "운동", "days" to 3)),
            409,
            "활성 습관이 있을 때 생성",
        )

        expectStatus(call(HttpMethod.POST, "/api/habits/fail/$habitId", member.accessToken), 200, "습관 포기")

        val groupMemberId = groupMemberId(member.accessToken, group.id, memberName)
        val penalties = expectStatus(
            call(HttpMethod.GET, "/api/groups/${group.id}/members/$groupMemberId/penalties", member.accessToken),
            200,
            "벌칙 목록",
        )
        assertThat(penalties.json.size()).isEqualTo(1)
        assertThat(penalties.json.get(0).get("status").asString()).isEqualTo("REQUIRED")

        // 활성 습관은 없어지고, 실패 목록에 들어간다.
        expectStatus(call(HttpMethod.GET, "/api/groups/${group.id}/habits/active", member.accessToken), 400, "활성 습관 조회")
        val failed = expectStatus(call(HttpMethod.GET, "/api/groups/${group.id}/habits/fail", member.accessToken), 200, "실패 습관 목록")
        assertThat(failed.json.size()).isEqualTo(1)
        assertThat(failed.json.get(0).get("id").asLong()).isEqualTo(habitId)

        // 새 습관은 바로 만들 수 있다.
        val created = expectStatus(
            call(HttpMethod.POST, "/api/groups/${group.id}/habits", member.accessToken, mapOf("title" to "운동", "days" to 3)),
            200,
            "새 습관 생성",
        )
        assertThat(created.json.get("status").asString()).isEqualTo("ACTIVE")
    }

    @Test
    @DisplayName("시나리오 4: 인증 없음은 401, 다른 그룹 회원은 403, 만료된 토큰은 401이고 재발급 받은 토큰으로 다시 요청하면 성공한다")
    fun 권한과_토큰_흐름() {
        val ownerName = signUp("방장")
        val memberName = signUp("멤버")
        val outsiderName = signUp("외부인")
        val owner = login(ownerName)
        val member = login(memberName)
        val outsider = login(outsiderName)
        val group = createGroup(owner.accessToken)
        joinGroup(member.accessToken, group)
        val habitId = createHabit(member.accessToken, group.id)
        val detailPath = "/api/habits/$habitId"

        // 접근 권한
        expectStatus(call(HttpMethod.GET, detailPath), 401, "토큰 없이 습관 조회")
        expectStatus(call(HttpMethod.GET, detailPath, outsider.accessToken), 403, "다른 그룹 회원의 습관 조회")
        expectStatus(call(HttpMethod.GET, detailPath, member.accessToken), 200, "같은 그룹 회원의 습관 조회")
        expectStatus(call(HttpMethod.POST, "/api/groups/${group.id}/weekly-check", member.accessToken), 403, "방장이 아닌 회원의 주간 판정")

        // 만료된 액세스 토큰은 401 (프론트는 이 응답을 보고 토큰을 재발급한다)
        val memberId = expectStatus(call(HttpMethod.GET, "/api/members/me", member.accessToken), 200, "내 정보").json.get("memberId").asLong()
        val expired = Jwts.builder()
            .subject(memberId.toString())
            .claim("tokenType", "access")
            .issuedAt(Date(System.currentTimeMillis() - 3_600_000))
            .expiration(Date(System.currentTimeMillis() - 1_800_000))
            .signWith(Keys.hmacShaKeyFor(jwtSecret.toByteArray(StandardCharsets.UTF_8)))
            .compact()
        expectStatus(call(HttpMethod.GET, detailPath, expired), 401, "만료된 토큰으로 습관 조회")

        // 액세스 토큰을 재발급 자리에 쓰면 거절된다.
        expectStatus(call(HttpMethod.POST, "/api/auth/refresh", body = mapOf("refreshToken" to member.accessToken)), 401, "액세스 토큰으로 재발급")

        // 리프레시 토큰으로 재발급 받으면 같은 요청이 성공한다.
        val refreshed = expectStatus(
            call(HttpMethod.POST, "/api/auth/refresh", body = mapOf("refreshToken" to member.refreshToken)),
            200,
            "토큰 재발급",
        )
        val newAccessToken = refreshed.json.get("accessToken").asString()
        val detail = expectStatus(call(HttpMethod.GET, detailPath, newAccessToken), 200, "재발급 받은 토큰으로 습관 조회")
        assertThat(detail.json.get("id").asLong()).isEqualTo(habitId)
    }
}
