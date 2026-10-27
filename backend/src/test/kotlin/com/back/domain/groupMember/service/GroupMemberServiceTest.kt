package com.back.domain.groupMember.service

import com.back.domain.group.dto.GroupRequest
import com.back.domain.group.entity.Group
import com.back.domain.group.repository.GroupRepository
import com.back.domain.groupMember.dto.GroupMemberResponse
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.entity.GroupMemberStatus
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.habit.repository.HabitRepository
import com.back.domain.habitVerify.repository.HabitVerifyRepository
import com.back.domain.member.entity.Member
import com.back.domain.member.repository.MemberRepository
import com.back.domain.penaltyverify.repository.PenaltyVerifyRepository
import com.back.global.exception.BusinessRuleException
import com.back.global.exception.EntityNotFoundException
import com.back.global.exception.ForbiddenException
import com.back.global.exception.GroupLimitExceededException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import java.time.LocalDate
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionTemplate

@ExtendWith(MockKExtension::class)
class GroupMemberServiceTest {

    @InjectMockKs
    private lateinit var groupMemberService: GroupMemberService

    @MockK(relaxUnitFun = true)
    private lateinit var groupMemberRepository: GroupMemberRepository

    @MockK(relaxUnitFun = true)
    private lateinit var groupRepository: GroupRepository

    @MockK(relaxUnitFun = true)
    private lateinit var memberRepository: MemberRepository

    @MockK(relaxUnitFun = true)
    private lateinit var habitRepository: HabitRepository

    @MockK(relaxUnitFun = true)
    private lateinit var habitVerifyRepository: HabitVerifyRepository

    @MockK(relaxUnitFun = true)
    private lateinit var penaltyVerifyRepository: PenaltyVerifyRepository

    @MockK
    private lateinit var passwordEncoder: PasswordEncoder

    @MockK(relaxUnitFun = true)
    private lateinit var entityManager: EntityManager

    @SpyK
    private var transactionTemplate = TransactionTemplate(
        object : PlatformTransactionManager {
            override fun getTransaction(
                definition: TransactionDefinition?
            ): TransactionStatus = SimpleTransactionStatus()

            override fun commit(status: TransactionStatus) = Unit

            override fun rollback(status: TransactionStatus) = Unit
        }
    )

    private val memberId = 1L
    private val groupId = 100L
    private val groupMemberId = 50L
    private val inviteCode = "abcdefgh"

    private fun createActiveGroup(): Group {
        val group = Group(
            password = "encoded_pass",
            memberLimit = 5,
            deadline = LocalDate.now().plusDays(7)
        )
        ReflectionTestUtils.setField(group, "id", groupId)
        return group
    }

    private fun newGroupMember(
        group: Group,
        role: GroupMemberRole
    ): GroupMember {
        val member = mockk<Member>()
        return GroupMember.create(group, member, role)
    }

    @BeforeEach
    fun setUp() {
        ReflectionTestUtils.setField(
            groupMemberService,
            "entityManager",
            entityManager
        )
    }

