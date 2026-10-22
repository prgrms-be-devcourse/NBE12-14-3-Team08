package com.back.domain.group.repository

import com.back.domain.group.entity.Group
import com.back.domain.group.entity.GroupStatus
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.member.entity.Member
import com.back.domain.member.repository.MemberRepository
import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import jakarta.persistence.EntityManager

@DataJpaTest
class GroupRepositoryTest @Autowired constructor(
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val memberRepository: MemberRepository,
    private val entityManager: EntityManager
) {

    private fun createGroup(
        title: String,
        inviteCode: String,
        status: GroupStatus,
        deadline: LocalDate?
    ): Group =
        Group(
            title = title,
            description = "테스트 설명",
            deadline = deadline,
            penalty = "테스트 벌칙",
            password = "encoded-password",
            inviteCode = inviteCode,
            memberLimit = 10,
            status = status
        )

    private fun createMember(
        nickname: String,
        username: String
    ): Member =
        memberRepository.save(
            Member.create(
                nickname,
                username,
                "encoded-password"
            )
        )

    private fun saveGroup(group: Group): Group =
        groupRepository.save(group)

    @Test
    @DisplayName("초대 코드로 그룹을 조회한다")
    fun findByInviteCodeSuccess() {
        // given
        val group = saveGroup(
            createGroup(
                "테스트 그룹",
                "TEST1234",
                GroupStatus.ACTIVE,
                LocalDate.now().plusDays(7)
            )
        )

        // when
        val result = groupRepository.findByInviteCode("TEST1234")

        // then
        val foundGroup = requireNotNull(result)

        assertThat(foundGroup.id).isEqualTo(group.id)
        assertThat(foundGroup.title).isEqualTo("테스트 그룹")
    }

    @Test
    @DisplayName("초대 코드의 존재 여부를 확인한다")
    fun existsByInviteCode() {
        // given
        saveGroup(
            createGroup(
                "테스트 그룹",
                "TEST1234",
                GroupStatus.ACTIVE,
                LocalDate.now().plusDays(7)
            )
        )

        // when & then
        assertThat(groupRepository.existsByInviteCode("TEST1234")).isTrue()
        assertThat(groupRepository.existsByInviteCode("NOTFOUND")).isFalse()
    }

    @Test
    @DisplayName("내 그룹 목록과 활성 멤버 수를 조회한다")
    fun findMyGroupsWithCountSuccess() {
        // given
        val member = createMember("테스트 사용자", "test-user")
        val anotherMember = createMember("다른 사용자", "another-user")

        val group = saveGroup(
            createGroup(
                "참여 중인 그룹",
                "ACTIVE123",
                GroupStatus.ACTIVE,
                LocalDate.now().plusDays(7)
            )
        )

        groupMemberRepository.save(
            GroupMember.create(group, member, GroupMemberRole.MEMBER)
        )
        groupMemberRepository.save(
            GroupMember.create(group, anotherMember, GroupMemberRole.OWNER)
        )

        entityManager.flush()
        entityManager.clear()

        // when
        val result = groupRepository.findMyGroupsWithCount(
            requireNotNull(member.id),
            GroupStatus.ACTIVE
        )

        // then
        assertThat(result).hasSize(1)
        assertThat(result[0].title).isEqualTo("참여 중인 그룹")
        assertThat(result[0].currentMemberCount).isEqualTo(2L)
        assertThat(result[0].status).isEqualTo("ACTIVE")
        assertThat(result[0].role).isEqualTo("MEMBER")
    }

    @Test
    @DisplayName("참여 중인 그룹이 없으면 빈 목록을 반환한다")
    fun findMyGroupsWithCountEmpty() {
        // given
        val member = createMember("테스트 사용자", "test-user")

        // when
        val result = groupRepository.findMyGroupsWithCount(
            requireNotNull(member.id),
            GroupStatus.ACTIVE
        )

        // then
        assertThat(result).isEmpty()
    }

    @Test
    @DisplayName("마감일이 지난 ACTIVE 그룹만 조회한다")
    fun findExpiredGroupsForUpdateSuccess() {
        // given
        val expiredGroup = saveGroup(
            createGroup(
                "마감된 그룹",
                "EXPIRED01",
                GroupStatus.ACTIVE,
                LocalDate.now().minusDays(1)
            )
        )

        saveGroup(
            createGroup(
                "오늘 마감 그룹",
                "TODAY0001",
                GroupStatus.ACTIVE,
                LocalDate.now()
            )
        )

        saveGroup(
            createGroup(
                "미래 마감 그룹",
                "FUTURE001",
                GroupStatus.ACTIVE,
                LocalDate.now().plusDays(1)
            )
        )

        saveGroup(
            createGroup(
                "종료된 그룹",
                "FINISH001",
                GroupStatus.FINISH,
                LocalDate.now().minusDays(1)
            )
        )

        entityManager.flush()
        entityManager.clear()

        // when
        val result = groupRepository.findExpiredGroupsForUpdate(LocalDate.now())

        // then
        assertThat(result.map { it.id })
            .containsExactly(expiredGroup.id)
    }
}