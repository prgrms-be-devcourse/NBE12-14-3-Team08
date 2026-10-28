package com.back.global.init

import com.back.domain.group.entity.Group
import com.back.domain.group.entity.GroupStatus
import com.back.domain.group.repository.GroupRepository
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.habit.entity.Habit
import com.back.domain.habit.entity.HabitStatus
import com.back.domain.habit.repository.HabitRepository
import com.back.domain.habitVerify.entity.HabitVerify
import com.back.domain.habitVerify.repository.HabitVerifyRepository
import com.back.domain.member.entity.Member
import com.back.domain.member.repository.MemberRepository
import com.back.domain.member.service.AuthTokenService
import com.back.domain.penaltyverify.entity.PenaltyVerify
import com.back.domain.penaltyverify.repository.PenaltyVerifyRepository
import com.back.global.entity.requiredId
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import kotlin.jvm.optionals.getOrNull

@Profile("local")
@Component
class TestDataInit(
    private val memberRepository: MemberRepository,
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val habitRepository: HabitRepository,
    private val habitVerifyRepository: HabitVerifyRepository,
    private val penaltyVerifyRepository: PenaltyVerifyRepository,
    private val passwordEncoder: PasswordEncoder,
    private val authTokenService: AuthTokenService,
) : CommandLineRunner {

    @Transactional
    override fun run(vararg args: String) {
        if (!groupRepository.existsByInviteCode("MOGAK100")) {
            seedBaseData()
        }
        // 기본 데이터가 이미 있는 DB에도 갤러리 예시 사진을 한 번만 채워 넣는다
        seedGalleryPhotos()
        // 방장asd가 방장인 완료된 방 예시도 같은 이유로 한 번만 채워 넣는다
        seedFinishedRoomForOwner()
        // 멤버가 많은 방(멤버 목록 스크롤 확인용)도 한 번만 채워 넣는다
        seedCrowdedRoomForOwner()
    }

    private fun seedBaseData() {

        // Spring Security의 encode()는 반환 타입이 nullable이라 MemberService와 같은 방식으로 확인한다
        val encodedPassword = checkNotNull(passwordEncoder.encode("1234")) { "비밀번호를 암호화하지 못했습니다." }

        // ====================================================
        // [GROUP 1] 모각코 챌린지 1기 (벌칙: 스타벅스 커피 쏘기)
        // ====================================================
        val ownerMember = getOrCreateMember("asd", "방장asd", encodedPassword)
        val chulsoo = getOrCreateMember("chulsoo", "김철수", encodedPassword)
        val younghee = getOrCreateMember("younghee", "이영희", encodedPassword)
        val minsoo = getOrCreateMember("minsoo", "박민수", encodedPassword)
        val jiwon = getOrCreateMember("jiwon", "이지원", encodedPassword)
        val hyunwoo = getOrCreateMember("hyunwoo", "정현우", encodedPassword)

        val group1 = groupRepository.save(
            Group(
                title = "모각코 챌린지 1기",
                description = "매일 꾸준히 코딩하고 습관 인증하는 스터디 그룹입니다.",
                deadline = LocalDate.now().plusDays(30),
                penalty = "스타벅스 커피 쏘기",
                password = encodedPassword,
                inviteCode = "MOGAK100",
                memberLimit = 10,
                status = GroupStatus.ACTIVE,
            )
        )

        val gmOwner = createGroupMember(group1, ownerMember, GroupMemberRole.OWNER)
        val gmChulsoo = createGroupMember(group1, chulsoo, GroupMemberRole.MEMBER)
        val gmYounghee = createGroupMember(group1, younghee, GroupMemberRole.MEMBER)
        val gmMinsoo = createGroupMember(group1, minsoo, GroupMemberRole.MEMBER)
        val gmJiwon = createGroupMember(group1, jiwon, GroupMemberRole.MEMBER)
        val gmHyunwoo = createGroupMember(group1, hyunwoo, GroupMemberRole.MEMBER)

        // 1인당 1개씩 현재 진행 중인 [ACTIVE] 습관 생성 (그룹 상세 페이지에 뜰 습관들)
        createActiveHabit(gmOwner, "아침 8시 기상하기", "기상 후 이불 개고 사진 찍기", 5)
        val hActiveChulsoo = createActiveHabit(gmChulsoo, "알고리즘 1문제 풀기", "백준 골드 문제 풀이", 7)
        val hActiveYounghee = createActiveHabit(gmYounghee, "하루 물 2L 마시기", "텀블러 채우고 마실 때마다 체크", 7)
        val hActiveMinsoo = createActiveHabit(gmMinsoo, "매일 기술 블로그 1포스팅", "TIL 작성 및 잔디 심기", 5)
        val hActiveJiwon = createActiveHabit(gmJiwon, "스쿼트 100회 하기", "하체 운동 및 스트레칭 30분", 3)
        val hActiveHyunwoo = createActiveHabit(gmHyunwoo, "CS 전공 서적 30p 읽기", "네트워크/운영체제 정리", 5)

        // Group 1 [PENDING] 습관 인증 데이터 (관리자 습관 인증 대기 카드 테스트용)
        createPendingHabitVerify(hActiveChulsoo, "백준 12865번 평범한 배낭 문제 DP로 풀어서 통과했습니다!", "https://images.unsplash.com/photo-1555066931-4365d14bab8c?w=600&auto=format&fit=crop&q=60")
        createPendingHabitVerify(hActiveYounghee, "오늘 500ml 텀블러로 4번 채워서 2L 완수했습니다!", "https://images.unsplash.com/photo-1548839140-29a749e1bc4e?w=600&auto=format&fit=crop&q=60")
        createPendingHabitVerify(hActiveMinsoo, "Spring JPA N+1 문제 해결 및 Fetch Join 정리 포스팅 올렸습니다.", "https://images.unsplash.com/photo-1499750310107-5fef28a66643?w=600&auto=format&fit=crop&q=60")
        createPendingHabitVerify(hActiveJiwon, "스쿼트 25회 4세트 완료했습니다. 허벅지 불타네요!", "https://images.unsplash.com/photo-1517838277536-f5f99be501cd?w=600&auto=format&fit=crop&q=60")
        createPendingHabitVerify(hActiveHyunwoo, "운영체제 가상 메모리와 페이징 기법 챕터 완독했습니다.", "https://images.unsplash.com/photo-1456513080510-7bf3a84b82f8?w=600&auto=format&fit=crop&q=60")

        // 과거에 실패하여 벌칙 인증이 걸려있는 [FAILED] 습관들 (관리자 대시보드 검토용)
        val hFailedChulsoo1 = createFailedHabit(gmChulsoo, "영어 단어 30개 외우기", "토익 보카 1챕터 암기", 7)
        val hFailedYounghee1 = createFailedHabit(gmYounghee, "밤 12시 전 취침하기", "수면 패턴 정상화 프로젝트", 7)
        val hFailedMinsoo1 = createFailedHabit(gmMinsoo, "러닝 3km 달리기", "퇴근 후 공원 트랙 러닝", 4)
        val hFailedJiwon1 = createFailedHabit(gmJiwon, "설탕 음료 안 마시기", "당류 줄이기 프로젝트", 5)
        val hFailedHyunwoo1 = createFailedHabit(gmHyunwoo, "매일 영양제 챙겨먹기", "비타민C, 오메가3 섭취", 7)
        val hFailedChulsoo2 = createFailedHabit(gmChulsoo, "일기 3줄 쓰기", "하루 회고 일기 작성", 7)
        val hFailedYounghee2 = createFailedHabit(gmYounghee, "스트레칭 15분", "목/어깨 폼롤러 스트레칭", 7)
        val hFailedMinsoo2 = createFailedHabit(gmMinsoo, "계단으로 출퇴근하기", "엘리베이터 금지 챌린지", 5)

        // Group 1 벌칙 대기 인증 데이터 (8건 세팅)
        createPendingPenalty(gmChulsoo, hFailedChulsoo1, "영단어 암기 깜빡 놓쳐서 약속대로 스타벅스 아메리카노 테이크아웃해서 인증합니다! ㅠㅠ", "https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?w=600&auto=format&fit=crop&q=60")
        createPendingPenalty(gmYounghee, hFailedYounghee1, "새벽 2시에 자버렸습니다 ㅠㅠ 팀원분들 드실 스타벅스 커피 쿠폰 구매해서 인증 올려요!", "https://images.unsplash.com/photo-1544787219-7f47ccb76574?w=600&auto=format&fit=crop&q=60")
        createPendingPenalty(gmMinsoo, hFailedMinsoo1, "비가 와서 러닝을 못 뛰었습니다... 반성의 의미로 스타벅스 콜드브루 사들고 출근 인증합니다.", "https://images.unsplash.com/photo-1509042239860-f550ce710b93?w=600&auto=format&fit=crop&q=60")
        createPendingPenalty(gmJiwon, hFailedJiwon1, "탄산음료 마셔버렸습니다 흑흑.. 벌칙으로 스터디룸 가는 길에 스벅 들러서 커피 픽업했습니다!", "https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?w=600&auto=format&fit=crop&q=60")
        createPendingPenalty(gmHyunwoo, hFailedHyunwoo1, "영양제 챙겨먹는 걸 깜빡했네요. 팀원들과 나눠 마시려고 스타벅스 따뜻한 라떼 주문 인증합니다.", "https://images.unsplash.com/photo-1517256064527-09c73fc73e38?w=600&auto=format&fit=crop&q=60")
        createPendingPenalty(gmChulsoo, hFailedChulsoo2, "피곤해서 일기 쓰다 잠들었습니다.. 벌칙 룰대로 스타벅스 아이스 아메리카노 마시며 반성 중입니다.", "https://images.unsplash.com/photo-1511920170033-f8396924c348?w=600&auto=format&fit=crop&q=60")
        createPendingPenalty(gmYounghee, hFailedYounghee2, "야근하느라 스트레칭 실패했네요 ㅠㅠ 벌칙 수행으로 아침 일찍 스벅 와서 모닝커피 샀습니다!", "https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb?w=600&auto=format&fit=crop&q=60")
        createPendingPenalty(gmMinsoo, hFailedMinsoo2, "다리가 아파서 엘리베이터 타버렸습니다. 방 공통 벌칙대로 스타벅스 디카페인 한 잔 구매 인증 제출합니다.", "https://images.unsplash.com/photo-1572442388796-11668ba67e53?w=600&auto=format&fit=crop&q=60")

        // ====================================================
        // [GROUP 2] 다른 팀: 헬스 & 피트니스 챌린지 (벌칙: 교촌치킨 쏘기)
        // ====================================================
        val otherOwner = getOrCreateMember("gym_king", "헬스왕김코치", encodedPassword)
        val otherMember1 = getOrCreateMember("runner_park", "박러너", encodedPassword)
        val otherMember2 = getOrCreateMember("diet_choi", "최다이어트", encodedPassword)

        val group2 = groupRepository.save(
            Group(
                title = "오운완 챌린지 2기",
                description = "하루 1시간 무조건 쇠질하는 헬스 모임",
                deadline = LocalDate.now().plusDays(20),
                penalty = "교촌치킨 쏘기",
                password = encodedPassword,
                inviteCode = "FITNESS200",
                memberLimit = 5,
                status = GroupStatus.ACTIVE,
            )
        )

        val gmOtherOwner = createGroupMember(group2, otherOwner, GroupMemberRole.OWNER)
        val gmOther1 = createGroupMember(group2, otherMember1, GroupMemberRole.MEMBER)
        val gmOther2 = createGroupMember(group2, otherMember2, GroupMemberRole.MEMBER)

        createActiveHabit(gmOtherOwner, "스트레칭 20분", "폼롤러 스트레칭 매일 하기", 3)
        createActiveHabit(gmOther1, "매일 벤치프레스 5세트", "가슴 운동 루틴 수행", 5)
        createActiveHabit(gmOther2, "야식 금지 (저녁 8시 이후)", "식단 조절하기", 7)

        val hFailedOther1 = createFailedHabit(gmOther1, "매일 유산소 40분", "트레드밀 달리기", 5)
        val hFailedOther2 = createFailedHabit(gmOther2, "하루 단백질 100g 채우기", "닭가슴살 섭취", 7)
        val hFailedOtherOwner = createFailedHabit(gmOtherOwner, "스쿼트 150kg 도전", "하체 최고 중량 도전", 3)

        createPendingPenalty(gmOther1, hFailedOther1, "[다른 팀 데이터] 유산소 빼먹어서 교촌 허니콤보 기프티콘 단톡방에 쏩니다!", "https://images.unsplash.com/photo-1626082927389-6cd097cdc6ec?w=600&auto=format&fit=crop&q=60")
        createPendingPenalty(gmOther2, hFailedOther2, "[다른 팀 데이터] 단백질 대신 피자 먹고 말았습니다... 벌칙으로 치킨 배달 주문 인증합니다.", "https://images.unsplash.com/photo-1562967914-608f82629710?w=600&auto=format&fit=crop&q=60")
        createPendingPenalty(gmOtherOwner, hFailedOtherOwner, "[다른 팀 데이터] 방장인데 하체 운동 실패했네요. 솔선수범해서 레드콤보 모바일 상품권 올립니다!", "https://images.unsplash.com/photo-1527477396000-e27163b481c2?w=600&auto=format&fit=crop&q=60")

        // ====================================================
        // [GROUP 3] 완료된 챌린지 (FINISH 상태, 기간: 2026-10-01 ~ 2026-10-14)
        // ====================================================
        val finishOwner = getOrCreateMember("finish_owner", "완료방장", encodedPassword)
        val finishMember1 = getOrCreateMember("finish_member1", "완료멤버1", encodedPassword)
        val finishMember2 = getOrCreateMember("finish_member2", "완료멤버2", encodedPassword)

        val group3 = groupRepository.save(
            Group(
                title = "독서 챌린지 10월 완료",
                description = "10월 한 달 동안 매일 책 30페이지 읽기",
                deadline = LocalDate.of(2026, 10, 14),
                penalty = "스타벅스 아메리카노 쏘기",
                password = encodedPassword,
                inviteCode = "FINISH300",
                memberLimit = 5,
                status = GroupStatus.FINISH,
            )
        )

        val gmFinishOwner = createGroupMember(group3, finishOwner, GroupMemberRole.OWNER)
        val gmFinish1 = createGroupMember(group3, finishMember1, GroupMemberRole.MEMBER)
        val gmFinish2 = createGroupMember(group3, finishMember2, GroupMemberRole.MEMBER)

        // 완료된 습관들 (SUCCESS 상태)
        val hFinishOwner = createActiveHabit(gmFinishOwner, "매일 책 30페이지 읽기", "기술 서적 독서", 14)
        val hFinish1 = createActiveHabit(gmFinish1, "영어 단어 50개 외우기", "토익 단어 암기", 14)
        val hFinish2 = createActiveHabit(gmFinish2, "매일 10분 명상", "마음챙김 명상", 14)

        // 완료된 습관 인증 데이터 (APPROVED 상태)
        for (day in 1..14) {
            val verifyDate = LocalDate.of(2026, 10, day)
            createApprovedHabitVerify(hFinishOwner, verifyDate, "${day}일차 독서 완료 - 기술 서적 30페이지 읽음", "https://images.unsplash.com/photo-1512820790803-83ca734da794?w=600&auto=format&fit=crop&q=60")

            if (day <= 12) { // 멤버1은 12일만 완료
                createApprovedHabitVerify(hFinish1, verifyDate, "${day}일차 단어 암기 완료 - 50개 외움", "https://images.unsplash.com/photo-1456324504439-367cee3b3c32?w=600&auto=format&fit=crop&q=60")
            }

            if (day <= 10) { // 멤버2는 10일만 완료
                createApprovedHabitVerify(hFinish2, verifyDate, "${day}일차 명상 완료 - 10분 집중", "https://images.unsplash.com/photo-1506126613408-eca07ce68773?w=600&auto=format&fit=crop&q=60")
            }
        }

        println("=================================================================")
        println(">>> [TEST DATA INITIALIZED] 습관/벌칙 PENDING 데이터 세팅 완료!")
        println(">>> [Group 1] ID: ${group1.id} (습관 인증 5건, 스타벅스 벌칙 8건)")
        println(">>> [Group 2] ID: ${group2.id} (치킨 벌칙 3건)")
        println(">>> [Group 3] ID: ${group3.id} (FINISH 상태, 완료된 인증 36건)")
        println("=================================================================")
    }

    // 모각코 챌린지 1기의 사진 갤러리용 승인 인증. 멤버마다 빠뜨리는 날을 달리해 최근 5주 사진을 만든다.
    private fun seedGalleryPhotos() {
        val group = groupRepository.findByInviteCode("MOGAK100") ?: return
        val groupId = group.requiredId

        val habits = listOf("asd", "chulsoo", "younghee", "minsoo", "jiwon", "hyunwoo").mapNotNull { username ->
            val member = memberRepository.findByUsername(username).getOrNull() ?: return@mapNotNull null
            habitRepository.findByGroupMember_Member_IdAndGroupMember_Group_IdAndStatus(
                member.requiredId,
                groupId,
                HabitStatus.ACTIVE,
            )
        }
        if (habits.isEmpty()) return

        // 어제 인증이 이미 있으면 이전에 채워 넣은 것으로 보고 건너뛴다
        val marker = LocalDate.now().minusDays(1)
        if (habitVerifyRepository.existsByHabitIdAndVerifyDate(habits.first().requiredId, marker)) return

        habits.forEachIndexed { memberIndex, habit ->
            for (daysAgo in 1..35) {
                if ((daysAgo + memberIndex) % (memberIndex + 3) == 0) continue
                val date = LocalDate.now().minusDays(daysAgo.toLong())
                if (habitVerifyRepository.existsByHabitIdAndVerifyDate(habit.requiredId, date)) continue
                val image = GALLERY_IMAGES[(daysAgo * 3 + memberIndex) % GALLERY_IMAGES.size]
                createApprovedHabitVerify(habit, date, "${daysAgo}일 전 인증 - ${habit.title}", image)
            }
        }
    }

    // 방장asd가 방장인 완료된 방. 마감이 지난 방의 멤버 목록(히트맵)·앨범·결산 화면을 볼 수 있게 한다.
    // 마감일 기준 최근 14일 동안 멤버마다 승인·거절 인증을 다르게 채우고, 방장은 벌칙(승인)도 한 건 받는다.
    private fun seedFinishedRoomForOwner() {
        if (groupRepository.existsByInviteCode(FINISHED_ROOM_INVITE_CODE)) return
        if (memberRepository.findByUsername(FINISHED_ROOM_OWNER).getOrNull() == null) return

        val encodedPassword = checkNotNull(passwordEncoder.encode("1234")) { "비밀번호를 암호화하지 못했습니다." }
        val deadline = LocalDate.now().minusDays(3)
        val group = groupRepository.save(
            Group(
                title = "새벽 러닝 챌린지 (완료)",
                description = "마감된 방 예시입니다. 2주 동안 아침마다 달리고 인증했어요.",
                deadline = deadline,
                penalty = "아이스크림 쏘기",
                password = encodedPassword,
                inviteCode = FINISHED_ROOM_INVITE_CODE,
                memberLimit = 5,
                status = GroupStatus.FINISH,
            )
        )

        // 방장 외 참가자는 기본 시드 회원 중 있는 사람만 넣는다
        val roster = listOf(
            FinishedRoomEntry(FINISHED_ROOM_OWNER, "아침 러닝 3km", "공원 트랙 3바퀴 달리기", approved = 14, rejected = 0),
            FinishedRoomEntry("chulsoo", "스트레칭 15분", "러닝 후 쿨다운", approved = 12, rejected = 2),
            FinishedRoomEntry("younghee", "물 2L 마시기", "텀블러 채우고 인증", approved = 9, rejected = 1),
            FinishedRoomEntry("minsoo", "계단으로 출근하기", "엘리베이터 금지", approved = 7, rejected = 0),
        )

        var ownerGroupMember: GroupMember? = null
        roster.forEach { entry ->
            val member = memberRepository.findByUsername(entry.username).getOrNull() ?: return@forEach
            val isOwner = entry.username == FINISHED_ROOM_OWNER
            val groupMember = createGroupMember(group, member, if (isOwner) GroupMemberRole.OWNER else GroupMemberRole.MEMBER)
            if (isOwner) ownerGroupMember = groupMember

            val habit = createActiveHabit(groupMember, entry.habitTitle, entry.habitDescription, 5)
            for (offset in 0 until FINISHED_ROOM_DAYS) {
                val date = deadline.minusDays((FINISHED_ROOM_DAYS - 1 - offset).toLong())
                val description = "${offset + 1}일차 - ${entry.habitTitle}"
                val image = GALLERY_IMAGES[(offset * 3 + entry.habitTitle.length) % GALLERY_IMAGES.size]
                when {
                    offset < entry.approved -> createApprovedHabitVerify(habit, date, description, image)
                    offset < entry.approved + entry.rejected -> createRejectedHabitVerify(habit, date, description, image)
                }
            }
        }

        // 방장은 중간에 포기한 습관이 있어 벌칙을 한 번 수행했다
        ownerGroupMember?.let { groupMember ->
            val failed = createFailedHabit(groupMember, "야식 끊기", "저녁 8시 이후 금식", 7)
            penaltyVerifyRepository.save(
                PenaltyVerify.create(groupMember, failed).apply {
                    submit(deadline.minusDays(1), "야식을 참지 못해 약속대로 아이스크림을 샀습니다!", GALLERY_IMAGES.first())
                    approve()
                }
            )
        }
    }

    // 방장asd가 방장이고 멤버가 14명인 진행 중인 방. 멤버 목록이 길어질 때 스크롤이 어떻게 생기는지 볼 수 있다.
    // 앞쪽 멤버는 습관이 있고(오늘 승인 인증이 있거나 없음), 마지막 둘은 습관이 없다.
    private fun seedCrowdedRoomForOwner() {
        if (groupRepository.existsByInviteCode(CROWDED_ROOM_INVITE_CODE)) return
        val owner = memberRepository.findByUsername(FINISHED_ROOM_OWNER).getOrNull() ?: return

        val encodedPassword = checkNotNull(passwordEncoder.encode("1234")) { "비밀번호를 암호화하지 못했습니다." }
        val group = groupRepository.save(
            Group(
                title = "북적북적 스터디 (멤버 많은 방)",
                description = "멤버가 많을 때 목록이 어떻게 스크롤되는지 보는 예시 방입니다.",
                deadline = LocalDate.now().plusDays(30),
                penalty = "점심 쏘기",
                password = encodedPassword,
                inviteCode = CROWDED_ROOM_INVITE_CODE,
                memberLimit = 20,
                status = GroupStatus.ACTIVE,
            )
        )

        val members = listOf(owner) + CROWDED_ROOM_NICKNAMES.mapIndexed { index, nickname ->
            getOrCreateMember("crowd%02d".format(index + 1), nickname, encodedPassword)
        }
        members.forEachIndexed { index, member ->
            val groupMember = createGroupMember(
                group,
                member,
                if (index == 0) GroupMemberRole.OWNER else GroupMemberRole.MEMBER,
            )
            // 마지막 둘은 습관 없이 참여만 한 멤버
            if (index >= members.size - 2) return@forEachIndexed

            val goal = 3 + index % 5
            val habit = createActiveHabit(groupMember, CROWDED_ROOM_HABITS[index % CROWDED_ROOM_HABITS.size], "주 ${goal}회 인증", goal)
            // 세 명 중 한 명은 이번 주 인증이 아직 없다
            if (index % 3 != 2) {
                createApprovedHabitVerify(habit, LocalDate.now(), "오늘 인증 완료", GALLERY_IMAGES[index % GALLERY_IMAGES.size])
            }
        }
    }

    // 있으면 그대로 쓰고, 없으면 만들어 저장한 뒤 리프레시 토큰까지 넣는다
    // findByUsername은 아직 Optional을 돌려줘서 getOrNull()로 받는다 (MemberRepository가 nullable로 바뀌면 함께 정리)
    private fun getOrCreateMember(username: String, nickname: String, password: String): Member =
        memberRepository.findByUsername(username).getOrNull()
            ?: memberRepository.save(Member.create(nickname, username, password))
                .apply { updateRefreshToken(authTokenService.createRefreshToken(requiredId)) }

    private fun createGroupMember(group: Group, member: Member, role: GroupMemberRole): GroupMember =
        groupMemberRepository.save(GroupMember.create(group, member, role))

    private fun createActiveHabit(groupMember: GroupMember, title: String, description: String, days: Int): Habit =
        habitRepository.save(Habit.create(groupMember, title, description, days))

    private fun createFailedHabit(groupMember: GroupMember, title: String, description: String, days: Int): Habit =
        habitRepository.save(Habit.create(groupMember, title, description, days).apply { fail() })

    // 습관 인증 대기 데이터 생성
    private fun createPendingHabitVerify(habit: Habit, description: String, imageUrl: String) {
        habitVerifyRepository.save(HabitVerify.create(habit, LocalDate.now(), description, imageUrl))
    }

    private fun createPendingPenalty(groupMember: GroupMember, habit: Habit, description: String, imageUrl: String) {
        penaltyVerifyRepository.save(
            PenaltyVerify.create(groupMember, habit).apply { submit(LocalDate.now(), description, imageUrl) }
        )
    }

    private fun createApprovedHabitVerify(habit: Habit, verifyDate: LocalDate, description: String, imageUrl: String) {
        habitVerifyRepository.save(HabitVerify.create(habit, verifyDate, description, imageUrl).apply { approve() })
    }

    private fun createRejectedHabitVerify(habit: Habit, verifyDate: LocalDate, description: String, imageUrl: String) {
        habitVerifyRepository.save(HabitVerify.create(habit, verifyDate, description, imageUrl).apply { reject() })
    }

    // 완료된 방 예시에 넣을 참가자 한 명: 습관 하나와 그 습관의 승인·거절 인증 횟수
    private class FinishedRoomEntry(
        val username: String,
        val habitTitle: String,
        val habitDescription: String,
        val approved: Int,
        val rejected: Int,
    )

    private companion object {
        const val FINISHED_ROOM_INVITE_CODE = "DONE400"
        const val FINISHED_ROOM_OWNER = "asd"
        const val FINISHED_ROOM_DAYS = 14
        const val CROWDED_ROOM_INVITE_CODE = "CROWD500"

        // 방장asd 외에 멤버 많은 방에 들어갈 13명
        val CROWDED_ROOM_NICKNAMES = listOf(
            "김하늘", "박서준", "최유나", "정민재", "오지우", "윤도현", "강다은",
            "송태오", "한소희", "문지후", "배서연", "조현빈", "류하린",
        )
        val CROWDED_ROOM_HABITS = listOf(
            "아침 7시 기상", "알고리즘 1문제", "영어 단어 30개", "스트레칭 15분", "독서 30페이지", "물 2L 마시기",
        )

        // 갤러리 예시 사진. 같은 사진이 여러 번 나오지 않도록 열 장을 돌려 쓴다.
        val GALLERY_IMAGES = listOf(
            "https://images.unsplash.com/photo-1555066931-4365d14bab8c?w=600&auto=format&fit=crop&q=60",
            "https://images.unsplash.com/photo-1488190211105-8b0e65b80b4e?w=600&auto=format&fit=crop&q=60",
            "https://images.unsplash.com/photo-1499750310107-5fef28a66643?w=600&auto=format&fit=crop&q=60",
            "https://images.unsplash.com/photo-1517838277536-f5f99be501cd?w=600&auto=format&fit=crop&q=60",
            "https://images.unsplash.com/photo-1456513080510-7bf3a84b82f8?w=600&auto=format&fit=crop&q=60",
            "https://images.unsplash.com/photo-1512820790803-83ca734da794?w=600&auto=format&fit=crop&q=60",
            "https://images.unsplash.com/photo-1456324504439-367cee3b3c32?w=600&auto=format&fit=crop&q=60",
            "https://images.unsplash.com/photo-1506126613408-eca07ce68773?w=600&auto=format&fit=crop&q=60",
            "https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?w=600&auto=format&fit=crop&q=60",
            "https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb?w=600&auto=format&fit=crop&q=60",
        )
    }
}