    // 그룹 가입
    @Nested
    @DisplayName("JoinGroup")
    inner class JoinGroup {

        @Nested
        @DisplayName("Success")
        inner class Success {

            @Test
            @DisplayName("이미 가입한 멤버는 기존 그룹 ID를 반환한다")
            fun joinGroup_AlreadyMember() {
                val group = createActiveGroup()
                val request = GroupRequest.Join("pass123")

                every {
                    groupRepository.findByInviteCode(inviteCode)
                } returns group

                every {
                    groupMemberRepository.countByGroupIdAndStatus(
                        groupId, GroupMemberStatus.ACTIVE
                    )
                } returns 1L

                every {
                    groupMemberRepository.existsByGroupIdAndMemberId(
                        groupId, memberId
                    )
                } returns true

                val result = groupMemberService.joinGroup(
                    inviteCode, memberId, request
                )

                assertThat(result).isEqualTo(groupId)

                verify(exactly = 0) {
                    groupMemberRepository.save(any<GroupMember>())
                }
            }

            @Test
            @DisplayName("비밀번호와 정원 검증을 통과하면 신규 가입한다")
            fun joinGroup_NewMember() {
                val group = createActiveGroup()
                val member = mockk<Member>()
                val request = GroupRequest.Join("pass123")

                every {
                    groupRepository.findByInviteCode(inviteCode)
                } returns group

                every {
                    groupMemberRepository.existsByGroupIdAndMemberId(
                        groupId, memberId
                    )
                } returns false

                every {
                    passwordEncoder.matches("pass123", "encoded_pass")
                } returns true

                every {
                    groupMemberRepository.countByGroupIdAndStatus(
                        groupId, GroupMemberStatus.ACTIVE
                    )
                } returns 3L

                every {
                    memberRepository.findById(memberId)
                } returns Optional.of(member)

                // save()는 Unit이 아니라 저장된 엔티티를 반환하므로 별도 설정한다.
                every {
                    groupMemberRepository.save(any<GroupMember>())
                } answers {
                    firstArg<GroupMember>()
                }

                val result = groupMemberService.joinGroup(
                    inviteCode, memberId, request
                )

                assertThat(result).isEqualTo(groupId)

                verify(exactly = 1) {
                    groupMemberRepository.save(any<GroupMember>())
                }
            }

            @Test
            @DisplayName("같은 회원의 요청이 먼저 잠금을 잡고 참여했으면 잠금 안에서 기존 그룹 ID를 반환한다")
            fun joinGroup_JoinedWhileWaitingForLock() {
                val group = createActiveGroup()
                val request = GroupRequest.Join("pass123")

                every {
                    groupRepository.findByInviteCode(inviteCode)
                } returns group

                every {
                    groupMemberRepository.existsByGroupIdAndMemberId(
                        groupId, memberId
                    )
                } returnsMany listOf(false, true)

                every {
                    passwordEncoder.matches("pass123", "encoded_pass")
                } returns true

                every {
                    groupMemberRepository.countByGroupIdAndStatus(
                        groupId, GroupMemberStatus.ACTIVE
                    )
                } returns 1L

                val result = groupMemberService.joinGroup(
                    inviteCode, memberId, request
                )

                assertThat(result).isEqualTo(groupId)

                verify(exactly = 0) {
                    groupMemberRepository.save(any<GroupMember>())
                }
            }
        }

        @Nested
        @DisplayName("Failure")
        inner class Failure {

            @Test
            @DisplayName("그룹 비밀번호가 일치하지 않으면 예외가 발생한다")
            fun joinGroup_WrongPassword() {
                val group = createActiveGroup()
                val request = GroupRequest.Join("wrong_pass")

                every {
                    groupRepository.findByInviteCode(inviteCode)
                } returns group

                every {
                    groupMemberRepository.existsByGroupIdAndMemberId(
                        groupId, memberId
                    )
                } returns false

                every {
                    groupMemberRepository.countByGroupIdAndStatus(
                        groupId, GroupMemberStatus.ACTIVE
                    )
                } returns 1L

                every {
                    passwordEncoder.matches(
                        "wrong_pass", "encoded_pass"
                    )
                } returns false

                assertThatThrownBy {
                    groupMemberService.joinGroup(
                        inviteCode, memberId, request
                    )
                }
                    .isInstanceOf(ForbiddenException::class.java)
                    .hasMessageContaining(
                        "그룹 비밀번호가 일치하지 않습니다."
                    )
            }

            @Test
            @DisplayName("이미 종료된 그룹이면 가입할 수 없다")
            fun joinGroup_AlreadyFinished() {
                val group = mockk<Group>()
                val request = GroupRequest.Join("pass123")

                every { group.isFinished() } returns true

                every {
                    groupRepository.findByInviteCode(inviteCode)
                } returns group

                assertThatThrownBy {
                    groupMemberService.joinGroup(
                        inviteCode, memberId, request
                    )
                }
                    .isInstanceOf(BusinessRuleException::class.java)
                    .hasMessageContaining("이미 마감된 그룹입니다.")
            }

            @Test
            @DisplayName("그룹 정원이 가득 차면 비밀번호를 확인하지 않고 가입할 수 없다")
            fun joinGroup_LimitExceeded() {
                val group = createActiveGroup()
                val request = GroupRequest.Join("pass123")

                every {
                    groupRepository.findByInviteCode(inviteCode)
                } returns group

                every {
                    groupMemberRepository.existsByGroupIdAndMemberId(
                        groupId, memberId
                    )
                } returns false

                every {
                    groupMemberRepository.countByGroupIdAndStatus(
                        groupId, GroupMemberStatus.ACTIVE
                    )
                } returns 5L

                assertThatThrownBy {
                    groupMemberService.joinGroup(inviteCode, memberId, request)
                }
                    .isInstanceOf(GroupLimitExceededException::class.java)
                    .hasMessageContaining(
                        "그룹의 최대 인원이 초과되어 입장할 수 없습니다."
                    )

                verify(exactly = 0) {
                    passwordEncoder.matches(any<String>(), any<String>())
                }
            }

            @Test
            @DisplayName("그룹 비밀번호가 틀리면 그룹 잠금을 잡지 않는다")
            fun joinGroup_WrongPassword_DoesNotLock() {
                val group = createActiveGroup()
                val request = GroupRequest.Join("wrong_pass")

                every {
                    groupRepository.findByInviteCode(inviteCode)
                } returns group

                every {
                    groupMemberRepository.existsByGroupIdAndMemberId(
                        groupId, memberId
                    )
                } returns false

                every {
                    groupMemberRepository.countByGroupIdAndStatus(
                        groupId, GroupMemberStatus.ACTIVE
                    )
                } returns 1L

                every {
                    passwordEncoder.matches("wrong_pass", "encoded_pass")
                } returns false

                assertThatThrownBy {
                    groupMemberService.joinGroup(inviteCode, memberId, request)
                }
                    .isInstanceOf(ForbiddenException::class.java)

                verify(exactly = 0) {
                    entityManager.refresh(
                        group, LockModeType.PESSIMISTIC_WRITE
                    )
                }

                verify(exactly = 0) {
                    groupMemberRepository.save(any<GroupMember>())
                }
            }

            @Test
            @DisplayName("그룹 정원이 가득 찼으면 비밀번호가 틀려도 정원 초과로 거절한다")
            fun joinGroup_LimitExceeded_WrongPassword() {
                val group = createActiveGroup()
                val request = GroupRequest.Join("wrong_pass")

                every {
                    groupRepository.findByInviteCode(inviteCode)
                } returns group

                every {
                    groupMemberRepository.existsByGroupIdAndMemberId(
                        groupId, memberId
                    )
                } returns false

                every {
                    groupMemberRepository.countByGroupIdAndStatus(
                        groupId, GroupMemberStatus.ACTIVE
                    )
                } returns 5L

                assertThatThrownBy {
                    groupMemberService.joinGroup(inviteCode, memberId, request)
                }
                    .isInstanceOf(GroupLimitExceededException::class.java)

                verify(exactly = 0) {
                    passwordEncoder.matches(any<String>(), any<String>())
                }
            }

            @Test
            @DisplayName("비밀번호를 확인하는 사이 마지막 자리가 차면 잠금 안에서 정원 초과로 거절한다")
            fun joinGroup_FilledWhileCheckingPassword() {
                val group = createActiveGroup()
                val request = GroupRequest.Join("pass123")

                every {
                    groupRepository.findByInviteCode(inviteCode)
                } returns group

                every {
                    groupMemberRepository.existsByGroupIdAndMemberId(
                        groupId, memberId
                    )
                } returns false

                every {
                    passwordEncoder.matches("pass123", "encoded_pass")
                } returns true

                every {
                    groupMemberRepository.countByGroupIdAndStatus(
                        groupId, GroupMemberStatus.ACTIVE
                    )
                } returnsMany listOf(4L, 5L)

                assertThatThrownBy {
                    groupMemberService.joinGroup(inviteCode, memberId, request)
                }
                    .isInstanceOf(GroupLimitExceededException::class.java)

                verify(exactly = 0) {
                    groupMemberRepository.save(any<GroupMember>())
                }
            }

            @Test
            @DisplayName("비밀번호 확인 뒤 잠금을 잡기 전에 그룹 비밀번호가 바뀌면 바뀐 비밀번호로 다시 확인한다")
            fun joinGroup_PasswordChangedBeforeLock() {
                val group = createActiveGroup()
                val request = GroupRequest.Join("pass123")

                every {
                    groupRepository.findByInviteCode(inviteCode)
                } returns group

                every {
                    entityManager.refresh(
                        group, LockModeType.PESSIMISTIC_WRITE
                    )
                } answers {
                    group.password = "changed_encoded_pass"
                    Unit
                }

                every {
                    groupMemberRepository.existsByGroupIdAndMemberId(
                        groupId, memberId
                    )
                } returns false

                every {
                    groupMemberRepository.countByGroupIdAndStatus(
                        groupId, GroupMemberStatus.ACTIVE
                    )
                } returns 1L

                every {
                    passwordEncoder.matches("pass123", "encoded_pass")
                } returns true

                every {
                    passwordEncoder.matches(
                        "pass123", "changed_encoded_pass"
                    )
                } returns false

                assertThatThrownBy {
                    groupMemberService.joinGroup(
                        inviteCode, memberId, request
                    )
                }
                    .isInstanceOf(ForbiddenException::class.java)
                    .hasMessageContaining("그룹 비밀번호가 일치하지 않습니다.")

                verify(exactly = 0) {
                    groupMemberRepository.save(any<GroupMember>())
                }
            }
        }
    }

