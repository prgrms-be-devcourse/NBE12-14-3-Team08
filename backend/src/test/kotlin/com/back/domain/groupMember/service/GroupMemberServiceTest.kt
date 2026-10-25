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
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Spy
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willAnswer
import org.mockito.Mockito.any
import org.mockito.Mockito.anyList
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionTemplate

@ExtendWith(MockitoExtension::class)
class GroupMemberServiceTest {

    @InjectMocks
    private lateinit var groupMemberService: GroupMemberService

    @Mock
    private lateinit var groupMemberRepository: GroupMemberRepository

    @Mock
    private lateinit var groupRepository: GroupRepository

    @Mock
    private lateinit var memberRepository: MemberRepository

    @Mock
    private lateinit var habitRepository: HabitRepository

    @Mock
    private lateinit var habitVerifyRepository: HabitVerifyRepository

    @Mock
    private lateinit var penaltyVerifyRepository: PenaltyVerifyRepository

    @Mock
    private lateinit var passwordEncoder: PasswordEncoder

    @Mock
    private lateinit var entityManager: EntityManager

    // 잠금 구간을 실제로 실행하도록, 트랜잭션 관리자만 아무것도 안 하는 가짜인 진짜 TransactionTemplate을 넣는다
    @Spy
    private val transactionTemplate = TransactionTemplate(
        object : PlatformTransactionManager {
            override fun getTransaction(definition: TransactionDefinition?): TransactionStatus =
                SimpleTransactionStatus()

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

                given(groupRepository.findByInviteCode(inviteCode))
                    .willReturn(group)
                given(
                    groupMemberRepository.existsByGroupIdAndMemberId(
                        groupId,
                        memberId
                    )
                ).willReturn(true)

                val result = groupMemberService.joinGroup(
                    inviteCode,
                    memberId,
                    request
                )

                assertThat(result).isEqualTo(groupId)
                verify(groupMemberRepository, never())
                    .save(any(GroupMember::class.java))
            }

            @Test
            @DisplayName("비밀번호와 정원 검증을 통과하면 신규 가입한다")
            fun joinGroup_NewMember() {
                val group = createActiveGroup()
                val member = org.mockito.Mockito.mock(Member::class.java)
                val request = GroupRequest.Join("pass123")

                given(groupRepository.findByInviteCode(inviteCode))
                    .willReturn(group)
                given(
                    groupMemberRepository.existsByGroupIdAndMemberId(
                        groupId,
                        memberId
                    )
                ).willReturn(false)
                given(passwordEncoder.matches("pass123", "encoded_pass"))
                    .willReturn(true)
                given(
                    groupMemberRepository.countByGroupIdAndStatus(
                        groupId,
                        GroupMemberStatus.ACTIVE
                    )
                ).willReturn(3L)
                given(memberRepository.findById(memberId))
                    .willReturn(Optional.of(member))

                val result = groupMemberService.joinGroup(
                    inviteCode,
                    memberId,
                    request
                )

                assertThat(result).isEqualTo(groupId)
                verify(groupMemberRepository, times(1))
                    .save(any(GroupMember::class.java))
            }

            @Test
            @DisplayName("같은 회원의 요청이 먼저 잠금을 잡고 참여했으면 잠금 안에서 기존 그룹 ID를 반환한다")
            fun joinGroup_JoinedWhileWaitingForLock() {
                val group = createActiveGroup()
                val request = GroupRequest.Join("pass123")

                given(groupRepository.findByInviteCode(inviteCode))
                    .willReturn(group)
                given(
                    groupMemberRepository.existsByGroupIdAndMemberId(
                        groupId,
                        memberId
                    )
                ).willReturn(false, true)
                given(passwordEncoder.matches("pass123", "encoded_pass"))
                    .willReturn(true)

                val result = groupMemberService.joinGroup(
                    inviteCode,
                    memberId,
                    request
                )

                assertThat(result).isEqualTo(groupId)
                verify(groupMemberRepository, never())
                    .save(any(GroupMember::class.java))
            }
        }

