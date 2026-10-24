package com.back.domain.penaltyverify

import com.back.domain.group.entity.Group
import com.back.domain.group.repository.GroupRepository
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.habit.entity.Habit
import com.back.domain.habit.repository.HabitRepository
import com.back.domain.member.entity.Member
import com.back.domain.member.repository.MemberRepository
import com.back.domain.penaltyverify.entity.PenaltyVerify
import com.back.domain.penaltyverify.repository.PenaltyVerifyRepository
import com.back.global.entity.requiredId
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.SessionFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest

// findById(Optional)를 findDetailById(nullable)로 바꾸면서 두 가지가 그대로인지 고정한다.
// 1) id로 정확히 한 건을 찾고, 없으면 null을 돌려준다.
// 2) @EntityGraph가 살아 있어서 벌칙 인증 + 멤버 + 그룹을 쿼리 한 번에 읽는다. (빠지면 접근할 때마다 쿼리가 더 나간다)
// 서비스 단위 테스트는 레포지토리를 목으로 바꾸기 때문에, 이 메서드의 실제 쿼리는 여기서만 확인된다.
@DataJpaTest
class PenaltyVerifyFindDetailByIdTest {

    @Autowired lateinit var penaltyVerifyRepository: PenaltyVerifyRepository
    @Autowired lateinit var habitRepository: HabitRepository
    @Autowired lateinit var groupRepository: GroupRepository
    @Autowired lateinit var groupMemberRepository: GroupMemberRepository
    @Autowired lateinit var memberRepository: MemberRepository
    @Autowired lateinit var entityManager: EntityManager

    private val statistics
        get() = entityManager.entityManagerFactory.unwrap(SessionFactory::class.java).statistics

    // 컨텍스트 설정을 바꾸면 다른 테스트와 Spring 컨텍스트(DB 커넥션 풀)를 공유하지 못하므로, 통계는 런타임에 켜고 끈다
    @BeforeEach
    fun enableStatistics() {
        statistics.isStatisticsEnabled = true
    }

    @AfterEach
    fun disableStatistics() {
        statistics.isStatisticsEnabled = false
    }

    @Test
    @DisplayName("id로 벌칙 인증을 찾는다")
    fun id로_찾는다() {
        val penaltyVerify = savePenaltyVerify("find-by-id")

        val found = penaltyVerifyRepository.findDetailById(penaltyVerify.requiredId)

        assertThat(found).isNotNull
        assertThat(found?.requiredId).isEqualTo(penaltyVerify.requiredId)
    }

    @Test
    @DisplayName("없는 id면 null을 돌려준다")
    fun 없는_id면_null() {
        assertThat(penaltyVerifyRepository.findDetailById(Long.MAX_VALUE)).isNull()
    }

    @Test
    @DisplayName("벌칙 인증 + 멤버 + 그룹을 쿼리 한 번에 읽는다")
    fun 연관_엔티티를_한_번에_읽는다() {
        val id = savePenaltyVerify("entity-graph").requiredId
        flushAndClear()

        statistics.clear()
        val found = requireNotNull(penaltyVerifyRepository.findDetailById(id))
        // 지연 로딩이었다면 여기서 쿼리가 더 나간다
        val nickname = found.groupMember.member.nickname
        val penalty = found.groupMember.group.penalty

        assertThat(nickname).isEqualTo("회원")
        assertThat(penalty).isEqualTo("커피 1잔 쏘기")
        assertThat(statistics.prepareStatementCount).isEqualTo(1)
    }

    // 위 테스트가 쿼리 수를 제대로 잡는지 확인한다: @EntityGraph가 없는 기본 findById는 접근할 때마다 쿼리가 더 나간다
    @Test
    @DisplayName("기본 findById는 같은 접근에서 쿼리가 더 나간다 (위 테스트가 의미 있다는 증거)")
    fun 기본_조회는_쿼리가_더_나간다() {
        val id = savePenaltyVerify("plain-find-by-id").requiredId
        flushAndClear()

        statistics.clear()
        val found = requireNotNull(penaltyVerifyRepository.findById(id).orElse(null))
        found.groupMember.member.nickname
        found.groupMember.group.penalty

        assertThat(statistics.prepareStatementCount).isGreaterThan(1)
    }

    private fun savePenaltyVerify(key: String): PenaltyVerify {
        val group = groupRepository.save(
            Group.builder()
                .title("아침 기상 챌린지")
                .penalty("커피 1잔 쏘기")
                .password("1234")
                .inviteCode(key.uppercase().take(10))
                .memberLimit(5)
                .build()
        )
        val member = memberRepository.save(Member.create("회원", key, "pass1234"))
        val groupMember = groupMemberRepository.save(GroupMember.create(group, member, GroupMemberRole.MEMBER))
        val habit = habitRepository.save(Habit.create(groupMember, "기상 후 스트레칭", "10분 스트레칭", 3))

        return penaltyVerifyRepository.save(PenaltyVerify.create(groupMember, habit))
    }

    // 영속성 컨텍스트(1차 캐시)를 비워야 다음 조회가 실제로 DB에서 읽는다
    private fun flushAndClear() {
        entityManager.flush()
        entityManager.clear()
    }
}