    // 그룹 멤버 목록 조회
    @Nested
    @DisplayName("GetGroupMembers")
    inner class GetGroupMembers {

        @Nested
        @DisplayName("Success")
        inner class Success {

            @Test
            @DisplayName("활성 멤버이면 그룹 멤버 목록을 반환한다")
            fun getGroupMembers() {
                every {
                    groupMemberRepository
                        .existsByGroupIdAndMemberIdAndStatus(
                            groupId, memberId, GroupMemberStatus.ACTIVE
                        )
                } returns true

                every {
                    groupMemberRepository.findByGroupIdWithHabit(groupId)
                } returns emptyList()

                val result: List<GroupMemberResponse.Simple> =
                    groupMemberService.getGroupMembers(groupId, memberId)

                assertThat(result).isNotNull
                assertThat(result).isEmpty()

                verify(exactly = 1) {
                    groupMemberRepository.findByGroupIdWithHabit(groupId)
                }
            }
        }

        @Nested
        @DisplayName("Failure")
        inner class Failure {

            @Test
            @DisplayName("그룹의 활성 멤버가 아니면 목록을 조회할 수 없다")
            fun getGroupMembers_NoAccess() {
                every {
                    groupMemberRepository
                        .existsByGroupIdAndMemberIdAndStatus(
                            groupId, memberId, GroupMemberStatus.ACTIVE
                        )
                } returns false

                assertThatThrownBy {
                    groupMemberService.getGroupMembers(groupId, memberId)
                }
                    .isInstanceOf(ForbiddenException::class.java)
                    .hasMessageContaining("해당 그룹의 접근 권한이 없습니다.")

                verify(exactly = 0) {
                    groupMemberRepository.findByGroupIdWithHabit(groupId)
                }
            }
        }
    }

