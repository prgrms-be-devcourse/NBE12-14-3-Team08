package com.back.domain.penaltyverify

import com.back.domain.group.entity.Group
import com.back.domain.group.repository.GroupRepository
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.habit.entity.Habit
import com.back.domain.habit.repository.HabitRepository
import com.back.domain.member.entity.Member
import com.back.domain.member.repository.MemberRepository
import com.back.domain.member.service.AuthTokenService
import com.back.domain.penaltyverify.entity.PenaltyVerify
import com.back.domain.penaltyverify.repository.PenaltyVerifyRepository
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

// 벌칙 인증 응답(상세·요약)의 JSON 키 모양을 고정한다.
// DTO를 Java record에서 Kotlin으로 바꿀 때 키 이름이 바뀌거나 null 값의 키가 빠지면 프론트가 조용히 깨지므로,
// 전환 전 Java 코드에서 먼저 통과시킨 뒤 전환한다. (미제출 벌칙이라 verifyDate·description·imageUrl은 null)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PenaltyVerifyResponseShapeTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val jsonMapper: JsonMapper,
    private val authTokenService: AuthTokenService,
    private val memberRepository: MemberRepository,
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val habitRepository: HabitRepository,
    private val penaltyVerifyRepository: PenaltyVerifyRepository
) {

    // 이 테스트는 사진 저장소를 쓰지 않지만, 설정을 PenaltyVerifyControllerTest와 똑같이 맞춰 Spring 컨텍스트를 재사용한다.
    // 새 설정 조합이면 컨텍스트(DB 커넥션 풀 최대 20개)가 하나 더 생겨 테스트 DB 접속 수 한도(100)를 넘길 수 있다.
    // relaxUnitFun: 정해 두지 않은 Unit 함수(사진 검사 validateImageUrl)는 Mockito처럼 아무것도 하지 않고 넘어간다
    @MockkBean(relaxUnitFun = true) lateinit var storageService: StorageService

    private lateinit var memberToken: String
    private var groupId: Long = 0
    private var groupMemberId: Long = 0
    private var penaltyId: Long = 0

    @BeforeEach
    fun setUp() {
        val group = groupRepository.save(
            Group.builder()
                .title("아침 기상 챌린지")
                .penalty("커피 1잔 쏘기")
                .password("1234")
                .inviteCode("SHAPECODE")
                .memberLimit(5)
                .build()
        )
        val member = memberRepository.save(Member.create("일반회원", "shape-member", "pass1234"))
        val groupMember = groupMemberRepository.save(GroupMember.create(group, member, GroupMemberRole.MEMBER))
        val habit = habitRepository.save(Habit.create(groupMember, "기상 후 스트레칭", "10분 스트레칭", 3))
        val penalty = penaltyVerifyRepository.save(PenaltyVerify.create(groupMember, habit))

        memberToken = authTokenService.createAccessToken(requireNotNull(member.id))
        groupId = requireNotNull(group.id)
        groupMemberId = requireNotNull(groupMember.id)
        penaltyId = requireNotNull(penalty.id)
    }

    private fun getJson(url: String, vararg vars: Any): String =
        mockMvc.get(url, *vars) {
            header("Authorization", "Bearer $memberToken")
        }
            .andExpect {
                status { isOk() }
            }
            .andReturn().response.contentAsString

    @Test
    @DisplayName("벌칙 상세 응답은 7개 키를 갖고, 값이 없는 키(verifyDate, description, imageUrl)도 null로 내려간다")
    fun 상세_응답_모양() {
        val body = jsonMapper.readValue(getJson("/api/penalties/{id}", penaltyId), Map::class.java)

        assertThat(body.keys).containsExactlyInAnyOrder(
            "id", "verifyDate", "habitTitle", "penaltyText", "description", "imageUrl", "status",
        )
        assertThat(body["verifyDate"]).isNull()
        assertThat(body["description"]).isNull()
        assertThat(body["imageUrl"]).isNull()
        assertThat(body["habitTitle"]).isEqualTo("기상 후 스트레칭")
        assertThat(body["penaltyText"]).isEqualTo("커피 1잔 쏘기")
        assertThat(body["status"]).isEqualTo("REQUIRED")
    }

    @Test
    @DisplayName("벌칙 요약 응답(멤버별 목록)은 9개 키를 갖고, 값이 없는 키도 null로 내려간다")
    fun 요약_응답_모양() {
        val list = jsonMapper.readValue(
            getJson("/api/groups/{groupId}/members/{groupMemberId}/penalties", groupId, groupMemberId),
            List::class.java,
        )
        val first = list.single() as Map<*, *>

        assertThat(first.keys).containsExactlyInAnyOrder(
            "id", "habitId", "verifyDate", "habitTitle", "description",
            "memberNickname", "imageUrl", "penaltyText", "status",
        )
        assertThat(first["verifyDate"]).isNull()
        assertThat(first["memberNickname"]).isEqualTo("일반회원")
        assertThat(first["status"]).isEqualTo("REQUIRED")
    }
}
