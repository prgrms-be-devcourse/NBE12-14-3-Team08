package com.back.domain.penaltyverify.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.domain.group.entity.Group;
import com.back.domain.group.repository.GroupRepository;
import com.back.domain.groupMember.entity.GroupMember;
import com.back.domain.groupMember.entity.GroupMemberRole;
import com.back.domain.groupMember.repository.GroupMemberRepository;
import com.back.domain.habit.entity.Habit;
import com.back.domain.habit.repository.HabitRepository;
import com.back.domain.member.entity.Member;
import com.back.domain.member.repository.MemberRepository;
import com.back.domain.penaltyverify.entity.PenaltyVerify;
import com.back.domain.penaltyverify.entity.PenaltyVerifyStatus;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

// Testcontainers PostgreSQL에서 PenaltyVerifyRepository의 조회 쿼리를 검증한다. (테스트마다 롤백)
// 배포 DB는 PostgreSQL이지만, 여기서 쓰는 WHERE / IN / ORDER BY / COUNT는 두 DB에서 같게 동작한다.
@DataJpaTest
class PenaltyVerifyRepositoryTest {

    @Autowired private PenaltyVerifyRepository penaltyVerifyRepository;
    @Autowired private HabitRepository habitRepository;
    @Autowired private GroupRepository groupRepository;
    @Autowired private GroupMemberRepository groupMemberRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private EntityManager entityManager;

    private Group groupA;
    private Group groupB;
    private Member memberX;
    private GroupMember xInA;
    private GroupMember yInA;
    private GroupMember xInB;

    @BeforeEach
    void setUp() {
        groupA = saveGroup();
        groupB = saveGroup();
        memberX = memberRepository.save(Member.create("회원X", uniqueName("x"), "pass1234"));
        Member memberY = memberRepository.save(Member.create("회원Y", uniqueName("y"), "pass1234"));
        // 같은 회원 X가 방 A, B에 모두 들어가 있다
        xInA = groupMemberRepository.save(GroupMember.create(groupA, memberX, GroupMemberRole.MEMBER));
        yInA = groupMemberRepository.save(GroupMember.create(groupA, memberY, GroupMemberRole.MEMBER));
        xInB = groupMemberRepository.save(GroupMember.create(groupB, memberX, GroupMemberRole.MEMBER));
    }

    private String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private Group saveGroup() {
        return groupRepository.save(Group.builder()
            .title("아침 기상 챌린지")
            .penalty("커피 1잔 쏘기")
            .password("1234")
            .inviteCode(uniqueName("code"))
            .memberLimit(10)
            .build());
    }

    // 벌칙은 습관 하나에 하나씩 생기므로, 벌칙마다 습관을 새로 만든다
    private PenaltyVerify savePenalty(GroupMember groupMember, PenaltyVerifyStatus status) {
        Habit habit = habitRepository.save(Habit.create(groupMember, "습관-" + uniqueName("h"), "설명", 3));
        PenaltyVerify pv = PenaltyVerify.create(groupMember, habit);
        if (status != PenaltyVerifyStatus.REQUIRED) {
            pv.submit(LocalDate.of(2026, 10, 20), "벌칙 수행", "https://example.com/p.jpg");
        }
        if (status == PenaltyVerifyStatus.APPROVED) {
            pv.approve();
        } else if (status == PenaltyVerifyStatus.REJECTED) {
            pv.reject();
        }
        return penaltyVerifyRepository.save(pv);
    }

    // 1차 캐시를 비워 다음 조회가 실제 쿼리로 DB를 읽게 한다
    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    @Nested
    @DisplayName("검토 대기 목록(findPendingByGroupId)")
    class FindPendingByGroupId {

        @Test
        @DisplayName("그 방의 PENDING 벌칙만 반환한다 (순서는 정해져 있지 않아 검사하지 않음, #187)")
        void 방의_대기_벌칙만() {
            PenaltyVerify xPending = savePenalty(xInA, PenaltyVerifyStatus.PENDING);
            PenaltyVerify yPending = savePenalty(yInA, PenaltyVerifyStatus.PENDING);
            savePenalty(xInA, PenaltyVerifyStatus.APPROVED);
            savePenalty(xInA, PenaltyVerifyStatus.REJECTED);
            savePenalty(xInA, PenaltyVerifyStatus.REQUIRED);
            savePenalty(xInB, PenaltyVerifyStatus.PENDING);
            flushAndClear();

            List<PenaltyVerify> result = penaltyVerifyRepository.findPendingByGroupId(groupA.getId());

            assertThat(result).extracting(PenaltyVerify::getId)
                .containsExactlyInAnyOrder(xPending.getId(), yPending.getId());
        }

