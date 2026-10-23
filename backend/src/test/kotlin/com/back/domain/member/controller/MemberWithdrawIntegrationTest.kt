package com.back.domain.member.controller

import com.back.domain.group.dto.GroupRequest
import com.back.domain.group.repository.GroupRepository
import com.back.domain.groupMember.entity.GroupMemberStatus
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.member.entity.MemberStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

// 회원 탈퇴 DELETE /api/members/me 통합 테스트.
// 진행 중인 방이 있으면 막고, 없으면 탈퇴시키면서 종료된 방의 멤버 기록은 LEFT로 남기는지, 탈퇴 후 토큰이 모두 거절되는지 확인한다.
class MemberWithdrawIntegrationTest : MemberApiTestSupport() {

    @Autowired
    private lateinit var groupRepository: GroupRepository

    @Autowired
    private lateinit var groupMemberRepository: GroupMemberRepository

    private var memberId: Long = 0L
    private lateinit var tokens: Tokens

    @BeforeEach
    fun setUp() {
        memberId = signUpOk("withdraw-user", "탈퇴할회원")
        tokens = loginOk("withdraw-user")
    }

    private fun withdraw(token: String = tokens.accessToken) = perform(HttpMethod.DELETE, "/api/members/me", token)

    // 이 회원이 방장인 그룹을 API로 만든다. (기본은 진행 중: 마감일이 한 달 뒤)
    private fun createGroup(title: String = "탈퇴 테스트 방"): Long {
        val result = perform(
            HttpMethod.POST,
            "/api/groups",
            tokens.accessToken,
            GroupRequest.Create(title, "설명", LocalDate.now().plusMonths(1), "커피 사기", "1234", 5),
        ).andExpect(status().isCreated())
        return json(result).get("id").asLong()
    }

    private fun setDeadline(groupId: Long, deadline: LocalDate) {
        val group = groupRepository.findById(groupId).orElseThrow()
        group.deadline = deadline
        groupRepository.saveAndFlush(group)
    }

    private fun finishGroup(groupId: Long) {
        val group = groupRepository.findById(groupId).orElseThrow()
        group.finish()
        groupRepository.saveAndFlush(group)
    }

    private fun memberStatus(): MemberStatus = memberRepository.findById(memberId).orElseThrow().status

    private fun membershipStatuses(): List<GroupMemberStatus> =
        groupMemberRepository.findAllByMemberId(memberId).map { it.status }

    @Test
    @DisplayName("참여 중인 방이 없으면 204이고 회원이 WITHDRAWN이 되며 리프레시 토큰이 폐기된다")
    fun 방이_없으면_탈퇴된다() {
        withdraw().andExpect(status().isNoContent())

        val member = memberRepository.findById(memberId).orElseThrow()
        assertThat(member.status).isEqualTo(MemberStatus.WITHDRAWN)
        assertThat(member.refreshToken).isNull()
    }

