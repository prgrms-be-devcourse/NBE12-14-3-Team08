package com.back.domain.settlement.controller

import com.back.domain.group.entity.Group
import com.back.domain.group.entity.GroupStatus
import com.back.domain.group.repository.GroupRepository
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.habit.entity.Habit
import com.back.domain.habit.repository.HabitRepository
import com.back.domain.habitVerify.entity.HabitVerify
import com.back.domain.habitVerify.entity.HabitVerifyStatus
import com.back.domain.habitVerify.repository.HabitVerifyRepository
import com.back.domain.member.entity.Member
import com.back.domain.member.repository.MemberRepository
import com.back.domain.member.service.AuthTokenService
import com.back.domain.penaltyverify.entity.PenaltyVerify
import com.back.domain.penaltyverify.repository.PenaltyVerifyRepository
import com.back.global.entity.requiredId
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.SessionFactory
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

// GET /api/groups/{groupId}/settlement 통합 테스트: 실제 보안 필터와 PostgreSQL(Testcontainers)을 거치고, 테스트마다 롤백한다.
// 그룹 생성 시각처럼 직접 바꿀 수 없는 값은 JDBC로 고친 뒤 영속성 컨텍스트를 비워서 서비스가 DB에서 다시 읽게 한다.
@SpringBootTest(properties = ["spring.jpa.properties.hibernate.generate_statistics=true"])
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SettlementApiIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val jsonMapper: JsonMapper,
    private val entityManager: EntityManager,
    private val jdbc: JdbcTemplate,
    private val memberRepository: MemberRepository,
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val habitRepository: HabitRepository,
    private val habitVerifyRepository: HabitVerifyRepository,
    private val penaltyVerifyRepository: PenaltyVerifyRepository,
    private val authTokenService: AuthTokenService
) {

    companion object {
        // 그룹을 14일 전에 만든 것으로 되돌린다. 이 날이 마감일 인증을 세는 시작일이다.
        private val START: LocalDate = LocalDate.now().minusDays(14)
    }

    // --- 픽스처 (모두 실제 DB에 저장한다) ---

    private class Participant(val member: Member, val groupMember: GroupMember, val habit: Habit) {
        val memberId: Long get() = member.requiredId
    }

    private fun saveMember(nickname: String): Member =
        memberRepository.save(Member.create(nickname, "st-" + UUID.randomUUID().toString().substring(0, 12), "encoded-password"))

    private fun token(member: Member): String = authTokenService.createAccessToken(member.requiredId)

    private fun saveGroup(status: GroupStatus = GroupStatus.FINISH, title: String = "결산 테스트 그룹"): Group {
        val group = Group(
            title = title,
            description = "설명",
            deadline = LocalDate.now().plusDays(30),
            penalty = "커피 사기",
            password = "encoded-password",
            inviteCode = UUID.randomUUID().toString().substring(0, 8),
            memberLimit = 10,
            status = status,
        )
        return groupRepository.save(group)
    }

    private fun join(group: Group, member: Member, role: GroupMemberRole = GroupMemberRole.MEMBER): Participant {
        val groupMember = groupMemberRepository.save(GroupMember.create(group, member, role))
        val habit = habitRepository.save(Habit.create(groupMember, "습관", "설명", 3))
        return Participant(member, groupMember, habit)
    }

    private fun saveVerify(who: Participant, daysAfterStart: Long, status: HabitVerifyStatus = HabitVerifyStatus.APPROVED, description: String? = null): HabitVerify {
        val verify = HabitVerify.create(who.habit, START.plusDays(daysAfterStart), description, "https://example.com/a.png")
        when (status) {
            HabitVerifyStatus.APPROVED -> verify.approve()
            HabitVerifyStatus.REJECTED -> verify.reject()
            HabitVerifyStatus.PENDING -> Unit
        }
        return habitVerifyRepository.save(verify)
    }

    // 벌칙은 생성 시각이 저장 시점으로 찍히므로, 지연 일수를 만들려면 생성일을 JDBC로 고쳐야 한다.
    // 벌칙은 (그룹 멤버, 습관)마다 하나라서 한 회원이 벌칙을 여러 개 가지려면 습관이 여러 개여야 한다. (DB 유니크 제약)
    private fun savePenalty(who: Participant, createdDaysAfterStart: Long, submittedDaysAfterStart: Long?, habit: Habit = who.habit): PenaltyVerify {
        val penalty = penaltyVerifyRepository.save(PenaltyVerify.create(who.groupMember, habit))
        if (submittedDaysAfterStart != null) penalty.submit(START.plusDays(submittedDaysAfterStart), "벌칙 수행", "https://example.com/p.png")
        entityManager.flush()
        jdbc.update(
            "update penalty_verify set create_date = ? where id = ?",
            Timestamp.valueOf(START.plusDays(createdDaysAfterStart).atTime(10, 0)),
            penalty.requiredId,
        )
        return penalty
    }

    // 그룹을 14일 전에 만든 것으로 바꾸고 영속성 컨텍스트를 비운다. 데이터를 모두 저장한 뒤 요청 직전에 부른다.
    private fun commitToDb(group: Group) {
        entityManager.flush()
        jdbc.update("update groups set create_date = ? where id = ?", Timestamp.valueOf(START.atTime(9, 0)), group.requiredId)
        entityManager.clear()
    }

    private fun settlement(groupId: Long, token: String? = null): ResultActions {
        val request = get("/api/groups/$groupId/settlement")
        if (token != null) request.header("Authorization", "Bearer $token")
        return mockMvc.perform(request)
    }

    private fun body(result: ResultActions): JsonNode = jsonMapper.readTree(result.andReturn().response.contentAsString)

    private fun keys(node: JsonNode): Set<String> = node.properties().map { it.key }.toSet()

    // --- 접근 조건 ---

    @Nested
    @DisplayName("접근 조건")
    inner class Access {

        @Test
        @DisplayName("토큰이 없으면 401이다")
        fun 토큰_없음() {
            val group = saveGroup()
            commitToDb(group)

            settlement(group.requiredId).andExpect(status().isUnauthorized())
        }

        @Test
        @DisplayName("잘못된 토큰이면 401이다")
        fun 잘못된_토큰() {
            val group = saveGroup()
            commitToDb(group)

            settlement(group.requiredId, "garbage").andExpect(status().isUnauthorized())
        }

        @Test
        @DisplayName("없는 그룹이면 404와 오류 메시지를 돌려준다")
        fun 없는_그룹() {
            val member = saveMember("회원")
            entityManager.flush()

            settlement(Long.MAX_VALUE, token(member))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("존재하지 않는 그룹입니다."))
        }

        @Test
        @DisplayName("그룹 멤버가 아니면 다른 그룹의 멤버여도 403이다")
        fun 멤버가_아니다() {
            val group = saveGroup()
            val owner = join(group, saveMember("방장"), GroupMemberRole.OWNER)
            val otherGroup = saveGroup(title = "다른 그룹")
            val outsider = join(otherGroup, saveMember("외부인"))
            commitToDb(group)

            settlement(group.requiredId, token(outsider.member))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("해당 그룹의 결산을 조회할 권한이 없습니다."))
            assertThat(owner.memberId).isNotEqualTo(outsider.memberId)
        }

        @Test
        @DisplayName("어느 그룹에도 속하지 않은 회원도 403이다")
        fun 그룹이_없는_회원() {
            val group = saveGroup()
            val loner = saveMember("무소속")
            commitToDb(group)

            settlement(group.requiredId, token(loner)).andExpect(status().isForbidden())
        }

        @Test
        @DisplayName("진행 중(ACTIVE)인 그룹이면 멤버여도 400이다")
        fun 진행_중인_그룹() {
            val group = saveGroup(status = GroupStatus.ACTIVE)
            val me = join(group, saveMember("방장"), GroupMemberRole.OWNER)
            commitToDb(group)

            settlement(group.requiredId, token(me.member))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("완료된 그룹만 결산을 조회할 수 있습니다."))
        }

        @Test
        @DisplayName("진행 중인 그룹의 멤버가 아니면 상태 오류가 아니라 403이 먼저다")
        fun 진행_중인_그룹의_외부인() {
            val group = saveGroup(status = GroupStatus.ACTIVE)
            join(group, saveMember("방장"), GroupMemberRole.OWNER)
            val outsider = saveMember("외부인")
            commitToDb(group)

            settlement(group.requiredId, token(outsider)).andExpect(status().isForbidden())
        }

        @Test
        @DisplayName("그룹에서 나간(LEFT) 멤버도 결산을 조회할 수 있다 (현재 동작: 멤버 행만 확인한다)")
        fun 나간_멤버() {
            val group = saveGroup()
            val stayer = join(group, saveMember("남은 사람"), GroupMemberRole.OWNER)
            val leaver = join(group, saveMember("나간 사람"))
            leaver.groupMember.leave()
            commitToDb(group)

            settlement(group.requiredId, token(leaver.member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personalStats.nickname").value("나간 사람"))
            assertThat(stayer.memberId).isNotEqualTo(leaver.memberId)
        }

        @Test
        @DisplayName("탈퇴한 회원의 토큰은 401이다")
        fun 탈퇴한_회원() {
            val group = saveGroup()
            val me = join(group, saveMember("탈퇴할 회원"), GroupMemberRole.OWNER)
            me.member.withdraw()
            commitToDb(group)

            settlement(group.requiredId, token(me.member)).andExpect(status().isUnauthorized())
        }
    }

    // --- 응답 ---

    @Nested
    @DisplayName("응답 형태")
    inner class Shape {

        @Test
        @DisplayName("기록이 없는 완료 그룹은 통계가 모두 0이고 1등은 네 부문 모두 null이다")
        fun 기록이_없다() {
            val group = saveGroup(title = "빈 그룹")
            val me = join(group, saveMember("나"), GroupMemberRole.OWNER)
            commitToDb(group)

            val result = settlement(group.requiredId, token(me.member)).andExpect(status().isOk())
            val json = body(result)

            assertThat(keys(json)).containsExactlyInAnyOrder("groupId", "groupTitle", "personalStats", "groupComparison")
            assertThat(json.get("groupId").asLong()).isEqualTo(group.requiredId)
            assertThat(json.get("groupTitle").asString()).isEqualTo("빈 그룹")
            assertThat(keys(json.get("personalStats")))
                .containsExactlyInAnyOrder("memberId", "nickname", "approvedCount", "totalVerifyCount", "rejectedCount", "penaltyCount")
            assertThat(json.get("personalStats").get("memberId").asLong()).isEqualTo(me.memberId)
            assertThat(listOf("approvedCount", "totalVerifyCount", "rejectedCount", "penaltyCount").map { json.get("personalStats").get(it).asLong() })
                .containsOnly(0L)
            assertThat(keys(json.get("groupComparison")))
                .containsExactlyInAnyOrder("mostDescriptionChars", "mostDeadlineVerifications", "mostRejectedVerifications", "longestPenaltyDelay")
            assertThat(json.get("groupComparison").properties().map { it.value.isNull }).containsOnly(true)
        }

        @Test
        @DisplayName("1등 항목은 회원 id, 닉네임, 값을 담는다")
        fun 일등_항목() {
            val group = saveGroup()
            val me = join(group, saveMember("반려왕"), GroupMemberRole.OWNER)
            saveVerify(me, 1, HabitVerifyStatus.REJECTED)
            saveVerify(me, 2, HabitVerifyStatus.REJECTED)
            commitToDb(group)

            val winner = body(settlement(group.requiredId, token(me.member)).andExpect(status().isOk())).get("groupComparison").get("mostRejectedVerifications")

            assertThat(keys(winner)).containsExactlyInAnyOrder("memberId", "nickname", "value")
            assertThat(winner.get("memberId").asLong()).isEqualTo(me.memberId)
            assertThat(winner.get("nickname").asString()).isEqualTo("반려왕")
            assertThat(winner.get("value").asLong()).isEqualTo(2)
        }
    }

    // --- 실제 DB 데이터 집계 ---

    @Nested
    @DisplayName("집계 결과")
    inner class Aggregation {

        // 세 명의 기록: 에이(방장)는 설명을 길게, 비는 마감일에, 씨는 반려와 벌칙 지연이 크다.
        private fun scenario(): Triple<Group, Triple<Participant, Participant, Participant>, Unit> {
            val group = saveGroup()
            val a = join(group, saveMember("에이"), GroupMemberRole.OWNER)
            val b = join(group, saveMember("비"))
            val c = join(group, saveMember("씨"))

            saveVerify(a, 1, description = "아주 길게 쓴 인증 설명입니다")
            saveVerify(a, 2, description = "오늘도 완료")
            saveVerify(b, 6)
            saveVerify(b, 13)
            saveVerify(b, 14, HabitVerifyStatus.PENDING)
            saveVerify(c, 3, HabitVerifyStatus.REJECTED, "반려됨")
            saveVerify(c, 4, HabitVerifyStatus.REJECTED)
            saveVerify(c, 5, HabitVerifyStatus.APPROVED)

            // 씨는 습관 두 개가 모두 실패해 벌칙이 두 개다. (지연 일수는 두 벌칙 중 큰 값 5일)
            val secondHabit = habitRepository.save(Habit.create(c.groupMember, "두 번째 습관", "설명", 3))
            savePenalty(c, createdDaysAfterStart = 7, submittedDaysAfterStart = 12)
            savePenalty(c, createdDaysAfterStart = 8, submittedDaysAfterStart = 9, habit = secondHabit)
            savePenalty(b, createdDaysAfterStart = 7, submittedDaysAfterStart = null)
            commitToDb(group)
            return Triple(group, Triple(a, b, c), Unit)
        }

        @Test
        @DisplayName("개인 통계는 조회한 회원의 인증과 벌칙만 센다")
        fun 개인_통계() {
            val (group, members, _) = scenario()
            val (a, b, c) = members

            val statsOf = { who: Participant ->
                body(settlement(group.requiredId, token(who.member)).andExpect(status().isOk())).get("personalStats")
            }

            val forA = statsOf(a)
            assertThat(listOf("approvedCount", "totalVerifyCount", "rejectedCount", "penaltyCount").map { forA.get(it).asLong() })
                .containsExactly(2L, 2L, 0L, 0L)

            val forB = statsOf(b)
            assertThat(listOf("approvedCount", "totalVerifyCount", "rejectedCount", "penaltyCount").map { forB.get(it).asLong() })
                .containsExactly(2L, 3L, 0L, 1L)

            val forC = statsOf(c)
            assertThat(forC.get("nickname").asString()).isEqualTo("씨")
            assertThat(listOf("approvedCount", "totalVerifyCount", "rejectedCount", "penaltyCount").map { forC.get(it).asLong() })
                .containsExactly(1L, 3L, 2L, 2L)
        }

        @Test
        @DisplayName("그룹 비교의 네 부문 1등은 누가 조회해도 같다")
        fun 그룹_비교() {
            val (group, members, _) = scenario()
            val (a, b, c) = members

            val comparison = { who: Participant ->
                body(settlement(group.requiredId, token(who.member)).andExpect(status().isOk())).get("groupComparison")
            }

            val forA = comparison(a)
            // 설명 글자 수(공백 포함): 에이는 "아주 길게 쓴 인증 설명입니다"(16) + "오늘도 완료"(6) = 22, 씨는 "반려됨"(3)
            assertThat(forA.get("mostDescriptionChars").get("nickname").asString()).isEqualTo("에이")
            assertThat(forA.get("mostDescriptionChars").get("value").asLong()).isEqualTo(22)
            // 마감일 인증: 비의 승인된 인증 중 시작일 + 6, 13일 두 건 (14일 인증은 대기 중이라 제외)
            assertThat(forA.get("mostDeadlineVerifications").get("nickname").asString()).isEqualTo("비")
            assertThat(forA.get("mostDeadlineVerifications").get("value").asLong()).isEqualTo(2)
            // 반려: 씨 두 건
            assertThat(forA.get("mostRejectedVerifications").get("nickname").asString()).isEqualTo("씨")
            assertThat(forA.get("mostRejectedVerifications").get("value").asLong()).isEqualTo(2)
            // 벌칙 지연: 씨의 5일(7일 생성 → 12일 제출)과 1일 중 최댓값. 비는 제출하지 않아 제외
            assertThat(forA.get("longestPenaltyDelay").get("nickname").asString()).isEqualTo("씨")
            assertThat(forA.get("longestPenaltyDelay").get("value").asLong()).isEqualTo(5)

            assertThat(comparison(b)).isEqualTo(forA)
            assertThat(comparison(c)).isEqualTo(forA)
        }

        @Test
        @DisplayName("다른 그룹의 인증과 벌칙은 집계에 들어가지 않는다")
        fun 다른_그룹은_제외() {
            val group = saveGroup(title = "내 그룹")
            val me = join(group, saveMember("나"), GroupMemberRole.OWNER)
            saveVerify(me, 1, description = "내 설명")

            val other = saveGroup(title = "남의 그룹")
            val stranger = join(other, saveMember("남"), GroupMemberRole.OWNER)
            saveVerify(stranger, 1, HabitVerifyStatus.REJECTED, "아주 아주 긴 남의 그룹 설명입니다")
            saveVerify(stranger, 6)
            savePenalty(stranger, createdDaysAfterStart = 1, submittedDaysAfterStart = 20)
            commitToDb(group)

            val json = body(settlement(group.requiredId, token(me.member)).andExpect(status().isOk()))

            assertThat(json.get("personalStats").get("totalVerifyCount").asLong()).isEqualTo(1)
            val comparison = json.get("groupComparison")
            assertThat(comparison.get("mostDescriptionChars").get("nickname").asString()).isEqualTo("나")
            assertThat(comparison.get("mostDeadlineVerifications").isNull).isTrue()
            assertThat(comparison.get("mostRejectedVerifications").isNull).isTrue()
            assertThat(comparison.get("longestPenaltyDelay").isNull).isTrue()
        }

        @Test
        @DisplayName("동률이면 회원 id가 작은 쪽이 1등이다 (먼저 가입한 회원)")
        fun 동률() {
            val group = saveGroup()
            val first = join(group, saveMember("먼저"), GroupMemberRole.OWNER)
            val second = join(group, saveMember("나중"))
            saveVerify(second, 1, HabitVerifyStatus.REJECTED)
            saveVerify(first, 1, HabitVerifyStatus.REJECTED)
            commitToDb(group)

            val winner = body(settlement(group.requiredId, token(second.member)).andExpect(status().isOk())).get("groupComparison").get("mostRejectedVerifications")

            assertThat(winner.get("nickname").asString()).isEqualTo("먼저")
            assertThat(winner.get("memberId").asLong()).isEqualTo(first.memberId)
        }
    }

    // --- 쿼리 ---

    @Nested
    @DisplayName("쿼리 수")
    inner class QueryCount {

        // 데이터 크기를 키워도 쿼리 수가 늘지 않는지 보려고, 같은 요청의 SQL 실행 수를 센다.
        // 인증과 벌칙은 서로 다른 쿼리가 회원을 채우므로, 한쪽 데이터만 두어야 그쪽 쿼리의 누락이 다른 쿼리에 가려지지 않는다.
        private fun queriesFor(memberCount: Int, verifiesPerMember: Int, penaltiesPerMember: Int): Long {
            val group = saveGroup(title = "쿼리 $memberCount")
            val people = (1..memberCount).map { index ->
                join(group, saveMember("회원$index"), if (index == 1) GroupMemberRole.OWNER else GroupMemberRole.MEMBER)
            }
            people.forEach { who ->
                repeat(verifiesPerMember) { day -> saveVerify(who, day.toLong(), description = "설명 $day") }
                repeat(penaltiesPerMember) { index ->
                    val habit = if (index == 0) who.habit else habitRepository.save(Habit.create(who.groupMember, "추가 습관 $index", "설명", 3))
                    savePenalty(who, createdDaysAfterStart = 1, submittedDaysAfterStart = 3, habit = habit)
                }
            }
            commitToDb(group)

            val statistics = entityManager.entityManagerFactory.unwrap(SessionFactory::class.java).statistics
            statistics.clear()
            settlement(group.requiredId, token(people.first().member)).andExpect(status().isOk())
            return statistics.prepareStatementCount
        }

        @Test
        @DisplayName("인증을 올린 회원과 인증 수가 늘어도 SQL 실행 횟수는 같다 (인증 조회에 N+1이 없다)")
        fun 인증_쿼리는_데이터_크기와_무관하다() {
            val small = queriesFor(memberCount = 2, verifiesPerMember = 2, penaltiesPerMember = 0)
            val large = queriesFor(memberCount = 8, verifiesPerMember = 10, penaltiesPerMember = 0)

            assertThat(large).describedAs("작은 그룹 $small 회, 큰 그룹 $large 회").isEqualTo(small)
        }

        @Test
        @DisplayName("벌칙을 가진 회원과 벌칙 수가 늘어도 SQL 실행 횟수는 같다 (벌칙 조회에 N+1이 없다)")
        fun 벌칙_쿼리는_데이터_크기와_무관하다() {
            val small = queriesFor(memberCount = 2, verifiesPerMember = 0, penaltiesPerMember = 1)
            val large = queriesFor(memberCount = 8, verifiesPerMember = 0, penaltiesPerMember = 3)

            assertThat(large).describedAs("작은 그룹 $small 회, 큰 그룹 $large 회").isEqualTo(small)
        }
    }
}
