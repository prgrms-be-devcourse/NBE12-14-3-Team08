package com.back.domain.habitVerify

import com.back.domain.group.entity.Group
import com.back.domain.group.repository.GroupRepository
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.groupMember.service.GroupMemberService
import com.back.domain.habit.entity.Habit
import com.back.domain.habit.repository.HabitRepository
import com.back.domain.habitVerify.entity.HabitVerify
import com.back.domain.habitVerify.entity.HabitVerifyComment
import com.back.domain.habitVerify.entity.HabitVerifyReaction
import com.back.domain.habitVerify.repository.HabitVerifyCommentRepository
import com.back.domain.habitVerify.repository.HabitVerifyReactionRepository
import com.back.domain.habitVerify.repository.HabitVerifyRepository
import com.back.domain.member.entity.Member
import com.back.domain.member.repository.MemberRepository
import com.back.global.entity.requiredId
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

// 반응·댓글은 인증과 멤버를 가리키는 FK에 ON DELETE CASCADE를 걸어 두었다. (docs/adr/0002-verify-reaction-comment-tables.md)
// 방 삭제, 진행 중인 방의 탈퇴·추방, 인증 삭제가 모두 직접 쿼리로 행을 지우므로, 각 경로에서 삭제가 막히지 않고
// 반응·댓글이 규칙대로 함께 지워지는지 실제 DB로 확인한다.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HabitVerifyEngagementCascadeTest @Autowired constructor(
    private val jdbc: JdbcTemplate,
    private val entityManager: EntityManager,
    private val memberRepository: MemberRepository,
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val habitRepository: HabitRepository,
    private val habitVerifyRepository: HabitVerifyRepository,
    private val reactionRepository: HabitVerifyReactionRepository,
    private val commentRepository: HabitVerifyCommentRepository,
    private val groupMemberService: GroupMemberService
) {

    private lateinit var group: Group
    private lateinit var owner: Member
    private lateinit var memberA: Member
    private lateinit var memberB: Member
    private lateinit var ownerRow: GroupMember
    private lateinit var rowA: GroupMember
    private lateinit var rowB: GroupMember
    private lateinit var habitA: Habit
    private lateinit var habitB: Habit
    private lateinit var verifyA: HabitVerify
    private lateinit var verifyB: HabitVerify

    // 방장 O, 멤버 A·B. 각자 승인된 인증이 하나씩 있고, 서로의 인증에 하트와 댓글을 남겼다.
    //   A의 인증: B의 하트, B의 댓글, 방장의 댓글
    //   B의 인증: A의 하트, A의 댓글, 방장의 댓글
    @BeforeEach
    fun setUp() {
        group = groupRepository.save(
            Group.builder().title("삭제 경로 방").penalty("커피").password("1234").inviteCode("CASCADE1").memberLimit(10).build(),
        )
        owner = memberRepository.save(Member.create("방장", "cas-owner", "pass1234"))
        memberA = memberRepository.save(Member.create("에이", "cas-a", "pass1234"))
        memberB = memberRepository.save(Member.create("비", "cas-b", "pass1234"))
        ownerRow = groupMemberRepository.save(GroupMember.create(group, owner, GroupMemberRole.OWNER))
        rowA = groupMemberRepository.save(GroupMember.create(group, memberA, GroupMemberRole.MEMBER))
        rowB = groupMemberRepository.save(GroupMember.create(group, memberB, GroupMemberRole.MEMBER))

        habitA = habitRepository.save(Habit.create(rowA, "습관 A", null, 3))
        habitB = habitRepository.save(Habit.create(rowB, "습관 B", null, 3))
        verifyA = approved(habitA)
        verifyB = approved(habitB)

        reactionRepository.save(HabitVerifyReaction.create(verifyA, rowB))
        reactionRepository.save(HabitVerifyReaction.create(verifyB, rowA))
        commentRepository.save(HabitVerifyComment.create(verifyA, rowB, "B가 A에게"))
        commentRepository.save(HabitVerifyComment.create(verifyA, ownerRow, "방장이 A에게"))
        commentRepository.save(HabitVerifyComment.create(verifyB, rowA, "A가 B에게"))
        commentRepository.save(HabitVerifyComment.create(verifyB, ownerRow, "방장이 B에게"))
        entityManager.flush()
    }

    private fun approved(habit: Habit): HabitVerify =
        habitVerifyRepository.save(HabitVerify.create(habit, LocalDate.now(), "설명", "https://example.com/p.png").also { it.approve() })

    private fun reactions(verify: HabitVerify): Long =
        requireNotNull(jdbc.queryForObject("select count(*) from habit_verify_reaction where habit_verify_id = ?", Long::class.java, verify.requiredId))

    private fun comments(verify: HabitVerify): Long =
        requireNotNull(jdbc.queryForObject("select count(*) from habit_verify_comment where habit_verify_id = ?", Long::class.java, verify.requiredId))

    private fun total(table: String): Long =
        requireNotNull(
            jdbc.queryForObject(
                "select count(*) from $table where habit_verify_id in (select hv.id from habit_verify hv join habit h on h.id = hv.habit_id join group_member gm on gm.id = h.group_member_id where gm.group_id = ?)",
                Long::class.java,
                group.requiredId,
            ),
        )

    @Test
    @DisplayName("준비한 데이터: 인증마다 하트 1개와 댓글 2개가 있다")
    fun 준비한_데이터() {
        assertThat(reactions(verifyA)).isEqualTo(1)
        assertThat(comments(verifyA)).isEqualTo(2)
        assertThat(reactions(verifyB)).isEqualTo(1)
        assertThat(comments(verifyB)).isEqualTo(2)
    }

    @Test
    @DisplayName("두 테이블의 FK 네 개가 모두 ON DELETE CASCADE로 만들어져 있다")
    fun FK는_모두_CASCADE() {
        val rules = jdbc.queryForList(
            """
            select tc.table_name || '.' || rc.constraint_name as fk, rc.delete_rule
            from information_schema.referential_constraints rc
            join information_schema.table_constraints tc on tc.constraint_name = rc.constraint_name
            where tc.table_name in ('habit_verify_reaction', 'habit_verify_comment')
            """.trimIndent(),
        )

        assertThat(rules).hasSize(4)
        assertThat(rules.map { it["delete_rule"] }).containsOnly("CASCADE")
    }

    @Test
    @DisplayName("인증을 지우면 그 인증의 하트·댓글만 함께 지워진다")
    fun 인증_삭제() {
        habitVerifyRepository.deleteByHabitIds(listOf(habitA.requiredId))

        assertThat(reactions(verifyA)).isZero()
        assertThat(comments(verifyA)).isZero()
        assertThat(reactions(verifyB)).isEqualTo(1)
        assertThat(comments(verifyB)).isEqualTo(2)
    }

    @Test
    @DisplayName("진행 중인 방에서 멤버가 탈퇴하면, 그 멤버의 인증에 달린 하트·댓글과 그 멤버가 남의 인증에 남긴 하트·댓글이 모두 지워진다")
    fun 진행_중인_방의_탈퇴() {
        groupMemberService.leaveGroup(group.requiredId, memberB.requiredId)
        // 멤버 행 삭제는 저장 시점(flush)에 실행되므로, 실제 서비스에서는 커밋 때 일어나는 것을 여기서 앞당긴다
        entityManager.flush()

        // B의 인증(과 거기 달린 A·방장의 글)이 사라졌다
        assertThat(habitVerifyRepository.findById(verifyB.requiredId)).isEmpty
        assertThat(reactions(verifyB)).isZero()
        assertThat(comments(verifyB)).isZero()
        // A의 인증에서는 B가 남긴 하트·댓글만 사라지고 방장의 댓글은 남는다
        assertThat(reactions(verifyA)).isZero()
        assertThat(comments(verifyA)).isEqualTo(1)
    }

    @Test
    @DisplayName("방장이 멤버를 추방해도 같은 규칙으로 지워진다")
    fun 추방() {
        groupMemberService.kickMember(group.requiredId, rowB.requiredId, owner.requiredId)
        entityManager.flush()

        assertThat(habitVerifyRepository.findById(verifyB.requiredId)).isEmpty
        assertThat(reactions(verifyA)).isZero()
        assertThat(comments(verifyA)).isEqualTo(1)
        assertThat(commentRepository.findAll().map { it.content }).doesNotContain("B가 A에게", "A가 B에게", "방장이 B에게")
    }

    @Test
    @DisplayName("방을 지우면 모든 하트·댓글과 방이 막힘 없이 지워진다")
    fun 방_삭제() {
        groupMemberService.deleteGroupAndAllMembers(group)

        assertThat(groupRepository.findById(group.requiredId)).isEmpty
        assertThat(jdbc.queryForObject("select count(*) from habit_verify_reaction", Long::class.java)).isZero()
        assertThat(jdbc.queryForObject("select count(*) from habit_verify_comment", Long::class.java)).isZero()
    }

    @Test
    @DisplayName("종료된 방에서 탈퇴하면 멤버 기록이 남으므로 인증과 하트·댓글도 그대로 남는다")
    fun 종료된_방의_탈퇴() {
        group.finish()
        groupRepository.save(group)

        groupMemberService.leaveGroup(group.requiredId, memberB.requiredId)

        assertThat(total("habit_verify_reaction")).isEqualTo(2)
        assertThat(total("habit_verify_comment")).isEqualTo(4)
        assertThat(habitVerifyRepository.findById(verifyB.requiredId)).isPresent
    }
}
