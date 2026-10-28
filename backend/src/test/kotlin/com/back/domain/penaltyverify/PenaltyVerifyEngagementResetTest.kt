package com.back.domain.penaltyverify

import com.back.domain.group.entity.Group
import com.back.domain.group.repository.GroupRepository
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.groupMember.service.GroupMemberService
import com.back.domain.habit.entity.Habit
import com.back.domain.habit.repository.HabitRepository
import com.back.domain.member.entity.Member
import com.back.domain.member.repository.MemberRepository
import com.back.domain.member.service.AuthTokenService
import com.back.domain.penaltyverify.entity.PenaltyVerify
import com.back.domain.penaltyverify.entity.PenaltyVerifyComment
import com.back.domain.penaltyverify.entity.PenaltyVerifyReaction
import com.back.domain.penaltyverify.entity.PenaltyVerifyStatus
import com.back.domain.penaltyverify.repository.PenaltyVerifyCommentRepository
import com.back.domain.penaltyverify.repository.PenaltyVerifyReactionRepository
import com.back.domain.penaltyverify.repository.PenaltyVerifyRepository
import com.back.global.entity.requiredId
import com.back.global.storage.StorageService
import com.ninjasquad.springmockk.MockkBean
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.patch
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

// 벌칙 인증은 지우지 않고 REQUIRED로 되돌리므로(ADR 0001), 되돌릴 때 하트·댓글을 함께 지우는지와
// 방 삭제·탈퇴·추방에서 FK CASCADE가 삭제를 막지 않는지를 실제 DB로 확인한다. (ADR 0002)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PenaltyVerifyEngagementResetTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val jdbc: JdbcTemplate,
    private val entityManager: EntityManager,
    private val authTokenService: AuthTokenService,
    private val memberRepository: MemberRepository,
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val habitRepository: HabitRepository,
    private val penaltyVerifyRepository: PenaltyVerifyRepository,
    private val reactionRepository: PenaltyVerifyReactionRepository,
    private val commentRepository: PenaltyVerifyCommentRepository,
    private val groupMemberService: GroupMemberService
) {

    @MockkBean(relaxUnitFun = true) private lateinit var storageService: StorageService

    private lateinit var group: Group
    private lateinit var owner: Member
    private lateinit var memberA: Member
    private lateinit var memberB: Member
    private lateinit var ownerRow: GroupMember
    private lateinit var rowA: GroupMember
    private lateinit var rowB: GroupMember
    private lateinit var penaltyA: PenaltyVerify
    private lateinit var penaltyB: PenaltyVerify

    // 방장 O, 멤버 A·B. 각자 승인된 벌칙이 하나씩 있고, 서로의 벌칙에 하트와 댓글을 남겼다.
    @BeforeEach
    fun setUp() {
        group = groupRepository.save(
            Group.builder().title("벌칙 정리 방").penalty("커피").password("1234").inviteCode("PRESET01").memberLimit(10).build(),
        )
        owner = memberRepository.save(Member.create("방장", "prs-owner", "pass1234"))
        memberA = memberRepository.save(Member.create("에이", "prs-a", "pass1234"))
        memberB = memberRepository.save(Member.create("비", "prs-b", "pass1234"))
        ownerRow = groupMemberRepository.save(GroupMember.create(group, owner, GroupMemberRole.OWNER))
        rowA = groupMemberRepository.save(GroupMember.create(group, memberA, GroupMemberRole.MEMBER))
        rowB = groupMemberRepository.save(GroupMember.create(group, memberB, GroupMemberRole.MEMBER))

        penaltyA = approvedPenalty(rowA, "습관 A")
        penaltyB = approvedPenalty(rowB, "습관 B")

        reactionRepository.save(PenaltyVerifyReaction.create(penaltyA, rowB))
        reactionRepository.save(PenaltyVerifyReaction.create(penaltyB, rowA))
        commentRepository.save(PenaltyVerifyComment.create(penaltyA, rowB, "B가 A에게"))
        commentRepository.save(PenaltyVerifyComment.create(penaltyA, ownerRow, "방장이 A에게"))
        commentRepository.save(PenaltyVerifyComment.create(penaltyB, rowA, "A가 B에게"))
        entityManager.flush()
    }

    private fun approvedPenalty(row: GroupMember, title: String): PenaltyVerify {
        val habit = habitRepository.save(Habit.create(row, title, null, 3))
        val penalty = PenaltyVerify.create(row, habit)
        penalty.submit(LocalDate.now(), "벌칙 수행", "https://example.com/p.png")
        penalty.approve()
        return penaltyVerifyRepository.save(penalty)
    }

    private fun token(member: Member) = "Bearer " + authTokenService.createAccessToken(member.requiredId)

    private fun count(table: String, column: String, id: Long): Long =
        requireNotNull(jdbc.queryForObject("select count(*) from $table where $column = ?", Long::class.java, id))

    private fun reactions(penalty: PenaltyVerify) = count("penalty_verify_reaction", "penalty_verify_id", penalty.requiredId)
    private fun comments(penalty: PenaltyVerify) = count("penalty_verify_comment", "penalty_verify_id", penalty.requiredId)

    private fun cancel(member: Member, penalty: PenaltyVerify) =
        mockMvc.patch("/api/penalties/${penalty.requiredId}/cancel") { header("Authorization", token(member)) }

    private fun status(penalty: PenaltyVerify): PenaltyVerifyStatus {
        entityManager.flush()
        entityManager.clear()
        return penaltyVerifyRepository.findById(penalty.requiredId).get().status
    }

    @Test
    @DisplayName("두 테이블의 FK 네 개가 모두 ON DELETE CASCADE로 만들어져 있다")
    fun FK는_모두_CASCADE() {
        val rules = jdbc.queryForList(
            """
            select rc.delete_rule
            from information_schema.referential_constraints rc
            join information_schema.table_constraints tc on tc.constraint_name = rc.constraint_name
            where tc.table_name in ('penalty_verify_reaction', 'penalty_verify_comment')
            """.trimIndent(),
        )

        assertThat(rules).hasSize(4)
        assertThat(rules.map { it["delete_rule"] }).containsOnly("CASCADE")
    }

    @Test
    @DisplayName("방장이 승인된 벌칙을 초기화하면 REQUIRED로 돌아가고 그 벌칙의 하트·댓글이 모두 지워진다")
    fun 방장_초기화() {
        cancel(owner, penaltyA).andReturn().response.also { assertThat(it.status).isEqualTo(200) }

        assertThat(status(penaltyA)).isEqualTo(PenaltyVerifyStatus.REQUIRED)
        assertThat(reactions(penaltyA)).isZero()
        assertThat(comments(penaltyA)).isZero()
    }

    @Test
    @DisplayName("다른 벌칙의 하트·댓글은 그대로 남는다")
    fun 다른_벌칙은_유지() {
        cancel(owner, penaltyA)

        assertThat(reactions(penaltyB)).isEqualTo(1)
        assertThat(comments(penaltyB)).isEqualTo(1)
    }

    @Test
    @DisplayName("방장이 본인 벌칙을 초기화해도 같은 규칙으로 지워진다")
    fun 방장_본인_벌칙() {
        val ownerPenalty = approvedPenalty(ownerRow, "방장 습관")
        reactionRepository.save(PenaltyVerifyReaction.create(ownerPenalty, rowA))
        commentRepository.save(PenaltyVerifyComment.create(ownerPenalty, rowB, "방장 수고"))
        entityManager.flush()

        cancel(owner, ownerPenalty)

        assertThat(reactions(ownerPenalty)).isZero()
        assertThat(comments(ownerPenalty)).isZero()
    }

    @Test
    @DisplayName("권한이 없어 초기화가 거절되면 상태도 하트·댓글도 그대로다")
    fun 거절되면_유지() {
        cancel(memberB, penaltyA).andReturn().response.also { assertThat(it.status).isEqualTo(403) }

        assertThat(status(penaltyA)).isEqualTo(PenaltyVerifyStatus.APPROVED)
        assertThat(reactions(penaltyA)).isEqualTo(1)
        assertThat(comments(penaltyA)).isEqualTo(2)
    }

    @Test
    @DisplayName("작성자가 승인 전 제출을 취소하면 문제없이 REQUIRED로 돌아간다")
    fun 작성자_제출_취소() {
        val habit = habitRepository.save(Habit.create(rowA, "대기 습관", null, 3))
        val pending = PenaltyVerify.create(rowA, habit).also { it.submit(LocalDate.now(), "설명", "https://example.com/a.png") }
        penaltyVerifyRepository.save(pending)

        cancel(memberA, pending).andReturn().response.also { assertThat(it.status).isEqualTo(200) }

        assertThat(status(pending)).isEqualTo(PenaltyVerifyStatus.REQUIRED)
    }

    @Test
    @DisplayName("작성자는 승인된 벌칙을 취소할 수 없고(409), 이때도 하트·댓글은 그대로다")
    fun 작성자_승인후_취소_거절() {
        cancel(memberA, penaltyA).andReturn().response.also { assertThat(it.status).isEqualTo(409) }

        assertThat(reactions(penaltyA)).isEqualTo(1)
        assertThat(comments(penaltyA)).isEqualTo(2)
    }

    @Test
    @DisplayName("종료된 방에서는 초기화가 거절되고 하트·댓글이 남는다")
    fun 종료된_방의_초기화() {
        group.finish()
        groupRepository.save(group)

        cancel(owner, penaltyA).andReturn().response.also { assertThat(it.status).isEqualTo(400) }

        assertThat(reactions(penaltyA)).isEqualTo(1)
        assertThat(comments(penaltyA)).isEqualTo(2)
    }

    @Test
    @DisplayName("진행 중인 방에서 멤버가 탈퇴하면 그 멤버의 벌칙에 달린 글과 그 멤버가 남긴 하트·댓글이 지워진다")
    fun 진행_중인_방의_탈퇴() {
        groupMemberService.leaveGroup(group.requiredId, memberB.requiredId)
        entityManager.flush()

        assertThat(penaltyVerifyRepository.findById(penaltyB.requiredId)).isEmpty
        assertThat(reactions(penaltyB)).isZero()
        assertThat(comments(penaltyB)).isZero()
        // A의 벌칙에서는 B가 남긴 것만 사라진다
        assertThat(reactions(penaltyA)).isZero()
        assertThat(comments(penaltyA)).isEqualTo(1)
    }

    @Test
    @DisplayName("방장이 멤버를 추방해도 같은 규칙으로 지워진다")
    fun 추방() {
        groupMemberService.kickMember(group.requiredId, rowB.requiredId, owner.requiredId)
        entityManager.flush()

        assertThat(penaltyVerifyRepository.findById(penaltyB.requiredId)).isEmpty
        assertThat(reactions(penaltyA)).isZero()
        assertThat(comments(penaltyA)).isEqualTo(1)
    }

    @Test
    @DisplayName("방을 지우면 모든 벌칙의 하트·댓글과 방이 막힘 없이 지워진다")
    fun 방_삭제() {
        groupMemberService.deleteGroupAndAllMembers(group)

        assertThat(groupRepository.findById(group.requiredId)).isEmpty
        assertThat(jdbc.queryForObject("select count(*) from penalty_verify_reaction", Long::class.java)).isZero()
        assertThat(jdbc.queryForObject("select count(*) from penalty_verify_comment", Long::class.java)).isZero()
    }

    @Test
    @DisplayName("종료된 방에서 탈퇴하면 멤버 기록이 남으므로 벌칙과 하트·댓글도 그대로 남는다")
    fun 종료된_방의_탈퇴() {
        group.finish()
        groupRepository.save(group)

        groupMemberService.leaveGroup(group.requiredId, memberB.requiredId)

        assertThat(reactions(penaltyB)).isEqualTo(1)
        assertThat(comments(penaltyB)).isEqualTo(1)
        assertThat(penaltyVerifyRepository.findById(penaltyB.requiredId)).isPresent
    }
}