    // 그룹 멤버 상세 조회
    @Nested
    @DisplayName("GetGroupMemberDetail")
    inner class GetGroupMemberDetail {

        @Nested
        @DisplayName("Success")
        inner class Success {

            @Test
            @DisplayName("활성 멤버이면 벌칙 횟수를 포함한 상세 정보를 반환한다")
            fun getGroupMemberDetail() {
                val detail = GroupMemberResponse.Detail(
                    groupMemberId,
                    memberId,
                    "닉네임",
                    "아이디",
                    "MEMBER",
                    2L
                )

                every {
                    groupMemberRepository
                        .existsByGroupIdAndMemberIdAndStatus(
                            groupId, memberId, GroupMemberStatus.ACTIVE
                        )
                } returns true

                every {
                    groupMemberRepository.findMemberDetailWithPenaltyCount(
                        groupId, groupMemberId
                    )
                } returns Optional.of(detail)

                val result = groupMemberService.getGroupMemberDetail(
                    groupId, groupMemberId, memberId
                )

                assertThat(result).isNotNull
                assertThat(result.groupMemberId).isEqualTo(groupMemberId)
                assertThat(result.penaltyCount).isEqualTo(2L)
            }
        }

        @Nested
        @DisplayName("Failure")
        inner class Failure {

            @Test
            @DisplayName("활성 멤버가 아니면 상세 정보를 조회할 수 없다")
            fun getGroupMemberDetail_NoAccess() {
                every {
                    groupMemberRepository
                        .existsByGroupIdAndMemberIdAndStatus(
                            groupId, memberId, GroupMemberStatus.ACTIVE
                        )
                } returns false

                assertThatThrownBy {
                    groupMemberService.getGroupMemberDetail(
                        groupId, groupMemberId, memberId
                    )
                }
                    .isInstanceOf(ForbiddenException::class.java)
                    .hasMessageContaining("해당 그룹의 접근 권한이 없습니다.")

                verify(exactly = 0) {
                    groupMemberRepository.findMemberDetailWithPenaltyCount(
                        groupId, groupMemberId
                    )
                }
            }

            @Test
            @DisplayName("상세 조회 대상이 없으면 예외가 발생한다")
            fun getGroupMemberDetail_NotFound() {
                every {
                    groupMemberRepository
                        .existsByGroupIdAndMemberIdAndStatus(
                            groupId, memberId, GroupMemberStatus.ACTIVE
                        )
                } returns true

                every {
                    groupMemberRepository.findMemberDetailWithPenaltyCount(
                        groupId, groupMemberId
                    )
                } returns Optional.empty()

                assertThatThrownBy {
                    groupMemberService.getGroupMemberDetail(
                        groupId, groupMemberId, memberId
                    )
                }
                    .isInstanceOf(EntityNotFoundException::class.java)
                    .hasMessageContaining(
                        "존재하지 않거나 해당 그룹의 가입 멤버가 아닙니다."
                    )
            }
        }
    }

