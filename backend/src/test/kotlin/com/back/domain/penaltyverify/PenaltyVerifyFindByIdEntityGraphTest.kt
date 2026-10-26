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
import org.springframework.data.repository.findByIdOrNull
import kotlin.jvm.optionals.getOrNull

// PenaltyVerifyService는 @EntityGraph를 붙여 재정의한 findById(id).getOrNull()로 벌칙 인증을 찾는다. 세 가지를 고정한다.
// 1) id로 정확히 한 건을 찾고, 없으면 null을 돌려준다.
// 2) @EntityGraph가 살아 있어서 벌칙 인증 + 멤버 + 그룹을 쿼리 한 번에 읽는다.
// 3) Spring Data의 findByIdOrNull(id)은 일반 findById를 불러 @EntityGraph가 빠진다 → 서비스가 이걸 쓰면 안 되는 이유.
//    (언젠가 Spring이 바뀌어 이 테스트가 실패하면 findByIdOrNull로 바꿔도 된다는 신호)
// 서비스 단위 테스트는 레포지토리를 목으로 바꾸기 때문에, 실제 쿼리는 여기서만 확인된다.
@DataJpaTest
class PenaltyVerifyFindByIdEntityGraphTest {

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

        val found = penaltyVerifyRepository.findById(penaltyVerify.requiredId).getOrNull()

        assertThat(found).isNotNull
        assertThat(found?.requiredId).isEqualTo(penaltyVerify.requiredId)
    }

    @Test
    @DisplayName("없는 id면 null을 돌려준다")
    fun 없는_id면_null() {
        assertThat(penaltyVerifyRepository.findById(Long.MAX_VALUE).getOrNull()).isNull()
    }

    @Test
    @DisplayName("벌칙 인증 + 멤버 + 그룹을 쿼리 한 번에 읽는다")
    fun 연관_엔티티를_한_번에_읽는다() {
        val id = savePenaltyVerify("entity-graph").requiredId
        flushAndClear()

        statistics.clear()
        val found = requireNotNull(penaltyVerifyRepository.findById(id).getOrNull())
        // 지연 로딩이었다면 여기서 쿼리가 더 나간다
        val nickname = found.groupMember.member.nickname
        val penalty = found.groupMember.group.penalty

        assertThat(nickname).isEqualTo("회원")
        assertThat(penalty).isEqualTo("커피 1잔 쏘기")
        assertThat(statistics.prepareStatementCount).isEqualTo(1)
    }

    // 서비스가 findByIdOrNull을 쓰지 않는 이유를 고정한다. 위 테스트가 쿼리 수를 제대로 센다는 증거도 된다.
    @Test
    @DisplayName("findByIdOrNull은 재정의한 findById를 거치지 않아 @EntityGraph가 빠지고 쿼리가 더 나간다")
    fun findByIdOrNull은_EntityGraph가_빠진다() {
        val id = savePenaltyVerify("plain-find-by-id").requiredId
        flushAndClear()

        statistics.clear()
        val found = requireNotNull(penaltyVerifyRepository.findByIdOrNull(id))
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
