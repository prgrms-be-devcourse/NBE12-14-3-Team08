package com.back.domain.habitVerify.controller

import com.back.domain.group.entity.Group
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
import com.back.global.entity.requiredId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate
import java.util.UUID

// GET /api/groups/{groupId}/habits/verifications/photos 를 실제 DB(Testcontainers PostgreSQL)로 확인한다.
// 읽기 전용 API라 테스트마다 롤백한다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HabitVerifyPhotoControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val objectMapper: JsonMapper,
    private val authTokenService: AuthTokenService,
    private val memberRepository: MemberRepository,
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val habitRepository: HabitRepository,
    private val habitVerifyRepository: HabitVerifyRepository
) {

    private lateinit var group: Group
    private lateinit var alice: Member
    private lateinit var bob: Member
    private lateinit var outsider: Member
    private lateinit var aliceHabit: Habit
    private lateinit var bobHabit: Habit

    private fun uniqueName(prefix: String) = prefix + "-" + UUID.randomUUID().toString().substring(0, 8)

    private fun token(member: Member) = "Bearer " + authTokenService.createAccessToken(member.requiredId)

    @BeforeEach
    fun setUp() {
        group = groupRepository.save(
            Group.builder().title("그룹").penalty("벌칙").password("1234").inviteCode(uniqueName("code")).memberLimit(10).build(),
        )
        alice = memberRepository.save(Member.create("앨리스", uniqueName("alice"), "pass1234"))
        bob = memberRepository.save(Member.create("밥", uniqueName("bob"), "pass1234"))
        outsider = memberRepository.save(Member.create("외부인", uniqueName("outsider"), "pass1234"))
        aliceHabit = habitRepository.save(Habit.create(groupMemberRepository.save(GroupMember.create(group, alice, GroupMemberRole.OWNER)), "스트레칭", "설명", 3))
        bobHabit = habitRepository.save(Habit.create(groupMemberRepository.save(GroupMember.create(group, bob, GroupMemberRole.MEMBER)), "물 마시기", "설명", 3))
    }

    private fun photo(
        habit: Habit,
        date: LocalDate,
        status: HabitVerifyStatus = HabitVerifyStatus.APPROVED,
        imageUrl: String? = "https://example.com/${UUID.randomUUID()}.png",
        width: Int? = null,
        height: Int? = null,
    ): HabitVerify {
        val verify = HabitVerify.create(habit, date, "인증 설명", imageUrl, width, height)
        when (status) {
            HabitVerifyStatus.APPROVED -> verify.approve()
            HabitVerifyStatus.REJECTED -> verify.reject()
            HabitVerifyStatus.PENDING -> Unit
        }
        return habitVerifyRepository.save(verify)
    }

    private fun request(member: Member, query: String = "", groupId: Long = group.requiredId) =
        mockMvc.perform(get("/api/groups/$groupId/habits/verifications/photos$query").header("Authorization", token(member)))

    private fun json(result: MvcResult): JsonNode = objectMapper.readTree(result.response.contentAsString)

    private fun keys(node: JsonNode): Set<String> = node.properties().map { it.key }.toSet()

    private fun ids(node: JsonNode): List<Long> = node.get("items").let { items -> (0 until items.size()).map { items.get(it).get("id").asLong() } }

    @Nested
    @DisplayName("접근 권한")
    inner class Access {

        @Test
        @DisplayName("토큰이 없으면 401이다")
        fun 토큰이_없다() {
            mockMvc.perform(get("/api/groups/${group.id}/habits/verifications/photos")).andExpect(status().isUnauthorized)
        }

        @Test
        @DisplayName("없는 그룹이면 404이다")
        fun 없는_그룹() {
            request(alice, groupId = 999_999_999L).andExpect(status().isNotFound).andExpect(jsonPath("$.message").value("존재하지 않는 그룹입니다."))
        }

        @Test
        @DisplayName("그룹 멤버가 아니면 403이다")
        fun 멤버가_아니다() {
            photo(aliceHabit, LocalDate.of(2026, 10, 20))

            request(outsider).andExpect(status().isForbidden)
        }

        @Test
        @DisplayName("그룹 멤버면 누구나 조회할 수 있다 (방장이 아니어도)")
        fun 일반_멤버도_조회() {
            photo(aliceHabit, LocalDate.of(2026, 10, 20))

            request(bob).andExpect(status().isOk).andExpect(jsonPath("$.items.length()").value(1))
        }

        @Test
        @DisplayName("나간(LEFT) 멤버도 조회할 수 있다 (현재 동작: 멤버 기록이 남아 있으면 허용, 정책 확인 필요)")
        fun 나간_멤버도_조회된다() {
            photo(aliceHabit, LocalDate.of(2026, 10, 20))
            groupMemberRepository.findByGroupIdAndMemberId(group.requiredId, bob.requiredId).get().leave()

            request(bob).andExpect(status().isOk)
        }
    }

    @Nested
    @DisplayName("응답")
    inner class Response {

        @Test
        @DisplayName("항목과 nextCursor만 내려가고, 항목은 정해진 필드를 가진다")
        fun 응답_형태() {
            val saved = photo(bobHabit, LocalDate.of(2026, 10, 20))

            val result = request(alice).andExpect(status().isOk).andReturn()
            val body = json(result)
            val item = body.get("items").single()

            assertThat(keys(body)).containsExactlyInAnyOrder("items", "nextCursor")
            assertThat(body.get("nextCursor").isNull).isTrue()
            assertThat(keys(item)).containsExactlyInAnyOrder(
                "id", "verifyDate", "habitTitle", "memberId", "memberNickname", "description", "imageUrl", "imageWidth", "imageHeight",
            )
            assertThat(item.get("id").asLong()).isEqualTo(saved.requiredId)
            assertThat(item.get("verifyDate").asString()).isEqualTo("2026-10-20")
            assertThat(item.get("habitTitle").asString()).isEqualTo("물 마시기")
            assertThat(item.get("memberId").asLong()).isEqualTo(bob.requiredId)
            assertThat(item.get("memberNickname").asString()).isEqualTo("밥")
            assertThat(item.get("description").asString()).isEqualTo("인증 설명")
            assertThat(item.get("imageUrl").asString()).isEqualTo(saved.imageUrl)
        }

        @Test
        @DisplayName("승인되고 사진이 있는 인증만 최신순으로 나온다")
        fun 승인된_사진만_최신순() {
            val old = photo(aliceHabit, LocalDate.of(2026, 10, 18))
            val recent = photo(bobHabit, LocalDate.of(2026, 10, 20))
            photo(aliceHabit, LocalDate.of(2026, 10, 19), status = HabitVerifyStatus.PENDING)
            photo(aliceHabit, LocalDate.of(2026, 10, 21), status = HabitVerifyStatus.REJECTED)
            photo(aliceHabit, LocalDate.of(2026, 10, 22), imageUrl = null)

            val body = json(request(alice).andExpect(status().isOk).andReturn())

            assertThat(ids(body)).containsExactly(recent.requiredId, old.requiredId)
        }

        @Test
        @DisplayName("크기를 저장한 사진은 가로·세로를 주고, 크기를 저장하기 전의 사진은 둘 다 null이다")
        fun 사진_크기() {
            photo(aliceHabit, LocalDate.of(2026, 10, 20), width = 4032, height = 3024)
            photo(bobHabit, LocalDate.of(2026, 10, 19))

            val items = json(request(alice).andExpect(status().isOk).andReturn()).get("items")

            assertThat(items.get(0).get("imageWidth").asInt()).isEqualTo(4032)
            assertThat(items.get(0).get("imageHeight").asInt()).isEqualTo(3024)
            assertThat(items.get(1).get("imageWidth").isNull).isTrue()
            assertThat(items.get(1).get("imageHeight").isNull).isTrue()
        }

        @Test
        @DisplayName("사진이 하나도 없으면 빈 목록이다")
        fun 사진이_없다() {
            request(alice).andExpect(status().isOk).andExpect(jsonPath("$.items.length()").value(0)).andExpect(jsonPath("$.nextCursor").doesNotExist())
        }

        @Test
        @DisplayName("memberId를 주면 그 멤버의 사진만 나온다")
        fun 멤버로_거른다() {
            val mine = photo(aliceHabit, LocalDate.of(2026, 10, 20))
            photo(bobHabit, LocalDate.of(2026, 10, 21))

            val body = json(request(bob, "?memberId=${alice.requiredId}").andExpect(status().isOk).andReturn())

            assertThat(ids(body)).containsExactly(mine.requiredId)
        }
    }

    @Nested
    @DisplayName("페이지")
    inner class Paging {

        private fun fiveByAlternating(): List<HabitVerify> =
            (1..5).map { day -> photo(if (day % 2 == 0) aliceHabit else bobHabit, LocalDate.of(2026, 10, day)) }   // 오래된 것부터 저장

        @Test
        @DisplayName("nextCursor를 따라가면 모든 사진을 빠짐없이, 중복 없이, 최신순으로 받고 마지막 페이지의 nextCursor는 null이다")
        fun 끝까지_따라간다() {
            val saved = fiveByAlternating()
            val collected = mutableListOf<Long>()
            var cursor: String? = null
            var pages = 0

            do {
                val query = "?size=2" + (cursor?.let { "&cursor=$it" } ?: "")
                val body = json(request(alice, query).andExpect(status().isOk).andReturn())
                collected += ids(body)
                cursor = body.get("nextCursor").takeIf { !it.isNull }?.asString()
                pages++
            } while (cursor != null)

            assertThat(pages).isEqualTo(3)
            assertThat(collected).containsExactlyElementsOf(saved.map { it.requiredId }.reversed())
        }

        @Test
        @DisplayName("첫 페이지를 받은 뒤 더 최신 인증이 올라와도 다음 페이지는 밀리지 않는다")
        fun 새_인증이_와도_밀리지_않는다() {
            val saved = fiveByAlternating()
            val first = json(request(alice, "?size=2").andExpect(status().isOk).andReturn())

            photo(aliceHabit, LocalDate.of(2026, 10, 25))   // 첫 페이지 뒤에 올라온 더 최신 인증

            val second = json(request(alice, "?size=2&cursor=${first.get("nextCursor").asString()}").andExpect(status().isOk).andReturn())

            assertThat(ids(first)).containsExactly(saved[4].requiredId, saved[3].requiredId)
            assertThat(ids(second)).containsExactly(saved[2].requiredId, saved[1].requiredId)
        }

        @Test
        @DisplayName("같은 날짜가 페이지 경계에 걸려도 빠지거나 겹치지 않는다")
        fun 같은_날짜가_경계에_걸린다() {
            val a = photo(aliceHabit, LocalDate.of(2026, 10, 20))
            val b = photo(bobHabit, LocalDate.of(2026, 10, 20))
            val c = photo(aliceHabit, LocalDate.of(2026, 10, 19))

            val first = json(request(alice, "?size=1").andExpect(status().isOk).andReturn())
            val second = json(request(alice, "?size=1&cursor=${first.get("nextCursor").asString()}").andExpect(status().isOk).andReturn())
            val third = json(request(alice, "?size=1&cursor=${second.get("nextCursor").asString()}").andExpect(status().isOk).andReturn())

            assertThat(ids(first) + ids(second) + ids(third)).containsExactly(b.requiredId, a.requiredId, c.requiredId)
            assertThat(third.get("nextCursor").isNull).isTrue()
        }

        @Test
        @DisplayName("size를 주지 않으면 60건씩 주고, 더 있으면 nextCursor가 있다")
        fun 기본_크기는_60() {
            val start = LocalDate.of(2026, 1, 1)
            (0 until 61).forEach { photo(aliceHabit, start.plusDays(it.toLong())) }

            val body = json(request(alice).andExpect(status().isOk).andReturn())

            assertThat(body.get("items")).hasSize(60)
            assertThat(body.get("nextCursor").isNull).isFalse()
        }

        @Test
        @DisplayName("size는 최대 120까지 받는다")
        fun 최대_크기는_120() {
            val start = LocalDate.of(2026, 1, 1)
            (0 until 121).forEach { photo(aliceHabit, start.plusDays(it.toLong())) }

            val body = json(request(alice, "?size=120").andExpect(status().isOk).andReturn())

            assertThat(body.get("items")).hasSize(120)
            assertThat(body.get("nextCursor").isNull).isFalse()
        }
    }

    @Nested
    @DisplayName("잘못된 요청")
    inner class BadRequest {

        @ParameterizedTest(name = "size={0}")
        @ValueSource(strings = ["0", "-1", "121", "abc", "1.5"])
        @DisplayName("size가 1~120의 정수가 아니면 400이다")
        fun 잘못된_size(size: String) {
            request(alice, "?size=$size").andExpect(status().isBadRequest)
        }

        @Test
        @DisplayName("size를 비워서 보내면 기본값(60)으로 처리한다")
        fun 빈_size는_기본값() {
            request(alice, "?size=").andExpect(status().isOk)
        }

        @ParameterizedTest(name = "cursor={0}")
        @ValueSource(strings = ["garbage", "2026-10-20", "2026-10-20:abc", "2026-13-40:1"])
        @DisplayName("형식이 맞지 않는 커서는 400이다")
        fun 잘못된_커서(cursor: String) {
            request(alice, "?cursor=$cursor").andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("잘못된 커서입니다."))
        }

        @Test
        @DisplayName("memberId가 숫자가 아니면 400이다")
        fun 잘못된_memberId() {
            request(alice, "?memberId=abc").andExpect(status().isBadRequest)
        }
    }
}
