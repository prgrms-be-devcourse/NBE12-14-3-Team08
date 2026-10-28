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
import com.back.domain.member.service.AuthTokenService
import com.back.domain.penaltyverify.entity.PenaltyVerify
import com.back.domain.penaltyverify.entity.PenaltyVerifyComment
import com.back.domain.penaltyverify.repository.PenaltyVerifyCommentRepository
import com.back.domain.penaltyverify.repository.PenaltyVerifyReactionRepository
import com.back.domain.penaltyverify.repository.PenaltyVerifyRepository
import com.back.domain.verifyEngagement.dto.CommentRequest
import com.back.global.entity.requiredId
import com.back.global.storage.StorageService
import com.ninjasquad.springmockk.MockkBean
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate

// 벌칙 인증의 반응(하트)·댓글 API (docs/adr/0002-verify-reaction-comment-tables.md)
// 규칙은 습관 인증과 같다: 읽기는 방 소속이면 되고, 쓰기는 활동 중인 멤버가 진행 중인 방에서 승인된(APPROVED) 인증에만 허용한다.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PenaltyVerifyEngagementApiTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val jsonMapper: JsonMapper,
    private val authTokenService: AuthTokenService,
    private val memberRepository: MemberRepository,
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val habitRepository: HabitRepository,
    private val penaltyVerifyRepository: PenaltyVerifyRepository,
    private val reactionRepository: PenaltyVerifyReactionRepository,
    private val commentRepository: PenaltyVerifyCommentRepository
) {

    @MockkBean(relaxUnitFun = true) private lateinit var storageService: StorageService

    private lateinit var group: Group
    private lateinit var owner: Member
    private lateinit var author: Member
    private lateinit var other: Member
    private lateinit var outsider: Member
    private lateinit var ownerRow: GroupMember
    private lateinit var authorRow: GroupMember
    private lateinit var otherRow: GroupMember
    private lateinit var penalty: PenaltyVerify

    @BeforeEach
    fun setUp() {
        group = groupRepository.save(
            Group.builder().title("벌칙 반응 방").penalty("커피 사기").password("1234").inviteCode("PENGAGE1").memberLimit(10).build(),
        )
        owner = memberRepository.save(Member.create("방장", "pen-owner", "pass1234"))
        author = memberRepository.save(Member.create("작성자", "pen-author", "pass1234"))
        other = memberRepository.save(Member.create("다른멤버", "pen-other", "pass1234"))
        outsider = memberRepository.save(Member.create("외부인", "pen-outsider", "pass1234"))

        ownerRow = groupMemberRepository.save(GroupMember.create(group, owner, GroupMemberRole.OWNER))
        authorRow = groupMemberRepository.save(GroupMember.create(group, author, GroupMemberRole.MEMBER))
        otherRow = groupMemberRepository.save(GroupMember.create(group, other, GroupMemberRole.MEMBER))

        penalty = approvedPenalty("물 마시기")
    }

    private fun penaltyFor(title: String): PenaltyVerify {
        val habit = habitRepository.save(Habit.create(authorRow, title, null, 3))
        return penaltyVerifyRepository.save(PenaltyVerify.create(authorRow, habit))
    }

    private fun approvedPenalty(title: String): PenaltyVerify =
        penaltyFor(title).also {
            it.submit(LocalDate.now(), "벌칙 수행", "https://example.com/p.png")
            it.approve()
        }

    private fun token(member: Member) = "Bearer " + authTokenService.createAccessToken(member.requiredId)

    private fun json(result: ResultActionsDsl): JsonNode = jsonMapper.readTree(result.andReturn().response.contentAsString)

    private fun reactionsPath(target: PenaltyVerify = penalty) = "/api/penalties/${target.requiredId}/reactions"
    private fun commentsPath(target: PenaltyVerify = penalty) = "/api/penalties/${target.requiredId}/comments"

    private fun react(member: Member, target: PenaltyVerify = penalty) =
        mockMvc.put(reactionsPath(target)) { header("Authorization", token(member)) }

    private fun cancelReaction(member: Member, target: PenaltyVerify = penalty) =
        mockMvc.delete(reactionsPath(target)) { header("Authorization", token(member)) }

    private fun readReaction(member: Member) = mockMvc.get(reactionsPath()) { header("Authorization", token(member)) }

    // 매개변수 이름을 text로 둔다: MockMvc DSL 안의 content(요청 본문)와 이름이 같으면 서로 가려진다
    private fun postComment(member: Member, text: String?, target: PenaltyVerify = penalty) =
        mockMvc.post(commentsPath(target)) {
            header("Authorization", token(member))
            contentType = MediaType.APPLICATION_JSON
            content = jsonMapper.writeValueAsString(CommentRequest(text))
        }

    private fun patchComment(member: Member, commentId: Long, text: String?) =
        mockMvc.patch("${commentsPath()}/$commentId") {
            header("Authorization", token(member))
            contentType = MediaType.APPLICATION_JSON
            content = jsonMapper.writeValueAsString(CommentRequest(text))
        }

    private fun deleteComment(member: Member, commentId: Long) =
        mockMvc.delete("${commentsPath()}/$commentId") { header("Authorization", token(member)) }

    private fun listComments(member: Member, query: String = "") =
        mockMvc.get("${commentsPath()}$query") { header("Authorization", token(member)) }

    private fun contents(page: JsonNode): List<String> =
        page.get("items").let { items -> (0 until items.size()).map { items.get(it).get("content").asString() } }

    private fun saveComment(row: GroupMember, content: String): PenaltyVerifyComment =
        commentRepository.save(PenaltyVerifyComment.create(penalty, row, content))

    private fun finishGroup() {
        group.finish()
        groupRepository.save(group)
    }

    private fun leave(row: GroupMember) {
        row.leave()
        groupMemberRepository.save(row)
    }

    @Nested
    @DisplayName("하트 PUT·DELETE·GET /api/penalties/{id}/reactions")
    inner class Reactions {

        @Test
        @DisplayName("다른 멤버가 승인된 벌칙에 하트를 누르면 개수와 눌렀음이 응답된다")
        fun 하트_누르기() {
            react(other).andExpect {
                status { isOk() }
                jsonPath("$.reacted") { value(true) }
                jsonPath("$.reactionCount") { value(1) }
            }
        }

        @Test
        @DisplayName("같은 하트를 여러 번 눌러도 하나만 남는다")
        fun 멱등() {
            repeat(3) { react(other).andExpect { status { isOk() } } }

            assertThat(reactionRepository.countByPenaltyVerifyId(penalty.requiredId)).isEqualTo(1)
        }

        @Test
        @DisplayName("하트를 취소하면 사라지고, 누른 적 없어도 성공한다")
        fun 취소() {
            react(other)

            cancelReaction(other).andExpect {
                status { isOk() }
                jsonPath("$.reacted") { value(false) }
                jsonPath("$.reactionCount") { value(0) }
            }
            cancelReaction(other).andExpect { status { isOk() } }
        }

        @Test
        @DisplayName("본인 벌칙에는 하트를 누를 수 없다")
        fun 본인_벌칙() {
            react(author).andExpect { status { isBadRequest() } }
        }

        @Test
        @DisplayName("방장도 다른 멤버의 벌칙에 하트를 누를 수 있다")
        fun 방장_하트() {
            react(owner).andExpect { status { isOk() } }
        }

        @Test
        @DisplayName("승인되지 않은 벌칙(미제출·대기·반려)에는 하트를 누를 수 없다")
        fun 승인_아님() {
            val required = penaltyFor("미제출")
            val pending = penaltyFor("대기").also { it.submit(LocalDate.now(), "설명", "https://example.com/a.png") }
            val rejected = penaltyFor("반려").also {
                it.submit(LocalDate.now(), "설명", "https://example.com/b.png")
                it.reject()
            }

            listOf(required, pending, rejected).forEach { react(other, it).andExpect { status { isBadRequest() } } }
        }

        @Test
        @DisplayName("방 멤버가 아니면 읽기·쓰기 모두 403")
        fun 외부인() {
            react(outsider).andExpect { status { isForbidden() } }
            readReaction(outsider).andExpect { status { isForbidden() } }
        }

        @Test
        @DisplayName("없는 벌칙 인증이면 404")
        fun 없는_벌칙() {
            mockMvc.put("/api/penalties/999999/reactions") { header("Authorization", token(other)) }
                .andExpect { status { isNotFound() } }
        }

        @Test
        @DisplayName("인증 없이 호출하면 401")
        fun 로그인_필요() {
            mockMvc.put(reactionsPath()).andExpect { status { isUnauthorized() } }
        }

        @Test
        @DisplayName("방을 나간 멤버는 하트를 누르지 못하지만 읽을 수는 있다")
        fun 나간_멤버() {
            react(other)
            leave(otherRow)

            react(other).andExpect { status { isForbidden() } }
            readReaction(other).andExpect {
                status { isOk() }
                jsonPath("$.reactionCount") { value(1) }
            }
        }

        @Test
        @DisplayName("종료된 방에서는 누르기·취소 모두 400, 읽기는 된다")
        fun 종료된_방() {
            react(other)
            finishGroup()

            react(owner).andExpect { status { isBadRequest() } }
            cancelReaction(other).andExpect { status { isBadRequest() } }
            readReaction(other).andExpect {
                status { isOk() }
                jsonPath("$.reactionCount") { value(1) }
            }
        }

        @Test
        @DisplayName("읽기 응답은 내가 눌렀는지를 보여 준다")
        fun 읽기() {
            react(other)

            readReaction(other).andExpect { jsonPath("$.reacted") { value(true) } }
            readReaction(owner).andExpect {
                jsonPath("$.reacted") { value(false) }
                jsonPath("$.reactionCount") { value(1) }
            }
        }
    }

    @Nested
    @DisplayName("댓글 /api/penalties/{id}/comments")
    inner class Comments {

        @Test
        @DisplayName("승인된 벌칙에 댓글을 남기면 201과 작성자 정보가 응답된다. 본인 벌칙에도 남길 수 있다")
        fun 작성() {
            val response = json(
                postComment(author, "  수고했어요  ").andExpect {
                    status { isCreated() }
                    jsonPath("$.content") { value("수고했어요") }
                    jsonPath("$.authorNickname") { value("작성자") }
                    jsonPath("$.mine") { value(true) }
                    jsonPath("$.edited") { value(false) }
                },
            )

            assertThat(response.get("id").asLong()).isPositive()
        }

        @Test
        @DisplayName("빈 내용·공백·500자 초과는 400")
        fun 검증() {
            postComment(other, null).andExpect { status { isBadRequest() } }
            postComment(other, "   ").andExpect { status { isBadRequest() } }
            postComment(other, "가".repeat(501)).andExpect { status { isBadRequest() } }
            postComment(other, "가".repeat(500)).andExpect { status { isCreated() } }
        }

        @Test
        @DisplayName("승인되지 않은 벌칙, 종료된 방, 나간 멤버, 외부인은 댓글을 쓸 수 없다")
        fun 쓰기_거절() {
            val pending = penaltyFor("대기").also { it.submit(LocalDate.now(), "설명", "https://example.com/a.png") }
            postComment(other, "안돼요", pending).andExpect { status { isBadRequest() } }
            postComment(outsider, "안돼요").andExpect { status { isForbidden() } }

            leave(otherRow)
            postComment(other, "나갔어요").andExpect { status { isForbidden() } }

            finishGroup()
            postComment(owner, "끝났어요").andExpect { status { isBadRequest() } }
        }

        @Test
        @DisplayName("목록은 오래된 순이고 커서로 이어 읽는다")
        fun 목록_커서() {
            (1..5).forEach { saveComment(otherRow, "댓글$it") }

            val first = json(listComments(owner, "?size=2").andExpect { status { isOk() } })
            assertThat(contents(first)).containsExactly("댓글1", "댓글2")
            assertThat(first.get("totalCount").asLong()).isEqualTo(5)

            val second = json(listComments(owner, "?size=2&cursor=${first.get("nextCursor").asLong()}"))
            assertThat(contents(second)).containsExactly("댓글3", "댓글4")

            val last = json(listComments(owner, "?size=2&cursor=${second.get("nextCursor").asLong()}"))
            assertThat(contents(last)).containsExactly("댓글5")
            assertThat(last.get("nextCursor").isNull).isTrue()
        }

        @Test
        @DisplayName("size가 범위를 벗어나면 400, 외부인은 403")
        fun 목록_검증() {
            listComments(owner, "?size=0").andExpect { status { isBadRequest() } }
            listComments(owner, "?size=101").andExpect { status { isBadRequest() } }
            listComments(outsider).andExpect { status { isForbidden() } }
        }

        @Test
        @DisplayName("목록의 mine·canEdit·canDelete는 보는 사람 기준이다")
        fun 목록_권한_표시() {
            saveComment(otherRow, "다른 멤버의 댓글")

            val asOther = json(listComments(other)).get("items").get(0)
            assertThat(asOther.get("mine").asBoolean()).isTrue()
            assertThat(asOther.get("canEdit").asBoolean()).isTrue()
            assertThat(asOther.get("canDelete").asBoolean()).isTrue()

            val asOwner = json(listComments(owner)).get("items").get(0)
            assertThat(asOwner.get("mine").asBoolean()).isFalse()
            assertThat(asOwner.get("canEdit").asBoolean()).isFalse()
            assertThat(asOwner.get("canDelete").asBoolean()).isTrue()

            val asAuthor = json(listComments(author)).get("items").get(0)
            assertThat(asAuthor.get("canDelete").asBoolean()).isFalse()
        }

        @Test
        @DisplayName("방을 나간 멤버의 댓글은 작성자가 '탈퇴한 멤버'로 보이고 본인 표시도 없다")
        fun 나간_멤버의_댓글() {
            saveComment(otherRow, "남기고 갑니다")
            leave(otherRow)

            val item = json(listComments(owner)).get("items").get(0)

            assertThat(item.get("authorNickname").asString()).isEqualTo("탈퇴한 멤버")
            assertThat(item.get("authorMemberId").isNull).isTrue()
            assertThat(json(listComments(other)).get("items").get(0).get("mine").asBoolean()).isFalse()
        }

        @Test
        @DisplayName("작성자는 댓글을 고칠 수 있고 수정됨으로 표시된다. 다른 사람은 방장도 고칠 수 없다")
        fun 수정() {
            val comment = saveComment(otherRow, "처음 내용")

            patchComment(owner, comment.requiredId, "방장이 고침").andExpect { status { isForbidden() } }
            patchComment(other, comment.requiredId, "  ").andExpect { status { isBadRequest() } }
            patchComment(other, comment.requiredId, "고친 내용").andExpect {
                status { isOk() }
                jsonPath("$.content") { value("고친 내용") }
                jsonPath("$.edited") { value(true) }
            }
        }

        @Test
        @DisplayName("작성자와 방장은 지울 수 있고, 다른 멤버는 지울 수 없다")
        fun 삭제() {
            val mine = saveComment(otherRow, "내 댓글")
            val theirs = saveComment(otherRow, "방장이 지울 댓글")

            deleteComment(author, mine.requiredId).andExpect { status { isForbidden() } }
            deleteComment(other, mine.requiredId).andExpect { status { isNoContent() } }
            deleteComment(owner, theirs.requiredId).andExpect { status { isNoContent() } }
            assertThat(commentRepository.countByPenaltyVerifyId(penalty.requiredId)).isZero()
        }

        @Test
        @DisplayName("없는 댓글이거나 다른 벌칙의 댓글이면 404")
        fun 없는_댓글() {
            val otherPenalty = approvedPenalty("운동")
            val foreign = commentRepository.save(PenaltyVerifyComment.create(otherPenalty, otherRow, "다른 벌칙"))

            deleteComment(other, 999999).andExpect { status { isNotFound() } }
            deleteComment(other, foreign.requiredId).andExpect { status { isNotFound() } }
            patchComment(other, foreign.requiredId, "고침").andExpect { status { isNotFound() } }
        }

        @Test
        @DisplayName("종료된 방에서는 읽기만 되고 수정·삭제는 400")
        fun 종료된_방() {
            val comment = saveComment(otherRow, "남은 댓글")
            finishGroup()

            listComments(other).andExpect { status { isOk() } }
            patchComment(other, comment.requiredId, "고침").andExpect { status { isBadRequest() } }
            deleteComment(owner, comment.requiredId).andExpect { status { isBadRequest() } }
        }
    }

    @Nested
    @DisplayName("경계 사례")
    inner class EdgeCases {

        @Test
        @DisplayName("토큰 없이 댓글 목록·작성·수정·삭제를 호출하면 모두 401이다")
        fun 댓글_API는_로그인이_필요하다() {
            val comment = saveComment(otherRow, "댓글")

            mockMvc.get(commentsPath()).andExpect { status { isUnauthorized() } }
            mockMvc.post(commentsPath()) {
                contentType = MediaType.APPLICATION_JSON
                content = jsonMapper.writeValueAsString(CommentRequest("내용"))
            }.andExpect { status { isUnauthorized() } }
            mockMvc.patch("${commentsPath()}/${comment.requiredId}") {
                contentType = MediaType.APPLICATION_JSON
                content = jsonMapper.writeValueAsString(CommentRequest("수정"))
            }.andExpect { status { isUnauthorized() } }
            mockMvc.delete("${commentsPath()}/${comment.requiredId}").andExpect { status { isUnauthorized() } }
        }

        @Test
        @DisplayName("방장을 넘기면 이전 방장은 남의 댓글을 지울 수 없고, 새 방장은 지울 수 있다")
        fun 방장이_바뀌면_삭제_권한도_바뀐다() {
            val comment = saveComment(authorRow, "작성자의 댓글")

            mockMvc.patch("/api/groups/${group.requiredId}/members/${otherRow.requiredId}/delegate") {
                header("Authorization", token(owner))
            }.andExpect { status { isOk() } }

            assertThat(json(listComments(owner)).get("items").get(0).get("canDelete").asBoolean()).isFalse()
            assertThat(json(listComments(other)).get("items").get(0).get("canDelete").asBoolean()).isTrue()
            deleteComment(owner, comment.requiredId).andExpect { status { isForbidden() } }
            deleteComment(other, comment.requiredId).andExpect { status { isNoContent() } }
        }

        @Test
        @DisplayName("검토 대기·반려·미제출 벌칙도 방 멤버는 하트·댓글 영역을 읽을 수 있다 (비어 있다)")
        fun 승인_전에도_읽을_수_있다() {
            val required = penaltyFor("미제출")
            val pending = penaltyFor("대기").also { it.submit(LocalDate.now(), "설명", "https://example.com/a.png") }

            listOf(required, pending).forEach { target ->
                mockMvc.get(reactionsPath(target)) { header("Authorization", token(other)) }.andExpect {
                    status { isOk() }
                    jsonPath("$.reactionCount") { value(0) }
                }
                mockMvc.get(commentsPath(target)) { header("Authorization", token(other)) }.andExpect {
                    status { isOk() }
                    jsonPath("$.totalCount") { value(0) }
                }
            }
        }

        @Test
        @DisplayName("승인된 벌칙을 방장이 초기화하면 그 뒤로는 새 하트·댓글을 남길 수 없고, 다시 제출·승인되면 처음부터 쌓인다")
        fun 초기화_뒤에는_새로_시작한다() {
            react(other)
            saveComment(otherRow, "수고했어요")

            mockMvc.patch("/api/penalties/${penalty.requiredId}/cancel") { header("Authorization", token(owner)) }
                .andExpect { status { isOk() } }

            react(other).andExpect { status { isBadRequest() } }
            postComment(other, "새 댓글").andExpect { status { isBadRequest() } }
            readReaction(other).andExpect { jsonPath("$.reactionCount") { value(0) } }

            // 초기화 서비스가 삭제 뒤 영속성 컨텍스트를 비우므로 다시 읽어서 쓴다
            penaltyVerifyRepository.findById(penalty.requiredId).get().also {
                it.submit(LocalDate.now(), "다시 수행", "https://example.com/again.png")
                it.approve()
            }
            react(other).andExpect {
                status { isOk() }
                jsonPath("$.reactionCount") { value(1) }
            }
        }
    }
}
