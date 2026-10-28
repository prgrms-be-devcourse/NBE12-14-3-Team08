package com.back.domain.groupMember.service

import com.back.domain.group.dto.GroupRequest
import com.back.domain.group.repository.GroupRepository
import com.back.domain.group.service.GroupService
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.habit.repository.HabitRepository
import com.back.domain.habitVerify.repository.HabitVerifyRepository
import com.back.domain.member.entity.Member
import com.back.domain.member.repository.MemberRepository
import com.back.domain.penaltyverify.repository.PenaltyVerifyRepository
import com.back.global.entity.requiredId
import com.back.support.ConcurrentRunner
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.LocalDate
import java.util.UUID

@SpringBootTest
class GroupMemberServiceConcurrencyTest @Autowired constructor(
    private val groupMemberService: GroupMemberService,
    private val groupService: GroupService,
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
    private val habitRepository: HabitRepository,
    private val habitVerifyRepository: HabitVerifyRepository,
    private val penaltyVerifyRepository: PenaltyVerifyRepository
) {

    private var groupId: Long = 0
    private lateinit var inviteCode: String
    private lateinit var applicants: List<Member>

    companion object {
        private const val MEMBER_LIMIT = 5
        private const val APPLICANT_COUNT = 10
        private const val RAW_PASSWORD = "test1234"
    }

    @BeforeEach
    fun setUp() {
        val suffix = "concur-" + UUID.randomUUID().toString().substring(0, 8)

        // 방장이 될 회원 생성
        val owner = createMember("ownerUser-$suffix")

        // 실제 서비스 로직으로 그룹 생성 → 방장 GroupMember가 함께 자동 생성됨
        val created = groupService.createGroup(
            owner.requiredId,
            GroupRequest.Create("동시성 테스트 그룹", "설명", LocalDate.now().plusDays(7), "벌칙", RAW_PASSWORD, MEMBER_LIMIT),
        )
        groupId = requireNotNull(created.id)
        // createGroup은 초대 코드를 자체 생성하므로, 실제 발급된 코드로 참여한다
        inviteCode = created.inviteCode

        applicants = List(APPLICANT_COUNT) { createMember("concurUser$it-$suffix") }
    }

    @AfterEach
    fun tearDown() {
        penaltyVerifyRepository.deleteAll()
        habitVerifyRepository.deleteAll()
        habitRepository.deleteAll()
        groupMemberRepository.deleteAll()
        groupRepository.deleteAll()
        memberRepository.deleteAll()
    }

    @Test
    fun 동시에_정원을_초과해_입장해도_방장을_포함해_정확히_정원만큼만_성공한다() {
        val expectedSuccess = MEMBER_LIMIT - 1 // 방장 1명이 이미 자리를 차지하고 있으므로
        val joinRequest = GroupRequest.Join(RAW_PASSWORD)

        // 각 결과는 성공이면 null, 실패면 던진 예외
        val results = ConcurrentRunner.runAll(
            applicants.map { applicant -> { groupMemberService.joinGroup(inviteCode, applicant.requiredId, joinRequest) } },
        )

        assertThat(results.count { it == null }).isEqualTo(expectedSuccess)
        assertThat(results.count { it != null }).isEqualTo(APPLICANT_COUNT - expectedSuccess)
        assertThat(groupMemberRepository.countByGroupId(groupId)).isEqualTo(MEMBER_LIMIT.toLong()) // 방장 1 + 성공한 4명 = 5
    }

    private fun createMember(name: String): Member =
        memberRepository.save(Member.create(name, name, checkNotNull(passwordEncoder.encode("pw1234!"))))
}
