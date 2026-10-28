package com.back.domain.habitVerify

import com.back.domain.group.entity.Group
import com.back.domain.group.repository.GroupRepository
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.habit.entity.Habit
import com.back.domain.habit.repository.HabitRepository
import com.back.domain.habitVerify.entity.HabitVerify
import com.back.domain.habitVerify.repository.HabitVerifyRepository
import com.back.domain.member.entity.Member
import com.back.domain.member.repository.MemberRepository
import com.back.domain.member.service.AuthTokenService
import com.back.global.storage.StorageService
import com.ninjasquad.springmockk.MockkBean
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.jsonMapper
import java.time.LocalDate

// 습관 인증 응답(목록·검토 대기 요약)의 JSON 키 모양을 고정한다.
// DTO를 Java record에서 Kotlin으로 바꿀 때 키 이름이 바뀌거나 null 값의 키가 빠지면 프론트가 조용히 깨지므로,
// 전환 전 Java 코드에서 먼저 통과시킨 뒤 전환한다. (설명 없이 제출한 인증이라 description은 null)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HabitVerifyResponseShapeTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val jsonMapper: JsonMapper,
    private val authTokenService: AuthTokenService,
    private val memberRepository: MemberRepository,
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val habitRepository: HabitRepository,
    private val habitVerifyRepository: HabitVerifyRepository
) {

    // 이 테스트는 사진 저장소를 쓰지 않지만, 설정을 HabitVerifyControllerTest와 똑같이 맞춰 Spring 컨텍스트를 재사용한다.
    // 새 설정 조합이면 컨텍스트(DB 커넥션 풀 최대 20개)가 하나 더 생겨 테스트 DB 접속 수 한도(100)를 넘길 수 있다.
    // relaxUnitFun: 정해 두지 않은 Unit 함수(사진 검사 validateImageUrl)는 Mockito처럼 아무것도 하지 않고 넘어간다
    @MockkBean(relaxUnitFun = true) lateinit var storageService: StorageService

    private lateinit var ownerToken: String
    private var groupId: Long = 0
    private var habitId: Long = 0

    @BeforeEach
    fun setUp() {
        val group = groupRepository.save(
            Group(
                title = "아침 기상 챌린지",
                penalty = "커피 1잔 쏘기",
                password = "1234",
                inviteCode = "HVSHAPECODE",
                memberLimit = 5,
            )
        )
        // 방장 본인의 습관 인증이라 목록 조회와 검토 대기 조회를 한 사람으로 할 수 있다.
        val owner = memberRepository.save(Member.create("방장", "hv-shape-owner", "pass1234"))
        val groupMember = groupMemberRepository.save(GroupMember.create(group, owner, GroupMemberRole.OWNER))
        val habit = habitRepository.save(Habit.create(groupMember, "기상 후 스트레칭", "10분 스트레칭", 3))
        habitVerifyRepository.save(HabitVerify.create(habit, LocalDate.now(), null, "https://example.com/a.png"))

        ownerToken = authTokenService.createAccessToken(requireNotNull(owner.id))
        groupId = requireNotNull(group.id)
        habitId = requireNotNull(habit.id)
    }

    private fun getJson(url: String, vararg vars: Any): String =
        mockMvc.get(url, *vars) {
            header("Authorization", "Bearer $ownerToken")
        }
            .andExpect {
                status { isOk() }
            }
            .andReturn().response.contentAsString

    @Test
    @DisplayName("습관 인증 목록 응답은 6개 키를 갖고, 값이 없는 키(description)도 null로 내려간다")
    fun 목록_응답_모양() {
        val list = jsonMapper.readValue(getJson("/api/habits/{habitId}/verifications", habitId), List::class.java)
        val first = list.single() as Map<*, *>

        assertThat(first.keys).containsExactlyInAnyOrder(
            "id", "habitId", "verifyDate", "status", "description", "imageUrl",
        )
        assertThat(first["description"]).isNull()
        assertThat(first["habitId"]).isEqualTo(habitId.toInt())
        assertThat(first["status"]).isEqualTo("PENDING")
    }

    @Test
    @DisplayName("검토 대기 요약 응답은 7개 키를 갖고, 값이 없는 키(description)도 null로 내려간다")
    fun 검토_대기_요약_응답_모양() {
        val list = jsonMapper.readValue(
            getJson("/api/groups/{groupId}/habits/verifications/pending", groupId),
            List::class.java,
        )
        val first = list.single() as Map<*, *>

        assertThat(first.keys).containsExactlyInAnyOrder(
            "id", "verifyDate", "habitTitle", "memberNickname", "description", "imageUrl", "status",
        )
        assertThat(first["description"]).isNull()
        assertThat(first["habitTitle"]).isEqualTo("기상 후 스트레칭")
        assertThat(first["memberNickname"]).isEqualTo("방장")
        assertThat(first["status"]).isEqualTo("PENDING")
    }
}
