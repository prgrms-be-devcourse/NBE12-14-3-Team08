package com.back.domain.penaltyverify.service

import com.back.domain.group.entity.Group
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.habit.entity.Habit
import com.back.domain.member.entity.Member
import com.back.domain.penaltyverify.dto.BulkActionRequest
import com.back.domain.penaltyverify.dto.SubmitPenaltyVerifyRequest
import com.back.domain.penaltyverify.entity.PenaltyVerify
import com.back.domain.penaltyverify.entity.PenaltyVerifyStatus
import com.back.domain.penaltyverify.repository.PenaltyVerifyRepository
import com.back.global.exception.BusinessRuleException
import com.back.global.exception.ForbiddenException
import com.back.global.storage.StorageService
import com.back.global.storage.UploadUrlResponse
import com.back.global.storage.VerificationImageType
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.justRun
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.security.access.AccessDeniedException
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDate
import java.util.Optional

// MockK는 정해 두지 않은 호출이 오면 실패한다(엄격). Mockito가 기본값으로 넘기던 호출(사진 검사 void, save)은
// 필요한 테스트에서 justRun / every로 명시한다.
@ExtendWith(MockKExtension::class)
class PenaltyVerifyServiceTest {

    @MockK
    private lateinit var penaltyVerifyRepository: PenaltyVerifyRepository

    @MockK
    private lateinit var groupMemberRepository: GroupMemberRepository

    @MockK
    private lateinit var storageService: StorageService

    @InjectMockKs
    private lateinit var penaltyVerifyService: PenaltyVerifyService

    private lateinit var ownerUser: Member
    private lateinit var normalUser: Member
    private lateinit var otherUser: Member
    private lateinit var group: Group
    private lateinit var ownerGroupMember: GroupMember
    private lateinit var normalGroupMember: GroupMember
    private lateinit var habit: Habit

    @BeforeEach
    fun setUp() {
        ownerUser = Member.create("방장", "owner", "pass1234")
        ReflectionTestUtils.setField(ownerUser, "id", 1L)

        normalUser = Member.create("일반회원", "member", "pass1234")
        ReflectionTestUtils.setField(normalUser, "id", 2L)

        otherUser = Member.create("외부인", "other", "pass1234")
        ReflectionTestUtils.setField(otherUser, "id", 999L)

        group = Group.builder()
            .title("아침 기상 챌린지")
            .penalty("커피 1잔 쏘기")
            .memberLimit(10)
            .build()
        ReflectionTestUtils.setField(group, "id", 100L)

        ownerGroupMember = GroupMember.create(group, ownerUser, GroupMemberRole.OWNER)
        ReflectionTestUtils.setField(ownerGroupMember, "id", 10L)

        normalGroupMember = GroupMember.create(group, normalUser, GroupMemberRole.MEMBER)
        ReflectionTestUtils.setField(normalGroupMember, "id", 20L)

        habit = Habit.create(normalGroupMember, "기상 후 스트레칭", "10분 스트레칭", 30)
        ReflectionTestUtils.setField(habit, "id", 50L)
    }

    private fun createFixturePenalty(id: Long, status: PenaltyVerifyStatus): PenaltyVerify {
        val pv = PenaltyVerify.create(normalGroupMember, habit)
        ReflectionTestUtils.setField(pv, "id", id)
        ReflectionTestUtils.setField(pv, "status", status)
        ReflectionTestUtils.setField(pv, "imageUrl", "https://example.com/test.jpg")
        ReflectionTestUtils.setField(pv, "description", "기존 제출 내용")
        return pv
    }

    // 사진 검사를 통과시킨다 (Mockito에서는 void 메서드가 기본으로 아무것도 하지 않았다)
    private fun imageValidationPasses() {
        justRun { storageService.validateImageUrl(any(), any(), any(), any(), any()) }
    }

