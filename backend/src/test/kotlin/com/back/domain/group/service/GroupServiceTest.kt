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
import com.back.domain.habit.entity.Habit
import com.back.domain.habitVerify.entity.HabitVerify
import com.back.domain.habitVerify.entity.HabitVerifyStatus
import com.back.domain.habitVerify.repository.HabitVerifyRepository
import com.back.domain.member.entity.Member
import com.back.domain.member.repository.MemberRepository
import com.back.domain.penaltyverify.entity.PenaltyVerify
import com.back.domain.penaltyverify.entity.PenaltyVerifyStatus
import com.back.domain.penaltyverify.repository.PenaltyVerifyRepository
import com.back.global.exception.BusinessRuleException
import com.back.global.exception.EntityNotFoundException
import com.back.global.exception.ForbiddenException
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.mockk.impl.annotations.MockK
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional

class GroupServiceTest {

    private lateinit var groupService: GroupService

    @MockK private lateinit var groupRepository: GroupRepository
    @MockK private lateinit var groupMemberRepository: GroupMemberRepository
    @MockK private lateinit var groupMemberService: GroupMemberService
    @MockK private lateinit var memberRepository: MemberRepository
    @MockK private lateinit var passwordEncoder: PasswordEncoder
    @MockK private lateinit var habitVerifyRepository: HabitVerifyRepository
    @MockK private lateinit var penaltyVerifyRepository: PenaltyVerifyRepository

