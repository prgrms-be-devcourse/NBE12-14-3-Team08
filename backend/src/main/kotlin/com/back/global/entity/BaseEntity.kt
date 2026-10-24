package com.back.global.entity

import jakarta.persistence.EntityListeners
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseEntity {

    @field:Id
    @field:GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @field:CreatedDate
    var createDate: LocalDateTime? = null
        protected set

    @field:LastModifiedDate
    var modifyDate: LocalDateTime? = null
        protected set
}

// 저장된 엔티티는 id·생성일이 항상 있으므로, null이면 저장 전 엔티티를 잘못 쓴 코드 실수다.
// 클래스 밖 확장 속성으로 둔다: 클래스 안 멤버 속성이면 지연 로딩 프록시에서 호출할 때 엔티티를 DB에서 읽어 버린다.
// (Hibernate 프록시는 getId()만 쿼리 없이 처리한다. 확장 속성은 안에서 id를 읽으므로 같은 처리를 받는다)
val BaseEntity.requiredId: Long
    get() = id ?: throw IllegalStateException("${this::class.simpleName} 엔티티의 ID가 아직 생성되지 않았습니다.")

val BaseEntity.requiredCreateDate: LocalDateTime
    get() = createDate ?: throw IllegalStateException("${this::class.simpleName} 엔티티의 생성일이 아직 기록되지 않았습니다.")