    @Nested
    @DisplayName("벌칙 생성 및 제출(submit) 테스트")
    inner class SubmitTest {

        @Test
        @DisplayName("submit: 본인의 습관에 대한 벌칙 제출 성공 시 PENDING 상태로 전환")
        fun submit_성공() {
            val pv = createFixturePenalty(1L, PenaltyVerifyStatus.REQUIRED)
            every { penaltyVerifyRepository.findByHabitId(50L) } returns Optional.of(pv)
            imageValidationPasses()

            val request = SubmitPenaltyVerifyRequest("스트레칭 인증합니다.", "https://example.com/stretch.jpg")

            val response = penaltyVerifyService.submit(2L, 50L, request)

            assertThat(response.status).isEqualTo(PenaltyVerifyStatus.PENDING)
            assertThat(response.description).isEqualTo("스트레칭 인증합니다.")
            assertThat(response.imageUrl).isEqualTo("https://example.com/stretch.jpg")
            assertThat(pv.verifyDate).isEqualTo(LocalDate.now())
        }

        @Test
        @DisplayName("submit: 타인의 습관에 벌칙을 제출하려 하면 ForbiddenException(403), 사진 검사는 하지 않는다")
        fun submit_타인습관_예외() {
            val pv = createFixturePenalty(1L, PenaltyVerifyStatus.REQUIRED)
            every { penaltyVerifyRepository.findByHabitId(50L) } returns Optional.of(pv)

            val request = SubmitPenaltyVerifyRequest("타인의 벌칙 제출", "https://example.com/test.jpg")

            assertThatThrownBy { penaltyVerifyService.submit(999L, 50L, request) }
                .isInstanceOf(ForbiddenException::class.java)
                .hasMessageContaining("본인의 습관에 대해서만 벌칙을 제출할 수 있습니다.")
            verify(exactly = 0) { storageService.validateImageUrl(any(), any(), any(), any(), any()) }
        }

        @Test
        @DisplayName("submit: REQUIRED 상태가 아닌 벌칙에 제출 시도 시 IllegalStateException 발생")
        fun submit_REQUIRED아님_예외() {
            val pv = createFixturePenalty(1L, PenaltyVerifyStatus.APPROVED)
            every { penaltyVerifyRepository.findByHabitId(50L) } returns Optional.of(pv)

            val request = SubmitPenaltyVerifyRequest("이미 승인된 벌칙", "https://example.com/test.jpg")

            assertThatThrownBy { penaltyVerifyService.submit(2L, 50L, request) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("벌칙 제출 대상(REQUIRED)가 아닙니다.")
        }
    }

    @Nested
    @DisplayName("벌칙 재제출(resubmit) 테스트")
    inner class ResubmitTest {

        @Test
        @DisplayName("resubmit: 반려된(REJECTED) 벌칙을 본인이 재제출하면 PENDING 상태로 전환")
        fun resubmit_성공() {
            val pv = createFixturePenalty(1L, PenaltyVerifyStatus.REJECTED)
            every { penaltyVerifyRepository.findById(1L) } returns Optional.of(pv)
            imageValidationPasses()

            val request = SubmitPenaltyVerifyRequest("재촬영 인증", "https://example.com/resubmit.jpg")

            val response = penaltyVerifyService.resubmit(2L, 1L, request)

            assertThat(response.status).isEqualTo(PenaltyVerifyStatus.PENDING)
            assertThat(response.description).isEqualTo("재촬영 인증")
        }

        @Test
        @DisplayName("resubmit: REJECTED 상태가 아닌 벌칙을 재제출하려 하면 예외 발생")
        fun resubmit_반려상태아님_예외() {
            val pv = createFixturePenalty(1L, PenaltyVerifyStatus.PENDING)
            every { penaltyVerifyRepository.findById(1L) } returns Optional.of(pv)

            val request = SubmitPenaltyVerifyRequest("재제출 시도", "https://example.com/test.jpg")

            assertThatThrownBy { penaltyVerifyService.resubmit(2L, 1L, request) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("반려된 벌칙만 재제출할 수 있습니다.")
        }

        @Test
        @DisplayName("resubmit: 타인의 벌칙을 재제출하려 하면 ForbiddenException(403), 사진 검사는 하지 않는다")
        fun resubmit_타인벌칙_예외() {
            val pv = createFixturePenalty(1L, PenaltyVerifyStatus.REJECTED)
            every { penaltyVerifyRepository.findById(1L) } returns Optional.of(pv)

            val request = SubmitPenaltyVerifyRequest("타인 재제출 시도", "https://example.com/test.jpg")

            assertThatThrownBy { penaltyVerifyService.resubmit(1L, 1L, request) }
                .isInstanceOf(ForbiddenException::class.java)
                .hasMessageContaining("본인의 벌칙만 재제출할 수 있습니다.")
            verify(exactly = 0) { storageService.validateImageUrl(any(), any(), any(), any(), any()) }
        }

        @Test
        @DisplayName("resubmit: 종료된 방이면 BusinessRuleException(400)이고, 사진 검사 없이 반려 상태 그대로다")
        fun resubmit_종료된_방_400() {
            val pv = createFixturePenalty(1L, PenaltyVerifyStatus.REJECTED)
            group.finish()
            every { penaltyVerifyRepository.findById(1L) } returns Optional.of(pv)

            val request = SubmitPenaltyVerifyRequest("재제출 시도", "https://example.com/test.jpg")

            assertThatThrownBy { penaltyVerifyService.resubmit(2L, 1L, request) }
                .isInstanceOf(BusinessRuleException::class.java)
                .hasMessage("이미 종료된 그룹에서는 벌칙을 재제출할 수 없습니다.")
            verify(exactly = 0) { storageService.validateImageUrl(any(), any(), any(), any(), any()) }
            assertThat(pv.status).isEqualTo(PenaltyVerifyStatus.REJECTED)
        }
    }

    @Nested
    @DisplayName("벌칙 승인(approve) 및 거절(reject) 테스트")
    inner class ApproveRejectTest {

        @Test
        @DisplayName("approve: 방장이 PENDING 상태의 벌칙을 정상 승인(APPROVED)")
        fun approve_성공() {
            val pv = createFixturePenalty(1L, PenaltyVerifyStatus.PENDING)
            every { penaltyVerifyRepository.findById(1L) } returns Optional.of(pv)
            every { groupMemberRepository.findByGroupIdAndMemberId(100L, 1L) } returns Optional.of(ownerGroupMember)

            val response = penaltyVerifyService.approve(1L, 1L)

            assertThat(response.status).isEqualTo(PenaltyVerifyStatus.APPROVED)
        }

        @Test
        @DisplayName("reject: 방장이 PENDING 상태의 벌칙을 정상 거절(REJECTED)")
        fun reject_성공() {
            val pv = createFixturePenalty(1L, PenaltyVerifyStatus.PENDING)
            every { penaltyVerifyRepository.findById(1L) } returns Optional.of(pv)
            every { groupMemberRepository.findByGroupIdAndMemberId(100L, 1L) } returns Optional.of(ownerGroupMember)

            val response = penaltyVerifyService.reject(1L, 1L)

            assertThat(response.status).isEqualTo(PenaltyVerifyStatus.REJECTED)
        }

        @Test
        @DisplayName("방장이 아닌 일반 회원이 승인 시도 시 ForbiddenException (403)")
        fun approve_방장아님_예외() {
            val pv = createFixturePenalty(1L, PenaltyVerifyStatus.PENDING)
            every { penaltyVerifyRepository.findById(1L) } returns Optional.of(pv)
            every { groupMemberRepository.findByGroupIdAndMemberId(100L, 2L) } returns Optional.of(normalGroupMember)

            assertThatThrownBy { penaltyVerifyService.approve(2L, 1L) }
                .isInstanceOf(ForbiddenException::class.java)
                .hasMessageContaining("승인/거절 권한이 없습니다.")
        }

        @Test
        @DisplayName("PENDING 상태가 아닌 벌칙을 승인/반려하려 할 경우 예외 발생")
        fun approve_PENDING아님_예외() {
            val pv = createFixturePenalty(1L, PenaltyVerifyStatus.REQUIRED)
            every { penaltyVerifyRepository.findById(1L) } returns Optional.of(pv)
            every { groupMemberRepository.findByGroupIdAndMemberId(100L, 1L) } returns Optional.of(ownerGroupMember)

            assertThatThrownBy { penaltyVerifyService.approve(1L, 1L) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("승인 대기 상태가 아닙니다.")
        }

        @Test
        @DisplayName("없는 벌칙 인증이면 NoSuchElementException (404), 권한 확인까지 가지 않는다")
        fun approve_없는벌칙_예외() {
            every { penaltyVerifyRepository.findById(404L) } returns Optional.empty()

            assertThatThrownBy { penaltyVerifyService.approve(1L, 404L) }
                .isInstanceOf(NoSuchElementException::class.java)
                .hasMessageContaining("존재하지 않는 벌칙 인증입니다.")

            verify(exactly = 0) { groupMemberRepository.findByGroupIdAndMemberId(100L, 1L) }
        }

        @Test
        @DisplayName("방에 가입하지 않은 사람이 승인 시도 시 ForbiddenException (403), 상태는 그대로")
        fun approve_방밖의사람_예외() {
            val pv = createFixturePenalty(1L, PenaltyVerifyStatus.PENDING)
            every { penaltyVerifyRepository.findById(1L) } returns Optional.of(pv)
            every { groupMemberRepository.findByGroupIdAndMemberId(100L, 999L) } returns Optional.empty()

            assertThatThrownBy { penaltyVerifyService.approve(999L, 1L) }
                .isInstanceOf(ForbiddenException::class.java)
                .hasMessageContaining("승인/거절 권한이 없습니다.")

            assertThat(pv.status).isEqualTo(PenaltyVerifyStatus.PENDING)
        }

        @Test
        @DisplayName("방장이 아닌 일반 회원이 반려 시도 시 ForbiddenException (403), 상태는 그대로")
        fun reject_방장아님_예외() {
            val pv = createFixturePenalty(1L, PenaltyVerifyStatus.PENDING)
            every { penaltyVerifyRepository.findById(1L) } returns Optional.of(pv)
            every { groupMemberRepository.findByGroupIdAndMemberId(100L, 2L) } returns Optional.of(normalGroupMember)

            assertThatThrownBy { penaltyVerifyService.reject(2L, 1L) }
                .isInstanceOf(ForbiddenException::class.java)

            assertThat(pv.status).isEqualTo(PenaltyVerifyStatus.PENDING)
        }

        @Test
        @DisplayName("종료된 방의 벌칙은 방장이어도 승인할 수 없다 (BusinessRuleException 400), 상태는 그대로")
        fun approve_종료된방_예외() {
            group.finish()
            val pv = createFixturePenalty(1L, PenaltyVerifyStatus.PENDING)
            every { penaltyVerifyRepository.findById(1L) } returns Optional.of(pv)
            every { groupMemberRepository.findByGroupIdAndMemberId(100L, 1L) } returns Optional.of(ownerGroupMember)

            assertThatThrownBy { penaltyVerifyService.approve(1L, 1L) }
                .isInstanceOf(BusinessRuleException::class.java)
                .hasMessageContaining("이미 종료된 그룹은 벌칙을 검토할 수 없습니다.")

            assertThat(pv.status).isEqualTo(PenaltyVerifyStatus.PENDING)
        }
    }

    @Nested
    @DisplayName("일괄 처리(bulkApprove, bulkReject) 테스트")
    inner class BulkActionTest {

        @Test
        @DisplayName("bulkApprove: 방장이 선택한 여러 건의 벌칙을 일괄 승인")
        fun bulkApprove_성공() {
            val pv1 = createFixturePenalty(1L, PenaltyVerifyStatus.PENDING)
            val pv2 = createFixturePenalty(2L, PenaltyVerifyStatus.PENDING)

            every { groupMemberRepository.findByGroupIdAndMemberId(100L, 1L) } returns Optional.of(ownerGroupMember)
            every { penaltyVerifyRepository.findAllByIdInAndGroupId(listOf(1L, 2L), 100L) } returns listOf(pv1, pv2)

            val responses = penaltyVerifyService.bulkApprove(1L, 100L, BulkActionRequest(listOf(1L, 2L)))

            assertThat(responses).hasSize(2)
            assertThat(pv1.status).isEqualTo(PenaltyVerifyStatus.APPROVED)
            assertThat(pv2.status).isEqualTo(PenaltyVerifyStatus.APPROVED)
        }

        @Test
        @DisplayName("bulkReject: 방장이 아닌 사용자가 일괄 처리 시 ForbiddenException (403)")
        fun bulkReject_권한없음_예외() {
            every { groupMemberRepository.findByGroupIdAndMemberId(100L, 2L) } returns Optional.of(normalGroupMember)

            assertThatThrownBy { penaltyVerifyService.bulkReject(2L, 100L, BulkActionRequest(listOf(1L))) }
                .isInstanceOf(ForbiddenException::class.java)
                .hasMessageContaining("승인/거절 권한이 없습니다.")

            verify(exactly = 0) { penaltyVerifyRepository.findAllByIdInAndGroupId(listOf(1L), 100L) }
        }

        @Test
        @DisplayName("bulkReject: 방장이 선택한 여러 건의 벌칙을 일괄 반려")
        fun bulkReject_성공() {
            val pv1 = createFixturePenalty(1L, PenaltyVerifyStatus.PENDING)
            val pv2 = createFixturePenalty(2L, PenaltyVerifyStatus.PENDING)
            every { groupMemberRepository.findByGroupIdAndMemberId(100L, 1L) } returns Optional.of(ownerGroupMember)
            every { penaltyVerifyRepository.findAllByIdInAndGroupId(listOf(1L, 2L), 100L) } returns listOf(pv1, pv2)

            val responses = penaltyVerifyService.bulkReject(1L, 100L, BulkActionRequest(listOf(1L, 2L)))

            assertThat(responses.map { it.status })
                .containsExactly(PenaltyVerifyStatus.REJECTED, PenaltyVerifyStatus.REJECTED)
        }

        @Test
        @DisplayName("bulkApprove: 방에 가입하지 않은 사람이면 ForbiddenException (403), 대상 벌칙을 조회하지 않는다")
        fun bulkApprove_방밖의사람_예외() {
            every { groupMemberRepository.findByGroupIdAndMemberId(100L, 999L) } returns Optional.empty()

            assertThatThrownBy { penaltyVerifyService.bulkApprove(999L, 100L, BulkActionRequest(listOf(1L))) }
                .isInstanceOf(ForbiddenException::class.java)

            verify(exactly = 0) { penaltyVerifyRepository.findAllByIdInAndGroupId(listOf(1L), 100L) }
        }

        @Test
        @DisplayName("bulkApprove: 종료된 방이면 BusinessRuleException (400), 대상 벌칙을 조회하지 않는다")
        fun bulkApprove_종료된방_예외() {
            group.finish()
            every { groupMemberRepository.findByGroupIdAndMemberId(100L, 1L) } returns Optional.of(ownerGroupMember)

            assertThatThrownBy { penaltyVerifyService.bulkApprove(1L, 100L, BulkActionRequest(listOf(1L))) }
                .isInstanceOf(BusinessRuleException::class.java)

            verify(exactly = 0) { penaltyVerifyRepository.findAllByIdInAndGroupId(listOf(1L), 100L) }
        }

        @Test
        @DisplayName("bulkApprove: 하나라도 검토 대기가 아니면 IllegalStateException (409) (실제 롤백은 PenaltyVerifyBulkRollbackTest에서 확인)")
        fun bulkApprove_하나라도처리됨_예외() {
            val pending = createFixturePenalty(1L, PenaltyVerifyStatus.PENDING)
            val approved = createFixturePenalty(2L, PenaltyVerifyStatus.APPROVED)
            every { groupMemberRepository.findByGroupIdAndMemberId(100L, 1L) } returns Optional.of(ownerGroupMember)
            every { penaltyVerifyRepository.findAllByIdInAndGroupId(listOf(1L, 2L), 100L) } returns listOf(pending, approved)

            assertThatThrownBy { penaltyVerifyService.bulkApprove(1L, 100L, BulkActionRequest(listOf(1L, 2L))) }
                .isInstanceOf(IllegalStateException::class.java)
        }

        @Test
        @DisplayName("bulkApprove: 조회되지 않은 id(없는 id, 다른 방 id)는 오류 없이 건너뛴다 (현재 동작, #187에서 팀 확인 필요)")
        fun bulkApprove_조회안된id_건너뜀() {
            val pending = createFixturePenalty(1L, PenaltyVerifyStatus.PENDING)
            every { groupMemberRepository.findByGroupIdAndMemberId(100L, 1L) } returns Optional.of(ownerGroupMember)
            every { penaltyVerifyRepository.findAllByIdInAndGroupId(listOf(1L, 404L), 100L) } returns listOf(pending)

            val responses = penaltyVerifyService.bulkApprove(1L, 100L, BulkActionRequest(listOf(1L, 404L)))

            assertThat(responses.map { it.id }).containsExactly(1L)
        }
    }

    @Nested
    @DisplayName("조회 관련(getPendingByGroup, getDetail, getPenaltiesByGroupMember) 테스트")
    inner class QueryTest {

        @Test
        @DisplayName("getPendingByGroup: 방장은 대기 중인 벌칙 목록을 정상 조회")
        fun getPendingByGroup_성공() {
            val pv = createFixturePenalty(1L, PenaltyVerifyStatus.PENDING)
            every { groupMemberRepository.findByGroupIdAndMemberId(100L, 1L) } returns Optional.of(ownerGroupMember)
            every { penaltyVerifyRepository.findPendingByGroupId(100L) } returns listOf(pv)

            val result = penaltyVerifyService.getPendingByGroup(1L, 100L)

            assertThat(result).hasSize(1)
            assertThat(result[0].status).isEqualTo(PenaltyVerifyStatus.PENDING)
        }

        @Test
        @DisplayName("getPendingByGroup: 일반 멤버가 대기 목록 조회를 시도하면 AccessDeniedException 발생")
        fun getPendingByGroup_일반멤버_예외() {
            every { groupMemberRepository.findByGroupIdAndMemberId(100L, 2L) } returns Optional.of(normalGroupMember)

            assertThatThrownBy { penaltyVerifyService.getPendingByGroup(2L, 100L) }
                .isInstanceOf(AccessDeniedException::class.java)
                .hasMessageContaining("방장만 대기 목록을 조회할 수 있습니다.")
        }

        @Test
        @DisplayName("getDetail: 동일 그룹 멤버는 벌칙 상세를 정상 조회")
        fun getDetail_성공() {
            val pv = createFixturePenalty(1L, PenaltyVerifyStatus.PENDING)
            every { penaltyVerifyRepository.findById(1L) } returns Optional.of(pv)
            every { groupMemberRepository.existsByGroupIdAndMemberId(100L, 2L) } returns true

            val response = penaltyVerifyService.getDetail(2L, 1L)

            assertThat(response.id).isEqualTo(1L)
            assertThat(response.habitTitle).isEqualTo("기상 후 스트레칭")
        }

        @Test
        @DisplayName("getDetail: 그룹에 속하지 않은 외부 사용자가 조회 시 AccessDeniedException 발생")
        fun getDetail_외부인_예외() {
            val pv = createFixturePenalty(1L, PenaltyVerifyStatus.PENDING)
            every { penaltyVerifyRepository.findById(1L) } returns Optional.of(pv)
            every { groupMemberRepository.existsByGroupIdAndMemberId(100L, 999L) } returns false

            assertThatThrownBy { penaltyVerifyService.getDetail(999L, 1L) }
                .isInstanceOf(AccessDeniedException::class.java)
                .hasMessageContaining("같은 그룹의 멤버만 조회할 수 있습니다.")
        }

        @Test
        @DisplayName("getPenaltiesByGroupMember: 해당 그룹에 속하지 않은 groupMemberId 조회 시 IllegalArgumentException 발생")
        fun getPenaltiesByGroupMember_없는멤버_예외() {
            every { groupMemberRepository.existsByGroupIdAndMemberId(100L, 1L) } returns true
            every { groupMemberRepository.existsByIdAndGroupId(9999L, 100L) } returns false

            assertThatThrownBy { penaltyVerifyService.getPenaltiesByGroupMember(1L, 100L, 9999L) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("해당 그룹에 속하지 않은 멤버입니다.")
        }

        @Test
        @DisplayName("getPendingByGroup: 방에 가입하지 않은 사람이 대기 목록을 조회하면 AccessDeniedException (403)")
        fun getPendingByGroup_방밖의사람_예외() {
            every { groupMemberRepository.findByGroupIdAndMemberId(100L, 999L) } returns Optional.empty()

            assertThatThrownBy { penaltyVerifyService.getPendingByGroup(999L, 100L) }
                .isInstanceOf(AccessDeniedException::class.java)
                .hasMessageContaining("해당 방의 멤버가 아닙니다.")

            verify(exactly = 0) { penaltyVerifyRepository.findPendingByGroupId(100L) }
        }

        @Test
        @DisplayName("getPenaltiesByGroupMember: 같은 방 멤버는 그 멤버의 벌칙 기록을 받는다")
        fun getPenaltiesByGroupMember_성공() {
            val newer = createFixturePenalty(2L, PenaltyVerifyStatus.PENDING)
            val older = createFixturePenalty(1L, PenaltyVerifyStatus.APPROVED)
            every { groupMemberRepository.existsByGroupIdAndMemberId(100L, 1L) } returns true
            every { groupMemberRepository.existsByIdAndGroupId(20L, 100L) } returns true
            every { penaltyVerifyRepository.findByGroupMemberIdOrderByIdDesc(20L) } returns listOf(newer, older)

            val result = penaltyVerifyService.getPenaltiesByGroupMember(1L, 100L, 20L)

            assertThat(result.map { it.id }).containsExactly(2L, 1L)
        }

        @Test
        @DisplayName("getPenaltiesByGroupMember: 조회하는 사람이 방 멤버가 아니면 AccessDeniedException (403), 목록을 조회하지 않는다")
        fun getPenaltiesByGroupMember_방밖의사람_예외() {
            every { groupMemberRepository.existsByGroupIdAndMemberId(100L, 999L) } returns false

            assertThatThrownBy { penaltyVerifyService.getPenaltiesByGroupMember(999L, 100L, 20L) }
                .isInstanceOf(AccessDeniedException::class.java)
                .hasMessageContaining("같은 그룹의 멤버만 조회할 수 있습니다.")

            verify(exactly = 0) { penaltyVerifyRepository.findByGroupMemberIdOrderByIdDesc(20L) }
        }
    }

    @Nested
    @DisplayName("벌칙 생성(createPenaltyVerify)과 누적 개수(getCount) 테스트")
    inner class CreateAndCountTest {

        @Test
        @DisplayName("createPenaltyVerify: 습관 주인의 미제출(REQUIRED) 벌칙을 그때의 습관 제목·벌칙 문구로 저장한다")
        fun createPenaltyVerify_저장() {
            val saved = slot<PenaltyVerify>()
            every { penaltyVerifyRepository.save(capture(saved)) } answers { saved.captured }

            penaltyVerifyService.createPenaltyVerify(habit)

            verify { penaltyVerifyRepository.save(any<PenaltyVerify>()) }
            assertThat(saved.captured.groupMember).isSameAs(normalGroupMember)
            assertThat(saved.captured.habit).isSameAs(habit)
            assertThat(saved.captured.status).isEqualTo(PenaltyVerifyStatus.REQUIRED)
            assertThat(saved.captured.habitTitle).isEqualTo("기상 후 스트레칭")
            assertThat(saved.captured.penaltyText).isEqualTo("커피 1잔 쏘기")
        }

        @Test
        @DisplayName("getCount: 그 방에서 그 회원이 받은 벌칙 개수를 돌려준다 (방 id, 회원 id 순서로 조회)")
        fun getCount_개수() {
            every { penaltyVerifyRepository.countByGroupMember_Group_IdAndGroupMember_Member_Id(100L, 2L) } returns 3L

            assertThat(penaltyVerifyService.getCount(100L, 2L)).isEqualTo(3L)
        }
    }

    @Nested
    @DisplayName("벌칙 제출 취소 및 초기화(cancelOrReset) 테스트")
    inner class CancelOrResetTest {

        private fun createOwnerPenalty(id: Long, status: PenaltyVerifyStatus): PenaltyVerify {
            val ownerHabit = Habit.create(ownerGroupMember, "아침 독서", "10쪽 읽기", 3)
            ReflectionTestUtils.setField(ownerHabit, "id", 51L)

            val pv = PenaltyVerify.create(ownerGroupMember, ownerHabit)
            ReflectionTestUtils.setField(pv, "id", id)
            ReflectionTestUtils.setField(pv, "status", status)
            ReflectionTestUtils.setField(pv, "imageUrl", "https://example.com/owner.jpg")
            ReflectionTestUtils.setField(pv, "description", "방장 제출 내용")
            return pv
        }

        @Test
        @DisplayName("작성자가 PENDING 벌칙을 제출 취소하면 REQUIRED가 되고 행은 지우지 않는다")
        fun 작성자_PENDING_제출취소_성공() {
            val pv = createFixturePenalty(1L, PenaltyVerifyStatus.PENDING)
            every { penaltyVerifyRepository.findById(1L) } returns Optional.of(pv)
            every { groupMemberRepository.findByGroupIdAndMemberId(100L, 2L) } returns Optional.of(normalGroupMember)

            val response = penaltyVerifyService.cancelOrReset(2L, 1L)

            assertThat(response.status).isEqualTo(PenaltyVerifyStatus.REQUIRED)
            assertThat(pv.imageUrl).isNull()
            assertThat(pv.description).isNull()
            assertThat(pv.verifyDate).isNull()
            verify(exactly = 0) { penaltyVerifyRepository.delete(pv) }
        }

        @Test
        @DisplayName("작성자는 APPROVED 벌칙을 제출 취소할 수 없다 (409)")
        fun 작성자_APPROVED_제출취소_예외() {
            val pv = createFixturePenalty(1L, PenaltyVerifyStatus.APPROVED)
            every { penaltyVerifyRepository.findById(1L) } returns Optional.of(pv)
            every { groupMemberRepository.findByGroupIdAndMemberId(100L, 2L) } returns Optional.of(normalGroupMember)

            assertThatThrownBy { penaltyVerifyService.cancelOrReset(2L, 1L) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("승인 대기(PENDING) 중인 벌칙만 제출을 취소할 수 있습니다.")
            assertThat(pv.status).isEqualTo(PenaltyVerifyStatus.APPROVED)
        }

        @Test
        @DisplayName("방장은 멤버의 APPROVED 벌칙을 초기화할 수 있다")
        fun 방장_APPROVED_초기화_성공() {
            val pv = createFixturePenalty(1L, PenaltyVerifyStatus.APPROVED)
            every { penaltyVerifyRepository.findById(1L) } returns Optional.of(pv)
            every { groupMemberRepository.findByGroupIdAndMemberId(100L, 1L) } returns Optional.of(ownerGroupMember)

            val response = penaltyVerifyService.cancelOrReset(1L, 1L)

            assertThat(response.status).isEqualTo(PenaltyVerifyStatus.REQUIRED)
            assertThat(pv.imageUrl).isNull()
            assertThat(pv.description).isNull()
            verify(exactly = 0) { penaltyVerifyRepository.delete(pv) }
        }

        @Test
        @DisplayName("방장은 자기 자신의 APPROVED 벌칙도 초기화할 수 있다")
        fun 방장_본인벌칙_APPROVED_초기화_성공() {
            val pv = createOwnerPenalty(2L, PenaltyVerifyStatus.APPROVED)
            every { penaltyVerifyRepository.findById(2L) } returns Optional.of(pv)
            every { groupMemberRepository.findByGroupIdAndMemberId(100L, 1L) } returns Optional.of(ownerGroupMember)

            val response = penaltyVerifyService.cancelOrReset(1L, 2L)

            assertThat(response.status).isEqualTo(PenaltyVerifyStatus.REQUIRED)
            assertThat(pv.imageUrl).isNull()
        }

        @Test
        @DisplayName("방 밖의 사람이 요청하면 ForbiddenException (403)")
        fun 방밖의_사람_ForbiddenException() {
            val pv = createFixturePenalty(1L, PenaltyVerifyStatus.PENDING)
            every { penaltyVerifyRepository.findById(1L) } returns Optional.of(pv)
            every { groupMemberRepository.findByGroupIdAndMemberId(100L, 999L) } returns Optional.empty()

            assertThatThrownBy { penaltyVerifyService.cancelOrReset(999L, 1L) }
                .isInstanceOf(ForbiddenException::class.java)
                .hasMessageContaining("벌칙 취소/초기화 권한이 없습니다.")
            assertThat(pv.status).isEqualTo(PenaltyVerifyStatus.PENDING)
        }

        @Test
        @DisplayName("같은 방의 일반 멤버가 남의 벌칙을 요청하면 ForbiddenException (403)")
        fun 같은방_일반멤버_ForbiddenException() {
            val otherGroupMember = GroupMember.create(group, otherUser, GroupMemberRole.MEMBER)
            ReflectionTestUtils.setField(otherGroupMember, "id", 30L)

            val pv = createFixturePenalty(1L, PenaltyVerifyStatus.PENDING)
            every { penaltyVerifyRepository.findById(1L) } returns Optional.of(pv)
            every { groupMemberRepository.findByGroupIdAndMemberId(100L, 999L) } returns Optional.of(otherGroupMember)

            assertThatThrownBy { penaltyVerifyService.cancelOrReset(999L, 1L) }
                .isInstanceOf(ForbiddenException::class.java)
                .hasMessageContaining("벌칙 취소/초기화 권한이 없습니다.")
            assertThat(pv.status).isEqualTo(PenaltyVerifyStatus.PENDING)
        }

        @Test
        @DisplayName("종료된 방의 벌칙은 권한이 있어도 BusinessRuleException (400)")
        fun 종료된방_BusinessRuleException() {
            group.finish()

            val pv = createFixturePenalty(1L, PenaltyVerifyStatus.PENDING)
            every { penaltyVerifyRepository.findById(1L) } returns Optional.of(pv)
            every { groupMemberRepository.findByGroupIdAndMemberId(100L, 2L) } returns Optional.of(normalGroupMember)

            assertThatThrownBy { penaltyVerifyService.cancelOrReset(2L, 1L) }
                .isInstanceOf(BusinessRuleException::class.java)
                .hasMessageContaining("이미 종료된 그룹의 벌칙 기록은 변경할 수 없습니다.")
            assertThat(pv.status).isEqualTo(PenaltyVerifyStatus.PENDING)
        }
    }

    @Nested
    @DisplayName("벌칙 사진 업로드 URL 발급(createUploadUrl) 테스트")
    inner class CreateUploadUrlTest {

        @ParameterizedTest(name = "{0} 벌칙은 발급 가능")
        @EnumSource(value = PenaltyVerifyStatus::class, names = ["REQUIRED", "REJECTED"])
        @DisplayName("내 벌칙이 제출 또는 재제출 가능한 상태면 벌칙 사진 업로드 URL을 발급한다")
        fun 업로드_URL_발급_성공(status: PenaltyVerifyStatus) {
            val pv = createFixturePenalty(1L, status)
            val expected = UploadUrlResponse("https://upload", "https://public/2/1/a.png")
            every { penaltyVerifyRepository.findById(1L) } returns Optional.of(pv)
            every { storageService.createUploadUrl(VerificationImageType.PENALTY, 2L, 1L, "proof.png") } returns expected

            val response = penaltyVerifyService.createUploadUrl(2L, 1L, "proof.png")

            assertThat(response).isEqualTo(expected)
        }

        @Test
        @DisplayName("내 벌칙이 아니면 ForbiddenException (403)")
        fun 내_벌칙_아님_403() {
            val pv = createFixturePenalty(1L, PenaltyVerifyStatus.REQUIRED)
            every { penaltyVerifyRepository.findById(1L) } returns Optional.of(pv)

            assertThatThrownBy { penaltyVerifyService.createUploadUrl(999L, 1L, "proof.png") }
                .isInstanceOf(ForbiddenException::class.java)

            verify(exactly = 0) { storageService.createUploadUrl(any(), any(), any(), any()) }
        }

        @ParameterizedTest(name = "{0} 벌칙은 발급 불가")
        @EnumSource(value = PenaltyVerifyStatus::class, names = ["PENDING", "APPROVED"])
        @DisplayName("이미 제출했거나 승인된 벌칙이면 IllegalStateException (409)")
        fun 제출_불가_상태_409(status: PenaltyVerifyStatus) {
            val pv = createFixturePenalty(1L, status)
            every { penaltyVerifyRepository.findById(1L) } returns Optional.of(pv)

            assertThatThrownBy { penaltyVerifyService.createUploadUrl(2L, 1L, "proof.png") }
                .isInstanceOf(IllegalStateException::class.java)

            verify(exactly = 0) { storageService.createUploadUrl(any(), any(), any(), any()) }
        }

        @Test
        @DisplayName("종료된 방이면 BusinessRuleException (400)")
        fun 종료된_방_400() {
            group.finish()
            val pv = createFixturePenalty(1L, PenaltyVerifyStatus.REQUIRED)
            every { penaltyVerifyRepository.findById(1L) } returns Optional.of(pv)

            assertThatThrownBy { penaltyVerifyService.createUploadUrl(2L, 1L, "proof.png") }
                .isInstanceOf(BusinessRuleException::class.java)

            verify(exactly = 0) { storageService.createUploadUrl(any(), any(), any(), any()) }
        }
    }

    @Nested
    @DisplayName("벌칙 제출 시 인증 사진 검사 테스트")
    inner class SubmitImageValidationTest {

        @Test
        @DisplayName("submit: 사진 URL 검사에 통과하면 지금 벌칙 id와 기존 사진을 넘겨 검사한 뒤 PENDING이 된다")
        fun submit_사진검사_통과() {
            val pv = createFixturePenalty(1L, PenaltyVerifyStatus.REQUIRED)
            ReflectionTestUtils.setField(pv, "imageUrl", null)
            every { penaltyVerifyRepository.findByHabitId(50L) } returns Optional.of(pv)
            imageValidationPasses()

            penaltyVerifyService.submit(2L, 50L, SubmitPenaltyVerifyRequest("수행", "https://storage/new.png"))

            verify { storageService.validateImageUrl(VerificationImageType.PENALTY, 2L, 1L, "https://storage/new.png", null) }
            assertThat(pv.status).isEqualTo(PenaltyVerifyStatus.PENDING)
        }

        @Test
        @DisplayName("submit: 사진 URL 검사에 실패하면 400이고 벌칙 상태는 그대로다")
        fun submit_사진검사_실패() {
            val pv = createFixturePenalty(1L, PenaltyVerifyStatus.REQUIRED)
            every { penaltyVerifyRepository.findByHabitId(50L) } returns Optional.of(pv)
            every { storageService.validateImageUrl(any(), any(), any(), any(), any()) } throws
                IllegalArgumentException("업로드한 사진만 사용할 수 있습니다.")

            assertThatThrownBy {
                penaltyVerifyService.submit(2L, 50L, SubmitPenaltyVerifyRequest("수행", "https://evil.com/a.png"))
            }
                .isInstanceOf(IllegalArgumentException::class.java)

            assertThat(pv.status).isEqualTo(PenaltyVerifyStatus.REQUIRED)
        }

        @Test
        @DisplayName("submit: 제출할 수 없는 상태(PENDING)면 사진 검사 전에 409")
        fun submit_상태먼저() {
            val pv = createFixturePenalty(1L, PenaltyVerifyStatus.PENDING)
            every { penaltyVerifyRepository.findByHabitId(50L) } returns Optional.of(pv)

            assertThatThrownBy {
                penaltyVerifyService.submit(2L, 50L, SubmitPenaltyVerifyRequest("수행", "https://storage/new.png"))
            }
                .isInstanceOf(IllegalStateException::class.java)

            verify(exactly = 0) { storageService.validateImageUrl(any(), any(), any(), any(), any()) }
        }

        @Test
        @DisplayName("resubmit: 지금 붙어 있는 사진을 기존 사진으로 넘겨 검사한다 (예전 경로 사진 재사용 허용)")
        fun resubmit_기존사진_전달() {
            val pv = createFixturePenalty(1L, PenaltyVerifyStatus.REJECTED)
            ReflectionTestUtils.setField(pv, "imageUrl", "https://storage/penalties/2/old.png")
            every { penaltyVerifyRepository.findById(1L) } returns Optional.of(pv)
            imageValidationPasses()

            penaltyVerifyService.resubmit(
                2L, 1L, SubmitPenaltyVerifyRequest("다시 수행", "https://storage/penalties/2/old.png")
            )

            verify {
                storageService.validateImageUrl(
                    VerificationImageType.PENALTY, 2L, 1L,
                    "https://storage/penalties/2/old.png", "https://storage/penalties/2/old.png",
                )
            }
            assertThat(pv.status).isEqualTo(PenaltyVerifyStatus.PENDING)
        }
    }
}
