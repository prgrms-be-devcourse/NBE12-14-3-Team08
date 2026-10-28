package com.back.domain.habitVerify

import com.back.domain.group.entity.Group
import com.back.domain.group.repository.GroupRepository
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.habit.entity.Habit
import com.back.domain.habit.repository.HabitRepository
import com.back.domain.habitVerify.entity.HabitVerify
import com.back.domain.habitVerify.repository.HabitVerifyCommentRepository
import com.back.domain.habitVerify.repository.HabitVerifyReactionRepository
import com.back.domain.habitVerify.repository.HabitVerifyRepository
import com.back.domain.habitVerify.service.HabitVerifyCommentService
import com.back.domain.habitVerify.service.HabitVerifyReactionService
import com.back.domain.member.entity.Member
import com.back.domain.member.repository.MemberRepository
import com.back.domain.verifyEngagement.dto.CommentRequest
import com.back.global.exception.EntityNotFoundException
import com.back.global.entity.requiredId
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

// 같은 사람이 하트를 동시에 눌러도(더블클릭, 두 기기) 오류 없이 하나만 남아야 한다. (docs/adr/0002-verify-reaction-comment-tables.md)
// "있는지 확인한 뒤 저장"이면 두 요청이 모두 없다고 보고 저장하다 유니크 제약 오류가 나므로, 저장을 DB에 맡긴 것을 확인한다.
// 실제로 커밋되는 트랜잭션이 필요해서 @Transactional 없이 실행하고, 끝나면 만든 데이터를 직접 지운다.
@SpringBootTest
@ActiveProfiles("test")
class HabitVerifyReactionConcurrencyTest @Autowired constructor(
    private val reactionService: HabitVerifyReactionService,
    private val commentService: HabitVerifyCommentService,
    private val reactionRepository: HabitVerifyReactionRepository,
    private val commentRepository: HabitVerifyCommentRepository,
    private val memberRepository: MemberRepository,
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val habitRepository: HabitRepository,
    private val habitVerifyRepository: HabitVerifyRepository,
    private val jdbc: JdbcTemplate
) {

    private var groupId: Long = 0
    private var verifyId: Long = 0
    private val memberIds = mutableListOf<Long>()
    private val reactorIds = mutableListOf<Long>()

    @BeforeEach
    fun setUp() {
        val suffix = UUID.randomUUID().toString().substring(0, 8)
        val group = groupRepository.save(
            Group.builder().title("하트 동시성 $suffix").penalty("커피").password("1234").inviteCode("R$suffix").memberLimit(20).build(),
        )
        groupId = group.requiredId

        val author = memberRepository.save(Member.create("작성자", "rx-author-$suffix", "pass1234"))
        memberIds += author.requiredId
        val authorRow = groupMemberRepository.save(GroupMember.create(group, author, GroupMemberRole.OWNER))
        val habit = habitRepository.save(Habit.create(authorRow, "습관", null, 3))
        verifyId = habitVerifyRepository.save(
            HabitVerify.create(habit, LocalDate.now(), "설명", "https://example.com/p.png").also { it.approve() },
        ).requiredId

        repeat(REACTORS) { i ->
            val member = memberRepository.save(Member.create("멤버$i", "rx-$i-$suffix", "pass1234"))
            memberIds += member.requiredId
            reactorIds += member.requiredId
            groupMemberRepository.save(GroupMember.create(group, member, GroupMemberRole.MEMBER))
        }
    }

    @AfterEach
    fun tearDown() {
        // 인증을 지우면 하트·댓글은 DB가 함께 지운다
        jdbc.update("delete from habit_verify where id = ?", verifyId)
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

        val errors = ConcurrentRunner.runAll(List(CONCURRENT_CALLS) { { reactionService.react(verifyId, actor) } })

        assertThat(errors).containsOnlyNulls()
        assertThat(reactionRepository.countByHabitVerifyId(verifyId)).isEqualTo(1)
    }

    @Test
    @DisplayName("서로 다른 멤버가 동시에 누르면 멤버 수만큼 하트가 남는다")
    fun 서로_다른_멤버의_동시_누르기() {
        val errors = ConcurrentRunner.runAll(reactorIds.map { id -> { reactionService.react(verifyId, id) } })

        assertThat(errors).containsOnlyNulls()
        assertThat(reactionRepository.countByHabitVerifyId(verifyId)).isEqualTo(REACTORS.toLong())
    }

    @Test
    @DisplayName("누르기와 취소를 동시에 섞어 보내도 오류가 없고 하트는 0개 또는 1개다")
    fun 누르기와_취소가_섞여도_오류가_없다() {
        val actor = reactorIds.first()
        val tasks: List<() -> Unit> = List(CONCURRENT_CALLS) { i ->
            if (i % 2 == 0) {
                { reactionService.react(verifyId, actor) }
            } else {
                { reactionService.cancel(verifyId, actor) }
            }
        }

        val errors = ConcurrentRunner.runAll(tasks)

        assertThat(errors).containsOnlyNulls()
        assertThat(reactionRepository.countByHabitVerifyId(verifyId)).isBetween(0, 1)
    }

    @Test
    @DisplayName("여러 멤버가 동시에 댓글을 써도 오류 없이 쓴 만큼 저장된다")
    fun 동시_댓글_작성() {
        val errors = ConcurrentRunner.runAll(
            reactorIds.map { id -> { commentService.create(verifyId, id, CommentRequest("동시 댓글 $id")) } },
        )

        assertThat(errors).containsOnlyNulls()
        assertThat(commentRepository.countByHabitVerifyId(verifyId)).isEqualTo(REACTORS.toLong())
    }

    @Test
    @DisplayName("같은 멤버가 동시에 댓글을 여러 개 써도 쓴 만큼 저장된다 (댓글은 하트와 달리 중복을 막지 않는다)")
    fun 같은_멤버의_동시_댓글_작성() {
        val actor = reactorIds.first()

        val errors = ConcurrentRunner.runAll(List(CONCURRENT_CALLS) { i -> { commentService.create(verifyId, actor, CommentRequest("연속 댓글 $i")) } })

        assertThat(errors).containsOnlyNulls()
        assertThat(commentRepository.countByHabitVerifyId(verifyId)).isEqualTo(CONCURRENT_CALLS.toLong())
    }

    @Test
    @DisplayName("인증이 지워지는 순간 하트·댓글이 달려도 지워진 인증에 매달린 글이 남지 않고, 실패하더라도 '없는 인증' 또는 제약 위반뿐이다")
    fun 인증_삭제와_하트_댓글이_겹친다() {
        val tasks = mutableListOf<() -> Unit>()
        reactorIds.forEach { id ->
            tasks += { reactionService.react(verifyId, id); Unit }
            tasks += { commentService.create(verifyId, id, CommentRequest("삭제와 겹친 댓글")); Unit }
        }
        tasks += { jdbc.update("delete from habit_verify where id = ?", verifyId); Unit }

        val errors = ConcurrentRunner.runAll(tasks)

        // 지워진 인증을 가리키는 하트·댓글이 남아 있으면 안 된다
        assertThat(jdbc.queryForObject("select count(*) from habit_verify_reaction where habit_verify_id = ?", Long::class.java, verifyId)).isZero()
        assertThat(jdbc.queryForObject("select count(*) from habit_verify_comment where habit_verify_id = ?", Long::class.java, verifyId)).isZero()
        // 겹쳐서 실패한 요청은 "없는 인증"(404) 또는 DB 제약 위반이어야 한다. 그 밖의 예외는 예상 밖이다
        assertThat(errors.filterNotNull()).allSatisfy { assertThat(it).isInstanceOfAny(EntityNotFoundException::class.java, DataIntegrityViolationException::class.java) }
    }

    companion object {
        private const val REACTORS = 8
        private const val CONCURRENT_CALLS = 10
    }
}
