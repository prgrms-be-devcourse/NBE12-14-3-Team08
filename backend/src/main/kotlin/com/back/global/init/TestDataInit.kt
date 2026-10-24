package com.back.global.init

import com.back.domain.group.entity.Group
import com.back.domain.group.entity.GroupStatus
import com.back.domain.group.repository.GroupRepository
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.habit.entity.Habit
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
        if (groupRepository.existsByInviteCode("MOGAK100")) {
            return
        }

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

        println("=================================================================")
        println(">>> [TEST DATA INITIALIZED] 습관/벌칙 PENDING 데이터 세팅 완료!")
        println(">>> [Group 1] ID: ${group1.id} (습관 인증 5건, 스타벅스 벌칙 8건)")
        println(">>> [Group 2] ID: ${group2.id} (치킨 벌칙 3건)")
        println("=================================================================")
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
        Habit.create(groupMember, title, description, days)
            .apply { fail() }
            .let(habitRepository::save)

    // 습관 인증 대기 데이터 생성
    private fun createPendingHabitVerify(habit: Habit, description: String, imageUrl: String) {
        habitVerifyRepository.save(HabitVerify.create(habit, LocalDate.now(), description, imageUrl))
    }

    private fun createPendingPenalty(groupMember: GroupMember, habit: Habit, description: String, imageUrl: String) {
        PenaltyVerify.create(groupMember, habit)
            .apply { submit(LocalDate.now(), description, imageUrl) }
            .let(penaltyVerifyRepository::save)
    }
}
