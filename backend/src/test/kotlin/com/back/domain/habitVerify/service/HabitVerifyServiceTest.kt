package com.back.domain.habitVerify.service

import com.back.domain.group.entity.Group
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.habit.entity.Habit
import com.back.domain.habit.repository.HabitRepository
import com.back.domain.habitVerify.dto.BulkActionRequest
import com.back.domain.habitVerify.dto.HabitVerifySummaryResponse
import com.back.domain.habitVerify.entity.HabitVerify
import com.back.domain.habitVerify.entity.HabitVerifyStatus
import com.back.domain.habitVerify.repository.HabitVerifyRepository
import com.back.domain.member.entity.Member
import com.back.global.exception.BusinessRuleException
import com.back.global.exception.EntityNotFoundException
import com.back.global.exception.ForbiddenException
import com.back.global.storage.StorageService
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.ThrowingConsumer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.security.access.AccessDeniedException
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDate
import java.util.Optional

@ExtendWith(MockKExtension::class)
class HabitVerifyServiceTest {

    companion object {
        private const val GROUP_ID = 100L
        private const val OWNER_ID = 1L
        private const val MEMBER_ID = 2L
        private const val OUTSIDER_ID = 9L
    }

    @MockK
    private lateinit var habitRepository: HabitRepository

    @MockK
    private lateinit var habitVerifyRepository: HabitVerifyRepository

    @MockK
    private lateinit var storageService: StorageService

    @MockK
    private lateinit var groupMemberRepository: GroupMemberRepository

    @InjectMockKs
    private lateinit var habitVerifyService: HabitVerifyService

    private lateinit var group: Group
    private lateinit var ownerGroupMember: GroupMember
    private lateinit var memberGroupMember: GroupMember
    private lateinit var habit: Habit

    @BeforeEach
    fun setUp() {
        group = Group.builder()
            .title("아침 기상 챌린지")
            .penalty("커피 1잔 쏘기")
            .memberLimit(10)
            .build()
        ReflectionTestUtils.setField(group, "id", GROUP_ID)

        val owner = Member.create("방장", "owner", "pass1234")
        ReflectionTestUtils.setField(owner, "id", OWNER_ID)
        ownerGroupMember = GroupMember.create(group, owner, GroupMemberRole.OWNER)

        val member = Member.create("일반회원", "member", "pass1234")
        ReflectionTestUtils.setField(member, "id", MEMBER_ID)
        memberGroupMember = GroupMember.create(group, member, GroupMemberRole.MEMBER)

        habit = Habit.create(memberGroupMember, "기상 후 스트레칭", "10분 스트레칭", 3)
        ReflectionTestUtils.setField(habit, "id", 50L)
    }

    private fun verifyIn(id: Long, status: HabitVerifyStatus): HabitVerify {
        val habitVerify = HabitVerify.create(habit, LocalDate.of(2026, 10, 21), "스트레칭 완료", "https://example.com/a.png")
        ReflectionTestUtils.setField(habitVerify, "id", id)
        ReflectionTestUtils.setField(habitVerify, "status", status)
        return habitVerify
    }

    private fun givenGroupMember(memberId: Long, groupMember: GroupMember?) {
        every { groupMemberRepository.findByGroupIdAndMemberId(GROUP_ID, memberId) } returns Optional.ofNullable(groupMember)
    }

