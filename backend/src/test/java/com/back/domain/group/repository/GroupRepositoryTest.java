
package com.back.domain.group.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.domain.group.dto.GroupResponse;
import com.back.domain.group.entity.Group;
import com.back.domain.group.entity.GroupStatus;
import com.back.domain.groupMember.entity.GroupMember;
import com.back.domain.groupMember.entity.GroupMemberRole;
import com.back.domain.groupMember.repository.GroupMemberRepository;
import com.back.domain.member.entity.Member;
import com.back.domain.member.repository.MemberRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class GroupRepositoryTest {

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("초대 코드로 그룹을 조회할 수 있다")
    void findByInviteCode_Success() {
        // given
        Group group = createGroup("초대 코드 조회", "invite001",
                GroupStatus.ACTIVE, LocalDate.now().plusDays(7));

        groupRepository.save(group);
        entityManager.flush();
        entityManager.clear();

        // when
        var result = groupRepository.findByInviteCode("invite001");

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).isEqualTo("초대 코드 조회");
    }

    @Test
    @DisplayName("초대 코드의 존재 여부를 확인할 수 있다")
    void existsByInviteCode() {
        // given
        groupRepository.save(createGroup("그룹", "invite002",
                GroupStatus.ACTIVE, LocalDate.now().plusDays(7)));
        entityManager.flush();

        // when & then
        assertThat(groupRepository.existsByInviteCode("invite002")).isTrue();
        assertThat(groupRepository.existsByInviteCode("not-exist")).isFalse();
    }

    @Test
    @DisplayName("회원이 가입한 그룹을 상태별로 조회하고 멤버 수와 역할을 반환한다")
    void findMyGroupsWithCount_Success() {
        // given
        Member owner = memberRepository.save(
                Member.create("소유자", "owner@test.com", "encoded-password")
        );
        Member member = memberRepository.save(
                Member.create("일반 회원", "member@test.com", "encoded-password")
        );
        Member other = memberRepository.save(
                Member.create("다른 회원", "other@test.com", "encoded-password")
        );

        Group group = groupRepository.save(
                createGroup("활성 그룹", "invite003",
                        GroupStatus.ACTIVE, LocalDate.now().plusDays(7))
        );

        Group finishedGroup = groupRepository.save(
                createGroup("종료 그룹", "invite004",
                        GroupStatus.FINISH, LocalDate.now().minusDays(1))
        );

        groupMemberRepository.save(
                GroupMember.create(group, owner, GroupMemberRole.OWNER)
        );
        groupMemberRepository.save(
                GroupMember.create(group, member, GroupMemberRole.MEMBER)
        );
        groupMemberRepository.save(
                GroupMember.create(finishedGroup, member, GroupMemberRole.OWNER)
        );

        entityManager.flush();
        entityManager.clear();

        // when
        List<GroupResponse.Simple> results =
                groupRepository.findMyGroupsWithCount(member.getId(), GroupStatus.ACTIVE);

        // then
        assertThat(results).hasSize(1);

        GroupResponse.Simple result = results.get(0);

        assertThat(result.title()).isEqualTo("활성 그룹");
        assertThat(result.currentMemberCount()).isEqualTo(2L);
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.role()).isEqualTo("MEMBER");
    }

    @Test
    @DisplayName("가입한 그룹이 없으면 빈 목록을 반환한다")
    void findMyGroupsWithCount_Empty() {
        // given
        Member member = memberRepository.save(
                Member.create("미가입 회원", "nobody@test.com", "encoded-password")
        );

        entityManager.flush();

        // when
        List<GroupResponse.Simple> results =
                groupRepository.findMyGroupsWithCount(member.getId(), GroupStatus.ACTIVE);

        // then
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("마감일이 오늘보다 이전인 ACTIVE 그룹만 조회한다")
    void findExpiredGroupsForUpdate_Success() {
        // given
        LocalDate today = LocalDate.now();

        Group expiredGroup = groupRepository.save(
                createGroup("만료 그룹", "invite005",
                        GroupStatus.ACTIVE, today.minusDays(1))
        );

        groupRepository.save(
                createGroup("오늘 마감 그룹", "invite006",
                        GroupStatus.ACTIVE, today)
        );

        groupRepository.save(
                createGroup("미래 마감 그룹", "invite007",
                        GroupStatus.ACTIVE, today.plusDays(1))
        );

        groupRepository.save(
                createGroup("이미 종료된 그룹", "invite008",
                        GroupStatus.FINISH, today.minusDays(3))
        );

        entityManager.flush();
        entityManager.clear();

        // when
        List<Group> results = groupRepository.findExpiredGroupsForUpdate(today);

        // then
        assertThat(results)
                .extracting(Group::getId)
                .containsExactly(expiredGroup.getId());
    }

    private Group createGroup(
            String title,
            String inviteCode,
            GroupStatus status,
            LocalDate deadline
    ) {
        return Group.builder()
                .title(title)
                .description("테스트 설명")
                .deadline(deadline)
                .penalty("테스트 벌칙")
                .password("encoded-password")
                .inviteCode(inviteCode)
                .memberLimit(10)
                .status(status)
                .build();
    }
}
