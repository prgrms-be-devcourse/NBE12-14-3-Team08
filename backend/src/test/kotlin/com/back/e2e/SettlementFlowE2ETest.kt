package com.back.e2e

import com.back.domain.group.service.GroupService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod
import tools.jackson.databind.JsonNode
import java.sql.Date
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime

// Settlement 중심 E2E 시나리오: 실제 서버에 HTTP로 요청해 가입, 습관 인증, 반려, 벌칙, 그룹 종료, 결산 조회를 한 흐름으로 확인한다.
// (외부 저장소만 가짜. 그룹 종료는 마감일을 과거로 돌린 뒤 실제 자동 종료 로직을 호출해 만든다)
class SettlementFlowE2ETest : E2ETestSupport() {

    @Autowired
    private lateinit var groupService: GroupService

    // 마감일을 어제로 돌리고 자동 종료(스케줄러가 매일 자정에 하는 일)를 직접 실행한다.
    private fun finishGroup(groupId: Long) {
        jdbc.update("update groups set deadline = ? where id = ?", Date.valueOf(LocalDate.now().minusDays(1)), groupId)
        groupService.autoCloseExpiredGroups()
        val status = jdbc.queryForObject("select status from groups where id = ?", String::class.java, groupId)
        assertThat(status).describedAs("자동 종료 후 그룹 상태").isEqualTo("FINISH")
    }

    private fun rejectVerification(ownerToken: String, verificationId: Long) {
        expectStatus(call(HttpMethod.PATCH, "/api/habits/verifications/$verificationId/reject", ownerToken), 200, "습관 인증 반려")
    }

    // 실패한 습관의 벌칙 인증을 올리고 방장이 승인한다. 벌칙이 생긴 날과 제출한 날은 DB를 직접 고쳐 며칠 전으로 되돌린다.
    private fun submitAndApprovePenalty(
        memberToken: String,
        ownerToken: String,
        groupId: Long,
        groupMemberId: Long,
        habitId: Long,
        habitTitle: String,
        createdDaysAgo: Long,
        submittedDaysAgo: Long,
    ) {
        val penalties = expectStatus(call(HttpMethod.GET, "/api/groups/$groupId/members/$groupMemberId/penalties", memberToken), 200, "벌칙 목록")
        val penaltyId = penalties.json.first { it.get("habitTitle").asString() == habitTitle }.get("id").asLong()
        val upload = expectStatus(
            call(HttpMethod.POST, "/api/penalties/$penaltyId/upload-url", memberToken, mapOf("filename" to "penalty.png")),
            200,
            "벌칙 업로드 URL 발급($habitTitle)",
        )
        expectStatus(
            call(
                HttpMethod.POST,
                "/api/habits/$habitId/penalties",
                memberToken,
                mapOf("description" to "벌칙 수행", "imageUrl" to upload.json.get("publicUrl").asString()),
            ),
            201,
            "벌칙 인증 제출($habitTitle)",
        )
        expectStatus(call(HttpMethod.PATCH, "/api/penalties/$penaltyId/approve", ownerToken), 200, "벌칙 승인($habitTitle)")
        jdbc.update(
            "update penalty_verify set create_date = ?, verify_date = ? where id = ?",
            Timestamp.valueOf(LocalDateTime.now().minusDays(createdDaysAgo).withHour(10)),
            Date.valueOf(LocalDate.now().minusDays(submittedDaysAgo)),
            penaltyId,
        )
    }

    private fun settlement(token: String?, groupId: Long) = call(HttpMethod.GET, "/api/groups/$groupId/settlement", token)

    private fun stats(node: JsonNode): List<Long> =
        listOf("totalVerifyCount", "approvedCount", "rejectedCount", "penaltyCount").map { node.get("personalStats").get(it).asLong() }

    private fun winner(node: JsonNode, category: String): Pair<String, Long>? {
        val winner = node.get("groupComparison").get(category)
        return if (winner.isNull) null else winner.get("nickname").asString() to winner.get("value").asLong()
    }

