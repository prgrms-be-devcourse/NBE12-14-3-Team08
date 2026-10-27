package com.back.domain.groupMember.service

import com.back.domain.group.dto.GroupRequest
import com.back.domain.group.repository.GroupRepository
import com.back.domain.group.service.GroupService
import com.back.domain.groupMember.entity.GroupMemberStatus
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.member.entity.Member
import com.back.domain.member.repository.MemberRepository
import com.back.global.entity.requiredId
import com.back.global.exception.BusinessRuleException
import com.back.global.exception.ForbiddenException
import com.back.global.exception.GroupLimitExceededException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// 참여 시 비밀번호 확인(BCrypt)을 그룹 잠금 밖에서 하도록 바꾼 뒤에도 정원이 지켜지는지 실제 DB로 확인한다.
// - 틀린 비밀번호 요청은 잠금을 잡지 않고 거절되며 자리를 차지하지 않는다.
// - 비밀번호를 확인하는 사이 방장이 정원을 줄여도, 잠금 안에서 최신 정원으로 다시 판단한다.
// 스레드마다 별도 트랜잭션이 필요해 @Transactional을 쓰지 않고, 끝나면 만든 데이터를 지운다.
@SpringBootTest
class GroupMemberJoinConcurrencyTest @Autowired constructor(
    private val groupMemberService: GroupMemberService,
    private val groupService: GroupService,
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder
) {

    private val createdGroupIds = mutableListOf<Long>()
    private val createdMemberIds = mutableListOf<Long>()

    @AfterEach
    fun tearDown() {
        createdGroupIds.forEach { groupId ->
            groupMemberRepository.deleteAll(groupMemberRepository.findAllByGroupId(groupId))
            groupRepository.deleteById(groupId)
        }
        memberRepository.deleteAllById(createdMemberIds)
    }

    @Test
    @DisplayName("틀린 비밀번호 요청이 절반 섞여 동시에 참여해도, 맞는 비밀번호로 빈자리만큼만 들어오고 틀린 요청은 자리를 차지하지 않는다")
    fun 틀린_비밀번호가_섞인_동시_참여() {
        val (groupId, inviteCode) = createGroup(memberLimit = 5)
        val correctApplicants = createMembers(10)
        val wrongApplicants = createMembers(10)

        val results = runConcurrently(
            correctApplicants.map { { joinResult(inviteCode, it, ROOM_PASSWORD) } } +
                wrongApplicants.map { { joinResult(inviteCode, it, "wrong-password") } }
        )

        assertThat(results.count { it == JoinResult.SUCCESS }).isEqualTo(4)
        // 정원이 찬 뒤에 도착한 요청은 비밀번호를 확인하기 전에 정원 초과로 거절되므로, 틀린 비밀번호 요청도 정원 초과가 될 수 있다
        assertThat(results.count { it == JoinResult.LIMIT_EXCEEDED || it == JoinResult.WRONG_PASSWORD }).isEqualTo(16)
        assertThat(results.count { it == JoinResult.WRONG_PASSWORD }).isLessThanOrEqualTo(10)
        assertThat(results.count { it == JoinResult.OTHER_ERROR }).isZero()
        assertThat(activeMemberCount(groupId)).isEqualTo(5)

        val joinedMemberIds = groupMemberRepository.findAllByGroupId(groupId).map { it.member.requiredId }
        assertThat(joinedMemberIds).doesNotContainAnyElementsOf(wrongApplicants.map { it.requiredId })
    }

    @Test
    @DisplayName("참여 요청이 비밀번호를 확인하는 사이 방장이 정원을 줄이면, 줄어든 정원을 넘겨 들어오지 않는다")
    fun 참여와_정원_변경이_동시에_일어나도_정원을_넘지_않는다() {
        val (groupId, inviteCode) = createGroup(memberLimit = 10)
        val owner = createdMemberIds.last()
        val applicants = createMembers(9)
        val reducedLimit = 3

        val joinTasks: List<() -> Any> = applicants.map { { joinResult(inviteCode, it, ROOM_PASSWORD) } }
        val updateTask: () -> Any = {
            // 참여 요청들이 잠금 없이 그룹을 읽고 비밀번호를 확인하기 시작한 뒤에 정원을 줄인다
            Thread.sleep(UPDATE_DELAY_MILLIS)
            try {
                groupService.updateGroup(
                    groupId,
                    owner,
                    GroupRequest.Update(null, null, null, null, null, reducedLimit)
                )
                UpdateResult.UPDATED
            } catch (e: BusinessRuleException) {
                UpdateResult.REJECTED
            }
        }

        val results = runConcurrently(joinTasks + updateTask)

        val finalLimit = groupRepository.findById(groupId).orElseThrow().memberLimit
        val finalCount = activeMemberCount(groupId)
        val successCount = results.count { it == JoinResult.SUCCESS }

        assertThat(results.count { it == JoinResult.OTHER_ERROR }).isZero()
        assertThat(finalCount).isLessThanOrEqualTo(finalLimit.toLong())
        assertThat(finalCount).isEqualTo(successCount + 1L)
        // 이 순서(정원 변경이 비밀번호 확인 중에 끝남)에서는 변경이 성공하고 줄어든 정원까지만 들어와야 한다.
        // 아주 느린 환경에서 참여가 정원 변경보다 먼저 끝나면 아래 세 줄만 실패할 수 있다(위의 정원 초과 확인은 순서와 상관없이 유효).
        assertThat(results).contains(UpdateResult.UPDATED)
        assertThat(finalLimit).isEqualTo(reducedLimit)
        assertThat(successCount).isEqualTo(reducedLimit - 1)
    }

    @Test
    @DisplayName("연결 풀보다 많은 참여 요청이 동시에 와도 연결을 기다리다 멈추지 않는다")
    fun 연결_풀보다_많은_동시_참여도_멈추지_않는다() {
        // 참여 요청 하나가 연결을 2개 쓰면(바깥에서 쥔 연결 + 잠금 구간의 새 연결), 풀 크기만큼 동시에 오는 순간
        // 모두 두 번째 연결을 기다리다 연결 대기 한도(30초)에 걸려 실패한다. 테스트 풀(20)보다 많이 보낸다.
        val (groupId, inviteCode) = createGroup(memberLimit = 5)
        val applicants = createMembers(POOL_EXCEEDING_APPLICANTS)

        val startedAt = System.nanoTime()
        val results = runConcurrently(applicants.map { { joinResult(inviteCode, it, ROOM_PASSWORD) } })
        val elapsedSeconds = (System.nanoTime() - startedAt) / 1_000_000_000.0

        assertThat(results.count { it == JoinResult.OTHER_ERROR }).isZero()
        assertThat(results.count { it == JoinResult.SUCCESS }).isEqualTo(4)
        assertThat(activeMemberCount(groupId)).isEqualTo(5)
        assertThat(elapsedSeconds).isLessThan(20.0)
    }

    private fun createGroup(memberLimit: Int): Pair<Long, String> {
        val owner = createMembers(1).single()
        val created = groupService.createGroup(
            owner.requiredId,
            GroupRequest.Create(
                "동시 참여 테스트 방",
                null,
                LocalDate.now().plusDays(7),
                "커피 쏘기",
                ROOM_PASSWORD,
                memberLimit
            )
        )
        val groupId = requireNotNull(created.id) { "그룹 ID가 없습니다." }
        createdGroupIds.add(groupId)
        return groupId to created.inviteCode
    }

    private fun createMembers(count: Int): List<Member> {
        val encodedPassword = passwordEncoder.encode("pw1234!")
            ?: throw IllegalStateException("비밀번호 인코딩에 실패했습니다.")
        return (1..count).map {
            val username = "join-concur-" + UUID.randomUUID().toString().substring(0, 12)
            memberRepository.save(Member.create(username, username, encodedPassword))
                .also { member -> createdMemberIds.add(member.requiredId) }
        }
    }

    private fun joinResult(inviteCode: String, member: Member, password: String): JoinResult =
        try {
            groupMemberService.joinGroup(inviteCode, member.requiredId, GroupRequest.Join(password))
            JoinResult.SUCCESS
        } catch (e: GroupLimitExceededException) {
            JoinResult.LIMIT_EXCEEDED
        } catch (e: ForbiddenException) {
            JoinResult.WRONG_PASSWORD
        } catch (e: Exception) {
            e.printStackTrace()
            JoinResult.OTHER_ERROR
        }

    // 모든 작업을 준비시킨 뒤 한꺼번에 출발시키고, 각 작업의 결과를 모은다
    private fun runConcurrently(tasks: List<() -> Any>): List<Any> {
        val executor = Executors.newFixedThreadPool(tasks.size)
        val ready = CountDownLatch(tasks.size)
        val start = CountDownLatch(1)
        val results = ConcurrentLinkedQueue<Any>()

        tasks.forEach { task ->
            executor.submit {
                ready.countDown()
                start.await()
                results.add(task())
            }
        }

        ready.await()
        start.countDown()
        executor.shutdown()
        check(executor.awaitTermination(1, TimeUnit.MINUTES)) { "동시 실행이 1분 안에 끝나지 않았습니다." }
        return results.toList()
    }

    private fun activeMemberCount(groupId: Long): Long =
        groupMemberRepository.countByGroupIdAndStatus(groupId, GroupMemberStatus.ACTIVE)

    private enum class JoinResult { SUCCESS, LIMIT_EXCEEDED, WRONG_PASSWORD, OTHER_ERROR }

    private enum class UpdateResult { UPDATED, REJECTED }

    companion object {
        private const val ROOM_PASSWORD = "room1234"
        // BCrypt 한 번(약 80ms)보다 짧게 잡아, 참여 요청들이 비밀번호를 확인하는 도중에 정원 변경이 끝나게 한다
        private const val UPDATE_DELAY_MILLIS = 20L
        // 테스트 프로필의 연결 풀 최대 크기(20)보다 많게
        private const val POOL_EXCEEDING_APPLICANTS = 40
    }
}