    @Nested
    @DisplayName("단건 승인(approve)")
    inner class Approve {

        @Test
        @DisplayName("방장이 검토 대기 인증을 승인하면 APPROVED가 된다")
        fun 방장이_승인() {
            val habitVerify = verifyIn(70L, HabitVerifyStatus.PENDING)
            every { habitVerifyRepository.findById(70L) } returns Optional.of(habitVerify)
            givenGroupMember(OWNER_ID, ownerGroupMember)

            val response = habitVerifyService.approve(OWNER_ID, 70L)

            assertThat(habitVerify.status).isEqualTo(HabitVerifyStatus.APPROVED)
            assertThat(response.status).isEqualTo(HabitVerifyStatus.APPROVED)
        }

        @Test
        @DisplayName("없는 인증이면 404이고 권한 확인까지 가지 않는다")
        fun 없는_인증_404() {
            every { habitVerifyRepository.findById(999L) } returns Optional.empty()

            assertThatThrownBy { habitVerifyService.approve(OWNER_ID, 999L) }
                .isInstanceOf(EntityNotFoundException::class.java)
                .hasMessage("존재하지 않는 인증 기록입니다.")

            verify(exactly = 0) { groupMemberRepository.findByGroupIdAndMemberId(any(), any()) }
        }

        @Test
        @DisplayName("그룹 멤버가 아니면 403이고 상태는 그대로다")
        fun 그룹_멤버가_아니면_403() {
            val habitVerify = verifyIn(70L, HabitVerifyStatus.PENDING)
            every { habitVerifyRepository.findById(70L) } returns Optional.of(habitVerify)
            givenGroupMember(OUTSIDER_ID, null)

            assertThatThrownBy { habitVerifyService.approve(OUTSIDER_ID, 70L) }
                .isInstanceOf(AccessDeniedException::class.java)
                .hasMessage("승인/거절 권한이 없습니다.")

            assertThat(habitVerify.status).isEqualTo(HabitVerifyStatus.PENDING)
        }

        @Test
        @DisplayName("일반 멤버(방장 아님)면 403이고 상태는 그대로다")
        fun 일반_멤버면_403() {
            val habitVerify = verifyIn(70L, HabitVerifyStatus.PENDING)
            every { habitVerifyRepository.findById(70L) } returns Optional.of(habitVerify)
            givenGroupMember(MEMBER_ID, memberGroupMember)

            assertThatThrownBy { habitVerifyService.approve(MEMBER_ID, 70L) }
                .isInstanceOf(AccessDeniedException::class.java)
                .hasMessage("승인/거절 권한이 없습니다.")

            assertThat(habitVerify.status).isEqualTo(HabitVerifyStatus.PENDING)
        }

        @Test
        @DisplayName("종료된 그룹이면 400이고 상태는 그대로다")
        fun 종료된_그룹이면_400() {
            group.finish()
            val habitVerify = verifyIn(70L, HabitVerifyStatus.PENDING)
            every { habitVerifyRepository.findById(70L) } returns Optional.of(habitVerify)
            givenGroupMember(OWNER_ID, ownerGroupMember)

            assertThatThrownBy { habitVerifyService.approve(OWNER_ID, 70L) }
                .isInstanceOf(BusinessRuleException::class.java)
                .hasMessage("이미 종료된 그룹은 인증을 검토할 수 없습니다.")

            assertThat(habitVerify.status).isEqualTo(HabitVerifyStatus.PENDING)
        }

        @Test
        @DisplayName("이미 처리된 인증이면 409다")
        fun 이미_처리된_인증이면_409() {
            val habitVerify = verifyIn(70L, HabitVerifyStatus.REJECTED)
            every { habitVerifyRepository.findById(70L) } returns Optional.of(habitVerify)
            givenGroupMember(OWNER_ID, ownerGroupMember)

            assertThatThrownBy { habitVerifyService.approve(OWNER_ID, 70L) }
                .isInstanceOf(IllegalStateException::class.java)

            assertThat(habitVerify.status).isEqualTo(HabitVerifyStatus.REJECTED)
        }
    }

    @Nested
    @DisplayName("단건 반려(reject)")
    inner class Reject {

        @Test
        @DisplayName("방장이 검토 대기 인증을 반려하면 REJECTED가 된다")
        fun 방장이_반려() {
            val habitVerify = verifyIn(70L, HabitVerifyStatus.PENDING)
            every { habitVerifyRepository.findById(70L) } returns Optional.of(habitVerify)
            givenGroupMember(OWNER_ID, ownerGroupMember)

            val response = habitVerifyService.reject(OWNER_ID, 70L)

            assertThat(habitVerify.status).isEqualTo(HabitVerifyStatus.REJECTED)
            assertThat(response.status).isEqualTo(HabitVerifyStatus.REJECTED)
        }

        @Test
        @DisplayName("없는 인증이면 404이고 권한 확인까지 가지 않는다")
        fun 없는_인증_404() {
            every { habitVerifyRepository.findById(999L) } returns Optional.empty()

            assertThatThrownBy { habitVerifyService.reject(OWNER_ID, 999L) }
                .isInstanceOf(EntityNotFoundException::class.java)
                .hasMessage("존재하지 않는 인증 기록입니다.")

            verify(exactly = 0) { groupMemberRepository.findByGroupIdAndMemberId(any(), any()) }
        }

        @Test
        @DisplayName("일반 멤버(방장 아님)면 403이고 상태는 그대로다")
        fun 일반_멤버면_403() {
            val habitVerify = verifyIn(70L, HabitVerifyStatus.PENDING)
            every { habitVerifyRepository.findById(70L) } returns Optional.of(habitVerify)
            givenGroupMember(MEMBER_ID, memberGroupMember)

            assertThatThrownBy { habitVerifyService.reject(MEMBER_ID, 70L) }
                .isInstanceOf(AccessDeniedException::class.java)

            assertThat(habitVerify.status).isEqualTo(HabitVerifyStatus.PENDING)
        }

        @Test
        @DisplayName("이미 처리된 인증이면 409다")
        fun 이미_처리된_인증이면_409() {
            val habitVerify = verifyIn(70L, HabitVerifyStatus.APPROVED)
            every { habitVerifyRepository.findById(70L) } returns Optional.of(habitVerify)
            givenGroupMember(OWNER_ID, ownerGroupMember)

            assertThatThrownBy { habitVerifyService.reject(OWNER_ID, 70L) }
                .isInstanceOf(IllegalStateException::class.java)

            assertThat(habitVerify.status).isEqualTo(HabitVerifyStatus.APPROVED)
        }
    }

