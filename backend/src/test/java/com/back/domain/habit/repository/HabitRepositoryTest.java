package com.back.domain.habit.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.domain.group.entity.Group;
import com.back.domain.group.entity.GroupStatus;
import com.back.domain.group.repository.GroupRepository;
import com.back.domain.groupMember.entity.GroupMember;
import com.back.domain.groupMember.entity.GroupMemberRole;
import com.back.domain.groupMember.repository.GroupMemberRepository;
import com.back.domain.habit.entity.Habit;
import com.back.domain.habit.entity.HabitStatus;
import com.back.domain.member.entity.Member;
import com.back.domain.member.repository.MemberRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.util.ReflectionTestUtils;

// 임베디드 DB에서 HabitRepository의 커스텀 쿼리를 검증한다. (테스트마다 롤백)
@DataJpaTest
class HabitRepositoryTest {

    @Autowired private HabitRepository habitRepository;
    @Autowired private GroupRepository groupRepository;
    @Autowired private GroupMemberRepository groupMemberRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private EntityManager entityManager;

    private Member member;
    private Group group;
    private GroupMember groupMember;

    @BeforeEach
    void setUp() {
        member = memberRepository.save(Member.create("일반회원", uniqueName("member"), "pass1234"));
        group = saveGroup(GroupStatus.ACTIVE);
        groupMember = groupMemberRepository.save(GroupMember.create(group, member, GroupMemberRole.MEMBER));
    }

    private String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private Group saveGroup(GroupStatus status) {
        return groupRepository.save(Group.builder()
            .title("아침 기상 챌린지")
            .penalty("커피 1잔 쏘기")
            .password("1234")
            .inviteCode(uniqueName("code"))
            .memberLimit(10)
            .status(status)
            .build());
    }

    private Habit saveHabit(GroupMember owner, String title, HabitStatus status) {
        Habit habit = Habit.create(owner, title, "설명", 3);
        if (status == HabitStatus.FAILED) {
            habit.fail();
        }
        return habitRepository.saveAndFlush(habit);
    }

    @Nested
    @DisplayName("활성 습관 존재/조회")
    class ActiveHabit {

        @Test
        @DisplayName("existsByGroupMember_IdAndStatus: 해당 멤버의 해당 상태 습관이 있을 때만 true다")
        void 상태별_존재_여부() {
            saveHabit(groupMember, "물 마시기", HabitStatus.FAILED);

            assertThat(habitRepository.existsByGroupMember_IdAndStatus(groupMember.getId(), HabitStatus.FAILED))
                .isTrue();
            assertThat(habitRepository.existsByGroupMember_IdAndStatus(groupMember.getId(), HabitStatus.ACTIVE))
                .isFalse();
        }

        @Test
        @DisplayName("existsByGroupMember_IdAndStatus: 다른 멤버의 습관은 포함하지 않는다")
        void 다른_멤버의_습관은_포함하지_않는다() {
            Member other = memberRepository.save(Member.create("다른회원", uniqueName("other"), "pass1234"));
            GroupMember otherGroupMember =
                groupMemberRepository.save(GroupMember.create(group, other, GroupMemberRole.MEMBER));
            saveHabit(otherGroupMember, "독서", HabitStatus.ACTIVE);

            assertThat(habitRepository.existsByGroupMember_IdAndStatus(groupMember.getId(), HabitStatus.ACTIVE))
                .isFalse();
        }

        @Test
        @DisplayName("회원과 그룹으로 활성 습관을 조회한다")
        void 회원과_그룹으로_활성_습관_조회() {
            Habit active = saveHabit(groupMember, "물 마시기", HabitStatus.ACTIVE);
            saveHabit(groupMember, "예전 습관", HabitStatus.FAILED);

            Habit found = habitRepository.findByGroupMember_Member_IdAndGroupMember_Group_IdAndStatus(
                member.getId(), group.getId(), HabitStatus.ACTIVE);

            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(active.getId());
        }

        @Test
        @DisplayName("다른 그룹의 활성 습관은 조회되지 않는다")
        void 다른_그룹의_활성_습관은_조회되지_않는다() {
            Group otherGroup = saveGroup(GroupStatus.ACTIVE);
            GroupMember inOtherGroup =
                groupMemberRepository.save(GroupMember.create(otherGroup, member, GroupMemberRole.MEMBER));
            saveHabit(inOtherGroup, "다른 그룹 습관", HabitStatus.ACTIVE);

            assertThat(habitRepository.findByGroupMember_Member_IdAndGroupMember_Group_IdAndStatus(
                member.getId(), group.getId(), HabitStatus.ACTIVE)).isNull();
        }

        @Test
        @DisplayName("본인 소유일 때만 id로 습관을 조회한다")
        void 본인_습관만_id로_조회() {
            Habit habit = saveHabit(groupMember, "물 마시기", HabitStatus.ACTIVE);

            assertThat(habitRepository.findByIdAndGroupMember_Member_Id(habit.getId(), member.getId())).isPresent();
            assertThat(habitRepository.findByIdAndGroupMember_Member_Id(habit.getId(), member.getId() + 999))
                .isEmpty();
        }
    }

    @Nested
    @DisplayName("실패 습관 목록")
    class FailedHabits {

