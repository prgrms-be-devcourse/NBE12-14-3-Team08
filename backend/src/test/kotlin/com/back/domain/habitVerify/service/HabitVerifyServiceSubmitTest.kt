package com.back.domain.habitVerify.service

import com.back.domain.group.entity.Group
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.habit.entity.Habit
import com.back.domain.habit.repository.HabitRepository
import com.back.domain.habitVerify.dto.HabitVerifyRequest
import com.back.domain.habitVerify.entity.HabitVerify
import com.back.domain.habitVerify.entity.HabitVerifyStatus
import com.back.domain.habitVerify.repository.HabitVerifyRepository
import com.back.domain.member.entity.Member
import com.back.global.exception.BusinessRuleException
import com.back.global.exception.ForbiddenException
import com.back.global.storage.StorageService
import com.back.global.storage.VerificationImageType
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.justRun
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional

// MockK는 정해 두지 않은 호출이 오면 실패한다(엄격). Mockito가 기본으로 넘기던 사진 검사(void)는
// 검사를 통과하는 테스트에서 justRun으로 명시한다.
@ExtendWith(MockKExtension::class)
class HabitVerifyServiceSubmitTest {

    @MockK
    private lateinit var habitRepository: HabitRepository

    @MockK
    private lateinit var habitVerifyRepository: HabitVerifyRepository

    @MockK
    private lateinit var storageService: StorageService

    @MockK
    private lateinit var groupMemberRepository: GroupMemberRepository

    @InjectMockKs
    private lateinit var habitVerifyService: HabitVerifyService

    private lateinit var habit: Habit

    @BeforeEach
    fun setUp() {
        val member = Member.create("일반회원", "member", "pass1234")
        ReflectionTestUtils.setField(member, "id", 2L)

        val group = Group.builder()
            .title("아침 기상 챌린지")
            .penalty("커피 1잔 쏘기")
            .memberLimit(10)
            .build()
        ReflectionTestUtils.setField(group, "createDate", LocalDateTime.now())

        val groupMember = GroupMember.create(group, member, GroupMemberRole.MEMBER)
        habit = Habit.create(groupMember, "기상 후 스트레칭", "10분 스트레칭", 3)
        ReflectionTestUtils.setField(habit, "id", 50L)
    }

    // 사진 검사를 통과시킨다 (Mockito에서는 void 메서드가 기본으로 아무것도 하지 않았다)
    private fun imageValidationPasses() {
        justRun { storageService.validateImageUrl(any(), any(), any(), any(), any()) }
    }