        @Test
        @DisplayName("대기 중인 벌칙이 없으면 빈 목록이다")
        void 대기_벌칙이_없으면_빈_목록() {
            savePenalty(xInA, PenaltyVerifyStatus.APPROVED);
            flushAndClear();

            assertThat(penaltyVerifyRepository.findPendingByGroupId(groupA.getId())).isEmpty();
        }
    }

    @Nested
    @DisplayName("일괄 처리 대상(findAllByIdInAndGroupId)")
    class FindAllByIdInAndGroupId {

        @Test
        @DisplayName("요청한 id 중 그 방의 벌칙만 반환한다 (다른 방 id, 없는 id는 제외)")
        void 방의_벌칙만_반환() {
            PenaltyVerify a1 = savePenalty(xInA, PenaltyVerifyStatus.PENDING);
            PenaltyVerify a2 = savePenalty(yInA, PenaltyVerifyStatus.PENDING);
            PenaltyVerify b = savePenalty(xInB, PenaltyVerifyStatus.PENDING);
            flushAndClear();

            List<PenaltyVerify> result = penaltyVerifyRepository.findAllByIdInAndGroupId(
                List.of(a1.getId(), a2.getId(), b.getId(), 999_999L), groupA.getId());

            assertThat(result).extracting(PenaltyVerify::getId).containsExactlyInAnyOrder(a1.getId(), a2.getId());
        }
    }

    @Nested
    @DisplayName("멤버별 벌칙 기록(findByGroupMemberIdOrderByIdDesc)")
    class FindByGroupMemberId {

        @Test
        @DisplayName("그 그룹멤버의 벌칙만 최신(id 큰 것)부터 반환한다")
        void 그_멤버의_벌칙만_최신순() {
            PenaltyVerify first = savePenalty(xInA, PenaltyVerifyStatus.APPROVED);
            PenaltyVerify second = savePenalty(xInA, PenaltyVerifyStatus.PENDING);
            PenaltyVerify third = savePenalty(xInA, PenaltyVerifyStatus.REQUIRED);
            savePenalty(yInA, PenaltyVerifyStatus.PENDING);
            savePenalty(xInB, PenaltyVerifyStatus.PENDING);
            flushAndClear();

            List<PenaltyVerify> result = penaltyVerifyRepository.findByGroupMemberIdOrderByIdDesc(xInA.getId());

            assertThat(result).extracting(PenaltyVerify::getId)
                .containsExactly(third.getId(), second.getId(), first.getId());
        }
    }

    @Nested
    @DisplayName("누적 벌칙 개수(countByGroupMember_Group_IdAndGroupMember_Member_Id)")
    class CountByGroupAndMember {

        @Test
        @DisplayName("그 방에서 그 회원의 벌칙만 센다 (같은 회원의 다른 방 벌칙, 같은 방 다른 회원 벌칙은 제외)")
        void 방과_회원으로_센다() {
            savePenalty(xInA, PenaltyVerifyStatus.REQUIRED);
            savePenalty(xInA, PenaltyVerifyStatus.APPROVED);
            savePenalty(xInB, PenaltyVerifyStatus.PENDING);
            savePenalty(yInA, PenaltyVerifyStatus.PENDING);
            flushAndClear();

            long count = penaltyVerifyRepository.countByGroupMember_Group_IdAndGroupMember_Member_Id(
                groupA.getId(), memberX.getId());

            assertThat(count).isEqualTo(2);
        }

        @Test
        @DisplayName("벌칙이 없으면 0이다")
        void 벌칙이_없으면_0() {
            assertThat(penaltyVerifyRepository.countByGroupMember_Group_IdAndGroupMember_Member_Id(
                groupB.getId(), memberX.getId())).isZero();
        }
    }
}
