package com.back.domain.habit.controller

import com.back.domain.group.dto.GroupRequest
import com.back.domain.group.entity.Group
import com.back.domain.group.repository.GroupRepository
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.habit.dto.CreateHabitRequest
import com.back.domain.habit.entity.Habit
import com.back.domain.habit.entity.HabitStatus
import com.back.domain.habit.repository.HabitRepository
import com.back.domain.member.dto.CreateMemberRequest
import com.back.domain.member.repository.MemberRepository
import com.back.domain.member.service.AuthTokenService
import com.back.domain.penaltyverify.entity.PenaltyVerifyStatus
import com.back.domain.penaltyverify.repository.PenaltyVerifyRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.stream.Stream

// 습관 생성·포기·조회 API 통합 테스트. (주간 판정은 HabitWeeklyCheckControllerTest, 상세 조회는 HabitDetailControllerTest)
// Testcontainers PostgreSQL(test 프로필)을 쓰고 테스트 종료 시 롤백한다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HabitControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jsonMapper: JsonMapper

    @Autowired
    private lateinit var authTokenService: AuthTokenService

    @Autowired
    private lateinit var memberRepository: MemberRepository

    @Autowired
    private lateinit var groupRepository: GroupRepository

    @Autowired
    private lateinit var groupMemberRepository: GroupMemberRepository

    @Autowired
    private lateinit var habitRepository: HabitRepository

    @Autowired
    private lateinit var penaltyVerifyRepository: PenaltyVerifyRepository

    private var groupId: Long = 0L
    private lateinit var ownerToken: String // 방장 (습관 없음)
    private var memberId: Long = 0L
    private lateinit var memberToken: String // 일반 멤버 (활성 습관 "물 마시기" 1개 보유)
    private var habitId: Long = 0L

    companion object {
        // @Nested 클래스(inner)에는 static 멤버를 둘 수 없어 바깥 클래스에 두고 이름으로 참조한다.
        @JvmStatic
        fun invalidRequests(): Stream<Arguments> = Stream.of(
            Arguments.of("제목이 빈 문자열", CreateHabitRequest("", "설명", 3)),
            Arguments.of("제목이 없음", CreateHabitRequest(null, "설명", 3)),
            Arguments.of("실천 일수가 0", CreateHabitRequest("운동", "설명", 0)),
            Arguments.of("실천 일수가 8", CreateHabitRequest("운동", "설명", 8)),
        )
    }

    @BeforeEach
    fun setUp() {
        // 1. 방장 멤버 생성 -> 로그인 토큰 발급
        val ownerId = createMember("owner01", "방장")
        ownerToken = authTokenService.createAccessToken(ownerId)

        // 2. 방장이 그룹 생성 (그룹 생성 시 방장은 자동으로 OWNER GroupMember가 됨)
        // deadline은 null이면 가입 시 서비스에서 NPE가 나므로 미래 날짜로 지정한다.
        val createGroupBody = jsonMapper.writeValueAsString(
            GroupRequest.Create("테스트 그룹", "설명", LocalDate.now().plusMonths(1), "커피 사기", "1234", 5),
        )

        val groupResponse = mockMvc.perform(
            post("/api/groups")
                .header("Authorization", "Bearer $ownerToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createGroupBody),
        )
            .andExpect(status().isCreated())
            .andReturn().response.contentAsString

        val groupNode = jsonMapper.readTree(groupResponse)
        groupId = groupNode.get("id").asLong()
        val inviteCode = groupNode.get("inviteCode").asText()

        // 3. 일반 멤버 생성 -> 로그인 토큰 발급
        memberId = createMember("member01", "멤버1")
        memberToken = authTokenService.createAccessToken(memberId)

        // 4. 일반 멤버가 초대 코드 + 그룹 비밀번호로 그룹 가입
        val joinBody = jsonMapper.writeValueAsString(GroupRequest.Join("1234"))

        mockMvc.perform(
            post("/api/groups/join/{inviteCode}", inviteCode)
                .header("Authorization", "Bearer $memberToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(joinBody),
        )
            .andExpect(status().isOk())

        // 5. 가입한 멤버가 습관 생성 (주 3일 인증 필요)
        val createHabitBody = jsonMapper.writeValueAsString(CreateHabitRequest("물 마시기", "하루 2L 마시기", 3))

        val habitResponse = mockMvc.perform(
            post("/api/groups/{groupId}/habits", groupId)
                .header("Authorization", "Bearer $memberToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createHabitBody),
        )
            .andReturn().response.contentAsString

        habitId = jsonMapper.readTree(habitResponse).get("id").asLong()
    }

    private fun createMember(username: String, nickname: String): Long {
        val body = jsonMapper.writeValueAsString(CreateMemberRequest(nickname, username, "password1234"))

        val response = mockMvc.perform(
            post("/api/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )
            .andExpect(status().isCreated())
            .andReturn().response.contentAsString

        return jsonMapper.readTree(response).get("memberId").asLong()
    }

    private fun createHabit(token: String, request: Any): ResultActions =
        mockMvc.perform(
            post("/api/groups/{groupId}/habits", groupId)
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)),
        )

    private fun failHabit(token: String, targetHabitId: Long): ResultActions =
        mockMvc.perform(
            post("/api/habits/fail/{habitId}", targetHabitId)
                .header("Authorization", "Bearer $token"),
        )

    private fun finishGroup() {
        val group = groupRepository.findById(groupId).orElseThrow()
        group.finish()
        groupRepository.saveAndFlush(group)
    }

    @Nested
    @DisplayName("습관 생성 POST /api/groups/{groupId}/habits")
    inner class CreateHabitApi {

        @Test
        @DisplayName("활성 습관이 없는 멤버가 생성하면 ACTIVE 습관이 만들어진다")
        fun 생성_성공() {
            createHabit(ownerToken, CreateHabitRequest("독서 30분", "책 읽기", 5))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("독서 30분"))
                .andExpect(jsonPath("$.description").value("책 읽기"))
                .andExpect(jsonPath("$.days").value(5))
                .andExpect(jsonPath("$.status").value("ACTIVE"))

            // 방장 1개 + setUp에서 만든 멤버 1개
            assertThat(habitRepository.findAllByGroupMember_Group_IdAndStatus(groupId, HabitStatus.ACTIVE))
                .hasSize(2)
        }

        @Test
        @DisplayName("이미 활성 습관이 있으면 409를 반환하고 습관이 더 생기지 않는다")
        fun 활성_습관이_이미_있으면_409() {
            createHabit(memberToken, CreateHabitRequest("독서 30분", "책 읽기", 5))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 활성화된 습관이 존재합니다."))

            assertThat(habitRepository.findAllByGroupMember_Group_IdAndStatus(groupId, HabitStatus.ACTIVE))
                .hasSize(1)
        }

        @ParameterizedTest(name = "{0}이면 400")
        @MethodSource("com.back.domain.habit.controller.HabitControllerTest#invalidRequests")
        @DisplayName("요청 값이 올바르지 않으면 400을 반환하고 습관을 만들지 않는다")
        fun 잘못된_요청은_400(ignoredCase: String, request: CreateHabitRequest) {
            createHabit(ownerToken, request)
                .andExpect(status().isBadRequest())

            assertThat(habitRepository.findAllByGroupMember_Group_IdAndStatus(groupId, HabitStatus.ACTIVE))
                .hasSize(1)
        }

        @Test
        @DisplayName("그룹 멤버가 아니면 400을 반환한다")
        fun 그룹_멤버가_아니면_400() {
            val otherToken = authTokenService.createAccessToken(createMember("other01", "다른멤버"))

            createHabit(otherToken, CreateHabitRequest("독서 30분", "책 읽기", 5))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("해당 그룹의 멤버를 찾을 수 없습니다."))
        }

        @Test
        @DisplayName("종료된 그룹에서는 400을 반환하고 습관을 만들지 않는다")
        fun 종료된_그룹이면_400() {
            finishGroup()

            createHabit(ownerToken, CreateHabitRequest("독서 30분", "책 읽기", 5))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이미 종료된 그룹에서는 습관을 추가할 수 없습니다."))

            assertThat(habitRepository.findAllByGroupMember_Group_IdAndStatus(groupId, HabitStatus.ACTIVE))
                .hasSize(1)
        }
    }

    @Nested
    @DisplayName("습관 포기 POST /api/habits/fail/{habitId}")
    inner class FailHabitApi {

        @Test
        @DisplayName("본인 습관을 포기하면 FAILED가 되고 REQUIRED 벌칙이 그룹의 벌칙 문구로 생성된다")
        fun 포기_성공() {
            failHabit(memberToken, habitId).andExpect(status().isOk())

            val habit = habitRepository.findById(habitId).orElseThrow()
            assertThat(habit.status).isEqualTo(HabitStatus.FAILED)

            val penalty = penaltyVerifyRepository.findByHabitId(habitId).orElseThrow()
            assertThat(penalty.status).isEqualTo(PenaltyVerifyStatus.REQUIRED)
            assertThat(penalty.penaltyText).isEqualTo("커피 사기")
            assertThat(penalty.habitTitle).isEqualTo("물 마시기")
        }

        @Test
        @DisplayName("포기하면 활성 습관이 없어지고, 벌칙과 무관하게 새 습관을 바로 만들 수 있다")
        fun 포기_후_새_습관_생성_가능() {
            failHabit(memberToken, habitId).andExpect(status().isOk())

            mockMvc.perform(
                get("/api/groups/{groupId}/habits/active", groupId)
                    .header("Authorization", "Bearer $memberToken"),
            )
                .andExpect(status().isBadRequest())

            createHabit(memberToken, CreateHabitRequest("독서 30분", "책 읽기", 5))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
        }

        @Test
        @DisplayName("다른 사람의 습관은 포기시킬 수 없다 (400, 상태와 벌칙은 그대로)")
        fun 타인의_습관이면_400() {
            failHabit(ownerToken, habitId).andExpect(status().isBadRequest())

            assertThat(habitRepository.findById(habitId).orElseThrow().status)
                .isEqualTo(HabitStatus.ACTIVE)
            assertThat(penaltyVerifyRepository.findByHabitId(habitId)).isEmpty()
        }

        @Test
        @DisplayName("존재하지 않는 습관이면 400을 반환한다")
        fun 없는_습관이면_400() {
            failHabit(memberToken, 999_999L).andExpect(status().isBadRequest())
        }

        @Test
        @DisplayName("이미 포기한 습관을 다시 포기하면 409를 반환하고 벌칙이 더 생기지 않는다")
        fun 이미_포기한_습관이면_409() {
            failHabit(memberToken, habitId).andExpect(status().isOk())
            val penaltyCountAfterFirstFail = penaltyVerifyRepository.count()

            failHabit(memberToken, habitId).andExpect(status().isConflict())

            assertThat(penaltyVerifyRepository.count()).isEqualTo(penaltyCountAfterFirstFail)
        }

        @Test
        @DisplayName("종료된 그룹에서는 포기할 수 없다 (400, 습관은 ACTIVE 그대로)")
        fun 종료된_그룹이면_400() {
            finishGroup()

            failHabit(memberToken, habitId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이미 종료된 그룹의 습관은 포기할 수 없습니다."))

            assertThat(habitRepository.findById(habitId).orElseThrow().status)
                .isEqualTo(HabitStatus.ACTIVE)
            assertThat(penaltyVerifyRepository.findByHabitId(habitId)).isEmpty()
        }
    }

    @Nested
    @DisplayName("습관 목록 조회")
    inner class HabitListApi {

        @Test
        @DisplayName("활성 습관 조회: 진행 중인 습관을 반환한다")
        fun 활성_습관_조회() {
            mockMvc.perform(
                get("/api/groups/{groupId}/habits/active", groupId)
                    .header("Authorization", "Bearer $memberToken"),
            )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(habitId))
                .andExpect(jsonPath("$.title").value("물 마시기"))
                .andExpect(jsonPath("$.days").value(3))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
        }

        @Test
        @DisplayName("활성 습관 조회: 습관이 없는 멤버는 400을 받는다")
        fun 활성_습관이_없으면_400() {
            mockMvc.perform(
                get("/api/groups/{groupId}/habits/active", groupId)
                    .header("Authorization", "Bearer $ownerToken"),
            )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("현재 진행 중인 습관이 없습니다."))
        }

        @Test
        @DisplayName("실패 습관 조회: 포기한 적이 없으면 빈 목록이다")
        fun 실패한_습관이_없으면_빈_목록() {
            mockMvc.perform(
                get("/api/groups/{groupId}/habits/fail", groupId)
                    .header("Authorization", "Bearer $memberToken"),
            )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0))
        }

        @Test
        @DisplayName("실패 습관 조회: 최근에 만든 습관이 먼저 나온다")
        fun 실패한_습관은_최신순() {
            failHabit(memberToken, habitId).andExpect(status().isOk())
            val first = habitRepository.findById(habitId).orElseThrow()
            ReflectionTestUtils.setField(first, "createDate", LocalDateTime.now().minusDays(2))
            habitRepository.saveAndFlush(first)

            val secondResponse = createHabit(memberToken, CreateHabitRequest("독서 30분", "책 읽기", 5))
                .andExpect(status().isOk())
                .andReturn().response.contentAsString
            val secondId = jsonMapper.readTree(secondResponse).get("id").asLong()
            failHabit(memberToken, secondId).andExpect(status().isOk())

            mockMvc.perform(
                get("/api/groups/{groupId}/habits/fail", groupId)
                    .header("Authorization", "Bearer $memberToken"),
            )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(secondId))
                .andExpect(jsonPath("$[1].id").value(habitId))
        }

        @Test
        @DisplayName("실패 습관 조회: 다른 멤버의 실패 습관은 포함되지 않는다")
        fun 다른_멤버의_실패_습관은_제외() {
            failHabit(memberToken, habitId).andExpect(status().isOk())
            val ownerHabitResponse = createHabit(ownerToken, CreateHabitRequest("독서 30분", "책 읽기", 5))
                .andReturn().response.contentAsString
            failHabit(ownerToken, jsonMapper.readTree(ownerHabitResponse).get("id").asLong())
                .andExpect(status().isOk())

            mockMvc.perform(
                get("/api/groups/{groupId}/habits/fail", groupId)
                    .header("Authorization", "Bearer $memberToken"),
            )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(habitId))
        }

        @Test
        @DisplayName("실패 습관 조회: 같은 멤버라도 다른 그룹의 실패 습관은 포함되지 않는다")
        fun 다른_그룹의_실패_습관은_제외() {
            val otherGroup = groupRepository.save(
                Group.builder()
                    .title("다른 그룹")
                    .penalty("커피 사기")
                    .password("1234")
                    .inviteCode("OTHERGROUP01")
                    .memberLimit(5)
                    .build(),
            )
            val inOtherGroup = groupMemberRepository.save(
                GroupMember.create(otherGroup, memberRepository.findById(memberId).orElseThrow(), GroupMemberRole.MEMBER),
            )
            val otherHabit = Habit.create(inOtherGroup, "다른 그룹 습관", "설명", 3)
            otherHabit.fail()
            habitRepository.saveAndFlush(otherHabit)

            failHabit(memberToken, habitId).andExpect(status().isOk())

            mockMvc.perform(
                get("/api/groups/{groupId}/habits/fail", groupId)
                    .header("Authorization", "Bearer $memberToken"),
            )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(habitId))
        }
    }
}
