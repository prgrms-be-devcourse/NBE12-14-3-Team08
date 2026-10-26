package com.back.domain.habitVerify.service

import com.back.domain.group.entity.Group
import com.back.domain.group.repository.GroupRepository
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.habit.entity.Habit
import com.back.domain.habitVerify.entity.HabitVerify
import com.back.domain.habitVerify.entity.HabitVerifyStatus
import com.back.domain.habitVerify.repository.HabitVerifyRepository
import com.back.domain.member.entity.Member
import com.back.global.exception.EntityNotFoundException
import com.back.global.exception.ForbiddenException
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.data.domain.Pageable
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDate

// 사진 목록의 권한 확인 순서, 요청 값 검증, 한 건 더 읽어 다음 페이지를 판단하는 로직을 확인한다.
// 쿼리 자체(상태·사진 유무·정렬·커서 경계)는 HabitVerifyPhotoRepositoryTest가 PostgreSQL에서 확인한다.
@ExtendWith(MockKExtension::class)
class HabitVerifyPhotoServiceTest {

    companion object {
        private const val GROUP_ID = 100L
        private const val LOGIN_ID = 1L
    }

    @MockK
    private lateinit var groupRepository: GroupRepository

    @MockK
    private lateinit var groupMemberRepository: GroupMemberRepository

    @MockK
    private lateinit var habitVerifyRepository: HabitVerifyRepository

    @InjectMockKs
    private lateinit var service: HabitVerifyPhotoService

    private fun allowAccess() {
        every { groupRepository.existsById(GROUP_ID) } returns true
        every { groupMemberRepository.existsByGroupIdAndMemberId(GROUP_ID, LOGIN_ID) } returns true
    }

    private fun photo(
        id: Long,
        date: LocalDate,
        memberId: Long = 7L,
        nickname: String = "철수",
        title: String = "스트레칭",
        width: Int? = null,
        height: Int? = null,
    ): HabitVerify {
        val member = Member.create(nickname, "user$memberId", "pw")
        ReflectionTestUtils.setField(member, "id", memberId)
        val group = Group.builder().title("그룹").penalty("벌칙").password("1234").inviteCode("code").memberLimit(10).build()
        val habit = Habit.create(GroupMember.create(group, member, GroupMemberRole.MEMBER), title, "설명", 3)
        val verify = HabitVerify.create(habit, date, "설명 $id", "https://example.com/$id.png", width, height)
        ReflectionTestUtils.setField(verify, "id", id)
        return verify
    }

    private fun stubQuery(result: List<HabitVerify>) {
        every { habitVerifyRepository.findPhotosByGroupId(any(), any(), any(), any(), any(), any()) } returns result
    }