    @Nested
    @DisplayName("일괄 승인·반려(bulkApprove, bulkReject)")
    inner class Bulk {

        @Test
        @DisplayName("방장이 일괄 승인하면 가져온 인증이 모두 APPROVED가 된다")
        fun 일괄_승인() {
            val first = verifyIn(70L, HabitVerifyStatus.PENDING)
            val second = verifyIn(71L, HabitVerifyStatus.PENDING)
            givenGroupMember(OWNER_ID, ownerGroupMember)
            every { habitVerifyRepository.findAllByIdInAndGroupId(listOf(70L, 71L), GROUP_ID) } returns listOf(first, second)

            val responses = habitVerifyService.bulkApprove(OWNER_ID, GROUP_ID, BulkActionRequest(listOf(70L, 71L)))

            assertThat(responses.map { it.status })
                .containsExactly(HabitVerifyStatus.APPROVED, HabitVerifyStatus.APPROVED)
        }

        @Test
        @DisplayName("방장이 일괄 반려하면 가져온 인증이 모두 REJECTED가 된다")
        fun 일괄_반려() {
            val first = verifyIn(70L, HabitVerifyStatus.PENDING)
            val second = verifyIn(71L, HabitVerifyStatus.PENDING)
            givenGroupMember(OWNER_ID, ownerGroupMember)
            every { habitVerifyRepository.findAllByIdInAndGroupId(listOf(70L, 71L), GROUP_ID) } returns listOf(first, second)

            val responses = habitVerifyService.bulkReject(OWNER_ID, GROUP_ID, BulkActionRequest(listOf(70L, 71L)))

            assertThat(responses.map { it.status })
                .containsExactly(HabitVerifyStatus.REJECTED, HabitVerifyStatus.REJECTED)
        }

        @Test
        @DisplayName("일반 멤버(방장 아님)면 403이고 대상 인증을 조회하지 않는다")
        fun 일반_멤버면_403() {
            givenGroupMember(MEMBER_ID, memberGroupMember)

            assertThatThrownBy { habitVerifyService.bulkApprove(MEMBER_ID, GROUP_ID, BulkActionRequest(listOf(70L))) }
                .isInstanceOf(AccessDeniedException::class.java)

            verify(exactly = 0) { habitVerifyRepository.findAllByIdInAndGroupId(any(), any()) }
        }

        @Test
        @DisplayName("종료된 그룹이면 400이고 대상 인증을 조회하지 않는다")
        fun 종료된_그룹이면_400() {
            group.finish()
            givenGroupMember(OWNER_ID, ownerGroupMember)

            assertThatThrownBy { habitVerifyService.bulkReject(OWNER_ID, GROUP_ID, BulkActionRequest(listOf(70L))) }
                .isInstanceOf(BusinessRuleException::class.java)

            verify(exactly = 0) { habitVerifyRepository.findAllByIdInAndGroupId(any(), any()) }
        }

        @Test
        @DisplayName("하나라도 검토 대기가 아니면 409다 (실제 롤백은 HabitVerifyAdminControllerTest에서 확인)")
        fun 하나라도_처리된_인증이면_409() {
            val pending = verifyIn(70L, HabitVerifyStatus.PENDING)
            val approved = verifyIn(71L, HabitVerifyStatus.APPROVED)
            givenGroupMember(OWNER_ID, ownerGroupMember)
            every { habitVerifyRepository.findAllByIdInAndGroupId(listOf(70L, 71L), GROUP_ID) } returns listOf(pending, approved)

            assertThatThrownBy {
                habitVerifyService.bulkApprove(OWNER_ID, GROUP_ID, BulkActionRequest(listOf(70L, 71L)))
            }
                .isInstanceOf(IllegalStateException::class.java)
        }

        @Test
        @DisplayName("조회되지 않은 id(없는 id, 다른 그룹 id)는 오류 없이 건너뛴다 (현재 동작, #186에서 팀 확인 필요)")
        fun 조회되지_않은_id는_건너뛴다() {
            val pending = verifyIn(70L, HabitVerifyStatus.PENDING)
            givenGroupMember(OWNER_ID, ownerGroupMember)
            every { habitVerifyRepository.findAllByIdInAndGroupId(listOf(70L, 999L), GROUP_ID) } returns listOf(pending)

            val responses = habitVerifyService.bulkApprove(OWNER_ID, GROUP_ID, BulkActionRequest(listOf(70L, 999L)))

            assertThat(responses.map { it.id }).containsExactly(70L)
        }
    }

