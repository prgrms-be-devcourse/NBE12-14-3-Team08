package com.back.support

import com.back.domain.group.entity.Group
import com.back.domain.group.entity.GroupStatus
import com.back.domain.member.entity.Member
import com.back.domain.member.entity.MemberStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

// Kotlin 테스트 코드가 Java 롬복 클래스(getter, builder)를 쓸 수 있는지, 그리고 id처럼 null일 수 있는 값을
// 다룰 때의 주의점을 확인한다.
//
// 참고: 테스트 소스의 Kotlin은 이미 컴파일된 main 클래스를 참조하므로 kotlin-lombok 플러그인 없이도 이 테스트는 통과한다.
// 플러그인이 필요한 경우는 "main 소스의 Kotlin이 같은 모듈의 Java 롬복 클래스를 참조할 때"(도메인 전환 중)이며,
// 이 경우 플러그인이 없으면 `member.nickname`이 Unresolved reference로 컴파일에 실패한다.
class KotlinLombokInteropTest {

    @Test
    @DisplayName("Kotlin에서 Java 롬복 @Getter를 프로퍼티로 접근할 수 있다")
    fun 롬복_getter를_프로퍼티로_접근한다() {
        val member = Member.create("닉네임", "username", "encoded")

        assertThat(member.nickname).isEqualTo("닉네임")
        assertThat(member.username).isEqualTo("username")
        assertThat(member.status).isEqualTo(MemberStatus.ACTIVE)
    }

    @Test
    @DisplayName("Kotlin에서 상속받은 부모 클래스(BaseEntity)의 롬복 getter도 접근할 수 있다")
    fun 부모_클래스의_롬복_getter를_접근한다() {
        val member = Member.create("닉네임", "username", "encoded")

        // 저장 전 엔티티의 id는 null이다. Java 롬복 getter의 반환 타입(Long!)을 그대로 assertThat에 넘기면
        // Kotlin이 기본형(long) 오버로드로 언박싱하면서 NPE가 나므로, 반드시 nullable 타입으로 받는다.
        val id: Long? = member.id
        val createDate: LocalDateTime? = member.createDate

        assertThat(id).isNull()
        assertThat(createDate).isNull()
    }

    @Test
    @DisplayName("Kotlin에서 Java 롬복 @Builder를 쓸 수 있고 @Builder.Default 값도 적용된다")
    fun 롬복_빌더를_사용한다() {
        val group = Group.builder()
            .title("아침 기상 챌린지")
            .penalty("커피 사기")
            .password("1234")
            .inviteCode("invite-code")
            .memberLimit(5)
            .build()

        assertThat(group.title).isEqualTo("아침 기상 챌린지")
        assertThat(group.memberLimit).isEqualTo(5)
        assertThat(group.status).isEqualTo(GroupStatus.ACTIVE)
    }
}
