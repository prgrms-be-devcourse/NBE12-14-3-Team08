package com.back.domain.habitVerify.service

import com.back.domain.group.entity.Group
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.habit.entity.Habit
import com.back.domain.habit.repository.HabitRepository
import com.back.domain.habitVerify.repository.HabitVerifyRepository
import com.back.domain.member.entity.Member
import com.back.global.exception.BusinessRuleException
import com.back.global.exception.ForbiddenException
import com.back.global.storage.StorageService
import com.back.global.storage.UploadUrlResponse
import com.back.global.storage.VerificationImageType
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.util.ReflectionTestUtils
import java.util.Optional

@ExtendWith(MockKExtension::class)
class HabitVerifyServiceUploadTest {

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

    private lateinit var group: Group
    private lateinit var habit: Habit

    @BeforeEach
    fun setUp() {
        val member = Member.create("일반회원", "member", "pass1234")
        ReflectionTestUtils.setField(member, "id", 2L)

        group = Group.builder()
            .title("아침 기상 챌린지")
            .penalty("커피 1잔 쏘기")
            .memberLimit(10)
            .build()

        val groupMember = GroupMember.create(group, member, GroupMemberRole.MEMBER)
        habit = Habit.create(groupMember, "기상 후 스트레칭", "10분 스트레칭", 3)
        ReflectionTestUtils.setField(habit, "id", 50L)
    }

    @Test
    @DisplayName("내 진행 중인 습관이면 습관 사진 업로드 URL을 발급한다")
    fun 업로드_URL_발급_성공() {
        val expected = UploadUrlResponse("https://upload", "https://public/2/50/a.png")
        every { habitRepository.findByIdAndGroupMember_Member_Id(50L, 2L) } returns Optional.of(habit)
        every { storageService.createUploadUrl(VerificationImageType.HABIT, 2L, 50L, "today.png") } returns expected

        val response = habitVerifyService.createUploadUrl(2L, 50L, "today.png")

        assertThat(response).isEqualTo(expected)
    }

    @Test
    @DisplayName("내 습관이 아니면 ForbiddenException (403)")
    fun 내_습관_아님_403() {
        every { habitRepository.findByIdAndGroupMember_Member_Id(50L, 999L) } returns Optional.empty()

        assertThatThrownBy { habitVerifyService.createUploadUrl(999L, 50L, "today.png") }
            .isInstanceOf(ForbiddenException::class.java)

        verify(exactly = 0) { storageService.createUploadUrl(any(), any(), any(), any()) }
    }

    @Test
    @DisplayName("실패(FAILED)한 습관이면 IllegalStateException (409)")
    fun 실패한_습관_409() {
        habit.fail()
        every { habitRepository.findByIdAndGroupMember_Member_Id(50L, 2L) } returns Optional.of(habit)

        assertThatThrownBy { habitVerifyService.createUploadUrl(2L, 50L, "today.png") }
            .isInstanceOf(IllegalStateException::class.java)

        verify(exactly = 0) { storageService.createUploadUrl(any(), any(), any(), any()) }
    }

    @Test
    @DisplayName("종료된 방이면 BusinessRuleException (400)")
    fun 종료된_방_400() {
        group.finish()
        every { habitRepository.findByIdAndGroupMember_Member_Id(50L, 2L) } returns Optional.of(habit)

        assertThatThrownBy { habitVerifyService.createUploadUrl(2L, 50L, "today.png") }
            .isInstanceOf(BusinessRuleException::class.java)

        verify(exactly = 0) { storageService.createUploadUrl(any(), any(), any(), any()) }
    }
}
