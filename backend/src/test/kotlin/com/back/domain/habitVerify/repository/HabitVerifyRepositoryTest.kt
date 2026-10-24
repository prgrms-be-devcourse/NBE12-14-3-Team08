package com.back.domain.habitVerify.repository

import com.back.domain.group.entity.Group
import com.back.domain.group.repository.GroupRepository
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.habit.entity.Habit
import com.back.domain.habit.repository.HabitRepository
import com.back.domain.habitVerify.entity.HabitVerify
import com.back.domain.habitVerify.entity.HabitVerifyStatus
import com.back.domain.member.entity.Member
import com.back.domain.member.repository.MemberRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import java.time.LocalDate
import java.util.UUID

// Testcontainers PostgreSQL에서 HabitVerifyRepository의 조회 쿼리를 검증한다. (테스트마다 롤백)
// 배포 DB는 PostgreSQL이지만, 여기서 쓰는 WHERE / IN / ORDER BY는 두 DB에서 같게 동작한다.
@DataJpaTest
class HabitVerifyRepositoryTest {

    @Autowired private lateinit var habitVerifyRepository: HabitVerifyRepository
    @Autowired private lateinit var habitRepository: HabitRepository
    @Autowired private lateinit var groupRepository: GroupRepository
    @Autowired private lateinit var groupMemberRepository: GroupMemberRepository
    @Autowired private lateinit var memberRepository: MemberRepository
    @Autowired private lateinit var entityManager: EntityManager

    private lateinit var groupA: Group
    private lateinit var habitA1: Habit
    private lateinit var habitA2: Habit
    private lateinit var habitB: Habit

    @BeforeEach
    fun setUp() {
        groupA = saveGroup()
        val groupB = saveGroup()
        habitA1 = saveHabit(groupA, "기상 후 스트레칭")
        habitA2 = saveHabit(groupA, "물 2L 마시기")
        habitB = saveHabit(groupB, "독서 30분")
    }

    private fun uniqueName(prefix: String): String = prefix + "-" + UUID.randomUUID().toString().substring(0, 8)

    private fun saveGroup(): Group =
        groupRepository.save(
            Group.builder()
                .title("아침 기상 챌린지")
                .penalty("커피 1잔 쏘기")
                .password("1234")
                .inviteCode(uniqueName("code"))
                .memberLimit(10)
                .build()
        )

    private fun saveHabit(group: Group, title: String): Habit {
        val member = memberRepository.save(Member.create("회원", uniqueName("member"), "pass1234"))
        val groupMember = groupMemberRepository.save(GroupMember.create(group, member, GroupMemberRole.MEMBER))
        return habitRepository.save(Habit.create(groupMember, title, "설명", 3))
    }

    private fun saveVerify(habit: Habit, verifyDate: LocalDate, status: HabitVerifyStatus): HabitVerify {
        val habitVerify = HabitVerify.create(habit, verifyDate, "인증", "https://example.com/a.png")
        if (status == HabitVerifyStatus.APPROVED) {
            habitVerify.approve()
        } else if (status == HabitVerifyStatus.REJECTED) {
            habitVerify.reject()
        }
        return habitVerifyRepository.save(habitVerify)
    }

    // 1차 캐시를 비워 다음 조회가 실제 쿼리로 DB를 읽게 한다
    private fun flushAndClear() {
        entityManager.flush()
        entityManager.clear()
    }

    @Nested
    @DisplayName("검토 대기 목록(findPendingByGroupId)")
    inner class FindPendingByGroupId {

        @Test
        @DisplayName("그 그룹의 PENDING 인증만 인증 날짜 최신순으로 반환한다")
        fun 그룹의_대기_인증만_최신순() {
            val older = saveVerify(habitA1, LocalDate.of(2026, 10, 18), HabitVerifyStatus.PENDING)
            saveVerify(habitA1, LocalDate.of(2026, 10, 19), HabitVerifyStatus.APPROVED)
            saveVerify(habitA1, LocalDate.of(2026, 10, 20), HabitVerifyStatus.REJECTED)
            val newer = saveVerify(habitA2, LocalDate.of(2026, 10, 21), HabitVerifyStatus.PENDING)
            saveVerify(habitB, LocalDate.of(2026, 10, 21), HabitVerifyStatus.PENDING)
            flushAndClear()

            val result = habitVerifyRepository.findPendingByGroupId(requireNotNull(groupA.id))

            assertThat(result.map { it.id }).containsExactly(newer.id, older.id)
        }

        @Test
        @DisplayName("대기 중인 인증이 없으면 빈 목록이다")
        fun 대기_인증이_없으면_빈_목록() {
            saveVerify(habitA1, LocalDate.of(2026, 10, 19), HabitVerifyStatus.APPROVED)
            flushAndClear()

            assertThat(habitVerifyRepository.findPendingByGroupId(requireNotNull(groupA.id))).isEmpty()
        }
    }

