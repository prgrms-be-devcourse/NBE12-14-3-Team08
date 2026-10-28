package com.back.global.init

import com.back.domain.group.entity.GroupStatus
import com.back.domain.group.repository.GroupRepository
import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.habit.entity.HabitStatus
import com.back.domain.habit.repository.HabitRepository
import com.back.domain.habitVerify.entity.HabitVerifyStatus
import com.back.domain.habitVerify.repository.HabitVerifyRepository
import com.back.domain.member.repository.MemberRepository
import com.back.domain.member.service.AuthTokenService
import com.back.domain.penaltyverify.entity.PenaltyVerifyStatus
import com.back.domain.penaltyverify.repository.PenaltyVerifyRepository
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.LocalDate
import kotlin.jvm.optionals.getOrNull

// TestDataInit은 local 프로필에서만 실행돼서 다른 테스트로는 확인되지 않는다.
// 시드 데이터(그룹 5, 회원 25, 습관 40, 습관 인증 대기 5·승인 253, 벌칙 대기 11)를 만드는지 고정한다.
// 승인 253건은 완료된 방(독서 챌린지)의 36건, 모각코 챌린지 1기의 갤러리 예시 사진 167건(최근 35일),
// 방장asd가 방장인 완료된 방(새벽 러닝 챌린지)의 42건, 멤버 많은 방(북적북적 스터디)의 오늘 인증 8건이다.
// 시드를 바꾸면 이 테스트의 숫자도 함께 바꾼다.
// 스프링 빈으로 띄우지 않고 직접 만들어서, 다른 @DataJpaTest와 같은 컨텍스트(DB 커넥션)를 쓴다.
@DataJpaTest
class TestDataInitTest @Autowired constructor(
    private val memberRepository: MemberRepository,
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val habitRepository: HabitRepository,
    private val habitVerifyRepository: HabitVerifyRepository,
    private val penaltyVerifyRepository: PenaltyVerifyRepository
) {

    private lateinit var testDataInit: TestDataInit

    @BeforeEach
    fun setUp() {
        val passwordEncoder = mockk<PasswordEncoder> { every { encode(any()) } returns "encoded-1234" }
        // refresh_token 칸은 유니크라 회원마다 다른 값을 돌려준다
        val authTokenService = mockk<AuthTokenService> {
            every { createRefreshToken(any()) } answers { "refresh-token-${firstArg<Long>()}" }
        }

        testDataInit = TestDataInit(
            memberRepository,
            groupRepository,
            groupMemberRepository,
            habitRepository,
            habitVerifyRepository,
            penaltyVerifyRepository,
            passwordEncoder,
            authTokenService,
        )
    }

    @Test
    @DisplayName("그룹 5개와 회원·습관·습관 인증(대기·승인)·벌칙 대기 데이터를 만든다")
    fun 시드_데이터를_만든다() {
        val before = counts()

        testDataInit.run()

        val after = counts()
        assertThat(groupRepository.existsByInviteCode("MOGAK100")).isTrue()
        assertThat(groupRepository.existsByInviteCode("FITNESS200")).isTrue()
        assertThat(groupRepository.existsByInviteCode("FINISH300")).isTrue()
        assertThat(groupRepository.existsByInviteCode("DONE400")).isTrue()
        assertThat(groupRepository.existsByInviteCode("CROWD500")).isTrue()
        assertThat(after.groups - before.groups).isEqualTo(5)
        assertThat(after.members - before.members).isEqualTo(25)
        assertThat(after.groupMembers - before.groupMembers).isEqualTo(30)
        assertThat(after.habits - before.habits).isEqualTo(40)
        assertThat(after.failedHabits - before.failedHabits).isEqualTo(12)
        assertThat(after.pendingHabitVerifies - before.pendingHabitVerifies).isEqualTo(5)
        assertThat(after.approvedHabitVerifies - before.approvedHabitVerifies).isEqualTo(253)
        assertThat(after.pendingPenalties - before.pendingPenalties).isEqualTo(11)
    }

    @Test
    @DisplayName("새로 만든 회원에는 암호화된 비밀번호와 리프레시 토큰이 들어간다")
    fun 회원에_토큰과_비밀번호가_들어간다() {
        testDataInit.run()

        val owner = requireNotNull(memberRepository.findByUsername("asd").getOrNull())
        assertThat(owner.nickname).isEqualTo("방장asd")
        assertThat(owner.password).isEqualTo("encoded-1234")
        assertThat(owner.refreshToken).isEqualTo("refresh-token-${owner.id}")
    }

    @Test
    @DisplayName("방장asd가 방장인 완료된 방에는 마감일 이전의 승인·거절 인증과 승인된 벌칙이 들어간다")
    fun 방장이_방장인_완료된_방을_만든다() {
        testDataInit.run()

        val owner = requireNotNull(memberRepository.findByUsername("asd").getOrNull())
        val group = requireNotNull(groupRepository.findByInviteCode("DONE400"))
        val ownerMember = groupMemberRepository.findAll().single { it.group.id == group.id && it.member.id == owner.id }
        assertThat(ownerMember.role).isEqualTo(GroupMemberRole.OWNER)
        assertThat(group.status).isEqualTo(GroupStatus.FINISH)
        assertThat(group.deadline).isBefore(LocalDate.now())

        val verifies = habitVerifyRepository.findAllByGroupId(requireNotNull(group.id))
        assertThat(verifies).allSatisfy { assertThat(it.verifyDate).isBeforeOrEqualTo(group.deadline) }
        assertThat(verifies.count { it.status == HabitVerifyStatus.APPROVED }).isEqualTo(42)
        assertThat(verifies.count { it.status == HabitVerifyStatus.REJECTED }).isEqualTo(3)

        val penalties = penaltyVerifyRepository.findAllByGroupId(requireNotNull(group.id))
        assertThat(penalties).hasSize(1)
        assertThat(penalties.single().status).isEqualTo(PenaltyVerifyStatus.APPROVED)
    }

    @Test
    @DisplayName("멤버 많은 방은 방장asd를 포함해 14명이고 마지막 두 명은 습관이 없다")
    fun 멤버_많은_방을_만든다() {
        testDataInit.run()

        val group = requireNotNull(groupRepository.findByInviteCode("CROWD500"))
        val groupMembers = groupMemberRepository.findAll().filter { it.group.id == group.id }
        assertThat(groupMembers).hasSize(14)
        assertThat(groupMembers.single { it.role == GroupMemberRole.OWNER }.member.username).isEqualTo("asd")

        val habitOwners = habitRepository.findAll().filter { it.groupMember.group.id == group.id }
        assertThat(habitOwners).hasSize(12)
    }

    @Test
    @DisplayName("이미 시드 데이터가 있으면(초대 코드 MOGAK100) 두 번째 실행은 아무것도 만들지 않는다")
    fun 두_번_실행해도_한_번만_만든다() {
        testDataInit.run()
        val afterFirst = counts()

        testDataInit.run()

        assertThat(counts()).isEqualTo(afterFirst)
    }

    private data class Counts(
        val groups: Long,
        val members: Long,
        val groupMembers: Long,
        val habits: Long,
        val failedHabits: Long,
        val pendingHabitVerifies: Long,
        val approvedHabitVerifies: Long,
        val pendingPenalties: Long,
    )

    private fun counts() = Counts(
        groups = groupRepository.count(),
        members = memberRepository.count(),
        groupMembers = groupMemberRepository.count(),
        habits = habitRepository.count(),
        failedHabits = habitRepository.findAll().count { it.status == HabitStatus.FAILED }.toLong(),
        pendingHabitVerifies = habitVerifyRepository.findAll().count { it.status == HabitVerifyStatus.PENDING }.toLong(),
        approvedHabitVerifies = habitVerifyRepository.findAll().count { it.status == HabitVerifyStatus.APPROVED }.toLong(),
        pendingPenalties = penaltyVerifyRepository.findAll().count { it.status == PenaltyVerifyStatus.PENDING }.toLong(),
    )
}