        @Nested
        @DisplayName("Failure")
        inner class Failure {

            @Test
            @DisplayName("유효하지 않은 초대 코드이면 예외가 발생한다")
            fun joinGroup_InvalidInviteCode() {
                val request = GroupRequest.Join("pass123")

                given(groupRepository.findByInviteCode(inviteCode))
                    .willReturn(null)

                assertThatThrownBy {
                    groupMemberService.joinGroup(inviteCode, memberId, request)
                }
                    .isInstanceOf(EntityNotFoundException::class.java)
                    .hasMessageContaining("유효하지 않거나 존재하지 않는 초대 코드입니다.")
            }

            @Test
            @DisplayName("그룹 비밀번호가 일치하지 않으면 예외가 발생한다")
            fun joinGroup_WrongPassword() {
                val group = createActiveGroup()
                val request = GroupRequest.Join("wrong_pass")

                given(groupRepository.findByInviteCode(inviteCode))
                    .willReturn(group)
                given(
                    groupMemberRepository.existsByGroupIdAndMemberId(
                        groupId,
                        memberId
                    )
                ).willReturn(false)
                given(passwordEncoder.matches("wrong_pass", "encoded_pass"))
                    .willReturn(false)

                assertThatThrownBy {
                    groupMemberService.joinGroup(inviteCode, memberId, request)
                }
                    .isInstanceOf(ForbiddenException::class.java)
                    .hasMessageContaining("그룹 비밀번호가 일치하지 않습니다.")
            }

            @Test
            @DisplayName("이미 종료된 그룹이면 가입할 수 없다")
            fun joinGroup_AlreadyFinished() {
                val group = org.mockito.Mockito.mock(Group::class.java)
                val request = GroupRequest.Join("pass123")

                given(group.isFinished()).willReturn(true)
                given(groupRepository.findByInviteCode(inviteCode))
                    .willReturn(group)

                assertThatThrownBy {
                    groupMemberService.joinGroup(inviteCode, memberId, request)
                }
                    .isInstanceOf(BusinessRuleException::class.java)
                    .hasMessageContaining("이미 마감된 그룹입니다.")
            }

            @Test
            @DisplayName("그룹 정원이 가득 차면 가입할 수 없다")
            fun joinGroup_LimitExceeded() {
                val group = createActiveGroup()
                val request = GroupRequest.Join("pass123")

                given(groupRepository.findByInviteCode(inviteCode))
                    .willReturn(group)
                given(
                    groupMemberRepository.existsByGroupIdAndMemberId(
                        groupId,
                        memberId
                    )
                ).willReturn(false)
                given(passwordEncoder.matches("pass123", "encoded_pass"))
                    .willReturn(true)
                given(
                    groupMemberRepository.countByGroupIdAndStatus(
                        groupId,
                        GroupMemberStatus.ACTIVE
                    )
                ).willReturn(5L)

                assertThatThrownBy {
                    groupMemberService.joinGroup(inviteCode, memberId, request)
                }
                    .isInstanceOf(GroupLimitExceededException::class.java)
                    .hasMessageContaining(
                        "그룹의 최대 인원이 초과되어 입장할 수 없습니다."
                    )
            }

            @Test
            @DisplayName("그룹 비밀번호가 틀리면 그룹 잠금을 잡지 않는다")
            fun joinGroup_WrongPassword_DoesNotLock() {
                val group = createActiveGroup()
                val request = GroupRequest.Join("wrong_pass")

                given(groupRepository.findByInviteCode(inviteCode))
                    .willReturn(group)
                given(
                    groupMemberRepository.existsByGroupIdAndMemberId(
                        groupId,
                        memberId
                    )
                ).willReturn(false)
                given(passwordEncoder.matches("wrong_pass", "encoded_pass"))
                    .willReturn(false)

                assertThatThrownBy {
                    groupMemberService.joinGroup(inviteCode, memberId, request)
                }
                    .isInstanceOf(ForbiddenException::class.java)

                verify(entityManager, never())
                    .refresh(group, LockModeType.PESSIMISTIC_WRITE)
            }

            @Test
            @DisplayName("비밀번호 확인 뒤 잠금을 잡기 전에 그룹 비밀번호가 바뀌면 바뀐 비밀번호로 다시 확인한다")
            fun joinGroup_PasswordChangedBeforeLock() {
                val group = createActiveGroup()
                val request = GroupRequest.Join("pass123")

                given(groupRepository.findByInviteCode(inviteCode))
                    .willReturn(group)
                // 잠그며 다시 읽을 때(refresh) 다른 요청이 바꾼 비밀번호가 반영된 상황
                willAnswer { group.password = "changed_encoded_pass" }
                    .given(entityManager).refresh(group, LockModeType.PESSIMISTIC_WRITE)
                given(
                    groupMemberRepository.existsByGroupIdAndMemberId(
                        groupId,
                        memberId
                    )
                ).willReturn(false)
                given(passwordEncoder.matches("pass123", "encoded_pass"))
                    .willReturn(true)
                given(passwordEncoder.matches("pass123", "changed_encoded_pass"))
                    .willReturn(false)

                assertThatThrownBy {
                    groupMemberService.joinGroup(inviteCode, memberId, request)
                }
                    .isInstanceOf(ForbiddenException::class.java)
                    .hasMessageContaining("그룹 비밀번호가 일치하지 않습니다.")
                verify(groupMemberRepository, never())
                    .save(any(GroupMember::class.java))
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
                given(
                    groupMemberRepository.existsByGroupIdAndMemberIdAndStatus(
                        groupId,
                        memberId,
                        GroupMemberStatus.ACTIVE
                    )
                ).willReturn(true)
                given(groupMemberRepository.findByGroupIdWithHabit(groupId))
                    .willReturn(emptyList())

                val result: List<GroupMemberResponse.Simple> =
                    groupMemberService.getGroupMembers(groupId, memberId)

                assertThat(result).isNotNull
                assertThat(result).isEmpty()
                verify(groupMemberRepository).findByGroupIdWithHabit(groupId)
            }
        }