    // 그룹 탈퇴
    @Nested
    @DisplayName("LeaveGroup")
    inner class LeaveGroup {

        @Nested
        @DisplayName("Success")
        inner class Success {

            @Test
            @DisplayName("진행 중인 그룹에서 탈퇴하면 연관 데이터와 멤버가 삭제된다")
            fun leaveGroup_ActiveMember() {
                val group = createActiveGroup()
                val groupMember = newGroupMember(group, GroupMemberRole.MEMBER)
                ReflectionTestUtils.setField(
                    groupMember, "id", groupMemberId
                )

                every {
                    groupRepository.findByIdForUpdate(groupId)
                } returns group

                every {
                    groupMemberRepository.findByGroupIdAndMemberId(
                        groupId, memberId
                    )
                } returns Optional.of(groupMember)

                every {
                    groupMemberRepository.countByGroupIdAndStatus(
                        groupId, GroupMemberStatus.ACTIVE
                    )
                } returns 2L

                every {
                    habitRepository.findIdsByGroupMemberId(groupMemberId)
                } returns listOf(10L, 11L)

                groupMemberService.leaveGroup(groupId, memberId)

                verify(exactly = 1) {
                    habitVerifyRepository.deleteByHabitIds(any<List<Long>>())
                }
                verify(exactly = 1) {
                    penaltyVerifyRepository.deleteByGroupMemberId(groupMemberId)
                }
                verify(exactly = 1) {
                    habitRepository.deleteByIds(any<List<Long>>())
                }
                verify(exactly = 1) {
                    groupMemberRepository.delete(groupMember)
                }
            }

            @Test
            @DisplayName("종료된 그룹에서 탈퇴하면 멤버 상태를 LEFT로 변경한다")
            fun leaveGroup_FinishedGroup() {
                val group = mockk<Group>()
                val groupMember = newGroupMember(
                    group, GroupMemberRole.MEMBER
                )

                every { group.isFinished() } returns true

                every {
                    groupRepository.findByIdForUpdate(groupId)
                } returns group

                every {
                    groupMemberRepository.findByGroupIdAndMemberId(
                        groupId, memberId
                    )
                } returns Optional.of(groupMember)

                every {
                    groupMemberRepository.countByGroupIdAndStatus(
                        groupId, GroupMemberStatus.ACTIVE
                    )
                } returns 2L

                groupMemberService.leaveGroup(groupId, memberId)

                assertThat(groupMember.status).isEqualTo(
                    GroupMemberStatus.LEFT
                )

                verify(exactly = 0) {
                    groupMemberRepository.delete(any<GroupMember>())
                }
                verify(exactly = 0) {
                    habitRepository.findIdsByGroupMemberId(any())
                }
            }
        }

        @Nested
        @DisplayName("Failure")
        inner class Failure {

            @Test
            @DisplayName("진행 중인 그룹에 다른 멤버가 남아 있으면 방장은 바로 탈퇴할 수 없다")
            fun leaveGroup_OwnerCannotLeaveActiveGroup() {
                val group = createActiveGroup()
                val owner = newGroupMember(group, GroupMemberRole.OWNER)

                every {
                    groupRepository.findByIdForUpdate(groupId)
                } returns group

                every {
                    groupMemberRepository.findByGroupIdAndMemberId(
                        groupId, memberId
                    )
                } returns Optional.of(owner)

                every {
                    groupMemberRepository.countByGroupIdAndStatus(
                        groupId, GroupMemberStatus.ACTIVE
                    )
                } returns 2L

                assertThatThrownBy {
                    groupMemberService.leaveGroup(groupId, memberId)
                }
                    .isInstanceOf(BusinessRuleException::class.java)
                    .hasMessageContaining(
                        "방장은 다른 멤버가 남아있는 상태에서 바로 탈퇴할 수 없습니다."
                    )
            }

            @Test
            @DisplayName("존재하지 않는 그룹이면 탈퇴할 수 없다")
            fun leaveGroup_GroupNotFound() {
                every {
                    groupRepository.findByIdForUpdate(groupId)
                } returns null

                assertThatThrownBy {
                    groupMemberService.leaveGroup(groupId, memberId)
                }
                    .isInstanceOf(EntityNotFoundException::class.java)
                    .hasMessageContaining("존재하지 않는 그룹입니다.")
            }

            @Test
            @DisplayName("그룹에 가입하지 않은 멤버는 탈퇴할 수 없다")
            fun leaveGroup_MemberNotFound() {
                val group = createActiveGroup()

                every {
                    groupRepository.findByIdForUpdate(groupId)
                } returns group

                every {
                    groupMemberRepository.findByGroupIdAndMemberId(
                        groupId, memberId
                    )
                } returns Optional.empty()

                assertThatThrownBy {
                    groupMemberService.leaveGroup(groupId, memberId)
                }
                    .isInstanceOf(EntityNotFoundException::class.java)
                    .hasMessageContaining("해당 그룹의 멤버가 아닙니다.")
            }

            @Test
            @DisplayName("이미 탈퇴한 멤버는 다시 탈퇴할 수 없다")
            fun leaveGroup_AlreadyLeft() {
                val group = createActiveGroup()
                val groupMember = newGroupMember(
                    group, GroupMemberRole.MEMBER
                )
                groupMember.leave()

                every {
                    groupRepository.findByIdForUpdate(groupId)
                } returns group

                every {
                    groupMemberRepository.findByGroupIdAndMemberId(
                        groupId, memberId
                    )
                } returns Optional.of(groupMember)

                assertThatThrownBy {
                    groupMemberService.leaveGroup(groupId, memberId)
                }
                    .isInstanceOf(BusinessRuleException::class.java)
                    .hasMessageContaining("이미 탈퇴한 멤버입니다.")
            }
        }
    }

    // 멤버 데이터 일괄 삭제
    @Nested
    @DisplayName("DeleteGroupMemberDataBulk")
    inner class DeleteGroupMemberDataBulk {

        @Nested
        @DisplayName("Success")
        inner class Success {

            @Test
            @DisplayName("습관 데이터가 없어도 벌칙 인증 데이터는 삭제한다")
            fun deleteGroupMemberDataBulk_NoHabits() {
                every {
                    habitRepository.findIdsByGroupMemberId(groupMemberId)
                } returns emptyList()

                groupMemberService.deleteGroupMemberDataBulk(groupMemberId)

                verify(exactly = 1) {
                    habitRepository.findIdsByGroupMemberId(groupMemberId)
                }
                verify(exactly = 1) {
                    penaltyVerifyRepository.deleteByGroupMemberId(groupMemberId)
                }
                verify(exactly = 0) {
                    habitVerifyRepository.deleteByHabitIds(any<List<Long>>())
                }
                verify(exactly = 0) {
                    habitRepository.deleteByIds(any<List<Long>>())
                }
            }
        }
    }

