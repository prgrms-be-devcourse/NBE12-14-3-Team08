package com.back.domain.group.service

import com.back.domain.group.dto.GroupRequest
import com.back.domain.group.entity.Group
import com.back.domain.group.entity.GroupStatus
import com.back.domain.group.repository.GroupRepository
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.entity.GroupMemberStatus
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.groupMember.service.GroupMemberService
import com.back.domain.member.entity.Member
import com.back.domain.member.repository.MemberRepository
import com.back.global.exception.BusinessRuleException
import com.back.global.exception.EntityNotFoundException
import com.back.global.exception.ForbiddenException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class GroupServiceTest {

    @InjectMocks
    private lateinit var groupService: GroupService

    @Mock
    private lateinit var groupRepository: GroupRepository

    @Mock
    private lateinit var groupMemberRepository: GroupMemberRepository

    @Mock
    private lateinit var groupMemberService: GroupMemberService

    @Mock
    private lateinit var memberRepository: MemberRepository

    @Mock
    private lateinit var passwordEncoder: PasswordEncoder

    companion object {
        private const val BASE_INVITE_URL = "http://test-invite.com"
        private const val MEMBER_ID = 1L
        private const val GROUP_ID = 100L
    }

    @BeforeEach
    fun setUp() {
        ReflectionTestUtils.setField(
            groupService,
            "baseInviteUrl",
            BASE_INVITE_URL
        )
    }

    private fun createGroup(
        title: String = "테스트 그룹",
        status: GroupStatus = GroupStatus.ACTIVE,
        deadline: LocalDate? = null,
        inviteCode: String = "12345678",
        password: String = "encoded_pass",
        memberLimit: Int = 5
    ): Group {
        val group = Group(
            title = title,
            description = "설명",
            deadline = deadline,
            penalty = "벌칙",
            password = password,
            inviteCode = inviteCode,
            memberLimit = memberLimit,
            status = status
        )

        ReflectionTestUtils.setField(
            group,
            "createDate",
            LocalDateTime.now()
        )

        return group
    }

    private fun createGroupMember(
        group: Group,
        role: GroupMemberRole
    ): GroupMember {
        val member = mock(Member::class.java)
        return GroupMember.create(group, member, role)
    }

    // =========================
    // 그룹 생성
    // =========================

    @Nested
    @DisplayName("그룹 생성")
    inner class CreateGroup {

        @Nested
        @DisplayName("성공")
        inner class Success {

            @Test
            @DisplayName("방장 권한으로 멤버가 정상 등록된다")
            fun createGroupSuccess() {
                // given
                val request = GroupRequest.Create(
                    "테스트 그룹",
                    "설명",
                    LocalDate.now().plusDays(7),
                    "벌칙",
                    "pass123",
                    5
                )

                val mockMember = mock(Member::class.java)
                val mockGroup = createGroup(
                    title = request.title,
                    password = "encoded_pass"
                )

                ReflectionTestUtils.setField(
                    mockGroup,
                    "id",
                    GROUP_ID
                )

                given(groupRepository.existsByInviteCode(anyString()))
                    .willReturn(false)

                given(passwordEncoder.encode(request.password))
                    .willReturn("encoded_pass")

                given(groupRepository.save(any(Group::class.java)))
                    .willReturn(mockGroup)

                given(memberRepository.findById(MEMBER_ID))
                    .willReturn(Optional.of(mockMember))

                // when
                val response = groupService.createGroup(
                    MEMBER_ID,
                    request
                )

                // then
                assertThat(response).isNotNull
                assertThat(response.id).isEqualTo(GROUP_ID)
                assertThat(response.inviteLink)
                    .startsWith(BASE_INVITE_URL)

                verify(groupRepository).save(any(Group::class.java))
                verify(groupMemberRepository)
                    .save(any(GroupMember::class.java))
            }
        }

        @Nested
        @DisplayName("예외")
        inner class Failure {

            @Test
            @DisplayName("존재하지 않는 회원이면 EntityNotFoundException이 발생한다")
            fun createGroupMemberNotFound() {
                // given
                val request = GroupRequest.Create(
                    "제목",
                    "설명",
                    null,
                    "벌칙",
                    "123",
                    5
                )

                val mockGroup = createGroup(
                    title = "제목",
                    inviteCode = "mockcode"
                )

                given(passwordEncoder.encode(anyString()))
                    .willReturn("encoded_password")

                given(groupRepository.save(any(Group::class.java)))
                    .willReturn(mockGroup)

                given(memberRepository.findById(MEMBER_ID))
                    .willReturn(Optional.empty())

                // when & then
                assertThatThrownBy {
                    groupService.createGroup(MEMBER_ID, request)
                }
                    .isInstanceOf(EntityNotFoundException::class.java)
                    .hasMessageContaining("존재하지 않는 회원입니다.")

                verify(groupMemberRepository, never())
                    .save(any(GroupMember::class.java))
            }
        }
    }

    // =========================
    // 그룹 조회
    // =========================

    @Nested
    @DisplayName("그룹 조회")
    inner class GetGroup {

        @Nested
        @DisplayName("성공")
        inner class Success {

            @Test
            @DisplayName("그룹 목록 조회 - 결과가 없으면 빈 목록을 반환한다")
            fun getGroupSimpleListEmpty() {
                // given
                given(
                    groupRepository.findMyGroupsWithCount(
                        MEMBER_ID,
                        GroupStatus.ACTIVE
                    )
                ).willReturn(emptyList())

                // when
                val result = groupService.getGroupSimpleList(
                    MEMBER_ID,
                    GroupStatus.ACTIVE
                )

                // then
                assertThat(result).isEmpty()

                verify(groupRepository).findMyGroupsWithCount(
                    MEMBER_ID,
                    GroupStatus.ACTIVE
                )
            }

            @Test
            @DisplayName("가입한 멤버가 그룹 상세 정보를 정상 조회한다")
            fun getGroupDetailSuccess() {
                // given
                val mockGroup = createGroup(
                    title = "그룹",
                    deadline = LocalDate.now().plusDays(1),
                    inviteCode = "code"
                )

                given(groupRepository.findById(GROUP_ID))
                    .willReturn(Optional.of(mockGroup))

                given(
                    groupMemberRepository
                        .existsByGroupIdAndMemberIdAndStatus(
                            GROUP_ID,
                            MEMBER_ID,
                            GroupMemberStatus.ACTIVE
                        )
                ).willReturn(true)

                // when
                val response = groupService.getGroupDetail(
                    GROUP_ID,
                    MEMBER_ID
                )

                // then
                assertThat(response).isNotNull
                assertThat(mockGroup.status)
                    .isEqualTo(GroupStatus.ACTIVE)

                verify(groupRepository).findById(GROUP_ID)
                verify(groupMemberRepository)
                    .existsByGroupIdAndMemberIdAndStatus(
                        GROUP_ID,
                        MEMBER_ID,
                        GroupMemberStatus.ACTIVE
                    )
            }
        }

        @Nested
        @DisplayName("예외")
        inner class Failure {

            @Test
            @DisplayName("가입하지 않은 멤버는 그룹 상세 정보에 접근할 수 없다")
            fun getGroupDetailForbidden() {
                // given
                given(groupRepository.findById(GROUP_ID))
                    .willReturn(Optional.of(createGroup()))

                given(
                    groupMemberRepository
                        .existsByGroupIdAndMemberIdAndStatus(
                            GROUP_ID,
                            MEMBER_ID,
                            GroupMemberStatus.ACTIVE
                        )
                ).willReturn(false)

                // when & then
                assertThatThrownBy {
                    groupService.getGroupDetail(
                        GROUP_ID,
                        MEMBER_ID
                    )
                }
                    .isInstanceOf(ForbiddenException::class.java)
                    .hasMessageContaining("해당 그룹의 접근 권한이 없습니다.")
            }
        }
    }

    // =========================
    // 그룹 수정
    // =========================

    @Nested
    @DisplayName("그룹 수정")
    inner class UpdateGroup {

        @Nested
        @DisplayName("성공")
        inner class Success {

            @Test
            @DisplayName("방장이 그룹 정보를 변경한다")
            fun updateGroupSuccess() {
                // given
                val mockGroup = createGroup(title = "기존")
                val mockGroupMember = createGroupMember(
                    mockGroup,
                    GroupMemberRole.OWNER
                )

                val request = GroupRequest.Update(
                    "새제목",
                    null,
                    null,
                    null,
                    "newpass",
                    10
                )

                given(groupRepository.findByIdForUpdate(GROUP_ID))
                    .willReturn(mockGroup)

                given(
                    groupMemberRepository.findByGroupIdAndMemberId(
                        GROUP_ID,
                        MEMBER_ID
                    )
                ).willReturn(Optional.of(mockGroupMember))

                given(passwordEncoder.encode("newpass"))
                    .willReturn("encoded_newpass")

                // when
                groupService.updateGroup(
                    GROUP_ID,
                    MEMBER_ID,
                    request
                )

                // then
                assertThat(mockGroup.title).isEqualTo("새제목")

                verify(groupRepository).findByIdForUpdate(GROUP_ID)
                verify(groupMemberRepository)
                    .findByGroupIdAndMemberId(GROUP_ID, MEMBER_ID)
                verify(passwordEncoder).encode("newpass")
            }
        }

        @Nested
        @DisplayName("예외")
        inner class Failure {

            @Test
            @DisplayName("이미 종료된 그룹은 수정할 수 없다")
            fun updateGroupAlreadyFinished() {
                // given
                val mockGroup = createGroup(
                    title = "기존",
                    status = GroupStatus.FINISH
                )

                val request = GroupRequest.Update(
                    "제목",
                    null,
                    null,
                    null,
                    null,
                    5
                )

                given(groupRepository.findByIdForUpdate(GROUP_ID))
                    .willReturn(mockGroup)

                // when & then
                assertThatThrownBy {
                    groupService.updateGroup(
                        GROUP_ID,
                        MEMBER_ID,
                        request
                    )
                }
                    .isInstanceOf(BusinessRuleException::class.java)
                    .hasMessageContaining("이미 종료된 그룹은 수정할 수 없습니다.")
            }

            @Test
            @DisplayName("방장이 아닌 멤버는 그룹을 수정할 수 없다")
            fun updateGroupNotOwner() {
                // given
                val mockGroup = createGroup(title = "기존")
                val mockGroupMember = createGroupMember(
                    mockGroup,
                    GroupMemberRole.MEMBER
                )

                val request = GroupRequest.Update(
                    "제목",
                    null,
                    null,
                    null,
                    null,
                    5
                )

                given(groupRepository.findByIdForUpdate(GROUP_ID))
                    .willReturn(mockGroup)

                given(
                    groupMemberRepository.findByGroupIdAndMemberId(
                        GROUP_ID,
                        MEMBER_ID
                    )
                ).willReturn(Optional.of(mockGroupMember))

                // when & then
                assertThatThrownBy {
                    groupService.updateGroup(
                        GROUP_ID,
                        MEMBER_ID,
                        request
                    )
                }
                    .isInstanceOf(ForbiddenException::class.java)
                    .hasMessageContaining("그룹 수정은 방장만 가능합니다.")
            }
        }
    }

    // =========================
    // 그룹 삭제
    // =========================

    @Nested
    @DisplayName("그룹 삭제")
    inner class DeleteGroup {

        @Nested
        @DisplayName("성공")
        inner class Success {

            @Test
            @DisplayName("방장 혼자 남은 그룹을 삭제한다")
            fun deleteGroupSuccess() {
                // given
                val mockGroup = createGroup()
                val mockGroupMember = createGroupMember(
                    mockGroup,
                    GroupMemberRole.OWNER
                )

                ReflectionTestUtils.setField(
                    mockGroupMember,
                    "id",
                    50L
                )

                given(groupRepository.findByIdForUpdate(GROUP_ID))
                    .willReturn(mockGroup)

                given(
                    groupMemberRepository.findByGroupIdAndMemberId(
                        GROUP_ID,
                        MEMBER_ID
                    )
                ).willReturn(Optional.of(mockGroupMember))

                given(
                    groupMemberRepository.countByGroupIdAndStatus(
                        GROUP_ID,
                        GroupMemberStatus.ACTIVE
                    )
                ).willReturn(1L)

                // when
                groupService.deleteGroup(GROUP_ID, MEMBER_ID)

                // then
                verify(groupMemberService, times(1))
                    .deleteGroupAndAllMembers(mockGroup)
            }
        }

        @Nested
        @DisplayName("예외")
        inner class Failure {

            @Test
            @DisplayName("이미 종료된 그룹은 삭제할 수 없다")
            fun deleteGroupAlreadyFinished() {
                // given
                val mockGroup = createGroup(
                    status = GroupStatus.FINISH
                )

                given(groupRepository.findByIdForUpdate(GROUP_ID))
                    .willReturn(mockGroup)

                // when & then
                assertThatThrownBy {
                    groupService.deleteGroup(GROUP_ID, MEMBER_ID)
                }
                    .isInstanceOf(BusinessRuleException::class.java)
                    .hasMessageContaining("이미 종료된 그룹은 삭제할 수 없습니다.")

                verify(groupMemberService, never())
                    .deleteGroupAndAllMembers(any(Group::class.java))
            }

            @Test
            @DisplayName("방장이 아닌 멤버는 그룹을 삭제할 수 없다")
            fun deleteGroupNotOwner() {
                // given
                val mockGroup = createGroup()
                val mockGroupMember = createGroupMember(
                    mockGroup,
                    GroupMemberRole.MEMBER
                )

                given(groupRepository.findByIdForUpdate(GROUP_ID))
                    .willReturn(mockGroup)

                given(
                    groupMemberRepository.findByGroupIdAndMemberId(
                        GROUP_ID,
                        MEMBER_ID
                    )
                ).willReturn(Optional.of(mockGroupMember))

                // when & then
                assertThatThrownBy {
                    groupService.deleteGroup(GROUP_ID, MEMBER_ID)
                }
                    .isInstanceOf(ForbiddenException::class.java)
                    .hasMessageContaining("그룹 삭제는 방장만 가능합니다.")

                verify(groupMemberService, never())
                    .deleteGroupAndAllMembers(any(Group::class.java))
            }

            @Test
            @DisplayName("다른 멤버가 남아 있으면 그룹을 삭제할 수 없다")
            fun deleteGroupHasOtherMembers() {
                // given
                val mockGroup = createGroup(
                    deadline = LocalDate.now().plusDays(2)
                )
                val mockGroupMember = createGroupMember(
                    mockGroup,
                    GroupMemberRole.OWNER
                )

                given(groupRepository.findByIdForUpdate(GROUP_ID))
                    .willReturn(mockGroup)

                given(
                    groupMemberRepository.findByGroupIdAndMemberId(
                        GROUP_ID,
                        MEMBER_ID
                    )
                ).willReturn(Optional.of(mockGroupMember))

                given(
                    groupMemberRepository.countByGroupIdAndStatus(
                        GROUP_ID,
                        GroupMemberStatus.ACTIVE
                    )
                ).willReturn(3L)

                // when & then
                assertThatThrownBy {
                    groupService.deleteGroup(GROUP_ID, MEMBER_ID)
                }
                    .isInstanceOf(BusinessRuleException::class.java)
                    .hasMessageContaining(
                        "그룹을 삭제하려면 방장을 제외한 모든 멤버가 퇴장해야 합니다."
                    )

                verify(groupMemberService, never())
                    .deleteGroupAndAllMembers(any(Group::class.java))
            }
        }
    }

    // =========================
    // 그룹 자동 종료
    // =========================

    @Nested
    @DisplayName("그룹 자동 종료")
    inner class AutoCloseExpiredGroups {

        @Nested
        @DisplayName("성공")
        inner class Success {

            @Test
            @DisplayName("만료된 그룹을 자동 종료한다")
            fun autoCloseExpiredGroupsSuccess() {
                // given
                val today = LocalDate.now()

                val mockGroup1 = createGroup()
                val mockGroup2 = createGroup(title = "두 번째 그룹")

                given(groupRepository.findExpiredGroupsForUpdate(today))
                    .willReturn(listOf(mockGroup1, mockGroup2))

                // when
                groupService.autoCloseExpiredGroups()

                // then
                verify(groupRepository)
                    .findExpiredGroupsForUpdate(today)

                assertThat(mockGroup1.status)
                    .isEqualTo(GroupStatus.FINISH)
                assertThat(mockGroup2.status)
                    .isEqualTo(GroupStatus.FINISH)
            }
        }
    }
}