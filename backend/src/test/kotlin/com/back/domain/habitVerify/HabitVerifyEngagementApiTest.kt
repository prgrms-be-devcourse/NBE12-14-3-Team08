package com.back.domain.habitVerify

import com.back.domain.group.entity.Group
import com.back.domain.group.repository.GroupRepository
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.habit.entity.Habit
import com.back.domain.habit.repository.HabitRepository
import com.back.domain.verifyEngagement.dto.CommentRequest
import com.back.domain.habitVerify.entity.HabitVerify
import com.back.domain.habitVerify.entity.HabitVerifyComment
import com.back.domain.habitVerify.repository.HabitVerifyCommentRepository
import com.back.domain.habitVerify.repository.HabitVerifyReactionRepository
import com.back.domain.habitVerify.repository.HabitVerifyRepository
import com.back.domain.member.entity.Member
import com.back.domain.member.repository.MemberRepository
import com.back.domain.member.service.AuthTokenService
import com.back.global.entity.requiredId
import com.back.global.storage.StorageService
import com.ninjasquad.springmockk.MockkBean
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
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

// 습관 인증의 반응(하트)·댓글 API (docs/adr/0002-verify-reaction-comment-tables.md)
// 읽기는 방 소속이면 되고, 쓰기는 활동 중인 멤버가 진행 중인 방에서 승인된 인증에만 허용한다.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HabitVerifyEngagementApiTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val jsonMapper: JsonMapper,
    private val jdbc: JdbcTemplate,
    private val entityManager: EntityManager,
    private val authTokenService: AuthTokenService,
    private val memberRepository: MemberRepository,
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val habitRepository: HabitRepository,
    private val habitVerifyRepository: HabitVerifyRepository,
    private val reactionRepository: HabitVerifyReactionRepository,
    private val commentRepository: HabitVerifyCommentRepository
) {

    // 다른 HabitVerify 컨트롤러 테스트와 같은 설정이라 스프링 컨텍스트를 함께 쓴다
    @MockkBean(relaxUnitFun = true) private lateinit var storageService: StorageService

    private lateinit var group: Group
    private lateinit var owner: Member
    private lateinit var author: Member
    private lateinit var other: Member
    private lateinit var outsider: Member
    private lateinit var authorRow: GroupMember
    private lateinit var otherRow: GroupMember
    private lateinit var ownerRow: GroupMember
    private lateinit var verify: HabitVerify
    private lateinit var habit: Habit

    @BeforeEach
    fun setUp() {
        group = groupRepository.save(
            Group.builder().title("반응 댓글 방").penalty("커피 사기").password("1234").inviteCode("ENGAGE01").memberLimit(10).build(),
        )
        owner = memberRepository.save(Member.create("방장", "eng-owner", "pass1234"))
        author = memberRepository.save(Member.create("작성자", "eng-author", "pass1234"))
        other = memberRepository.save(Member.create("다른멤버", "eng-other", "pass1234"))
        outsider = memberRepository.save(Member.create("외부인", "eng-outsider", "pass1234"))

        ownerRow = groupMemberRepository.save(GroupMember.create(group, owner, GroupMemberRole.OWNER))
        authorRow = groupMemberRepository.save(GroupMember.create(group, author, GroupMemberRole.MEMBER))
        otherRow = groupMemberRepository.save(GroupMember.create(group, other, GroupMemberRole.MEMBER))

        habit = habitRepository.save(Habit.create(authorRow, "물 마시기", "하루 2리터", 3))
        verify = approvedVerify(LocalDate.now())
    }

    private fun approvedVerify(date: LocalDate): HabitVerify =
        habitVerifyRepository.save(
            HabitVerify.create(habit, date, "인증 설명", "https://example.com/p.png").also { it.approve() },
        )

    private fun token(member: Member) = "Bearer " + authTokenService.createAccessToken(member.requiredId)

    private fun json(result: ResultActionsDsl): JsonNode = jsonMapper.readTree(result.andReturn().response.contentAsString)

    private val reactionsPath get() = "/api/habits/verifications/${verify.requiredId}/reactions"
    private val commentsPath get() = "/api/habits/verifications/${verify.requiredId}/comments"

    private fun react(member: Member, path: String = reactionsPath) = mockMvc.put(path) { header("Authorization", token(member)) }

    private fun cancelReaction(member: Member, path: String = reactionsPath) = mockMvc.delete(path) { header("Authorization", token(member)) }

    private fun readReaction(member: Member) = mockMvc.get(reactionsPath) { header("Authorization", token(member)) }

    // 매개변수 이름을 text로 둔다: MockMvc DSL 안의 content(요청 본문)와 이름이 같으면 서로 가려진다
    private fun postComment(member: Member, text: String?, path: String = commentsPath) =
        mockMvc.post(path) {
            header("Authorization", token(member))
            contentType = MediaType.APPLICATION_JSON
            content = jsonMapper.writeValueAsString(CommentRequest(text))
        }

    private fun patchComment(member: Member, commentId: Long, text: String?) =
        mockMvc.patch("$commentsPath/$commentId") {
            header("Authorization", token(member))
            contentType = MediaType.APPLICATION_JSON
            content = jsonMapper.writeValueAsString(CommentRequest(text))
        }

    private fun contents(page: JsonNode): List<String> = page.get("items").let { items -> (0 until items.size()).map { items.get(it).get("content").asString() } }

    private fun deleteComment(member: Member, commentId: Long) =
        mockMvc.delete("$commentsPath/$commentId") { header("Authorization", token(member)) }

    private fun listComments(member: Member, query: String = "") = mockMvc.get("$commentsPath$query") { header("Authorization", token(member)) }

    private fun saveComment(row: GroupMember, content: String): HabitVerifyComment =
        commentRepository.save(HabitVerifyComment.create(verify, row, content))

    private fun finishGroup() {
        group.finish()
        groupRepository.save(group)
    }

    private fun leave(row: GroupMember) {
        row.leave()
        groupMemberRepository.save(row)
    }

    // 승인된 인증이 나중에 반려로 바뀐 상황을 만든다 (엔티티 메서드는 검토 대기에서만 바꿀 수 있어 직접 고친다)
    private fun markRejected() {
        entityManager.flush()
        jdbc.update("update habit_verify set status = 'REJECTED' where id = ?", verify.requiredId)
        entityManager.clear()
    }

    @Nested
    @DisplayName("하트 PUT·DELETE·GET /api/habits/verifications/{id}/reactions")
    inner class Reactions {

        @Test
        @DisplayName("다른 멤버가 누르면 200이고, 같은 요청을 다시 보내도 하트는 하나다")
        fun 누르기는_멱등하다() {
            react(other).andExpect {
                status { isOk() }
                jsonPath("$.reacted") { value(true) }
                jsonPath("$.reactionCount") { value(1) }
            }
            react(other).andExpect {
                status { isOk() }
                jsonPath("$.reacted") { value(true) }
                jsonPath("$.reactionCount") { value(1) }
            }

            assertThat(reactionRepository.countByHabitVerifyId(verify.requiredId)).isEqualTo(1)
        }

        @Test
        @DisplayName("여러 멤버가 누르면 개수가 늘고, 각자 자신이 눌렀는지만 true로 본다")
        fun 개수와_내_상태() {
            react(other)
            react(owner)

            readReaction(other).andExpect {
                jsonPath("$.reacted") { value(true) }
                jsonPath("$.reactionCount") { value(2) }
            }
            readReaction(author).andExpect {
                jsonPath("$.reacted") { value(false) }
                jsonPath("$.reactionCount") { value(2) }
            }
        }

        @Test
        @DisplayName("취소하면 하트가 사라지고, 누른 적이 없어도 200이다")
        fun 취소는_멱등하다() {
            react(other)

            cancelReaction(other).andExpect {
                status { isOk() }
                jsonPath("$.reacted") { value(false) }
                jsonPath("$.reactionCount") { value(0) }
            }
            cancelReaction(other).andExpect {
                status { isOk() }
                jsonPath("$.reactionCount") { value(0) }
            }
        }

        @Test
        @DisplayName("본인 인증에는 누를 수 없다 (400)")
        fun 본인_인증은_불가() {
            react(author).andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { value("본인 인증에는 하트를 누를 수 없습니다.") }
            }

            assertThat(reactionRepository.countByHabitVerifyId(verify.requiredId)).isZero()
        }

        @Test
        @DisplayName("검토 대기 인증에는 누를 수 없다 (400)")
        fun 검토_대기는_불가() {
            val pending = habitVerifyRepository.save(HabitVerify.create(habit, LocalDate.now().minusDays(1), "설명", "https://example.com/a.png"))

            react(other, "/api/habits/verifications/${pending.requiredId}/reactions").andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { value("승인된 인증에만 남길 수 있습니다.") }
            }
        }

        @Test
        @DisplayName("반려된 인증에는 누를 수 없다 (400)")
        fun 반려는_불가() {
            val rejected = habitVerifyRepository.save(
                HabitVerify.create(habit, LocalDate.now().minusDays(2), "설명", "https://example.com/b.png").also { it.reject() },
            )

            react(other, "/api/habits/verifications/${rejected.requiredId}/reactions").andExpect { status { isBadRequest() } }
        }

        @Test
        @DisplayName("이미 누른 하트는 인증이 반려로 바뀐 뒤에도 취소할 수 있다")
        fun 반려된_뒤에도_취소() {
            react(other)
            markRejected()

            cancelReaction(other).andExpect {
                status { isOk() }
                jsonPath("$.reactionCount") { value(0) }
            }
        }

        @Test
        @DisplayName("방 멤버가 아니면 누르기·취소·조회 모두 403이다")
        fun 외부인은_403() {
            react(outsider).andExpect { status { isForbidden() } }
            cancelReaction(outsider).andExpect { status { isForbidden() } }
            readReaction(outsider).andExpect { status { isForbidden() } }
        }

        @Test
        @DisplayName("없는 인증이면 404이다")
        fun 없는_인증() {
            react(other, "/api/habits/verifications/999999999/reactions").andExpect {
                status { isNotFound() }
                jsonPath("$.message") { value("존재하지 않는 인증입니다.") }
            }
        }

        @Test
        @DisplayName("토큰이 없으면 401이다")
        fun 토큰이_없다() {
            mockMvc.put(reactionsPath).andExpect { status { isUnauthorized() } }
        }

        @Test
        @DisplayName("종료된 방에서는 누르기·취소가 400이고, 조회는 된다")
        fun 종료된_방은_읽기만() {
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
        @DisplayName("방을 나간 멤버는 누를 수 없고(403), 조회는 할 수 있다")
        fun 나간_멤버() {
            leave(otherRow)

            react(other).andExpect { status { isForbidden() } }
            readReaction(other).andExpect { status { isOk() } }
        }
    }

    @Nested
    @DisplayName("댓글 작성 POST /api/habits/verifications/{id}/comments")
    inner class CreateComment {

        @Test
        @DisplayName("다른 멤버가 쓰면 201이고, 앞뒤 공백은 지운 내용이 내가 쓴 댓글로 내려온다")
        fun 작성_성공() {
            postComment(other, "  오늘도 수고했어요  ").andExpect {
                status { isCreated() }
                jsonPath("$.content") { value("오늘도 수고했어요") }
                jsonPath("$.authorNickname") { value("다른멤버") }
                jsonPath("$.authorMemberId") { value(other.requiredId) }
                jsonPath("$.mine") { value(true) }
                jsonPath("$.canEdit") { value(true) }
                jsonPath("$.canDelete") { value(true) }
                jsonPath("$.edited") { value(false) }
            }

            assertThat(commentRepository.countByHabitVerifyId(verify.requiredId)).isEqualTo(1)
        }

        @Test
        @DisplayName("본인 인증에도 댓글을 쓸 수 있다")
        fun 본인_인증에도_가능() {
            postComment(author, "고마워요!").andExpect { status { isCreated() } }
        }

        @Test
        @DisplayName("500자는 되고 501자는 400이다")
        fun 길이_제한() {
            postComment(other, "가".repeat(500)).andExpect { status { isCreated() } }
            postComment(other, "가".repeat(501)).andExpect {
                status { isBadRequest() }
                jsonPath("$.errors[0]") { value("댓글은 500자 이하여야 합니다.") }
            }
        }

        @Test
        @DisplayName("내용이 비었거나 공백뿐이거나 없으면 400이다")
        fun 빈_내용() {
            postComment(other, "").andExpect { status { isBadRequest() } }
            postComment(other, "   ").andExpect { status { isBadRequest() } }
            postComment(other, null).andExpect { status { isBadRequest() } }
        }

        @Test
        @DisplayName("방 멤버가 아니면 403, 없는 인증이면 404이다")
        fun 권한과_존재() {
            postComment(outsider, "안녕").andExpect { status { isForbidden() } }
            postComment(other, "안녕", "/api/habits/verifications/999999999/comments").andExpect { status { isNotFound() } }
        }

        @Test
        @DisplayName("검토 대기 인증에는 쓸 수 없다 (400)")
        fun 검토_대기는_불가() {
            val pending = habitVerifyRepository.save(HabitVerify.create(habit, LocalDate.now().minusDays(1), "설명", "https://example.com/a.png"))

            postComment(other, "안녕", "/api/habits/verifications/${pending.requiredId}/comments").andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { value("승인된 인증에만 남길 수 있습니다.") }
            }
        }

        @Test
        @DisplayName("종료된 방에서는 쓸 수 없다 (400)")
        fun 종료된_방은_불가() {
            finishGroup()

            postComment(other, "안녕").andExpect {
                status { isBadRequest() }
                jsonPath("$.message") { value("이미 종료된 그룹에서는 남길 수 없습니다.") }
            }
        }

        @Test
        @DisplayName("방을 나간 멤버는 쓸 수 없다 (403)")
        fun 나간_멤버는_불가() {
            leave(otherRow)

            postComment(other, "안녕").andExpect { status { isForbidden() } }
        }
    }

    @Nested
    @DisplayName("댓글 목록 GET /api/habits/verifications/{id}/comments")
    inner class ListComments {

        @Test
        @DisplayName("오래된 순으로 나오고, 커서로 이어서 읽으며, 마지막 페이지는 nextCursor가 null이다")
        fun 커서_페이징() {
            val saved = (1..5).map { saveComment(if (it % 2 == 0) otherRow else ownerRow, "댓글 $it") }

            val first = json(listComments(other, "?size=2"))
            assertThat(contents(first)).containsExactly("댓글 1", "댓글 2")
            assertThat(first.get("totalCount").asLong()).isEqualTo(5)
            assertThat(first.get("nextCursor").asLong()).isEqualTo(saved[1].requiredId)

            val second = json(listComments(other, "?size=2&cursor=${first.get("nextCursor").asLong()}"))
            assertThat(contents(second)).containsExactly("댓글 3", "댓글 4")

            val last = json(listComments(other, "?size=2&cursor=${second.get("nextCursor").asLong()}"))
            assertThat(contents(last)).containsExactly("댓글 5")
            assertThat(last.get("nextCursor").isNull).isTrue()
        }

        @Test
        @DisplayName("댓글이 없으면 빈 목록과 0을 준다")
        fun 빈_목록() {
            listComments(other).andExpect {
                status { isOk() }
                jsonPath("$.items.length()") { value(0) }
                jsonPath("$.totalCount") { value(0) }
                jsonPath("$.nextCursor") { value(null) }
            }
        }

        @Test
        @DisplayName("내가 쓴 댓글은 수정·삭제할 수 있다고, 남의 댓글은 방장만 삭제할 수 있다고 알려 준다")
        fun 내_댓글과_권한_표시() {
            saveComment(otherRow, "다른 멤버의 댓글")

            val asOther = json(listComments(other)).get("items").single()
            assertThat(asOther.get("mine").asBoolean()).isTrue()
            assertThat(asOther.get("canEdit").asBoolean()).isTrue()
            assertThat(asOther.get("canDelete").asBoolean()).isTrue()

            val asAuthor = json(listComments(author)).get("items").single()
            assertThat(asAuthor.get("mine").asBoolean()).isFalse()
            assertThat(asAuthor.get("canEdit").asBoolean()).isFalse()
            assertThat(asAuthor.get("canDelete").asBoolean()).isFalse()

            val asOwner = json(listComments(owner)).get("items").single()
            assertThat(asOwner.get("mine").asBoolean()).isFalse()
            assertThat(asOwner.get("canEdit").asBoolean()).isFalse()
            assertThat(asOwner.get("canDelete").asBoolean()).isTrue()
        }

        @Test
        @DisplayName("방을 나간 멤버의 댓글은 작성자를 '탈퇴한 멤버'로 가리고 멤버 id도 주지 않는다")
        fun 나간_멤버의_댓글() {
            saveComment(otherRow, "떠나기 전에 남긴 댓글")
            leave(otherRow)

            val item = json(listComments(author)).get("items").single()

            assertThat(item.get("authorNickname").asString()).isEqualTo("탈퇴한 멤버")
            assertThat(item.get("authorMemberId").isNull).isTrue()
            assertThat(item.get("mine").asBoolean()).isFalse()
            assertThat(item.get("content").asString()).isEqualTo("떠나기 전에 남긴 댓글")
        }

        @Test
        @DisplayName("종료된 방이나 승인이 취소된 인증에서도 읽을 수 있고, 나간 멤버도 읽을 수 있다")
        fun 읽기는_항상_가능() {
            saveComment(otherRow, "댓글")
            finishGroup()
            leave(authorRow)
            markRejected()

            listComments(author).andExpect {
                status { isOk() }
                jsonPath("$.items.length()") { value(1) }
            }
        }

        @Test
        @DisplayName("방 멤버가 아니면 403이고, size가 범위를 벗어나면 400이다")
        fun 권한과_요청값() {
            listComments(outsider).andExpect { status { isForbidden() } }
            listComments(other, "?size=0").andExpect { status { isBadRequest() } }
            listComments(other, "?size=101").andExpect { status { isBadRequest() } }
            listComments(other, "?cursor=-1").andExpect { status { isBadRequest() } }
        }
    }

    @Nested
    @DisplayName("댓글 수정 PATCH /api/habits/verifications/{id}/comments/{commentId}")
    inner class EditComment {

        @Test
        @DisplayName("작성자가 고치면 내용이 바뀌고 수정됨으로 표시된다")
        fun 수정_성공() {
            val comment = saveComment(otherRow, "처음 내용")
            // 같은 시각에 만들어지고 고쳐지는 것을 피하려고 생성일을 과거로 돌린다
            entityManager.flush()
            jdbc.update("update habit_verify_comment set create_date = create_date - interval '1 hour', modify_date = modify_date - interval '1 hour' where id = ?", comment.requiredId)
            entityManager.clear()

            patchComment(other, comment.requiredId, "  고친 내용  ").andExpect {
                status { isOk() }
                jsonPath("$.content") { value("고친 내용") }
                jsonPath("$.edited") { value(true) }
            }
        }

        @Test
        @DisplayName("작성자가 아니면 방장도 고칠 수 없다 (403)")
        fun 남의_댓글은_불가() {
            val comment = saveComment(otherRow, "내용")

            patchComment(author, comment.requiredId, "고침").andExpect { status { isForbidden() } }
            patchComment(owner, comment.requiredId, "고침").andExpect { status { isForbidden() } }
        }

        @Test
        @DisplayName("없는 댓글이거나 다른 인증의 댓글이면 404이다")
        fun 없는_댓글() {
            val otherVerify = approvedVerify(LocalDate.now().minusDays(3))
            val onOtherVerify = commentRepository.save(HabitVerifyComment.create(otherVerify, otherRow, "다른 인증의 댓글"))

            patchComment(other, 999_999_999L, "고침").andExpect { status { isNotFound() } }
            patchComment(other, onOtherVerify.requiredId, "고침").andExpect { status { isNotFound() } }
        }

        @Test
        @DisplayName("500자 초과·빈 내용은 400, 종료된 방은 400, 나간 멤버는 403이다")
        fun 검증과_상태() {
            val comment = saveComment(otherRow, "내용")

            patchComment(other, comment.requiredId, "가".repeat(501)).andExpect { status { isBadRequest() } }
            patchComment(other, comment.requiredId, " ").andExpect { status { isBadRequest() } }

            finishGroup()
            patchComment(other, comment.requiredId, "고침").andExpect { status { isBadRequest() } }
        }

        @Test
        @DisplayName("나간 멤버는 고칠 수 없다 (403)")
        fun 나간_멤버() {
            val comment = saveComment(otherRow, "내용")
            leave(otherRow)

            patchComment(other, comment.requiredId, "고침").andExpect { status { isForbidden() } }
        }

        @Test
        @DisplayName("인증이 반려로 바뀐 뒤에도 내 댓글은 고칠 수 있다")
        fun 반려된_뒤에도_수정() {
            val comment = saveComment(otherRow, "내용")
            markRejected()

            patchComment(other, comment.requiredId, "고침").andExpect { status { isOk() } }
        }
    }

    @Nested
    @DisplayName("댓글 삭제 DELETE /api/habits/verifications/{id}/comments/{commentId}")
    inner class DeleteComment {

        @Test
        @DisplayName("작성자가 지우면 204이고 행이 지워진다")
        fun 작성자가_삭제() {
            val comment = saveComment(otherRow, "내용")

            deleteComment(other, comment.requiredId).andExpect { status { isNoContent() } }

            assertThat(commentRepository.findById(comment.requiredId)).isEmpty
        }

        @Test
        @DisplayName("방장은 남의 댓글을 지울 수 있다")
        fun 방장이_삭제() {
            val comment = saveComment(otherRow, "내용")

            deleteComment(owner, comment.requiredId).andExpect { status { isNoContent() } }

            assertThat(commentRepository.findById(comment.requiredId)).isEmpty
        }

        @Test
        @DisplayName("방장이 아닌 멤버는 남의 댓글을 지울 수 없다 (403)")
        fun 남의_댓글은_불가() {
            val comment = saveComment(otherRow, "내용")

            deleteComment(author, comment.requiredId).andExpect { status { isForbidden() } }

            assertThat(commentRepository.findById(comment.requiredId)).isPresent
        }

        @Test
        @DisplayName("없는 댓글이면 404, 종료된 방이면 400이다")
        fun 없는_댓글과_종료된_방() {
            val comment = saveComment(otherRow, "내용")

            deleteComment(other, 999_999_999L).andExpect { status { isNotFound() } }

            finishGroup()
            deleteComment(owner, comment.requiredId).andExpect { status { isBadRequest() } }
        }

        @Test
        @DisplayName("나간 멤버는 지울 수 없다 (403)")
        fun 나간_멤버() {
            val comment = saveComment(otherRow, "내용")
            leave(otherRow)

            deleteComment(other, comment.requiredId).andExpect { status { isForbidden() } }
        }

        @Test
        @DisplayName("방장이 나간 멤버의 댓글도 지울 수 있다")
        fun 나간_멤버의_댓글을_방장이_삭제() {
            val comment = saveComment(otherRow, "내용")
            leave(otherRow)

            deleteComment(owner, comment.requiredId).andExpect { status { isNoContent() } }
        }
    }

    @Nested
    @DisplayName("갤러리 사진 목록 GET /api/groups/{groupId}/habits/verifications/photos")
    inner class PhotoGallery {

        private fun photos(member: Member, query: String = "") =
            mockMvc.get("/api/groups/${group.requiredId}/habits/verifications/photos$query") { header("Authorization", token(member)) }

        @Test
        @DisplayName("includeEngagement를 주지 않으면 하트·댓글 필드가 응답에 없다 (기존 응답 모양 유지)")
        fun 기본은_필드_없음() {
            react(other)
            saveComment(otherRow, "댓글")

            val item = json(photos(other)).get("items").single()

            assertThat(item.has("reactionCount")).isFalse()
            assertThat(item.has("commentCount")).isFalse()
            assertThat(item.has("reactedByMe")).isFalse()
        }

        @Test
        @DisplayName("includeEngagement=true면 사진마다 하트 개수·댓글 개수·내가 눌렀는지가 더해진다")
        fun 개수와_내_상태() {
            val second = approvedVerify(LocalDate.now().minusDays(1))
            react(other)
            react(owner)
            saveComment(otherRow, "댓글 1")
            saveComment(ownerRow, "댓글 2")
            saveComment(otherRow, "댓글 3")

            val items = json(photos(other, "?includeEngagement=true")).get("items")
            val byId = items.associateBy { it.get("id").asLong() }

            val first = byId.getValue(verify.requiredId)
            assertThat(first.get("reactionCount").asLong()).isEqualTo(2)
            assertThat(first.get("commentCount").asLong()).isEqualTo(3)
            assertThat(first.get("reactedByMe").asBoolean()).isTrue()

            val untouched = byId.getValue(second.requiredId)
            assertThat(untouched.get("reactionCount").asLong()).isZero()
            assertThat(untouched.get("commentCount").asLong()).isZero()
            assertThat(untouched.get("reactedByMe").asBoolean()).isFalse()
        }

        @Test
        @DisplayName("내가 누르지 않은 인증은 reactedByMe가 false다")
        fun 내가_누르지_않았다() {
            react(other)

            val item = json(photos(author, "?includeEngagement=true")).get("items").single()

            assertThat(item.get("reactionCount").asLong()).isEqualTo(1)
            assertThat(item.get("reactedByMe").asBoolean()).isFalse()
        }

        @Test
        @DisplayName("사진이 하나도 없어도 includeEngagement=true는 오류 없이 빈 목록을 준다")
        fun 빈_목록() {
            jdbc.update("update habit_verify set status = 'PENDING' where id = ?", verify.requiredId)
            entityManager.clear()

            photos(other, "?includeEngagement=true").andExpect {
                status { isOk() }
                jsonPath("$.items.length()") { value(0) }
            }
        }
    }

    @Nested
    @DisplayName("경계 사례")
    inner class EdgeCases {

        @Test
        @DisplayName("토큰 없이 댓글 목록·작성·수정·삭제를 호출하면 모두 401이다")
        fun 댓글_API는_로그인이_필요하다() {
            val comment = saveComment(otherRow, "댓글")

            mockMvc.get(commentsPath).andExpect { status { isUnauthorized() } }
            mockMvc.post(commentsPath) {
                contentType = MediaType.APPLICATION_JSON
                content = jsonMapper.writeValueAsString(CommentRequest("내용"))
            }.andExpect { status { isUnauthorized() } }
            mockMvc.patch("$commentsPath/${comment.requiredId}") {
                contentType = MediaType.APPLICATION_JSON
                content = jsonMapper.writeValueAsString(CommentRequest("수정"))
            }.andExpect { status { isUnauthorized() } }
            mockMvc.delete("$commentsPath/${comment.requiredId}").andExpect { status { isUnauthorized() } }
        }

        @Test
        @DisplayName("다른 인증의 댓글 번호로 삭제하면 404이고 그 댓글은 그대로다")
        fun 다른_인증의_댓글은_지울_수_없다() {
            val otherVerify = approvedVerify(LocalDate.now().minusDays(1))
            val foreign = commentRepository.save(HabitVerifyComment.create(otherVerify, otherRow, "다른 인증의 댓글"))

            deleteComment(owner, foreign.requiredId).andExpect { status { isNotFound() } }

            assertThat(commentRepository.findById(foreign.requiredId)).isPresent
        }

        @Test
        @DisplayName("반려된 인증을 다시 제출해도 이미 달린 하트·댓글은 남고, 검토 대기인 동안에는 새로 남길 수 없다")
        fun 재제출해도_하트와_댓글은_유지된다() {
            react(other)
            saveComment(otherRow, "수고했어요")
            markRejected()

            mockMvc.patch("/api/habits/${habit.requiredId}/verifications/${verify.requiredId}/resubmit") {
                header("Authorization", token(author))
                contentType = MediaType.APPLICATION_JSON
                content = """{"description":"다시 올립니다","imageUrl":"https://example.com/new.png"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("PENDING") }
            }

            readReaction(owner).andExpect { jsonPath("$.reactionCount") { value(1) } }
            assertThat(json(listComments(owner)).get("totalCount").asLong()).isEqualTo(1)
            react(owner).andExpect { status { isBadRequest() } }
            postComment(owner, "새 댓글").andExpect { status { isBadRequest() } }
        }

        @Test
        @DisplayName("방장을 넘기면 이전 방장은 남의 댓글을 지울 수 없고, 새 방장은 지울 수 있다")
        fun 방장이_바뀌면_삭제_권한도_바뀐다() {
            val comment = saveComment(authorRow, "작성자의 댓글")

            mockMvc.patch("/api/groups/${group.requiredId}/members/${otherRow.requiredId}/delegate") {
                header("Authorization", token(owner))
            }.andExpect { status { isOk() } }

            val asOldOwner = json(listComments(owner)).get("items").get(0)
            assertThat(asOldOwner.get("canDelete").asBoolean()).isFalse()
            val asNewOwner = json(listComments(other)).get("items").get(0)
            assertThat(asNewOwner.get("canDelete").asBoolean()).isTrue()

            deleteComment(owner, comment.requiredId).andExpect { status { isForbidden() } }
            deleteComment(other, comment.requiredId).andExpect { status { isNoContent() } }
        }

        @Test
        @DisplayName("갤러리에서 멤버로 걸러도 그 멤버 인증의 개수만 정확히 나온다")
        fun 갤러리_멤버_필터와_개수() {
            val otherHabit = habitRepository.save(Habit.create(otherRow, "운동", null, 3))
            val otherVerify = habitVerifyRepository.save(
                HabitVerify.create(otherHabit, LocalDate.now(), "운동 인증", "https://example.com/o.png").also { it.approve() },
            )
            react(owner, "/api/habits/verifications/${otherVerify.requiredId}/reactions")
            react(author, "/api/habits/verifications/${otherVerify.requiredId}/reactions")
            react(other) // 작성자의 인증에 누른 하트: 필터 결과에는 섞이지 않아야 한다

            val item = json(
                mockMvc.get("/api/groups/${group.requiredId}/habits/verifications/photos?memberId=${other.requiredId}&includeEngagement=true") {
                    header("Authorization", token(owner))
                },
            ).get("items").single()

            assertThat(item.get("id").asLong()).isEqualTo(otherVerify.requiredId)
            assertThat(item.get("reactionCount").asLong()).isEqualTo(2)
            assertThat(item.get("reactedByMe").asBoolean()).isTrue()
        }

        @Test
        @DisplayName("갤러리를 다음 페이지로 넘겨도 페이지마다 개수가 정확하다")
        fun 갤러리_페이지마다_개수() {
            val older = approvedVerify(LocalDate.now().minusDays(1))
            react(other)
            react(other, "/api/habits/verifications/${older.requiredId}/reactions")
            react(owner, "/api/habits/verifications/${older.requiredId}/reactions")
            commentRepository.save(HabitVerifyComment.create(older, otherRow, "어제 인증 댓글"))

            fun page(query: String) = json(
                mockMvc.get("/api/groups/${group.requiredId}/habits/verifications/photos?size=1&includeEngagement=true$query") {
                    header("Authorization", token(other))
                },
            )

            val first = page("")
            assertThat(first.get("items").single().get("id").asLong()).isEqualTo(verify.requiredId)
            assertThat(first.get("items").single().get("reactionCount").asLong()).isEqualTo(1)

            val second = page("&cursor=${first.get("nextCursor").asString()}")
            val item = second.get("items").single()
            assertThat(item.get("id").asLong()).isEqualTo(older.requiredId)
            assertThat(item.get("reactionCount").asLong()).isEqualTo(2)
            assertThat(item.get("commentCount").asLong()).isEqualTo(1)
        }

        @Test
        @DisplayName("종료된 방에서 나간 멤버도 갤러리를 읽을 수 있고, 이전에 누른 하트는 그대로 내 하트로 보인다")
        fun 나간_멤버의_갤러리() {
            react(other)
            finishGroup()
            leave(otherRow)

            val item = json(
                mockMvc.get("/api/groups/${group.requiredId}/habits/verifications/photos?includeEngagement=true") {
                    header("Authorization", token(other))
                },
            ).get("items").single()

            assertThat(item.get("reactionCount").asLong()).isEqualTo(1)
            assertThat(item.get("reactedByMe").asBoolean()).isTrue()
        }
    }
}