    companion object {
        private const val BASE_INVITE_URL = "http://test-invite.com"
        private const val MEMBER_ID = 1L
        private const val GROUP_ID = 100L
    }

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)

        groupService = GroupService(
            groupRepository = groupRepository,
            passwordEncoder = passwordEncoder,
            groupMemberRepository = groupMemberRepository,
            memberRepository = memberRepository,
            groupMemberService = groupMemberService,
            baseInviteUrl = BASE_INVITE_URL,
            habitVerifyRepository = habitVerifyRepository,
            penaltyVerifyRepository = penaltyVerifyRepository
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
        val member = mockk<Member>(relaxed = true)
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

                val mockMember = mockk<Member>(relaxed = true)
                val mockGroup = createGroup(
                    title = request.title,
                    password = "encoded_pass"
                )

                ReflectionTestUtils.setField(
                    mockGroup,
                    "id",
                    GROUP_ID
                )

                every {
                    groupRepository.existsByInviteCode(any<String>())
                } returns false

                every {
                    passwordEncoder.encode(request.password)
                } returns "encoded_pass"

                every {
                    groupRepository.save(any<Group>())
                } returns mockGroup

                every {
                    memberRepository.findById(MEMBER_ID)
                } returns Optional.of(mockMember)

                every {
                    groupMemberRepository.save(any<GroupMember>())
                } answers { firstArg() }

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

                verify(exactly = 1) {
                    groupRepository.save(any<Group>())
                }

                verify(exactly = 1) {
                    groupMemberRepository.save(any<GroupMember>())
                }
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

                // 추가: 초대 코드가 중복되지 않았음을 설정
                every {
                    groupRepository.existsByInviteCode(any<String>())
                } returns false

                every {
                    passwordEncoder.encode(any<String>())
                } returns "encoded_password"

                every {
                    groupRepository.save(any<Group>())
                } returns mockGroup

                every {
                    memberRepository.findById(MEMBER_ID)
                } returns Optional.empty()

                // when & then
                assertThatThrownBy {
                    groupService.createGroup(MEMBER_ID, request)
                }
                    .isInstanceOf(EntityNotFoundException::class.java)
                    .hasMessageContaining("존재하지 않는 회원입니다.")

                verify(exactly = 0) {
                    groupMemberRepository.save(any<GroupMember>())
                }
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
                every {
                    groupRepository.findMyGroupsWithCount(
                        MEMBER_ID,
                        GroupStatus.ACTIVE
                    )
                } returns emptyList()

                // when
                val result = groupService.getGroupSimpleList(
                    MEMBER_ID,
                    GroupStatus.ACTIVE
                )

                // then
                assertThat(result).isEmpty()

                verify(exactly = 1) {
                    groupRepository.findMyGroupsWithCount(
                        MEMBER_ID,
                        GroupStatus.ACTIVE
                    )
                }
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

                every {
                    groupRepository.findById(GROUP_ID)
                } returns Optional.of(mockGroup)

                every {
                    groupMemberRepository.existsByGroupIdAndMemberIdAndStatus(
                        GROUP_ID,
                        MEMBER_ID,
                        GroupMemberStatus.ACTIVE
                    )
                } returns true

                every {
                    groupMemberRepository.countByGroupIdAndStatus(
                        GROUP_ID,
                        GroupMemberStatus.ACTIVE
                    )
                } returns 1L

                // when
                val response = groupService.getGroupDetail(
                    GROUP_ID,
                    MEMBER_ID
                )

                // then
                assertThat(response).isNotNull
                assertThat(mockGroup.status)
                    .isEqualTo(GroupStatus.ACTIVE)
                assertThat(response.rankings).isNull()

                verify(exactly = 1) {
                    groupRepository.findById(GROUP_ID)
                }

                verify(exactly = 1) {
                    groupMemberRepository.existsByGroupIdAndMemberIdAndStatus(
                        GROUP_ID,
                        MEMBER_ID,
                        GroupMemberStatus.ACTIVE
                    )
                }

                verify(exactly = 0) {
                    habitVerifyRepository.findAllByGroupId(GROUP_ID)
                }

                verify(exactly = 0) {
                    penaltyVerifyRepository.findAllByGroupId(GROUP_ID)
                }
            }

            @Test
            @DisplayName("마감된 그룹은 세 가지 랭킹 정보를 반환한다")
            fun getGroupDetailFinishedWithRankings() {
                // given
                val finishedGroup = createGroup(
                    status = GroupStatus.FINISH
                )

                every {
                    groupRepository.findById(GROUP_ID)
                } returns Optional.of(finishedGroup)

                every {
                    groupMemberRepository.existsByGroupIdAndMemberIdAndStatus(
                        GROUP_ID,
                        MEMBER_ID,
                        GroupMemberStatus.ACTIVE
                    )
                } returns true

                every {
                    groupMemberRepository.countByGroupIdAndStatus(
                        GROUP_ID,
                        GroupMemberStatus.ACTIVE
                    )
                } returns 0L

                every {
                    groupMemberRepository.findAllByGroupId(GROUP_ID)
                } returns emptyList()

                every {
                    habitVerifyRepository.findAllByGroupId(GROUP_ID)
                } returns emptyList()

                every {
                    penaltyVerifyRepository.findAllByGroupId(GROUP_ID)
                } returns emptyList()

                // when
                val response = groupService.getGroupDetail(
                    GROUP_ID,
                    MEMBER_ID
                )

                // then
                assertThat(response.status)
                    .isEqualTo(GroupStatus.FINISH)

                assertThat(response.rankings).isNotNull
                assertThat(response.rankings?.habitVerifyTop3).isEmpty()
                assertThat(response.rankings?.penaltyVerifyTop3).isEmpty()
                assertThat(response.rankings?.habitSuccessTop3).isEmpty()

                verify(exactly = 1) {
                    groupMemberRepository.findAllByGroupId(GROUP_ID)
                }

                verify(exactly = 1) {
                    habitVerifyRepository.findAllByGroupId(GROUP_ID)
                }

                verify(exactly = 1) {
                    penaltyVerifyRepository.findAllByGroupId(GROUP_ID)
                }
            }

            @Test
            @DisplayName("종료된 그룹의 랭킹은 인증 상태와 횟수에 따라 상위 3명을 반환한다")
            fun getGroupDetailFinishedWithRankingData() {
                // given
                val finishedGroup = createGroup(status = GroupStatus.FINISH)

                val members = (1L..5L).map { id ->
                    Member.create(
                        nickname = "member$id",
                        username = "user$id",
                        encodedPassword = "password",
                    ).also {
                        ReflectionTestUtils.setField(it, "id", id)
                    }
                }

                val groupMembers = members.mapIndexed { index, member ->
                    GroupMember.create(
                        finishedGroup,
                        member,
                        GroupMemberRole.MEMBER,
                    ).also {
                        ReflectionTestUtils.setField(it, "id", index + 1L)

                        // 5번 회원은 퇴장했어도 랭킹에 포함되어야 한다.
                        if (index == 4) {
                            it.leave()
                        }
                    }
                }

                // 회원별 습관 4개를 생성한다.
                // 벌칙 인증은 회원-습관 조합당 하나만 생성한다.
                val habitsByMember = groupMembers.mapIndexed { memberIndex, groupMember ->
                    (1..4).map { habitIndex ->
                        Habit.create(
                            groupMember = groupMember,
                            title = "habit${memberIndex + 1}-$habitIndex",
                            description = null,
                            days = 7,
                        )
                    }
                }

                fun createHabitVerify(
                    habit: Habit,
                    status: HabitVerifyStatus,
                    day: Int,
                ): HabitVerify {
                    val verify = HabitVerify.create(
                        habit = habit,
                        verifyDate = LocalDate.of(2026, 1, day),
                        description = null,
                        imageUrl = null,
                    )

                    when (status) {
                        HabitVerifyStatus.APPROVED -> verify.approve()
                        HabitVerifyStatus.REJECTED -> verify.reject()
                        HabitVerifyStatus.PENDING -> Unit
                    }

                    return verify
                }

                val habitStatusesByMember = listOf(
                    listOf(
                        HabitVerifyStatus.APPROVED,
                        HabitVerifyStatus.APPROVED,
                        HabitVerifyStatus.PENDING,
                    ),
                    listOf(
                        HabitVerifyStatus.APPROVED,
                        HabitVerifyStatus.REJECTED,
                        HabitVerifyStatus.PENDING,
                    ),
                    listOf(
                        HabitVerifyStatus.APPROVED,
                        HabitVerifyStatus.APPROVED,
                    ),
                    listOf(HabitVerifyStatus.APPROVED),
                    listOf(
                        HabitVerifyStatus.PENDING,
                        HabitVerifyStatus.PENDING,
                        HabitVerifyStatus.PENDING,
                        HabitVerifyStatus.PENDING,
                    ),
                )

                val habitVerifies = habitStatusesByMember.flatMapIndexed { memberIndex, statuses ->
                    statuses.mapIndexed { habitIndex, status ->
                        createHabitVerify(
                            habit = habitsByMember[memberIndex][habitIndex],
                            status = status,
                            day = habitIndex + 1,
                        )
                    }
                }

                fun createPenaltyVerify(
                    groupMember: GroupMember,
                    habit: Habit,
                    status: PenaltyVerifyStatus,
                ): PenaltyVerify {
                    val verify = PenaltyVerify.create(groupMember, habit)

                    when (status) {
                        PenaltyVerifyStatus.REQUIRED -> Unit
                        PenaltyVerifyStatus.PENDING -> {
                            verify.submit(LocalDate.now(), null, null)
                        }
                        PenaltyVerifyStatus.APPROVED -> {
                            verify.submit(LocalDate.now(), null, null)
                            verify.approve()
                        }
                        PenaltyVerifyStatus.REJECTED -> {
                            verify.submit(LocalDate.now(), null, null)
                            verify.reject()
                        }
                    }

                    return verify
                }

                // REQUIRED는 집계에서 제외되어야 한다.
                val penaltyStatusesByMember = listOf(
                    listOf(
                        PenaltyVerifyStatus.APPROVED,
                        PenaltyVerifyStatus.PENDING,
                        PenaltyVerifyStatus.REQUIRED,
                        PenaltyVerifyStatus.REQUIRED,
                    ),
                    listOf(
                        PenaltyVerifyStatus.REJECTED,
                        PenaltyVerifyStatus.PENDING,
                        PenaltyVerifyStatus.REQUIRED,
                        PenaltyVerifyStatus.REQUIRED,
                    ),
                    listOf(
                        PenaltyVerifyStatus.APPROVED,
                        PenaltyVerifyStatus.REQUIRED,
                        PenaltyVerifyStatus.REQUIRED,
                        PenaltyVerifyStatus.REQUIRED,
                    ),
                    List(4) { PenaltyVerifyStatus.REQUIRED },
                    List(4) { PenaltyVerifyStatus.REQUIRED },
                )

                val penaltyVerifies = penaltyStatusesByMember.flatMapIndexed { memberIndex, statuses ->
                    statuses.mapIndexed { habitIndex, status ->
                        createPenaltyVerify(
                            groupMember = groupMembers[memberIndex],
                            habit = habitsByMember[memberIndex][habitIndex],
                            status = status,
                        )
                    }
                }

                every {
                    groupRepository.findById(GROUP_ID)
                } returns Optional.of(finishedGroup)

                every {
                    groupMemberRepository.existsByGroupIdAndMemberIdAndStatus(
                        GROUP_ID,
                        MEMBER_ID,
                        GroupMemberStatus.ACTIVE,
                    )
                } returns true

                every {
                    groupMemberRepository.countByGroupIdAndStatus(
                        GROUP_ID,
                        GroupMemberStatus.ACTIVE,
                    )
                } returns 4L

                every {
                    groupMemberRepository.findAllByGroupId(GROUP_ID)
                } returns groupMembers

                every {
                    habitVerifyRepository.findAllByGroupId(GROUP_ID)
                } returns habitVerifies

                every {
                    penaltyVerifyRepository.findAllByGroupId(GROUP_ID)
                } returns penaltyVerifies

                // when
                val response = groupService.getGroupDetail(GROUP_ID, MEMBER_ID)

                // then
                val rankings = response.rankings!!

                // 전체 습관 인증: 모든 상태를 포함하고, 동점이면 회원 ID 오름차순
                assertThat(rankings.habitVerifyTop3.map { it.memberId to it.count })
                    .containsExactly(
                        5L to 4,
                        1L to 3,
                        2L to 3,
                    )

                // 습관 성공: APPROVED만 포함
                assertThat(rankings.habitSuccessTop3.map { it.memberId to it.count })
                    .containsExactly(
                        1L to 2,
                        3L to 2,
                        2L to 1,
                    )

                // 벌칙 인증: REQUIRED를 제외하고, 동점이면 회원 ID 오름차순
                assertThat(rankings.penaltyVerifyTop3.map { it.memberId to it.count })
                    .containsExactly(
                        1L to 2,
                        2L to 2,
                        3L to 1,
                    )

                assertThat(rankings.habitVerifyTop3).hasSize(3)
                assertThat(rankings.habitSuccessTop3).hasSize(3)
                assertThat(rankings.penaltyVerifyTop3).hasSize(3)
            }

            @Test
            @DisplayName("그룹에 속하지 않은 회원의 인증 기록은 랭킹에 집계되지 않는다")
            fun getGroupDetailFinishedIgnoresVerifiesFromOtherMembers() {
                // given
                val finishedGroup = createGroup(status = GroupStatus.FINISH)
                val otherGroup = createGroup()

                // 현재 그룹의 회원
                val member = Member.create(
                    nickname = "member1",
                    username = "user1",
                    encodedPassword = "password",
                ).also {
                    ReflectionTestUtils.setField(it, "id", 1L)
                }

                val groupMember = GroupMember.create(
                    finishedGroup,
                    member,
                    GroupMemberRole.MEMBER,
                ).also {
                    ReflectionTestUtils.setField(it, "id", 1L)
                }

                // 다른 그룹에 속한 회원
                val outsider = Member.create(
                    nickname = "outsider",
                    username = "outsider",
                    encodedPassword = "password",
                ).also {
                    ReflectionTestUtils.setField(it, "id", 99L)
                }

                val outsiderGroupMember = GroupMember.create(
                    otherGroup,
                    outsider,
                    GroupMemberRole.MEMBER,
                ).also {
                    ReflectionTestUtils.setField(it, "id", 99L)
                }

                // 외부 회원의 습관 및 인증 기록
                val outsiderHabit = Habit.create(
                    groupMember = outsiderGroupMember,
                    title = "outsider habit",
                    description = null,
                    days = 7,
                )

                val outsiderHabitVerify = HabitVerify.create(
                    habit = outsiderHabit,
                    verifyDate = LocalDate.of(2026, 1, 1),
                    description = null,
                    imageUrl = null,
                ).also {
                    it.approve()
                }

                val outsiderPenaltyVerify = PenaltyVerify.create(
                    outsiderGroupMember,
                    outsiderHabit,
                ).also {
                    it.submit(LocalDate.of(2026, 1, 1), null, null)
                    it.approve()
                }

                every {
                    groupRepository.findById(GROUP_ID)
                } returns Optional.of(finishedGroup)

                every {
                    groupMemberRepository.existsByGroupIdAndMemberIdAndStatus(
                        GROUP_ID,
                        MEMBER_ID,
                        GroupMemberStatus.ACTIVE,
                    )
                } returns true

                every {
                    groupMemberRepository.countByGroupIdAndStatus(
                        GROUP_ID,
                        GroupMemberStatus.ACTIVE,
                    )
                } returns 1L

                // 현재 그룹의 회원만 랭킹 대상에 포함
                every {
                    groupMemberRepository.findAllByGroupId(GROUP_ID)
                } returns listOf(groupMember)

                // 외부 회원의 인증 기록이 결과에 섞여 들어오는 상황을 가정
                every {
                    habitVerifyRepository.findAllByGroupId(GROUP_ID)
                } returns listOf(outsiderHabitVerify)

                every {
                    penaltyVerifyRepository.findAllByGroupId(GROUP_ID)
                } returns listOf(outsiderPenaltyVerify)

                // when
                val response = groupService.getGroupDetail(GROUP_ID, MEMBER_ID)
                val rankings = response.rankings!!

                // then
                assertThat(rankings.habitVerifyTop3.map { it.memberId })
                    .containsExactly(1L)

                assertThat(rankings.habitSuccessTop3.map { it.memberId })
                    .containsExactly(1L)

                assertThat(rankings.penaltyVerifyTop3.map { it.memberId })
                    .containsExactly(1L)

                assertThat(rankings.habitVerifyTop3.map { it.count })
                    .containsExactly(0)

                assertThat(rankings.habitSuccessTop3.map { it.count })
                    .containsExactly(0)

                assertThat(rankings.penaltyVerifyTop3.map { it.count })
                    .containsExactly(0)
            }
        }

        @Nested
        @DisplayName("예외")
        inner class Failure {

            @Test
            @DisplayName("가입하지 않은 멤버는 그룹 상세 정보에 접근할 수 없다")
            fun getGroupDetailForbidden() {
                // given
                every {
                    groupRepository.findById(GROUP_ID)
                } returns Optional.of(createGroup())

                every {
                    groupMemberRepository.existsByGroupIdAndMemberIdAndStatus(
                        GROUP_ID,
                        MEMBER_ID,
                        GroupMemberStatus.ACTIVE
                    )
                } returns false

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

                every {
                    groupRepository.findByIdForUpdate(GROUP_ID)
                } returns mockGroup

                every {
                    groupMemberRepository.findByGroupIdAndMemberId(
                        GROUP_ID,
                        MEMBER_ID
                    )
                } returns Optional.of(mockGroupMember)

                // 추가: 그룹 수정 시 활성 멤버 수 조회에 대한 응답
                every {
                    groupMemberRepository.countByGroupIdAndStatus(
                        GROUP_ID,
                        GroupMemberStatus.ACTIVE
                    )
                } returns 1L

                every {
                    passwordEncoder.encode("newpass")
                } returns "encoded_newpass"

                // when
                groupService.updateGroup(
                    GROUP_ID,
                    MEMBER_ID,
                    request
                )

                // then
                assertThat(mockGroup.title).isEqualTo("새제목")

                verify(exactly = 1) {
                    groupRepository.findByIdForUpdate(GROUP_ID)
                }

                verify(exactly = 1) {
                    groupMemberRepository.findByGroupIdAndMemberId(
                        GROUP_ID,
                        MEMBER_ID
                    )
                }

                verify(exactly = 1) {
                    passwordEncoder.encode("newpass")
                }
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

                every {
                    groupRepository.findByIdForUpdate(GROUP_ID)
                } returns mockGroup

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

                every {
                    groupRepository.findByIdForUpdate(GROUP_ID)
                } returns mockGroup

                every {
                    groupMemberRepository.findByGroupIdAndMemberId(
                        GROUP_ID,
                        MEMBER_ID
                    )
                } returns Optional.of(mockGroupMember)

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

                every {
                    groupRepository.findByIdForUpdate(GROUP_ID)
                } returns mockGroup

                every {
                    groupMemberRepository.findByGroupIdAndMemberId(
                        GROUP_ID,
                        MEMBER_ID
                    )
                } returns Optional.of(mockGroupMember)

                every {
                    groupMemberRepository.countByGroupIdAndStatus(
                        GROUP_ID,
                        GroupMemberStatus.ACTIVE
                    )
                } returns 1L

                every {
                    groupMemberService.deleteGroupAndAllMembers(mockGroup)
                } just Runs

                // when
                groupService.deleteGroup(GROUP_ID, MEMBER_ID)

                // then
                verify(exactly = 1) {
                    groupMemberService.deleteGroupAndAllMembers(mockGroup)
                }
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

                every {
                    groupRepository.findByIdForUpdate(GROUP_ID)
                } returns mockGroup

                // when & then
                assertThatThrownBy {
                    groupService.deleteGroup(GROUP_ID, MEMBER_ID)
                }
                    .isInstanceOf(BusinessRuleException::class.java)
                    .hasMessageContaining("이미 종료된 그룹은 삭제할 수 없습니다.")

                verify(exactly = 0) {
                    groupMemberService.deleteGroupAndAllMembers(mockGroup)
                }
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

                every {
                    groupRepository.findByIdForUpdate(GROUP_ID)
                } returns mockGroup

                every {
                    groupMemberRepository.findByGroupIdAndMemberId(
                        GROUP_ID,
                        MEMBER_ID
                    )
                } returns Optional.of(mockGroupMember)

                // when & then
                assertThatThrownBy {
                    groupService.deleteGroup(GROUP_ID, MEMBER_ID)
                }
                    .isInstanceOf(ForbiddenException::class.java)
                    .hasMessageContaining("그룹 삭제는 방장만 가능합니다.")

                verify(exactly = 0) {
                    groupMemberService.deleteGroupAndAllMembers(mockGroup)
                }
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

                every {
                    groupRepository.findByIdForUpdate(GROUP_ID)
                } returns mockGroup

                every {
                    groupMemberRepository.findByGroupIdAndMemberId(
                        GROUP_ID,
                        MEMBER_ID
                    )
                } returns Optional.of(mockGroupMember)

                every {
                    groupMemberRepository.countByGroupIdAndStatus(
                        GROUP_ID,
                        GroupMemberStatus.ACTIVE
                    )
                } returns 3L

                // when & then
                assertThatThrownBy {
                    groupService.deleteGroup(GROUP_ID, MEMBER_ID)
                }
                    .isInstanceOf(BusinessRuleException::class.java)
                    .hasMessageContaining(
                        "그룹을 삭제하려면 방장을 제외한 모든 멤버가 퇴장해야 합니다."
                    )

                verify(exactly = 0) {
                    groupMemberService.deleteGroupAndAllMembers(mockGroup)
                }
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

                every {
                    groupRepository.findExpiredGroupsForUpdate(today)
                } returns listOf(mockGroup1, mockGroup2)

                // when
                groupService.autoCloseExpiredGroups()

                // then
                verify(exactly = 1) {
                    groupRepository.findExpiredGroupsForUpdate(today)
                }

                assertThat(mockGroup1.status)
                    .isEqualTo(GroupStatus.FINISH)
                assertThat(mockGroup2.status)
                    .isEqualTo(GroupStatus.FINISH)
            }
        }
    }
}