    @Test
    @DisplayName("탈퇴하면 기존 액세스 토큰, 리프레시 토큰, 로그인이 모두 거절된다")
    fun 탈퇴_후에는_토큰과_로그인이_모두_거절된다() {
        withdraw().andExpect(status().isNoContent())

        perform(HttpMethod.GET, "/api/members/me", tokens.accessToken).andExpect(status().isUnauthorized())
        perform(HttpMethod.POST, "/api/auth/refresh", body = mapOf("refreshToken" to tokens.refreshToken))
            .andExpect(status().isUnauthorized())
        login("withdraw-user")
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("탈퇴한 회원은 로그인할 수 없습니다."))
    }

    @Test
    @DisplayName("이미 탈퇴한 회원의 토큰으로 다시 탈퇴를 요청하면 401이다")
    fun 탈퇴한_뒤_다시_탈퇴하면_401이다() {
        withdraw().andExpect(status().isNoContent())

        withdraw().andExpect(status().isUnauthorized())
    }

    @Test
    @DisplayName("탈퇴한 아이디로는 다시 가입할 수 없다 (현재 동작: 아이디가 계속 점유됨)")
    fun 탈퇴한_아이디로_재가입할_수_없다() {
        withdraw().andExpect(status().isNoContent())

        signUp("withdraw-user")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("이미 사용 중인 아이디입니다."))
    }

    @Test
    @DisplayName("진행 중인 방에 참여 중이면 409이고 회원 상태와 토큰이 그대로다")
    fun 진행_중인_방이_있으면_탈퇴할_수_없다() {
        createGroup()

        withdraw()
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("진행 중인 방이 있어 회원 탈퇴를 할 수 없습니다."))

        assertThat(memberStatus()).isEqualTo(MemberStatus.ACTIVE)
        perform(HttpMethod.GET, "/api/members/me", tokens.accessToken).andExpect(status().isOk())
        assertThat(membershipStatuses()).containsOnly(GroupMemberStatus.ACTIVE)
    }

    @Test
    @DisplayName("마감일이 오늘인 방은 아직 진행 중이라 탈퇴할 수 없다")
    fun 마감일이_오늘이면_진행_중이다() {
        setDeadline(createGroup(), LocalDate.now())

        withdraw().andExpect(status().isConflict())

        assertThat(memberStatus()).isEqualTo(MemberStatus.ACTIVE)
    }

    @Test
    @DisplayName("마감일이 어제인 방은 끝난 방이라 탈퇴할 수 있고 멤버 기록은 LEFT가 된다")
    fun 마감일이_어제면_끝난_방이다() {
        setDeadline(createGroup(), LocalDate.now().minusDays(1))

        withdraw().andExpect(status().isNoContent())

        assertThat(memberStatus()).isEqualTo(MemberStatus.WITHDRAWN)
        assertThat(membershipStatuses()).containsOnly(GroupMemberStatus.LEFT)
    }

    @Test
    @DisplayName("종료(FINISH)된 방만 있으면 탈퇴할 수 있고 멤버 기록은 LEFT로 남는다")
    fun 종료된_방만_있으면_탈퇴된다() {
        finishGroup(createGroup())

        withdraw().andExpect(status().isNoContent())

        assertThat(memberStatus()).isEqualTo(MemberStatus.WITHDRAWN)
        assertThat(membershipStatuses()).containsOnly(GroupMemberStatus.LEFT)
    }

    @Test
    @DisplayName("이미 나간(LEFT) 방은 진행 중이어도 탈퇴를 막지 않는다")
    fun 이미_나간_방은_탈퇴를_막지_않는다() {
        createGroup()
        groupMemberRepository.findAllByMemberId(memberId).forEach { it.leave() }
        groupMemberRepository.flush()

        withdraw().andExpect(status().isNoContent())

        assertThat(memberStatus()).isEqualTo(MemberStatus.WITHDRAWN)
    }

    @Test
    @DisplayName("진행 중인 방과 끝난 방에 함께 참여 중이면 409이고 끝난 방의 멤버 기록도 바뀌지 않는다")
    fun 진행_중인_방이_하나라도_있으면_아무것도_바꾸지_않는다() {
        createGroup("진행 중인 방")
        finishGroup(createGroup("끝난 방"))

        withdraw().andExpect(status().isConflict())

        assertThat(memberStatus()).isEqualTo(MemberStatus.ACTIVE)
        assertThat(membershipStatuses()).hasSize(2).containsOnly(GroupMemberStatus.ACTIVE)
    }

    @Test
    @DisplayName("끝난 방이 여러 개여도 모두 LEFT로 남기고 탈퇴한다")
    fun 끝난_방이_여러_개여도_모두_정리된다() {
        finishGroup(createGroup("끝난 방 1"))
        finishGroup(createGroup("끝난 방 2"))

        withdraw().andExpect(status().isNoContent())

        assertThat(membershipStatuses()).hasSize(2).containsOnly(GroupMemberStatus.LEFT)
    }
}