    // 멤버 추방
    @Nested
    @DisplayName("KickMember")
    inner class KickMember {

        @Nested
        @DisplayName("Success")
        inner class Success {

            @Test
            @DisplayName("방장이 진행 중인 그룹의 일반 멤버를 추방한다")
            fun kickMember() {
                val group = createActiveGroup()
                val owner = newGroupMember(group, GroupMemberRole.OWNER)
                ReflectionTestUtils.setField(owner, "id", groupMemberId)

                val targetId = 55L
                val target = newGroupMember(group, GroupMemberRole.MEMBER)
                ReflectionTestUtils.setField(target, "id", targetId)

                every {
                    groupMemberRepository.findByGroupIdAndMemberId(
                        groupId, memberId
                    )
                } returns Optional.of(owner)

                every {
                    groupMemberRepository.findById(targetId)
                } returns Optional.of(target)

                every {
                    habitRepository.findIdsByGroupMemberId(targetId)
                } returns emptyList()

                groupMemberService.kickMember(groupId, targetId, memberId)

                verify(exactly = 1) {
                    penaltyVerifyRepository.deleteByGroupMemberId(targetId)
                }
                verify(exactly = 1) {
                    groupMemberRepository.delete(target)
                }
            }
            @Test
            @DisplayName("마지막 멤버가 탈퇴하면 연관 데이터를 삭제하고 그룹도 삭제한다")
            fun leaveGroup_LastMember() {
                val group = createActiveGroup()
                val groupMember = newGroupMember(
                    group, GroupMemberRole.OWNER
                )

                every {
                    groupRepository.findByIdForUpdate(groupId)
                } returns group

                every {
                    groupMemberRepository.findByGroupIdAndMemberId(
                        groupId, memberId
                    )
                } returns Optional.of(groupMember)

                every {
                    groupMemberRepository.countByGroupIdAndStatus(
                        groupId, GroupMemberStatus.ACTIVE
                    )
                } returns 1L

                every {
                    groupRepository.findById(groupId)
                } returns Optional.of(group)

                every {
                    habitVerifyRepository.deleteHabitVerifiesByGroupId(groupId)
                } returns 0

                every {
                    penaltyVerifyRepository.deletePenaltyVerifiesByGroupId(groupId)
                } returns 0

                every {
                    habitRepository.deleteHabitsByGroupId(groupId)
                } returns 0

                every {
                    groupMemberRepository.deleteGroupMembersByGroupId(groupId)
                } returns 0

                groupMemberService.leaveGroup(groupId, memberId)

                verify(exactly = 1) {
                    habitVerifyRepository.deleteHabitVerifiesByGroupId(groupId)
                }
                verify(exactly = 1) {
                    penaltyVerifyRepository.deletePenaltyVerifiesByGroupId(groupId)
                }
                verify(exactly = 1) {
                    habitRepository.deleteHabitsByGroupId(groupId)
                }
                verify(exactly = 1) {
                    groupMemberRepository.deleteGroupMembersByGroupId(groupId)
                }
                verify(exactly = 1) { entityManager.flush() }
                verify(exactly = 1) { entityManager.clear() }
                verify(exactly = 1) { groupRepository.delete(group) }
            }
        }

        @Nested
        @DisplayName("Failure")
        inner class Failure {

            @Test
            @DisplayName("방장이 아닌 멤버는 다른 멤버를 추방할 수 없다")
            fun kickMember_NotOwner() {
                val group = createActiveGroup()
                val normalMember = newGroupMember(
                    group, GroupMemberRole.MEMBER
                )

                every {
                    groupMemberRepository.findByGroupIdAndMemberId(
                        groupId, memberId
                    )
                } returns Optional.of(normalMember)

                assertThatThrownBy {
                    groupMemberService.kickMember(groupId, 55L, memberId)
                }
                    .isInstanceOf(ForbiddenException::class.java)
                    .hasMessageContaining("그룹 멤버 추방은 방장만 가능합니다.")
            }

            @Test
            @DisplayName("방장은 자기 자신을 추방할 수 없다")
            fun kickMember_KickSelf() {
                val group = createActiveGroup()
                val owner = newGroupMember(group, GroupMemberRole.OWNER)
                ReflectionTestUtils.setField(owner, "id", groupMemberId)

                every {
                    groupMemberRepository.findByGroupIdAndMemberId(
                        groupId, memberId
                    )
                } returns Optional.of(owner)

                every {
                    groupMemberRepository.findById(groupMemberId)
                } returns Optional.of(owner)

                assertThatThrownBy {
                    groupMemberService.kickMember(
                        groupId, groupMemberId, memberId
                    )
                }
                    .isInstanceOf(BusinessRuleException::class.java)
                    .hasMessageContaining("방장 스스로를 추방할 수 없습니다.")
            }

            @Test
            @DisplayName("추방 대상 멤버가 존재하지 않으면 예외가 발생한다")
            fun kickMember_TargetNotFound() {
                val group = createActiveGroup()
                val owner = newGroupMember(group, GroupMemberRole.OWNER)

                every {
                    groupMemberRepository.findByGroupIdAndMemberId(
                        groupId, memberId
                    )
                } returns Optional.of(owner)

                every {
                    groupMemberRepository.findById(groupMemberId)
                } returns Optional.empty()

                assertThatThrownBy {
                    groupMemberService.kickMember(
                        groupId, groupMemberId, memberId
                    )
                }
                    .isInstanceOf(EntityNotFoundException::class.java)
                    .hasMessageContaining(
                        "존재하지 않거나 해당 그룹의 멤버가 아닙니다."
                    )
            }

            @Test
            @DisplayName("다른 그룹의 멤버는 추방할 수 없다")
            fun kickMember_DifferentGroup() {
                val group = createActiveGroup()
                val otherGroup = Group(
                    deadline = LocalDate.now().plusDays(7)
                )
                ReflectionTestUtils.setField(otherGroup, "id", 200L)

                val owner = newGroupMember(group, GroupMemberRole.OWNER)
                ReflectionTestUtils.setField(owner, "id", groupMemberId)

                val target = newGroupMember(
                    otherGroup, GroupMemberRole.MEMBER
                )
                ReflectionTestUtils.setField(target, "id", 60L)

                every {
                    groupMemberRepository.findByGroupIdAndMemberId(
                        groupId, memberId
                    )
                } returns Optional.of(owner)

                every {
                    groupMemberRepository.findById(60L)
                } returns Optional.of(target)

                assertThatThrownBy {
                    groupMemberService.kickMember(groupId, 60L, memberId)
                }
                    .isInstanceOf(BusinessRuleException::class.java)
                    .hasMessageContaining("해당 그룹에 속한 멤버가 아닙니다.")
            }

            @Test
            @DisplayName("종료된 그룹에서는 멤버를 추방할 수 없다")
            fun kickMember_FinishedGroup() {
                val group = mockk<Group>()
                every { group.id } returns groupId
                every { group.isFinished() } returns true

                val owner = newGroupMember(group, GroupMemberRole.OWNER)
                ReflectionTestUtils.setField(owner, "id", groupMemberId)

                val target = newGroupMember(group, GroupMemberRole.MEMBER)
                ReflectionTestUtils.setField(target, "id", 60L)

                every {
                    groupMemberRepository.findByGroupIdAndMemberId(
                        groupId, memberId
                    )
                } returns Optional.of(owner)

                every {
                    groupMemberRepository.findById(60L)
                } returns Optional.of(target)

                assertThatThrownBy {
                    groupMemberService.kickMember(groupId, 60L, memberId)
                }
                    .isInstanceOf(BusinessRuleException::class.java)
                    .hasMessageContaining(
                        "이미 종료된 그룹은 멤버를 추방할 수 없습니다."
                    )
            }

            @Test
            fun kickMember_LeftMember() {
                val group = createActiveGroup()
                val owner = mockk<GroupMember>()
                val kickTarget = mockk<GroupMember>()

                every {
                    groupMemberRepository.findByGroupIdAndMemberId(
                        groupId, memberId
                    )
                } returns Optional.of(owner)

                every { owner.role } returns GroupMemberRole.OWNER
                // 서비스에서 요청자 ID를 비교하므로 반드시 설정한다.
                every { owner.id } returns memberId

                every {
                    groupMemberRepository.findById(groupMemberId)
                } returns Optional.of(kickTarget)

                every { kickTarget.group } returns group
                every { kickTarget.id } returns groupMemberId
                every { kickTarget.status } returns GroupMemberStatus.LEFT

                assertThatThrownBy {
                    groupMemberService.kickMember(
                        groupId, groupMemberId, memberId
                    )
                }
                    .isInstanceOf(BusinessRuleException::class.java)
                    .hasMessage(
                        "현재 활동 중인 멤버만 추방할 수 있습니다."
                    )

                verify(exactly = 0) {
                    groupMemberRepository.delete(kickTarget)
                }
            }
        }
    }