        @Nested
        @DisplayName("Failure")
        inner class Failure {

            @Test
            @DisplayName("그룹의 활성 멤버가 아니면 목록을 조회할 수 없다")
            fun getGroupMembers_NoAccess() {
                given(
                    groupMemberRepository.existsByGroupIdAndMemberIdAndStatus(
                        groupId,
                        memberId,
                        GroupMemberStatus.ACTIVE
                    )
                ).willReturn(false)

                assertThatThrownBy {
                    groupMemberService.getGroupMembers(groupId, memberId)
                }
                    .isInstanceOf(ForbiddenException::class.java)
                    .hasMessageContaining("해당 그룹의 접근 권한이 없습니다.")

                verify(groupMemberRepository, never())
                    .findByGroupIdWithHabit(groupId)
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

                given(
                    groupMemberRepository.existsByGroupIdAndMemberIdAndStatus(
                        groupId,
                        memberId,
                        GroupMemberStatus.ACTIVE
                    )
                ).willReturn(true)
                given(
                    groupMemberRepository.findMemberDetailWithPenaltyCount(
                        groupId,
                        groupMemberId
                    )
                ).willReturn(Optional.of(detail))

                val result = groupMemberService.getGroupMemberDetail(
                    groupId,
                    groupMemberId,
                    memberId
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
                given(
                    groupMemberRepository.existsByGroupIdAndMemberIdAndStatus(
                        groupId,
                        memberId,
                        GroupMemberStatus.ACTIVE
                    )
                ).willReturn(false)

                assertThatThrownBy {
                    groupMemberService.getGroupMemberDetail(
                        groupId,
                        groupMemberId,
                        memberId
                    )
                }
                    .isInstanceOf(ForbiddenException::class.java)
                    .hasMessageContaining("해당 그룹의 접근 권한이 없습니다.")

                verify(groupMemberRepository, never())
                    .findMemberDetailWithPenaltyCount(groupId, groupMemberId)
            }

            @Test
            @DisplayName("상세 조회 대상이 없으면 예외가 발생한다")
            fun getGroupMemberDetail_NotFound() {
                given(
                    groupMemberRepository.existsByGroupIdAndMemberIdAndStatus(
                        groupId,
                        memberId,
                        GroupMemberStatus.ACTIVE
                    )
                ).willReturn(true)
                given(
                    groupMemberRepository.findMemberDetailWithPenaltyCount(
                        groupId,
                        groupMemberId
                    )
                ).willReturn(Optional.empty())

                assertThatThrownBy {
                    groupMemberService.getGroupMemberDetail(
                        groupId,
                        groupMemberId,
                        memberId
                    )
                }
                    .isInstanceOf(EntityNotFoundException::class.java)
                    .hasMessageContaining(
                        "존재하지 않거나 해당 그룹의 가입 멤버가 아닙니다."
                    )
            }
        }
    }    // 그룹 탈퇴
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
                ReflectionTestUtils.setField(groupMember, "id", groupMemberId)