        @Test
        @DisplayName("해당 멤버와 그룹의 실패 습관만 생성일 최신순으로 반환한다")
        void 최신순_실패_습관_목록() {
            Habit older = saveHabit(groupMember, "오래된 습관", HabitStatus.FAILED);
            ReflectionTestUtils.setField(older, "createDate", LocalDateTime.now().minusDays(3));
            Habit newer = saveHabit(groupMember, "최근 습관", HabitStatus.FAILED);
            ReflectionTestUtils.setField(newer, "createDate", LocalDateTime.now().minusDays(1));
            saveHabit(groupMember, "진행 중 습관", HabitStatus.ACTIVE);
            habitRepository.flush();
            entityManager.clear();

            List<Habit> result = habitRepository
                .findAllByGroupMember_Member_IdAndGroupMember_Group_IdAndStatusOrderByCreateDateDesc(
                    member.getId(), group.getId(), HabitStatus.FAILED);

            assertThat(result).extracting(Habit::getTitle).containsExactly("최근 습관", "오래된 습관");
        }
    }

    @Nested
    @DisplayName("주간 판정 대상 조회")
    class WeeklyCheckTargets {

        @Test
        @DisplayName("findAllByStatusInProgressGroup: 종료(FINISH)된 그룹의 습관은 제외하고 진행 중 그룹의 활성 습관만 반환한다")
        void 종료된_그룹은_제외() {
            Habit inProgress = saveHabit(groupMember, "진행 중 그룹 습관", HabitStatus.ACTIVE);

            Group finishedGroup = saveGroup(GroupStatus.FINISH);
            GroupMember inFinished =
                groupMemberRepository.save(GroupMember.create(finishedGroup, member, GroupMemberRole.MEMBER));
            saveHabit(inFinished, "종료된 그룹 습관", HabitStatus.ACTIVE);

            saveHabit(groupMemberRepository.save(GroupMember.create(
                saveGroup(GroupStatus.ACTIVE), member, GroupMemberRole.MEMBER)), "실패한 습관", HabitStatus.FAILED);

            List<Habit> result = habitRepository.findAllByStatusInProgressGroup(HabitStatus.ACTIVE);

            assertThat(result).extracting(Habit::getId).contains(inProgress.getId());
            assertThat(result).extracting(Habit::getTitle)
                .doesNotContain("종료된 그룹 습관", "실패한 습관");
        }

        @Test
        @DisplayName("findAllByGroupMember_Group_IdAndStatus: 해당 그룹의 활성 습관만 반환한다")
        void 그룹의_활성_습관만_반환() {
            Habit active = saveHabit(groupMember, "활성 습관", HabitStatus.ACTIVE);
            Member other = memberRepository.save(Member.create("다른회원", uniqueName("other"), "pass1234"));
            GroupMember otherInSameGroup =
                groupMemberRepository.save(GroupMember.create(group, other, GroupMemberRole.MEMBER));
            saveHabit(otherInSameGroup, "실패 습관", HabitStatus.FAILED);
            GroupMember inOtherGroup = groupMemberRepository.save(
                GroupMember.create(saveGroup(GroupStatus.ACTIVE), member, GroupMemberRole.MEMBER));
            saveHabit(inOtherGroup, "다른 그룹 활성 습관", HabitStatus.ACTIVE);

            List<Habit> result = habitRepository.findAllByGroupMember_Group_IdAndStatus(group.getId(), HabitStatus.ACTIVE);

            assertThat(result).extracting(Habit::getId).containsExactly(active.getId());
        }
    }

    @Nested
    @DisplayName("삭제 쿼리")
    class DeleteQueries {

        @Test
        @DisplayName("findIdsByGroupMemberId: 멤버의 습관 id를 상태와 무관하게 모두 반환한다")
        void 멤버의_습관_id_조회() {
            Habit active = saveHabit(groupMember, "활성", HabitStatus.ACTIVE);
            Habit failed = saveHabit(groupMember, "실패", HabitStatus.FAILED);

            assertThat(habitRepository.findIdsByGroupMemberId(groupMember.getId()))
                .containsExactlyInAnyOrder(active.getId(), failed.getId());
        }

        @Test
        @DisplayName("deleteByIds: 지정한 습관만 삭제한다")
        void 지정한_습관만_삭제() {
            Habit target = saveHabit(groupMember, "삭제 대상", HabitStatus.FAILED);
            Habit keep = saveHabit(groupMember, "유지", HabitStatus.ACTIVE);

            habitRepository.deleteByIds(List.of(target.getId()));

            assertThat(habitRepository.findById(target.getId())).isEmpty();
            assertThat(habitRepository.findById(keep.getId())).isPresent();
        }

        @Test
        @DisplayName("deleteHabitsByGroupId: 해당 그룹 멤버들의 습관만 삭제하고 삭제 건수를 반환한다")
        void 그룹의_습관만_삭제() {
            Habit inGroup1 = saveHabit(groupMember, "그룹 습관 1", HabitStatus.ACTIVE);
            Habit inGroup2 = saveHabit(groupMember, "그룹 습관 2", HabitStatus.FAILED);
            GroupMember inOtherGroup = groupMemberRepository.save(
                GroupMember.create(saveGroup(GroupStatus.ACTIVE), member, GroupMemberRole.MEMBER));
            Habit otherGroupHabit = saveHabit(inOtherGroup, "다른 그룹 습관", HabitStatus.ACTIVE);
            entityManager.clear();

            int deleted = habitRepository.deleteHabitsByGroupId(group.getId());
            entityManager.clear();

            assertThat(deleted).isEqualTo(2);
            assertThat(habitRepository.findById(inGroup1.getId())).isEmpty();
            assertThat(habitRepository.findById(inGroup2.getId())).isEmpty();
            assertThat(habitRepository.findById(otherGroupHabit.getId())).isPresent();
        }
    }
}
