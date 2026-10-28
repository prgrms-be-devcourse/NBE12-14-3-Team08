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
import com.back.global.entity.requiredId
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.Hibernate
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.data.domain.PageRequest
import java.time.LocalDate
import java.util.UUID

// 그룹 인증 사진 쿼리(findPhotosByGroupId)를 PostgreSQL에서 확인한다. (테스트마다 롤백)
// 서비스는 이 결과를 그대로 자르기만 하므로, 어떤 인증이 어떤 순서로 나오는지는 여기서 고정한다.
@DataJpaTest
class HabitVerifyPhotoRepositoryTest @Autowired constructor(
    private val habitVerifyRepository: HabitVerifyRepository,
    private val habitRepository: HabitRepository,
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val memberRepository: MemberRepository,
    private val entityManager: EntityManager
) {

    private class Participant(val member: Member, val habit: Habit)

    private lateinit var group: Group
    private lateinit var other: Group
    private lateinit var first: Participant
    private lateinit var second: Participant
    private lateinit var stranger: Participant

    private val approved = HabitVerifyStatus.APPROVED

    @BeforeEach
    fun setUp() {
        group = saveGroup()
        other = saveGroup()
        first = join(group, "첫째")
        second = join(group, "둘째")
        stranger = join(other, "남")
    }

    private fun uniqueName(prefix: String) = prefix + "-" + UUID.randomUUID().toString().substring(0, 8)

    private fun saveGroup(): Group =
        groupRepository.save(
            Group.builder().title("그룹").penalty("벌칙").password("1234").inviteCode(uniqueName("code")).memberLimit(10).build(),
        )

    private fun join(group: Group, nickname: String): Participant {
        val member = memberRepository.save(Member.create(nickname, uniqueName("user"), "pass1234"))
        val groupMember = groupMemberRepository.save(GroupMember.create(group, member, GroupMemberRole.MEMBER))
        return Participant(member, habitRepository.save(Habit.create(groupMember, "$nickname 습관", "설명", 3)))
    }

    private fun save(
        who: Participant,
        date: LocalDate,
        status: HabitVerifyStatus = approved,
        imageUrl: String? = "https://example.com/${UUID.randomUUID()}.png",
    ): HabitVerify {
        val verify = HabitVerify.create(who.habit, date, "설명", imageUrl)
        when (status) {
            HabitVerifyStatus.APPROVED -> verify.approve()
            HabitVerifyStatus.REJECTED -> verify.reject()
            HabitVerifyStatus.PENDING -> Unit
        }
        return habitVerifyRepository.save(verify)
    }

    private fun photos(
        group: Group = this.group,
        memberId: Long? = null,
        cursorDate: LocalDate? = null,
        cursorId: Long? = null,
        size: Int = 100,
    ): List<HabitVerify> {
        entityManager.flush()
        entityManager.clear()
        return habitVerifyRepository.findPhotosByGroupId(group.requiredId, approved, memberId, cursorDate, cursorId, PageRequest.of(0, size))
    }

    private fun ids(list: List<HabitVerify>) = list.map { it.requiredId }

    @Nested
    @DisplayName("어떤 인증이 나오는지")
    inner class Selection {

        @Test
        @DisplayName("승인되고 사진이 있는 인증만 나온다 (대기, 반려, 사진 없음, 빈 사진 주소 제외)")
        fun 승인되고_사진이_있는_인증만() {
            val ok = save(first, LocalDate.of(2026, 10, 20))
            save(first, LocalDate.of(2026, 10, 21), status = HabitVerifyStatus.PENDING)
            save(first, LocalDate.of(2026, 10, 22), status = HabitVerifyStatus.REJECTED)
            save(first, LocalDate.of(2026, 10, 23), imageUrl = null)
            save(first, LocalDate.of(2026, 10, 24), imageUrl = "")

            assertThat(ids(photos())).containsExactly(ok.requiredId)
        }

        @Test
        @DisplayName("다른 그룹의 인증은 나오지 않는다")
        fun 다른_그룹은_제외() {
            val mine = save(first, LocalDate.of(2026, 10, 20))
            save(stranger, LocalDate.of(2026, 10, 21))

            assertThat(ids(photos())).containsExactly(mine.requiredId)
            assertThat(photos(group = other)).hasSize(1)
        }

        @Test
        @DisplayName("멤버를 지정하면 그 멤버의 인증만 나온다")
        fun 멤버로_거른다() {
            val a = save(first, LocalDate.of(2026, 10, 20))
            save(second, LocalDate.of(2026, 10, 21))

            assertThat(ids(photos(memberId = first.member.requiredId))).containsExactly(a.requiredId)
        }

        @Test
        @DisplayName("이 그룹에 없는 멤버를 지정하면 빈 목록이다")
        fun 그룹_밖의_멤버() {
            save(first, LocalDate.of(2026, 10, 20))

            assertThat(photos(memberId = stranger.member.requiredId)).isEmpty()
        }

        @Test
        @DisplayName("인증이 없는 그룹은 빈 목록이다")
        fun 빈_그룹() {
            assertThat(photos()).isEmpty()
        }
    }

    @Nested
    @DisplayName("정렬")
    inner class Ordering {

        @Test
        @DisplayName("인증 날짜 내림차순이고, 같은 날짜는 id 내림차순이다")
        fun 날짜와_id_내림차순() {
            val oldest = save(first, LocalDate.of(2026, 10, 19))
            val sameDayLow = save(first, LocalDate.of(2026, 10, 20))
            val sameDayHigh = save(second, LocalDate.of(2026, 10, 20))
            val newest = save(second, LocalDate.of(2026, 10, 21))

            assertThat(ids(photos())).containsExactly(newest.requiredId, sameDayHigh.requiredId, sameDayLow.requiredId, oldest.requiredId)
        }

        @Test
        @DisplayName("날짜가 더 이른 인증은 id가 더 커도 뒤에 나온다 (id가 아니라 인증 날짜 기준)")
        fun id가_아니라_날짜_기준() {
            val newerDateSavedFirst = save(first, LocalDate.of(2026, 10, 21))
            val olderDateSavedLater = save(first, LocalDate.of(2026, 10, 19))

            assertThat(olderDateSavedLater.requiredId).isGreaterThan(newerDateSavedFirst.requiredId)
            assertThat(ids(photos())).containsExactly(newerDateSavedFirst.requiredId, olderDateSavedLater.requiredId)
        }
    }

    @Nested
    @DisplayName("커서")
    inner class Cursor {

        // 나오는 순서: newest(10/21) → sameDayHigh(10/20) → sameDayLow(10/20) → oldest(10/19)
        private lateinit var oldest: HabitVerify
        private lateinit var sameDayLow: HabitVerify
        private lateinit var sameDayHigh: HabitVerify
        private lateinit var newest: HabitVerify

        @BeforeEach
        fun saveFour() {
            oldest = save(first, LocalDate.of(2026, 10, 19))
            sameDayLow = save(first, LocalDate.of(2026, 10, 20))
            sameDayHigh = save(second, LocalDate.of(2026, 10, 20))
            newest = save(second, LocalDate.of(2026, 10, 21))
        }

        @Test
        @DisplayName("커서 항목은 포함하지 않고 그 다음 항목부터 나온다")
        fun 커서_다음부터() {
            assertThat(ids(photos(cursorDate = newest.verifyDate, cursorId = newest.requiredId)))
                .containsExactly(sameDayHigh.requiredId, sameDayLow.requiredId, oldest.requiredId)
        }

        @Test
        @DisplayName("같은 날짜에서는 id가 더 작은 항목만 나온다")
        fun 같은_날짜는_id가_작은_것만() {
            assertThat(ids(photos(cursorDate = sameDayHigh.verifyDate, cursorId = sameDayHigh.requiredId)))
                .containsExactly(sameDayLow.requiredId, oldest.requiredId)
        }

        @Test
        @DisplayName("같은 날짜의 id가 가장 작은 항목이 커서면 더 이른 날짜만 나온다")
        fun 같은_날짜의_마지막() {
            assertThat(ids(photos(cursorDate = sameDayLow.verifyDate, cursorId = sameDayLow.requiredId))).containsExactly(oldest.requiredId)
        }

        @Test
        @DisplayName("더 이른 날짜는 커서의 id와 상관없이 나오고, 더 늦은 날짜는 나오지 않는다")
        fun 날짜가_먼저_비교된다() {
            // 커서 id를 아주 작게 줘도 더 이른 날짜(10/19)는 나오고, 같은 날짜(10/20)는 id가 작아서 안 나온다
            assertThat(ids(photos(cursorDate = LocalDate.of(2026, 10, 20), cursorId = 1L))).containsExactly(oldest.requiredId)
            // 커서 id를 아주 크게 줘도 더 늦은 날짜(10/21)는 나오지 않는다
            assertThat(ids(photos(cursorDate = LocalDate.of(2026, 10, 20), cursorId = Long.MAX_VALUE)))
                .containsExactly(sameDayHigh.requiredId, sameDayLow.requiredId, oldest.requiredId)
        }

        @Test
        @DisplayName("마지막 항목이 커서면 빈 목록이다")
        fun 마지막_다음은_없다() {
            assertThat(photos(cursorDate = oldest.verifyDate, cursorId = oldest.requiredId)).isEmpty()
        }

        @Test
        @DisplayName("커서와 멤버 필터를 같이 쓸 수 있다")
        fun 커서와_필터를_같이() {
            assertThat(ids(photos(memberId = first.member.requiredId, cursorDate = newest.verifyDate, cursorId = newest.requiredId)))
                .containsExactly(sameDayLow.requiredId, oldest.requiredId)
        }

        @Test
        @DisplayName("커서 날짜만 있고 id가 없으면 같은 날짜 항목은 나오지 않는다 (서비스는 항상 둘을 함께 준다)")
        fun 커서는_짝으로_쓴다() {
            // id가 null이면 같은 날짜 비교가 참이 되지 않아 더 이른 날짜만 나온다
            assertThat(ids(photos(cursorDate = LocalDate.of(2026, 10, 20), cursorId = null))).containsExactly(oldest.requiredId)
        }
    }

    @Nested
    @DisplayName("크기와 연관 엔티티")
    inner class SizeAndFetch {

        @Test
        @DisplayName("요청한 크기만큼만 읽고 앞쪽(최신) 항목이 온다")
        fun 크기만큼만() {
            val newest = save(first, LocalDate.of(2026, 10, 22))
            val middle = save(first, LocalDate.of(2026, 10, 21))
            save(first, LocalDate.of(2026, 10, 20))

            assertThat(ids(photos(size = 2))).containsExactly(newest.requiredId, middle.requiredId)
        }

        @Test
        @DisplayName("습관, 그룹 멤버, 회원이 한 번에 채워져 있어 닉네임과 습관 제목을 읽어도 추가 쿼리가 없다")
        fun 연관_엔티티가_채워진다() {
            save(first, LocalDate.of(2026, 10, 20))

            val verify = photos().single()

            assertThat(Hibernate.isInitialized(verify.habit)).isTrue()
            assertThat(Hibernate.isInitialized(verify.habit.groupMember)).isTrue()
            assertThat(Hibernate.isInitialized(verify.habit.groupMember.member)).isTrue()
            assertThat(verify.habit.groupMember.member.nickname).isEqualTo("첫째")
            assertThat(verify.habit.title).isEqualTo("첫째 습관")
        }

        @Test
        @DisplayName("나간(LEFT) 멤버의 인증도 나온다 (멤버 상태로 거르지 않는다)")
        fun 나간_멤버의_인증도_나온다() {
            val saved = save(second, LocalDate.of(2026, 10, 20))
            second.habit.groupMember.leave()

            assertThat(ids(photos())).containsExactly(saved.requiredId)
        }
    }
}