                given(groupRepository.findByIdForUpdate(groupId))
                    .willReturn(group)
                given(groupMemberRepository.findByGroupIdAndMemberId(groupId, memberId))
                    .willReturn(Optional.of(groupMember))
                given(
                    groupMemberRepository.countByGroupIdAndStatus(
                        groupId,
                        GroupMemberStatus.ACTIVE
                    )
                ).willReturn(2L)
                given(habitRepository.findIdsByGroupMemberId(groupMemberId))
                    .willReturn(listOf(10L, 11L))

                groupMemberService.leaveGroup(groupId, memberId)

                verify(habitVerifyRepository).deleteByHabitIds(anyList())
                verify(penaltyVerifyRepository).deleteByGroupMemberId(groupMemberId)
                verify(habitRepository).deleteByIds(anyList())
                verify(groupMemberRepository).delete(groupMember)
            }

            @Test
            @DisplayName("종료된 그룹에서 탈퇴하면 멤버 상태를 LEFT로 변경한다")
            fun leaveGroup_FinishedGroup() {
                val group = org.mockito.Mockito.mock(Group::class.java)
                val groupMember = newGroupMember(group, GroupMemberRole.MEMBER)

                given(group.isFinished()).willReturn(true)
                given(groupRepository.findByIdForUpdate(groupId)).willReturn(group)
                given(groupMemberRepository.findByGroupIdAndMemberId(groupId, memberId))
                    .willReturn(Optional.of(groupMember))
                given(
                    groupMemberRepository.countByGroupIdAndStatus(
                        groupId,
                        GroupMemberStatus.ACTIVE
                    )
                ).willReturn(2L)

                groupMemberService.leaveGroup(groupId, memberId)

                assertThat(groupMember.status).isEqualTo(GroupMemberStatus.LEFT)
                verify(groupMemberRepository, never())
                    .delete(any(GroupMember::class.java))
                verify(habitRepository, never())
                    .findIdsByGroupMemberId(groupMember.id)
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

                given(groupRepository.findByIdForUpdate(groupId)).willReturn(group)
                given(groupMemberRepository.findByGroupIdAndMemberId(groupId, memberId))
                    .willReturn(Optional.of(owner))
                given(
                    groupMemberRepository.countByGroupIdAndStatus(
                        groupId,
                        GroupMemberStatus.ACTIVE
                    )
                ).willReturn(2L)

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
                given(groupRepository.findByIdForUpdate(groupId)).willReturn(null)

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

                given(groupRepository.findByIdForUpdate(groupId)).willReturn(group)
                given(groupMemberRepository.findByGroupIdAndMemberId(groupId, memberId))
                    .willReturn(Optional.empty())

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
                val groupMember = newGroupMember(group, GroupMemberRole.MEMBER)
                groupMember.leave()

                given(groupRepository.findByIdForUpdate(groupId)).willReturn(group)
                given(groupMemberRepository.findByGroupIdAndMemberId(groupId, memberId))
                    .willReturn(Optional.of(groupMember))

                assertThatThrownBy {
                    groupMemberService.leaveGroup(groupId, memberId)
                }
                    .isInstanceOf(BusinessRuleException::class.java)
                    .hasMessageContaining("이미 탈퇴한 멤버입니다.")
            }
        }
    }

    @Nested
    @DisplayName("DeleteGroupMemberDataBulk")
    inner class DeleteGroupMemberDataBulk {

        @Nested
        @DisplayName("Success")
        inner class Success {

            @Test
            @DisplayName("습관 데이터가 없어도 벌칙 인증 데이터는 삭제한다")
            fun deleteGroupMemberDataBulk_NoHabits() {
                given(habitRepository.findIdsByGroupMemberId(groupMemberId))
                    .willReturn(emptyList())

                groupMemberService.deleteGroupMemberDataBulk(groupMemberId)

                verify(habitRepository).findIdsByGroupMemberId(groupMemberId)
                verify(penaltyVerifyRepository).deleteByGroupMemberId(groupMemberId)
                verify(habitVerifyRepository, never()).deleteByHabitIds(anyList())
                verify(habitRepository, never()).deleteByIds(anyList())
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

                given(groupMemberRepository.findByGroupIdAndMemberId(groupId, memberId))
                    .willReturn(Optional.of(owner))
                given(groupMemberRepository.findById(targetId))
                    .willReturn(Optional.of(target))
                given(habitRepository.findIdsByGroupMemberId(targetId))
                    .willReturn(emptyList())

                groupMemberService.kickMember(groupId, targetId, memberId)

                verify(penaltyVerifyRepository).deleteByGroupMemberId(targetId)
                verify(groupMemberRepository).delete(target)
            }

            @Test
            @DisplayName("마지막 멤버가 탈퇴하면 연관 데이터를 삭제하고 그룹도 삭제한다")
            fun leaveGroup_LastMember() {
                val group = createActiveGroup()
                val groupMember = newGroupMember(group, GroupMemberRole.OWNER)

                given(groupRepository.findByIdForUpdate(groupId)).willReturn(group)
                given(groupMemberRepository.findByGroupIdAndMemberId(groupId, memberId))
                    .willReturn(Optional.of(groupMember))
                given(
                    groupMemberRepository.countByGroupIdAndStatus(
                        groupId,
                        GroupMemberStatus.ACTIVE
                    )
                ).willReturn(1L)
                given(groupRepository.findById(groupId)).willReturn(Optional.of(group))

                groupMemberService.leaveGroup(groupId, memberId)

                verify(habitVerifyRepository).deleteHabitVerifiesByGroupId(groupId)
                verify(penaltyVerifyRepository).deletePenaltyVerifiesByGroupId(groupId)
                verify(habitRepository).deleteHabitsByGroupId(groupId)
                verify(groupMemberRepository).deleteGroupMembersByGroupId(groupId)
                verify(entityManager).flush()
                verify(entityManager).clear()
                verify(groupRepository).delete(group)
            }
        }

        @Nested
        @DisplayName("Failure")
        inner class Failure {

            @Test
            @DisplayName("방장이 아닌 멤버는 다른 멤버를 추방할 수 없다")
            fun kickMember_NotOwner() {
                val group = createActiveGroup()
                val normalMember = newGroupMember(group, GroupMemberRole.MEMBER)

                given(groupMemberRepository.findByGroupIdAndMemberId(groupId, memberId))
                    .willReturn(Optional.of(normalMember))

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

                given(groupMemberRepository.findByGroupIdAndMemberId(groupId, memberId))
                    .willReturn(Optional.of(owner))
                given(groupMemberRepository.findById(groupMemberId))
                    .willReturn(Optional.of(owner))

                assertThatThrownBy {
                    groupMemberService.kickMember(groupId, groupMemberId, memberId)
                }
                    .isInstanceOf(BusinessRuleException::class.java)
                    .hasMessageContaining("방장 스스로를 추방할 수 없습니다.")
            }

            @Test
            @DisplayName("추방 대상 멤버가 존재하지 않으면 예외가 발생한다")
            fun kickMember_TargetNotFound() {
                val group = createActiveGroup()
                val owner = newGroupMember(group, GroupMemberRole.OWNER)

                given(groupMemberRepository.findByGroupIdAndMemberId(groupId, memberId))
                    .willReturn(Optional.of(owner))
                given(groupMemberRepository.findById(groupMemberId))
                    .willReturn(Optional.empty())

                assertThatThrownBy {
                    groupMemberService.kickMember(groupId, groupMemberId, memberId)
                }
                    .isInstanceOf(EntityNotFoundException::class.java)
                    .hasMessageContaining("존재하지 않거나 해당 그룹의 멤버가 아닙니다.")
            }

            @Test
            @DisplayName("다른 그룹의 멤버는 추방할 수 없다")
            fun kickMember_DifferentGroup() {
                val group = createActiveGroup()
                val otherGroup = Group(deadline = LocalDate.now().plusDays(7))
                ReflectionTestUtils.setField(otherGroup, "id", 200L)

                val owner = newGroupMember(group, GroupMemberRole.OWNER)
                ReflectionTestUtils.setField(owner, "id", groupMemberId)

                val target = newGroupMember(otherGroup, GroupMemberRole.MEMBER)
                ReflectionTestUtils.setField(target, "id", 60L)

                given(groupMemberRepository.findByGroupIdAndMemberId(groupId, memberId))
                    .willReturn(Optional.of(owner))
                given(groupMemberRepository.findById(60L))
                    .willReturn(Optional.of(target))

                assertThatThrownBy {
                    groupMemberService.kickMember(groupId, 60L, memberId)
                }
                    .isInstanceOf(BusinessRuleException::class.java)
                    .hasMessageContaining("해당 그룹에 속한 멤버가 아닙니다.")
            }

            @Test
            @DisplayName("종료된 그룹에서는 멤버를 추방할 수 없다")
            fun kickMember_FinishedGroup() {
                val group = org.mockito.Mockito.mock(Group::class.java)
                given(group.id).willReturn(groupId)
                given(group.isFinished()).willReturn(true)

                val owner = newGroupMember(group, GroupMemberRole.OWNER)
                ReflectionTestUtils.setField(owner, "id", groupMemberId)

                val target = newGroupMember(group, GroupMemberRole.MEMBER)
                ReflectionTestUtils.setField(target, "id", 60L)

                given(groupMemberRepository.findByGroupIdAndMemberId(groupId, memberId))
                    .willReturn(Optional.of(owner))
                given(groupMemberRepository.findById(60L))
                    .willReturn(Optional.of(target))

                assertThatThrownBy {
                    groupMemberService.kickMember(groupId, 60L, memberId)
                }
                    .isInstanceOf(BusinessRuleException::class.java)
                    .hasMessageContaining("이미 종료된 그룹은 멤버를 추방할 수 없습니다.")
            }

            @Test
            fun kickMember_LeftMember() {
                val group = createActiveGroup()
                val owner = org.mockito.Mockito.mock(GroupMember::class.java)
                val kickTarget = org.mockito.Mockito.mock(GroupMember::class.java)

                given(groupMemberRepository.findByGroupIdAndMemberId(groupId, memberId))
                    .willReturn(Optional.of(owner))
                given(owner.role).willReturn(GroupMemberRole.OWNER)

                given(groupMemberRepository.findById(groupMemberId))
                    .willReturn(Optional.of(kickTarget))
                given(kickTarget.group).willReturn(group)
                given(kickTarget.id).willReturn(groupMemberId)
                given(kickTarget.status).willReturn(GroupMemberStatus.LEFT)

                assertThatThrownBy {
                    groupMemberService.kickMember(groupId, groupMemberId, memberId)
                }
                    .isInstanceOf(BusinessRuleException::class.java)
                    .hasMessage("현재 활동 중인 멤버만 추방할 수 있습니다.")

                verify(groupMemberRepository, never()).delete(kickTarget)
            }
        }
    }    // 방장 권한 위임
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
                val currentOwner = newGroupMember(group, GroupMemberRole.OWNER)
                ReflectionTestUtils.setField(currentOwner, "id", groupMemberId)

                val nextOwnerId = 60L
                val nextOwner = newGroupMember(group, GroupMemberRole.MEMBER)
                ReflectionTestUtils.setField(nextOwner, "id", nextOwnerId)

                given(groupMemberRepository.findByGroupIdAndMemberId(groupId, memberId))
                    .willReturn(Optional.of(currentOwner))
                given(groupMemberRepository.findById(nextOwnerId))
                    .willReturn(Optional.of(nextOwner))

                groupMemberService.transferOwner(groupId, nextOwnerId, memberId)

                assertThat(currentOwner.role).isEqualTo(GroupMemberRole.MEMBER)
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

                given(groupMemberRepository.findByGroupIdAndMemberId(groupId, memberId))
                    .willReturn(Optional.of(owner))
                given(groupMemberRepository.findById(groupMemberId))
                    .willReturn(Optional.of(owner))

                assertThatThrownBy {
                    groupMemberService.transferOwner(groupId, groupMemberId, memberId)
                }
                    .isInstanceOf(BusinessRuleException::class.java)
                    .hasMessageContaining("자기 자신에게 방장 권한을 위임할 수 없습니다.")
            }

            @Test
            @DisplayName("방장이 아닌 멤버는 방장 권한을 위임할 수 없다")
            fun transferOwner_NotOwner() {
                val group = createActiveGroup()
                val member = newGroupMember(group, GroupMemberRole.MEMBER)

                given(groupMemberRepository.findByGroupIdAndMemberId(groupId, memberId))
                    .willReturn(Optional.of(member))

                assertThatThrownBy {
                    groupMemberService.transferOwner(groupId, 60L, memberId)
                }
                    .isInstanceOf(ForbiddenException::class.java)
                    .hasMessageContaining("방장 권한 위임은 현재 방장만 가능합니다.")
            }

            @Test
            @DisplayName("위임 대상 멤버가 존재하지 않으면 예외가 발생한다")
            fun transferOwner_TargetNotFound() {
                val group = createActiveGroup()
                val owner = newGroupMember(group, GroupMemberRole.OWNER)

                given(groupMemberRepository.findByGroupIdAndMemberId(groupId, memberId))
                    .willReturn(Optional.of(owner))
                given(groupMemberRepository.findById(60L))
                    .willReturn(Optional.empty())

                assertThatThrownBy {
                    groupMemberService.transferOwner(groupId, 60L, memberId)
                }
                    .isInstanceOf(EntityNotFoundException::class.java)
                    .hasMessageContaining("권한을 위임할 대상 멤버가 존재하지 않습니다.")
            }

            @Test
            @DisplayName("다른 그룹의 멤버에게는 방장 권한을 위임할 수 없다")
            fun transferOwner_DifferentGroup() {
                val group = createActiveGroup()
                val otherGroup = Group(deadline = LocalDate.now().plusDays(7))
                ReflectionTestUtils.setField(otherGroup, "id", 200L)

                val owner = newGroupMember(group, GroupMemberRole.OWNER)
                ReflectionTestUtils.setField(owner, "id", groupMemberId)

                val target = newGroupMember(otherGroup, GroupMemberRole.MEMBER)
                ReflectionTestUtils.setField(target, "id", 60L)

                given(groupMemberRepository.findByGroupIdAndMemberId(groupId, memberId))
                    .willReturn(Optional.of(owner))
                given(groupMemberRepository.findById(60L))
                    .willReturn(Optional.of(target))

                assertThatThrownBy {
                    groupMemberService.transferOwner(groupId, 60L, memberId)
                }
                    .isInstanceOf(BusinessRuleException::class.java)
                    .hasMessageContaining("해당 그룹에 속한 멤버가 아닙니다.")
            }

            @Test
            @DisplayName("종료된 그룹에서는 방장 권한을 위임할 수 없다")
            fun transferOwner_FinishedGroup() {
                val group = org.mockito.Mockito.mock(Group::class.java)
                given(group.id).willReturn(groupId)
                given(group.isFinished()).willReturn(true)

                val owner = newGroupMember(group, GroupMemberRole.OWNER)
                ReflectionTestUtils.setField(owner, "id", groupMemberId)

                val target = newGroupMember(group, GroupMemberRole.MEMBER)
                ReflectionTestUtils.setField(target, "id", 60L)

                given(groupMemberRepository.findByGroupIdAndMemberId(groupId, memberId))
                    .willReturn(Optional.of(owner))
                given(groupMemberRepository.findById(60L))
                    .willReturn(Optional.of(target))

                assertThatThrownBy {
                    groupMemberService.transferOwner(groupId, 60L, memberId)
                }
                    .isInstanceOf(BusinessRuleException::class.java)
                    .hasMessageContaining("이미 종료된 그룹은 방장을 위임할 수 없습니다.")
            }

            @Test
            fun transferOwner_LeftMember() {
                val group = createActiveGroup()
                val owner = org.mockito.Mockito.mock(GroupMember::class.java)
                val nextOwner = org.mockito.Mockito.mock(GroupMember::class.java)

                given(groupMemberRepository.findByGroupIdAndMemberId(groupId, memberId))
                    .willReturn(Optional.of(owner))
                given(owner.role).willReturn(GroupMemberRole.OWNER)
                given(owner.group).willReturn(group)

                given(groupMemberRepository.findById(groupMemberId))
                    .willReturn(Optional.of(nextOwner))
                given(nextOwner.group).willReturn(group)
                given(nextOwner.id).willReturn(groupMemberId)
                given(owner.id).willReturn(memberId)
                given(nextOwner.status).willReturn(GroupMemberStatus.LEFT)

                assertThatThrownBy {
                    groupMemberService.transferOwner(groupId, groupMemberId, memberId)
                }
                    .isInstanceOf(BusinessRuleException::class.java)
                    .hasMessage("현재 활동 중인 멤버에게만 방장 권한을 위임할 수 있습니다.")

                verify(nextOwner, never()).changeRole(GroupMemberRole.OWNER)
            }
        }
    }

    private fun newGroupMember(
        group: Group,
        role: GroupMemberRole
    ): GroupMember {
        val member = org.mockito.Mockito.mock(Member::class.java)
        return GroupMember.create(group, member, role)
    }
}