    @Nested
    @DisplayName("습관 인증 등록(create)")
    inner class Create {

        @Test
        @DisplayName("남의 습관이면 ForbiddenException(403), 사진 검사는 하지 않는다")
        fun 남의_습관_403() {
            every { habitRepository.findByIdAndGroupMember_Member_Id(50L, 999L) } returns Optional.empty()

            assertThatThrownBy {
                habitVerifyService.create(50L, HabitVerifyRequest("인증", "https://storage/a.png"), 999L)
            }
                .isInstanceOf(ForbiddenException::class.java)

            verify(exactly = 0) { storageService.validateImageUrl(any(), any(), any(), any(), any()) }
        }

        @Test
        @DisplayName("사진이 있으면 이 회원·이 습관 기준으로 검사한 뒤 저장한다")
        fun 사진_검사_후_저장() {
            every { habitRepository.findByIdAndGroupMember_Member_Id(50L, 2L) } returns Optional.of(habit)
            every { habitVerifyRepository.existsByHabitIdAndVerifyDate(50L, LocalDate.now()) } returns false
            every { habitVerifyRepository.save(any<HabitVerify>()) } answers { firstArg() }
            imageValidationPasses()

            habitVerifyService.create(50L, HabitVerifyRequest("인증", "https://storage/a.png"), 2L)

            verify { storageService.validateImageUrl(VerificationImageType.HABIT, 2L, 50L, "https://storage/a.png", null) }
            verify { habitVerifyRepository.save(any<HabitVerify>()) }
        }

        @Test
        @DisplayName("사진 검사에 실패하면 400이고 저장하지 않는다")
        fun 사진_검사_실패() {
            every { habitRepository.findByIdAndGroupMember_Member_Id(50L, 2L) } returns Optional.of(habit)
            every { habitVerifyRepository.existsByHabitIdAndVerifyDate(50L, LocalDate.now()) } returns false
            every { storageService.validateImageUrl(any(), any(), any(), any(), any()) } throws
                IllegalArgumentException("업로드한 사진만 사용할 수 있습니다.")

            assertThatThrownBy {
                habitVerifyService.create(50L, HabitVerifyRequest("인증", "https://evil.com/a.png"), 2L)
            }
                .isInstanceOf(IllegalArgumentException::class.java)

            verify(exactly = 0) { habitVerifyRepository.save(any<HabitVerify>()) }
        }

        @Test
        @DisplayName("포기·판정으로 실패(FAILED)한 습관이면 409이고, 사진 검사와 저장을 하지 않는다")
        fun 실패한_습관_409() {
            habit.fail()
            every { habitRepository.findByIdAndGroupMember_Member_Id(50L, 2L) } returns Optional.of(habit)

            assertThatThrownBy {
                habitVerifyService.create(50L, HabitVerifyRequest("인증", "https://storage/a.png"), 2L)
            }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessage("진행 중인 습관에만 인증을 등록할 수 있습니다. 현재 상태: FAILED")

            verify(exactly = 0) { storageService.validateImageUrl(any(), any(), any(), any(), any()) }
            verify(exactly = 0) { habitVerifyRepository.save(any<HabitVerify>()) }
        }

        @Test
        @DisplayName("종료된 방이면 BusinessRuleException(400)이고, 사진 검사와 저장을 하지 않는다")
        fun 종료된_방_400() {
            habit.groupMember.group.finish()
            every { habitRepository.findByIdAndGroupMember_Member_Id(50L, 2L) } returns Optional.of(habit)

            assertThatThrownBy {
                habitVerifyService.create(50L, HabitVerifyRequest("인증", "https://storage/a.png"), 2L)
            }
                .isInstanceOf(BusinessRuleException::class.java)
                .hasMessage("이미 종료된 그룹에서는 습관 인증을 등록할 수 없습니다.")

            verify(exactly = 0) { storageService.validateImageUrl(any(), any(), any(), any(), any()) }
            verify(exactly = 0) { habitVerifyRepository.save(any<HabitVerify>()) }
        }
    }

