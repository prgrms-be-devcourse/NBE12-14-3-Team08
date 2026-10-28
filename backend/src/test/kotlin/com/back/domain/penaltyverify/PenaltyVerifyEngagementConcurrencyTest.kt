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
import com.back.domain.penaltyverify.repository.PenaltyVerifyCommentRepository
import com.back.domain.penaltyverify.repository.PenaltyVerifyReactionRepository
import com.back.domain.penaltyverify.repository.PenaltyVerifyRepository
import com.back.domain.penaltyverify.service.PenaltyVerifyCommentService
import com.back.domain.penaltyverify.service.PenaltyVerifyReactionService
import com.back.domain.penaltyverify.service.PenaltyVerifyResetService
import com.back.domain.verifyEngagement.dto.CommentRequest
import com.back.global.entity.requiredId
import com.back.global.exception.EntityNotFoundException
import com.back.support.ConcurrentRunner
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.util.UUID

// 벌칙 인증의 하트·댓글 동시 요청 (docs/adr/0002-verify-reaction-comment-tables.md)
// 실제로 커밋되는 트랜잭션이 필요해서 @Transactional 없이 실행하고, 끝나면 만든 데이터를 직접 지운다.
@SpringBootTest
@ActiveProfiles("test")
class PenaltyVerifyEngagementConcurrencyTest @Autowired constructor(
    private val reactionService: PenaltyVerifyReactionService,
    private val commentService: PenaltyVerifyCommentService,
    private val resetService: PenaltyVerifyResetService,
    private val reactionRepository: PenaltyVerifyReactionRepository,
    private val commentRepository: PenaltyVerifyCommentRepository,
    private val memberRepository: MemberRepository,
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val habitRepository: HabitRepository,
    private val penaltyVerifyRepository: PenaltyVerifyRepository,
    private val jdbc: JdbcTemplate
) {

    private var groupId: Long = 0
    private var ownerMemberId: Long = 0
    private var penaltyId: Long = 0
    private val memberIds = mutableListOf<Long>()
    private val reactorIds = mutableListOf<Long>()

    @BeforeEach
    fun setUp() {
        val suffix = UUID.randomUUID().toString().substring(0, 8)
        val group = groupRepository.save(
            Group.builder().title("벌칙 동시성 $suffix").penalty("커피").password("1234").inviteCode("P$suffix").memberLimit(20).build(),
        )
        groupId = group.requiredId

        val author = memberRepository.save(Member.create("작성자", "pc-author-$suffix", "pass1234"))
        memberIds += author.requiredId
        ownerMemberId = author.requiredId
        val authorRow = groupMemberRepository.save(GroupMember.create(group, author, GroupMemberRole.OWNER))
        val habit = habitRepository.save(Habit.create(authorRow, "습관", null, 3))
        val penalty = PenaltyVerify.create(authorRow, habit)
        penalty.submit(LocalDate.now(), "벌칙 수행", "https://example.com/p.png")
        penalty.approve()
        penaltyId = penaltyVerifyRepository.save(penalty).requiredId

        repeat(REACTORS) { i ->
            val member = memberRepository.save(Member.create("멤버$i", "pc-$i-$suffix", "pass1234"))
            memberIds += member.requiredId
            reactorIds += member.requiredId
            groupMemberRepository.save(GroupMember.create(group, member, GroupMemberRole.MEMBER))
        }
    }

    @AfterEach
    fun tearDown() {
        // 벌칙 인증을 지우면 하트·댓글은 DB가 함께 지운다
        jdbc.update("delete from penalty_verify where id = ?", penaltyId)
        val gmIds = "select id from group_member where group_id = ?"
        jdbc.update("delete from habit where group_member_id in ($gmIds)", groupId)
        jdbc.update("delete from group_member where group_id = ?", groupId)
        jdbc.update("delete from groups where id = ?", groupId)
        memberIds.forEach { jdbc.update("delete from member where id = ?", it) }
    }

    @Test
    @DisplayName("같은 멤버가 동시에 여러 번 눌러도 모두 성공하고 하트는 하나만 남는다")
    fun 같은_멤버의_동시_누르기() {
        val actor = reactorIds.first()

        val errors = ConcurrentRunner.runAll(List(CONCURRENT_CALLS) { { reactionService.react(penaltyId, actor); Unit } })

        assertThat(errors).containsOnlyNulls()
        assertThat(reactionRepository.countByPenaltyVerifyId(penaltyId)).isEqualTo(1)
    }

    @Test
    @DisplayName("서로 다른 멤버가 동시에 누르면 멤버 수만큼 하트가 남는다")
    fun 서로_다른_멤버의_동시_누르기() {
        val errors = ConcurrentRunner.runAll(reactorIds.map { id -> { reactionService.react(penaltyId, id); Unit } })

        assertThat(errors).containsOnlyNulls()
        assertThat(reactionRepository.countByPenaltyVerifyId(penaltyId)).isEqualTo(REACTORS.toLong())
    }

    @Test
    @DisplayName("여러 멤버가 동시에 댓글을 써도 오류 없이 쓴 만큼 저장된다")
    fun 동시_댓글_작성() {
        val errors = ConcurrentRunner.runAll(
            reactorIds.map { id -> { commentService.create(penaltyId, id, CommentRequest("동시 댓글 $id")); Unit } },
        )

        assertThat(errors).containsOnlyNulls()
        assertThat(commentRepository.countByPenaltyVerifyId(penaltyId)).isEqualTo(REACTORS.toLong())
    }

    @Test
    @DisplayName("벌칙 인증이 지워지는 순간 하트·댓글이 겹쳐도 남는 글이 없고, 실패하더라도 '없는 벌칙' 또는 제약 위반뿐이다")
    fun 벌칙_삭제와_하트_댓글이_겹친다() {
        val tasks = mutableListOf<() -> Unit>()
        reactorIds.forEach { id ->
            tasks += { reactionService.react(penaltyId, id); Unit }
            tasks += { commentService.create(penaltyId, id, CommentRequest("삭제와 겹친 댓글")); Unit }
        }
        tasks += { jdbc.update("delete from penalty_verify where id = ?", penaltyId); Unit }

        val errors = ConcurrentRunner.runAll(tasks)

        assertThat(jdbc.queryForObject("select count(*) from penalty_verify_reaction where penalty_verify_id = ?", Long::class.java, penaltyId)).isZero()
        assertThat(jdbc.queryForObject("select count(*) from penalty_verify_comment where penalty_verify_id = ?", Long::class.java, penaltyId)).isZero()
        assertThat(errors.filterNotNull()).allSatisfy {
            assertThat(it).isInstanceOfAny(EntityNotFoundException::class.java, DataIntegrityViolationException::class.java)
        }
    }

    @Test
    @DisplayName("방장이 초기화하는 순간 하트·댓글이 겹쳐도, 끝났을 때 미제출 상태인 벌칙에는 하트·댓글이 남지 않는다")
    fun 초기화와_하트_댓글이_겹친다() {
        val tasks = mutableListOf<() -> Unit>()
        reactorIds.forEach { id ->
            tasks += { reactionService.react(penaltyId, id); Unit }
            tasks += { commentService.create(penaltyId, id, CommentRequest("초기화와 겹친 댓글")); Unit }
        }
        tasks += { resetService.cancelOrReset(ownerMemberId, penaltyId); Unit }

        ConcurrentRunner.runAll(tasks)

        val status = jdbc.queryForObject("select status from penalty_verify where id = ?", String::class.java, penaltyId)
        assertThat(status).isEqualTo("REQUIRED")
        assertThat(reactionRepository.countByPenaltyVerifyId(penaltyId)).isZero()
        assertThat(commentRepository.countByPenaltyVerifyId(penaltyId)).isZero()
    }

    companion object {
        private const val REACTORS = 8
        private const val CONCURRENT_CALLS = 10
    }
}