    // 방장 권한 위임
    @Nested
    @DisplayName("TransferOwner")
    inner class TransferOwner {

        @Nested
        @DisplayName("Success")
        inner class Success {

            @Test
            @DisplayName("같은 그룹의 다른 멤버에게 방장 권한을 위임한다")
            fun transferOwner() {
                val group = createActiveGroup()
                val currentOwner = newGroupMember(
                    group, GroupMemberRole.OWNER
                )
                ReflectionTestUtils.setField(
                    currentOwner, "id", groupMemberId
                )

                val nextOwnerId = 60L
                val nextOwner = newGroupMember(
                    group, GroupMemberRole.MEMBER
                )
                ReflectionTestUtils.setField(nextOwner, "id", nextOwnerId)

                every {
                    groupMemberRepository.findByGroupIdAndMemberId(
                        groupId, memberId
                    )
                } returns Optional.of(currentOwner)

                every {
                    groupMemberRepository.findById(nextOwnerId)
                } returns Optional.of(nextOwner)

                groupMemberService.transferOwner(
                    groupId, nextOwnerId, memberId
                )

                assertThat(currentOwner.role).isEqualTo(
                    GroupMemberRole.MEMBER
                )
                assertThat(nextOwner.role).isEqualTo(GroupMemberRole.OWNER)
            }
        }

        @Nested
        @DisplayName("Failure")
        inner class Failure {

            @Test
            @DisplayName("방장은 자기 자신에게 권한을 위임할 수 없다")
            fun transferOwner_ToSelf() {
                val group = createActiveGroup()
                val owner = newGroupMember(group, GroupMemberRole.OWNER)
                ReflectionTestUtils.setField(owner, "id", groupMemberId)

                every {
                    groupMemberRepository.findByGroupIdAndMemberId(
                        groupId, memberId
                    )
                } returns Optional.of(owner)

                every {
                    groupMemberRepository.findById(groupMemberId)
                } returns Optional.of(owner)

                assertThatThrownBy {
                    groupMemberService.transferOwner(
                        groupId, groupMemberId, memberId
                    )
                }
                    .isInstanceOf(BusinessRuleException::class.java)
                    .hasMessageContaining(
                        "자기 자신에게 방장 권한을 위임할 수 없습니다."
                    )
            }

            @Test
            @DisplayName("방장이 아닌 멤버는 방장 권한을 위임할 수 없다")
            fun transferOwner_NotOwner() {
                val group = createActiveGroup()
                val member = newGroupMember(group, GroupMemberRole.MEMBER)

                every {
                    groupMemberRepository.findByGroupIdAndMemberId(
                        groupId, memberId
                    )
                } returns Optional.of(member)

                assertThatThrownBy {
                    groupMemberService.transferOwner(
                        groupId, 60L, memberId
                    )
                }
                    .isInstanceOf(ForbiddenException::class.java)
                    .hasMessageContaining(
                        "방장 권한 위임은 현재 방장만 가능합니다."
                    )
            }

            @Test
            @DisplayName("위임 대상 멤버가 존재하지 않으면 예외가 발생한다")
            fun transferOwner_TargetNotFound() {
                val group = createActiveGroup()
                val owner = newGroupMember(group, GroupMemberRole.OWNER)

                every {
                    groupMemberRepository.findByGroupIdAndMemberId(
                        groupId, memberId
                    )
                } returns Optional.of(owner)

                every {
                    groupMemberRepository.findById(60L)
                } returns Optional.empty()

                assertThatThrownBy {
                    groupMemberService.transferOwner(
                        groupId, 60L, memberId
                    )
                }
                    .isInstanceOf(EntityNotFoundException::class.java)
                    .hasMessageContaining(
                        "권한을 위임할 대상 멤버가 존재하지 않습니다."
                    )
            }

            @Test
            @DisplayName("다른 그룹의 멤버에게는 방장 권한을 위임할 수 없다")
            fun transferOwner_DifferentGroup() {
                val group = createActiveGroup()
                val otherGroup = Group(
                    deadline = LocalDate.now().plusDays(7)
                )
                ReflectionTestUtils.setField(otherGroup, "id", 200L)

                val owner = newGroupMember(group, GroupMemberRole.OWNER)
                ReflectionTestUtils.setField(owner, "id", groupMemberId)

                val target = newGroupMember(
                    otherGroup, GroupMemberRole.MEMBER
                )
                ReflectionTestUtils.setField(target, "id", 60L)

                every {
                    groupMemberRepository.findByGroupIdAndMemberId(
                        groupId, memberId
                    )
                } returns Optional.of(owner)

                every {
                    groupMemberRepository.findById(60L)
                } returns Optional.of(target)

                assertThatThrownBy {
                    groupMemberService.transferOwner(
                        groupId, 60L, memberId
                    )
                }
                    .isInstanceOf(BusinessRuleException::class.java)
                    .hasMessageContaining("해당 그룹에 속한 멤버가 아닙니다.")
            }

            @Test
            @DisplayName("종료된 그룹에서는 방장 권한을 위임할 수 없다")
            fun transferOwner_FinishedGroup() {
                val group = mockk<Group>()
                every { group.id } returns groupId
                every { group.isFinished() } returns true

                val owner = newGroupMember(group, GroupMemberRole.OWNER)
                ReflectionTestUtils.setField(owner, "id", groupMemberId)

                val target = newGroupMember(group, GroupMemberRole.MEMBER)
                ReflectionTestUtils.setField(target, "id", 60L)

                every {
                    groupMemberRepository.findByGroupIdAndMemberId(
                        groupId, memberId
                    )
                } returns Optional.of(owner)

                every {
                    groupMemberRepository.findById(60L)
                } returns Optional.of(target)

                assertThatThrownBy {
                    groupMemberService.transferOwner(
                        groupId, 60L, memberId
                    )
                }
                    .isInstanceOf(BusinessRuleException::class.java)
                    .hasMessageContaining(
                        "이미 종료된 그룹은 방장을 위임할 수 없습니다."
                    )
            }

            @Test
            fun transferOwner_LeftMember() {
                val group = createActiveGroup()
                val owner = mockk<GroupMember>()
                val nextOwner = mockk<GroupMember>()

                every {
                    groupMemberRepository.findByGroupIdAndMemberId(
                        groupId, memberId
                    )
                } returns Optional.of(owner)

                every { owner.role } returns GroupMemberRole.OWNER
                every { owner.group } returns group

                every {
                    groupMemberRepository.findById(groupMemberId)
                } returns Optional.of(nextOwner)

                every { nextOwner.group } returns group
                every { nextOwner.id } returns groupMemberId
                every { owner.id } returns memberId
                every { nextOwner.status } returns GroupMemberStatus.LEFT

                assertThatThrownBy {
                    groupMemberService.transferOwner(
                        groupId, groupMemberId, memberId
                    )
                }
                    .isInstanceOf(BusinessRuleException::class.java)
                    .hasMessage(
                        "현재 활동 중인 멤버에게만 방장 권한을 위임할 수 있습니다."
                    )

                verify(exactly = 0) {
                    nextOwner.changeRole(GroupMemberRole.OWNER)
                }
            }
        }
    }
}