    @Nested
    @DisplayName("일괄 처리 대상(findAllByIdInAndGroupId)")
    inner class FindAllByIdInAndGroupId {

        @Test
        @DisplayName("요청한 id 중 그 그룹의 인증만 반환한다 (다른 그룹 id, 없는 id는 제외)")
        fun 그룹의_인증만_반환() {
            val a1 = saveVerify(habitA1, LocalDate.of(2026, 10, 20), HabitVerifyStatus.PENDING)
            val a2 = saveVerify(habitA2, LocalDate.of(2026, 10, 20), HabitVerifyStatus.PENDING)
            val b = saveVerify(habitB, LocalDate.of(2026, 10, 20), HabitVerifyStatus.PENDING)
            flushAndClear()

            val result = habitVerifyRepository.findAllByIdInAndGroupId(
                listOf(requireNotNull(a1.id), requireNotNull(a2.id), requireNotNull(b.id), 999_999L),
                groupA.id,
            )

            assertThat(result.map { it.id }).containsExactlyInAnyOrder(a1.id, a2.id)
        }

        @Test
        @DisplayName("상태와 관계없이 가져온다 (처리된 인증이 섞였는지는 서비스가 승인하면서 확인)")
        fun 상태와_관계없이_반환() {
            val pending = saveVerify(habitA1, LocalDate.of(2026, 10, 20), HabitVerifyStatus.PENDING)
            val approved = saveVerify(habitA1, LocalDate.of(2026, 10, 21), HabitVerifyStatus.APPROVED)
            flushAndClear()

            val result = habitVerifyRepository.findAllByIdInAndGroupId(
                listOf(requireNotNull(pending.id), requireNotNull(approved.id)),
                groupA.id,
            )

            assertThat(result.map { it.id }).containsExactlyInAnyOrder(pending.id, approved.id)
        }
    }

    @Nested
    @DisplayName("습관별 인증 기록(findAllByHabitIdOrderByVerifyDateDesc)")
    inner class FindAllByHabitId {

        @Test
        @DisplayName("그 습관의 인증만 인증 날짜 최신순으로 반환한다")
        fun 습관의_인증만_최신순() {
            val day18 = saveVerify(habitA1, LocalDate.of(2026, 10, 18), HabitVerifyStatus.APPROVED)
            val day20 = saveVerify(habitA1, LocalDate.of(2026, 10, 20), HabitVerifyStatus.PENDING)
            val day19 = saveVerify(habitA1, LocalDate.of(2026, 10, 19), HabitVerifyStatus.REJECTED)
            saveVerify(habitA2, LocalDate.of(2026, 10, 21), HabitVerifyStatus.PENDING)
            flushAndClear()

            val result = habitVerifyRepository.findAllByHabitIdOrderByVerifyDateDesc(requireNotNull(habitA1.id))

            assertThat(result.map { it.id }).containsExactly(day20.id, day19.id, day18.id)
        }
    }

    @Nested
    @DisplayName("같은 날 인증 여부(existsByHabitIdAndVerifyDate)")
    inner class ExistsByHabitIdAndVerifyDate {

        private val oct22 = LocalDate.of(2026, 10, 22)

        @Test
        @DisplayName("그 습관에 그 날짜 인증이 있으면 true다 (같은 날 두 번째 인증을 막는 근거)")
        fun 같은_습관_같은_날짜_true() {
            saveVerify(habitA1, oct22, HabitVerifyStatus.PENDING)
            flushAndClear()

            assertThat(habitVerifyRepository.existsByHabitIdAndVerifyDate(requireNotNull(habitA1.id), oct22)).isTrue()
        }

        @Test
        @DisplayName("그 습관의 인증이 다른 날짜에만 있으면 false다 (어제 인증했어도 오늘 인증할 수 있다)")
        fun 같은_습관_다른_날짜_false() {
            saveVerify(habitA1, oct22.minusDays(1), HabitVerifyStatus.APPROVED)
            flushAndClear()

            assertThat(habitVerifyRepository.existsByHabitIdAndVerifyDate(requireNotNull(habitA1.id), oct22)).isFalse()
        }

        @Test
        @DisplayName("같은 방의 다른 멤버가 그 날짜에 인증했어도 내 습관 기준으로는 false다")
        fun 같은_방_다른_멤버_습관_같은_날짜_false() {
            saveVerify(habitA2, oct22, HabitVerifyStatus.PENDING)
            flushAndClear()

            assertThat(habitVerifyRepository.existsByHabitIdAndVerifyDate(requireNotNull(habitA1.id), oct22)).isFalse()
        }
    }
}