    @Nested
    @DisplayName("습관 인증 재제출(resubmit)")
    inner class Resubmit {

        private fun verifyIn(status: HabitVerifyStatus): HabitVerify {
            val habitVerify = HabitVerify.create(habit, LocalDate.now(), "처음 인증", "https://storage/old.png")
            ReflectionTestUtils.setField(habitVerify, "id", 70L)
            ReflectionTestUtils.setField(habitVerify, "status", status)
            ReflectionTestUtils.setField(habitVerify, "createDate", LocalDateTime.now())
            return habitVerify
        }

        @Test
        @DisplayName("남의 습관이면 ForbiddenException(403), 사진 검사는 하지 않는다")
        fun 남의_습관_403() {
            every { habitRepository.findByIdAndGroupMember_Member_Id(50L, 999L) } returns Optional.empty()

            assertThatThrownBy {
                habitVerifyService.resubmit(50L, 70L, HabitVerifyRequest("수정", "https://storage/a.png"), 999L)
            }
                .isInstanceOf(ForbiddenException::class.java)

            verify(exactly = 0) { storageService.validateImageUrl(any(), any(), any(), any(), any()) }
        }

        @Test
        @DisplayName("반려되지 않은 인증이면 사진 검사 전에 409")
        fun 반려_아님_409() {
            val habitVerify = verifyIn(HabitVerifyStatus.APPROVED)
            every { habitRepository.findByIdAndGroupMember_Member_Id(50L, 2L) } returns Optional.of(habit)
            every { habitVerifyRepository.findByIdAndHabitId(70L, 50L) } returns habitVerify

            assertThatThrownBy {
                habitVerifyService.resubmit(50L, 70L, HabitVerifyRequest("수정", "https://storage/a.png"), 2L)
            }
                .isInstanceOf(IllegalStateException::class.java)

            verify(exactly = 0) { storageService.validateImageUrl(any(), any(), any(), any(), any()) }
        }

        @Test
        @DisplayName("지금 붙어 있는 사진을 기존 사진으로 넘겨 검사한 뒤 PENDING이 된다")
        fun 재제출_성공() {
            val habitVerify = verifyIn(HabitVerifyStatus.REJECTED)
            every { habitRepository.findByIdAndGroupMember_Member_Id(50L, 2L) } returns Optional.of(habit)
            every { habitVerifyRepository.findByIdAndHabitId(70L, 50L) } returns habitVerify
            imageValidationPasses()

            habitVerifyService.resubmit(50L, 70L, HabitVerifyRequest("수정", "https://storage/old.png"), 2L)

            verify {
                storageService.validateImageUrl(
                    VerificationImageType.HABIT, 2L, 50L,
                    "https://storage/old.png", "https://storage/old.png",
                )
            }
            assertThat(habitVerify.status).isEqualTo(HabitVerifyStatus.PENDING)
        }

        @Test
        @DisplayName("사진 검사에 실패하면 400이고 인증 상태는 그대로다")
        fun 사진_검사_실패() {
            val habitVerify = verifyIn(HabitVerifyStatus.REJECTED)
            every { habitRepository.findByIdAndGroupMember_Member_Id(50L, 2L) } returns Optional.of(habit)
            every { habitVerifyRepository.findByIdAndHabitId(70L, 50L) } returns habitVerify
            every { storageService.validateImageUrl(any(), any(), any(), any(), any()) } throws
                IllegalArgumentException("본인이 업로드한 사진만 사용할 수 있습니다.")

            assertThatThrownBy {
                habitVerifyService.resubmit(
                    50L, 70L, HabitVerifyRequest("수정", "https://storage/someone-else.png"), 2L
                )
            }
                .isInstanceOf(IllegalArgumentException::class.java)

            assertThat(habitVerify.status).isEqualTo(HabitVerifyStatus.REJECTED)
            assertThat(habitVerify.imageUrl).isEqualTo("https://storage/old.png")
        }

        @Test
        @DisplayName("그 인증이 속한 주의 마감이 지났으면 409이고, 사진 검사 없이 반려 상태 그대로다")
        fun 마감_지남_409() {
            // 8일 전에 제출했으면 그 주 마감(제출 후 최대 7일 안)은 요일과 관계없이 이미 지났다
            val habitVerify = verifyIn(HabitVerifyStatus.REJECTED)
            ReflectionTestUtils.setField(habitVerify, "createDate", LocalDateTime.now().minusDays(8))
            every { habitRepository.findByIdAndGroupMember_Member_Id(50L, 2L) } returns Optional.of(habit)
            every { habitVerifyRepository.findByIdAndHabitId(70L, 50L) } returns habitVerify

            assertThatThrownBy {
                habitVerifyService.resubmit(50L, 70L, HabitVerifyRequest("수정", "https://storage/new.png"), 2L)
            }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessage("마감일이 지나 더 이상 수정할 수 없습니다.")

            verify(exactly = 0) { storageService.validateImageUrl(any(), any(), any(), any(), any()) }
            assertThat(habitVerify.status).isEqualTo(HabitVerifyStatus.REJECTED)
        }

        @Test
        @DisplayName("종료된 방이면 BusinessRuleException(400)이고, 사진 검사 없이 반려 상태 그대로다")
        fun 종료된_방_400() {
            val habitVerify = verifyIn(HabitVerifyStatus.REJECTED)
            habit.groupMember.group.finish()
            every { habitRepository.findByIdAndGroupMember_Member_Id(50L, 2L) } returns Optional.of(habit)

            assertThatThrownBy {
                habitVerifyService.resubmit(50L, 70L, HabitVerifyRequest("수정", "https://storage/new.png"), 2L)
            }
                .isInstanceOf(BusinessRuleException::class.java)
                .hasMessage("이미 종료된 그룹의 습관 인증은 수정할 수 없습니다.")

            verify(exactly = 0) { storageService.validateImageUrl(any(), any(), any(), any(), any()) }
            assertThat(habitVerify.status).isEqualTo(HabitVerifyStatus.REJECTED)
        }
    }
}