    @Nested
    @DisplayName("검토 대기 목록(getPendingByGroup)")
    inner class Pending {

        @Test
        @DisplayName("방장은 그룹의 검토 대기 목록을 받는다")
        fun 방장이_조회() {
            givenGroupMember(OWNER_ID, ownerGroupMember)
            every { habitVerifyRepository.findPendingByGroupId(GROUP_ID) } returns
                listOf(verifyIn(70L, HabitVerifyStatus.PENDING))

            val responses = habitVerifyService.getPendingByGroup(OWNER_ID, GROUP_ID)

            assertThat(responses).singleElement().satisfies(
                ThrowingConsumer<HabitVerifySummaryResponse> { response ->
                    assertThat(response.id).isEqualTo(70L)
                    assertThat(response.habitTitle).isEqualTo("기상 후 스트레칭")
                    assertThat(response.memberNickname).isEqualTo("일반회원")
                }
            )
        }

        @Test
        @DisplayName("그룹 멤버가 아니면 403이고 목록을 조회하지 않는다")
        fun 그룹_멤버가_아니면_403() {
            givenGroupMember(OUTSIDER_ID, null)

            assertThatThrownBy { habitVerifyService.getPendingByGroup(OUTSIDER_ID, GROUP_ID) }
                .isInstanceOf(AccessDeniedException::class.java)
                .hasMessage("해당 그룹의 멤버가 아닙니다.")

            verify(exactly = 0) { habitVerifyRepository.findPendingByGroupId(any()) }
        }

        @Test
        @DisplayName("일반 멤버(방장 아님)면 403이고 목록을 조회하지 않는다")
        fun 일반_멤버면_403() {
            givenGroupMember(MEMBER_ID, memberGroupMember)

            assertThatThrownBy { habitVerifyService.getPendingByGroup(MEMBER_ID, GROUP_ID) }
                .isInstanceOf(AccessDeniedException::class.java)
                .hasMessage("방장만 대기 목록을 조회할 수 있습니다.")

            verify(exactly = 0) { habitVerifyRepository.findPendingByGroupId(any()) }
        }
    }

    @Nested
    @DisplayName("습관 인증 목록(findAll)")
    inner class FindAll {

        @Test
        @DisplayName("같은 그룹 멤버는 그 습관의 인증 목록을 받는다")
        fun 같은_그룹_멤버가_조회() {
            every { habitRepository.findById(50L) } returns Optional.of(habit)
            every { groupMemberRepository.existsByGroupIdAndMemberId(GROUP_ID, OWNER_ID) } returns true
            every { habitVerifyRepository.findAllByHabitIdOrderByVerifyDateDesc(50L) } returns
                listOf(verifyIn(71L, HabitVerifyStatus.PENDING), verifyIn(70L, HabitVerifyStatus.APPROVED))

            val responses = habitVerifyService.findAll(50L, OWNER_ID)

            assertThat(responses.map { it.id }).containsExactly(71L, 70L)
        }

        @Test
        @DisplayName("없는 습관이면 404다")
        fun 없는_습관이면_404() {
            every { habitRepository.findById(999L) } returns Optional.empty()

            assertThatThrownBy { habitVerifyService.findAll(999L, OWNER_ID) }
                .isInstanceOf(EntityNotFoundException::class.java)
                .hasMessage("존재하지 않는 습관입니다.")
        }

        @Test
        @DisplayName("같은 그룹 멤버가 아니면 403이고 목록을 조회하지 않는다")
        fun 같은_그룹이_아니면_403() {
            every { habitRepository.findById(50L) } returns Optional.of(habit)
            every { groupMemberRepository.existsByGroupIdAndMemberId(GROUP_ID, OUTSIDER_ID) } returns false

            assertThatThrownBy { habitVerifyService.findAll(50L, OUTSIDER_ID) }
                .isInstanceOf(ForbiddenException::class.java)
                .hasMessage("같은 그룹의 멤버만 조회할 수 있습니다.")

            verify(exactly = 0) { habitVerifyRepository.findAllByHabitIdOrderByVerifyDateDesc(any()) }
        }
    }
}