    @Nested
    @DisplayName("권한과 요청 값 확인")
    inner class Guards {

        @Test
        @DisplayName("그룹이 없으면 404이고 멤버 확인과 조회는 하지 않는다")
        fun 그룹이_없다() {
            every { groupRepository.existsById(GROUP_ID) } returns false

            assertThatThrownBy { service.getPhotos(LOGIN_ID, GROUP_ID, 60, null, null) }
                .isInstanceOf(EntityNotFoundException::class.java)
                .hasMessage("존재하지 않는 그룹입니다.")

            verify(exactly = 0) { groupMemberRepository.existsByGroupIdAndMemberId(any(), any()) }
            verify(exactly = 0) { habitVerifyRepository.findPhotosByGroupId(any(), any(), any(), any(), any(), any()) }
        }

        @Test
        @DisplayName("그룹 멤버가 아니면 403이고 조회하지 않는다")
        fun 멤버가_아니다() {
            every { groupRepository.existsById(GROUP_ID) } returns true
            every { groupMemberRepository.existsByGroupIdAndMemberId(GROUP_ID, LOGIN_ID) } returns false

            assertThatThrownBy { service.getPhotos(LOGIN_ID, GROUP_ID, 60, null, null) }
                .isInstanceOf(ForbiddenException::class.java)

            verify(exactly = 0) { habitVerifyRepository.findPhotosByGroupId(any(), any(), any(), any(), any(), any()) }
        }

        @Test
        @DisplayName("멤버가 아니면 요청 값이 잘못되어도 400이 아니라 403이다 (권한 확인이 먼저)")
        fun 권한_확인이_먼저다() {
            every { groupRepository.existsById(GROUP_ID) } returns true
            every { groupMemberRepository.existsByGroupIdAndMemberId(GROUP_ID, LOGIN_ID) } returns false

            assertThatThrownBy { service.getPhotos(LOGIN_ID, GROUP_ID, 0, "garbage", null) }
                .isInstanceOf(ForbiddenException::class.java)
        }

        @ParameterizedTest(name = "size={0}")
        @ValueSource(ints = [0, -1, 121, Int.MAX_VALUE])
        @DisplayName("size가 1~120을 벗어나면 거절하고 조회하지 않는다")
        fun 잘못된_size(size: Int) {
            allowAccess()

            assertThatThrownBy { service.getPhotos(LOGIN_ID, GROUP_ID, size, null, null) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("size는 1 이상 120 이하여야 합니다.")

            verify(exactly = 0) { habitVerifyRepository.findPhotosByGroupId(any(), any(), any(), any(), any(), any()) }
        }

        @Test
        @DisplayName("형식이 맞지 않는 커서는 거절하고 조회하지 않는다")
        fun 잘못된_커서() {
            allowAccess()

            assertThatThrownBy { service.getPhotos(LOGIN_ID, GROUP_ID, 60, "garbage", null) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("잘못된 커서입니다.")

            verify(exactly = 0) { habitVerifyRepository.findPhotosByGroupId(any(), any(), any(), any(), any(), any()) }
        }
    }

    @Nested
    @DisplayName("조회 조건")
    inner class QueryArguments {

        @Test
        @DisplayName("승인된 인증만, 그 그룹에서, size보다 한 건 많이 요청한다 (필터와 커서가 없으면 null)")
        fun 기본_조건() {
            allowAccess()
            val pageable = slot<Pageable>()
            every {
                habitVerifyRepository.findPhotosByGroupId(GROUP_ID, HabitVerifyStatus.APPROVED, null, null, null, capture(pageable))
            } returns emptyList()

            service.getPhotos(LOGIN_ID, GROUP_ID, 60, null, null)

            assertThat(pageable.captured.pageNumber).isEqualTo(0)
            assertThat(pageable.captured.pageSize).isEqualTo(61)
        }

        @Test
        @DisplayName("size의 경계 1과 120도 허용하고 각각 2건, 121건을 요청한다")
        fun size_경계() {
            allowAccess()
            val pageables = mutableListOf<Pageable>()
            every { habitVerifyRepository.findPhotosByGroupId(any(), any(), any(), any(), any(), capture(pageables)) } returns emptyList()

            service.getPhotos(LOGIN_ID, GROUP_ID, 1, null, null)
            service.getPhotos(LOGIN_ID, GROUP_ID, 120, null, null)

            assertThat(pageables.map { it.pageSize }).containsExactly(2, 121)
        }

        @Test
        @DisplayName("커서와 멤버 필터를 쿼리 조건으로 그대로 넘긴다")
        fun 커서와_필터() {
            allowAccess()
            every {
                habitVerifyRepository.findPhotosByGroupId(GROUP_ID, HabitVerifyStatus.APPROVED, 7L, LocalDate.of(2026, 10, 20), 55L, any())
            } returns emptyList()

            service.getPhotos(LOGIN_ID, GROUP_ID, 60, "2026-10-20:55", 7L)

            verify(exactly = 1) {
                habitVerifyRepository.findPhotosByGroupId(GROUP_ID, HabitVerifyStatus.APPROVED, 7L, LocalDate.of(2026, 10, 20), 55L, any())
            }
        }
    }

    @Nested
    @DisplayName("다음 페이지 판단")
    inner class Paging {

        @Test
        @DisplayName("size보다 한 건 더 읽히면 마지막 한 건은 돌려주지 않고, 돌려준 마지막 항목으로 nextCursor를 만든다")
        fun 다음_페이지가_있다() {
            allowAccess()
            stubQuery(
                listOf(
                    photo(30, LocalDate.of(2026, 10, 26)),
                    photo(20, LocalDate.of(2026, 10, 25)),
                    photo(10, LocalDate.of(2026, 10, 24)),
                ),
            )

            val page = service.getPhotos(LOGIN_ID, GROUP_ID, 2, null, null)

            assertThat(page.items.map { it.id }).containsExactly(30L, 20L)
            assertThat(page.nextCursor).isEqualTo("2026-10-25:20")
        }

        @Test
        @DisplayName("정확히 size건이면 마지막 페이지라 nextCursor가 null이다")
        fun 정확히_size건이다() {
            allowAccess()
            stubQuery(listOf(photo(30, LocalDate.of(2026, 10, 26)), photo(20, LocalDate.of(2026, 10, 25))))

            val page = service.getPhotos(LOGIN_ID, GROUP_ID, 2, null, null)

            assertThat(page.items.map { it.id }).containsExactly(30L, 20L)
            assertThat(page.nextCursor).isNull()
        }

        @Test
        @DisplayName("size보다 적으면 nextCursor가 null이다")
        fun size보다_적다() {
            allowAccess()
            stubQuery(listOf(photo(30, LocalDate.of(2026, 10, 26))))

            val page = service.getPhotos(LOGIN_ID, GROUP_ID, 60, null, null)

            assertThat(page.items).hasSize(1)
            assertThat(page.nextCursor).isNull()
        }

        @Test
        @DisplayName("사진이 하나도 없으면 빈 목록이고 nextCursor는 null이다")
        fun 사진이_없다() {
            allowAccess()
            stubQuery(emptyList())

            val page = service.getPhotos(LOGIN_ID, GROUP_ID, 60, null, null)

            assertThat(page.items).isEmpty()
            assertThat(page.nextCursor).isNull()
        }

        @Test
        @DisplayName("size가 1이면 한 건씩 돌려주고 그 항목이 커서가 된다")
        fun size가_1이다() {
            allowAccess()
            stubQuery(listOf(photo(30, LocalDate.of(2026, 10, 26)), photo(20, LocalDate.of(2026, 10, 25))))

            val page = service.getPhotos(LOGIN_ID, GROUP_ID, 1, null, null)

            assertThat(page.items.map { it.id }).containsExactly(30L)
            assertThat(page.nextCursor).isEqualTo("2026-10-26:30")
        }
    }

    @Test
    @DisplayName("항목에 인증 id, 날짜, 습관 제목, 작성자 id와 닉네임, 설명, 사진 주소를 담는다")
    fun 응답_항목() {
        allowAccess()
        stubQuery(listOf(photo(30, LocalDate.of(2026, 10, 26), memberId = 7L, nickname = "영희", title = "물 마시기")))

        val item = service.getPhotos(LOGIN_ID, GROUP_ID, 60, null, null).items.single()

        assertThat(item.id).isEqualTo(30L)
        assertThat(item.verifyDate).isEqualTo(LocalDate.of(2026, 10, 26))
        assertThat(item.habitTitle).isEqualTo("물 마시기")
        assertThat(item.memberId).isEqualTo(7L)
        assertThat(item.memberNickname).isEqualTo("영희")
        assertThat(item.description).isEqualTo("설명 30")
        assertThat(item.imageUrl).isEqualTo("https://example.com/30.png")
        assertThat(item.imageWidth).isNull()
        assertThat(item.imageHeight).isNull()
    }

    @Test
    @DisplayName("저장된 사진 크기를 그대로 담는다")
    fun 응답_항목_크기() {
        allowAccess()
        stubQuery(listOf(photo(30, LocalDate.of(2026, 10, 26), width = 4032, height = 3024)))

        val item = service.getPhotos(LOGIN_ID, GROUP_ID, 60, null, null).items.single()

        assertThat(item.imageWidth).isEqualTo(4032)
        assertThat(item.imageHeight).isEqualTo(3024)
    }
}
