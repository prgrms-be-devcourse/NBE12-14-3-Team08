package com.back.global.entity

import com.back.domain.group.entity.Group
import com.back.domain.group.repository.GroupRepository
import com.back.domain.groupMember.entity.GroupMember
import com.back.domain.groupMember.entity.GroupMemberRole
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.member.entity.Member
import com.back.domain.member.repository.MemberRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.Hibernate
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

// Kotlin BaseEntity가 JPA 지연 로딩을 깨지 않는지 지킨다.
// - BaseEntity 속성이 final이면 Hibernate가 프록시를 못 만들어(HHH000305) 모든 연관을 즉시 로딩한다.
//   결과값은 같아서 다른 테스트로는 드러나지 않고, 쿼리만 늘어난다.
// - requiredId를 클래스 안 멤버 속성으로 바꾸면 프록시에서 호출할 때 엔티티를 DB에서 읽는다.
// 저장 후 영속성 컨텍스트를 비워(clear) 실제 요청처럼 연관 엔티티가 프록시로 로딩되게 만든다. (테스트 종료 시 롤백)
@SpringBootTest
@Transactional
class BaseEntityLazyProxyTest @Autowired constructor(
    private val entityManager: EntityManager,
    private val memberRepository: MemberRepository,
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository
) {

    // 그룹 멤버를 새로 조회해, 아직 읽지 않은 회원(지연 로딩 프록시)과 실제 회원 id를 돌려준다
    private fun memberProxyAndId(): Pair<Member, Long> {
        val group = groupRepository.save(
            Group(
                title = "아침 기상 챌린지",
                penalty = "커피 1잔 쏘기",
                password = "1234",
                inviteCode = "PROXYCODE",
                memberLimit = 5,
            )
        )
        val member = memberRepository.save(Member.create("회원", "proxy-member", "pass1234"))
        val groupMember = groupMemberRepository.save(GroupMember.create(group, member, GroupMemberRole.MEMBER))
        entityManager.flush()
        entityManager.clear()

        val reloaded = groupMemberRepository.findById(groupMember.requiredId).orElseThrow()
        return reloaded.member to member.requiredId
    }

    @Test
    @DisplayName("연관 엔티티는 지연 로딩 프록시로 불러오고, id를 읽어도 DB를 읽지 않는다 (BaseEntity 속성이 final이면 실패)")
    fun 지연_로딩_프록시가_만들어진다() {
        val (memberProxy, memberId) = memberProxyAndId()

        assertThat(Hibernate.isInitialized(memberProxy)).isFalse()
        assertThat(memberProxy.id).isEqualTo(memberId)
        assertThat(Hibernate.isInitialized(memberProxy)).isFalse()
    }

    @Test
    @DisplayName("requiredId로 id를 읽어도 프록시를 초기화하지 않는다 (클래스 안 멤버 속성으로 바꾸면 실패)")
    fun requiredId는_프록시를_초기화하지_않는다() {
        val (memberProxy, memberId) = memberProxyAndId()

        assertThat(memberProxy.requiredId).isEqualTo(memberId)
        assertThat(Hibernate.isInitialized(memberProxy)).isFalse()
    }
}