    @Test
    @DisplayName("시나리오 1: 인증, 반려, 벌칙을 쌓고 그룹이 끝나면 개인 통계와 네 부문 1등이 결산으로 나온다")
    fun 결산_전체_흐름() {
        val ownerName = signUp("방장")
        val diligentName = signUp("성실한멤버")
        val rejectedName = signUp("반려받은멤버")
        val outsiderName = signUp("외부인")
        val owner = login(ownerName)
        val diligent = login(diligentName)
        val rejected = login(rejectedName)
        val outsider = login(outsiderName)

        val group = createGroup(owner.accessToken, "결산 E2E 그룹")
        joinGroup(diligent.accessToken, group)
        joinGroup(rejected.accessToken, group)
        val ownerHabit = createHabit(owner.accessToken, group.id, title = "방장 습관")
        val diligentHabit = createHabit(diligent.accessToken, group.id, title = "성실 습관")
        val rejectedHabit = createHabit(rejected.accessToken, group.id, title = "반려 습관")

        // 방장: 인증 한 번을 올리고 검토하지 않아 대기 상태로 남는다.
        backdateVerification(submitVerification(owner.accessToken, ownerHabit), daysAgo = 2)

        // 성실한 멤버: 세 번 모두 승인. 그룹 시작일(14일 전)부터 세어 1일 전만 13일째라 마감일 인증이다. (3일 전은 11일째, 5일 전은 9일째)
        listOf(1L, 3L, 5L).forEach { daysAgo ->
            val verificationId = submitVerification(diligent.accessToken, diligentHabit)
            approveVerification(owner.accessToken, verificationId)
            backdateVerification(verificationId, daysAgo)
        }

        // 반려받은 멤버: 두 번 모두 반려된다. 둘 다 마감일(8일 전은 6일째, 1일 전은 13일째)에 올렸지만 승인된 인증이 아니라서 마감일 인증으로 세지 않는다.
        listOf(8L, 1L).forEach { daysAgo ->
            val verificationId = submitVerification(rejected.accessToken, rejectedHabit)
            rejectVerification(owner.accessToken, verificationId)
            backdateVerification(verificationId, daysAgo)
        }

        // 반려받은 멤버는 습관 두 개를 모두 포기해 벌칙이 두 개 생기고, 둘 다 인증을 올려 방장이 승인한다.
        // 지연 일수는 각각 6일, 3일이고 결산에는 합(9일)이 아니라 가장 긴 6일이 나온다.
        expectStatus(call(HttpMethod.POST, "/api/habits/fail/$rejectedHabit", rejected.accessToken), 200, "첫 습관 포기")
        val secondHabit = createHabit(rejected.accessToken, group.id, title = "두 번째 반려 습관")
        expectStatus(call(HttpMethod.POST, "/api/habits/fail/$secondHabit", rejected.accessToken), 200, "두 번째 습관 포기")
        val rejectedGroupMemberId = groupMemberId(rejected.accessToken, group.id, rejectedName)
        submitAndApprovePenalty(rejected.accessToken, owner.accessToken, group.id, rejectedGroupMemberId, rejectedHabit, "반려 습관", createdDaysAgo = 7, submittedDaysAgo = 1)
        submitAndApprovePenalty(rejected.accessToken, owner.accessToken, group.id, rejectedGroupMemberId, secondHabit, "두 번째 반려 습관", createdDaysAgo = 5, submittedDaysAgo = 2)
        backdateGroup(group.id)

        // 그룹이 끝나기 전에는 결산을 볼 수 없다.
        val early = expectStatus(settlement(owner.accessToken, group.id), 400, "진행 중인 그룹의 결산")
        assertThat(early.json.get("message").asString()).isEqualTo("완료된 그룹만 결산을 조회할 수 있습니다.")

        finishGroup(group.id)

        // 접근 조건
        expectStatus(settlement(null, group.id), 401, "토큰 없이 결산 조회")
        val forbidden = expectStatus(settlement(outsider.accessToken, group.id), 403, "그룹 밖 회원의 결산 조회")
        assertThat(forbidden.json.get("message").asString()).isEqualTo("해당 그룹의 결산을 조회할 권한이 없습니다.")
        expectStatus(settlement(owner.accessToken, Long.MAX_VALUE), 404, "없는 그룹의 결산 조회")

        // 개인 통계: (전체, 승인, 반려, 벌칙)
        val forOwner = expectStatus(settlement(owner.accessToken, group.id), 200, "방장의 결산").json
        val forDiligent = expectStatus(settlement(diligent.accessToken, group.id), 200, "성실한 멤버의 결산").json
        val forRejected = expectStatus(settlement(rejected.accessToken, group.id), 200, "반려받은 멤버의 결산").json

        assertThat(forOwner.get("groupId").asLong()).isEqualTo(group.id)
        assertThat(forOwner.get("groupTitle").asString()).isEqualTo("결산 E2E 그룹")
        assertThat(forOwner.get("personalStats").get("nickname").asString()).isEqualTo("방장")
        assertThat(stats(forOwner)).containsExactly(1L, 0L, 0L, 0L)
        assertThat(stats(forDiligent)).containsExactly(3L, 3L, 0L, 0L)
        assertThat(stats(forRejected)).containsExactly(2L, 0L, 2L, 2L)

        // 그룹 비교는 누가 조회해도 같다. 설명은 인증마다 "오늘도 완료"(6자)라 인증이 많은 쪽이 길다.
        listOf(forOwner, forDiligent, forRejected).forEach { json ->
            assertThat(winner(json, "mostDescriptionChars")).isEqualTo("성실한멤버" to 18L)
            assertThat(winner(json, "mostDeadlineVerifications")).isEqualTo("성실한멤버" to 1L)
            assertThat(winner(json, "mostRejectedVerifications")).isEqualTo("반려받은멤버" to 2L)
            assertThat(winner(json, "longestPenaltyDelay")).isEqualTo("반려받은멤버" to 6L)
        }
    }

    @Test
    @DisplayName("시나리오 2: 인증과 벌칙이 없는 채로 그룹이 끝나면 결산은 모두 0이고 1등은 없다")
    fun 기록_없이_끝난_그룹() {
        val ownerName = signUp("방장")
        val memberName = signUp("멤버")
        val owner = login(ownerName)
        val member = login(memberName)
        val group = createGroup(owner.accessToken, "조용한 그룹")
        joinGroup(member.accessToken, group)

        finishGroup(group.id)

        listOf(owner to "방장", member to "멤버").forEach { (tokens, nickname) ->
            val json = expectStatus(settlement(tokens.accessToken, group.id), 200, "$nickname 의 결산").json

            assertThat(json.get("personalStats").get("nickname").asString()).isEqualTo(nickname)
            assertThat(stats(json)).containsExactly(0L, 0L, 0L, 0L)
            listOf("mostDescriptionChars", "mostDeadlineVerifications", "mostRejectedVerifications", "longestPenaltyDelay").forEach {
                assertThat(winner(json, it)).describedAs(it).isNull()
            }
        }
    }
}
