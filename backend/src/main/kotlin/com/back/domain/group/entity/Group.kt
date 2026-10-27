
package com.back.domain.group.entity

import com.back.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "groups")
class Group(
    @Column(nullable = false)
    var title: String = "",

    var description: String? = null,

    var deadline: LocalDate? = null,

    @Column(nullable = false)
    var penalty: String = "",

    @Column(nullable = false)
    var password: String = "",

    @Column(nullable = false, unique = true)
    var inviteCode: String = "",

    var memberLimit: Int = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: GroupStatus = GroupStatus.ACTIVE
) : BaseEntity() {

    fun updateGroup(
        title: String?,
        description: String?,
        deadline: LocalDate?,
        penalty: String?,
        password: String?,
        memberLimit: Int
    ) {
        if (!title.isNullOrBlank()) {
            this.title = title
        }

        if (description != null) {
            this.description = description
        }

        if (deadline != null) {
            this.deadline = deadline
        }

        if (penalty != null) {
            this.penalty = penalty
        }

        if (!password.isNullOrBlank()) {
            this.password = password
        }

        if (memberLimit > 0) {
            this.memberLimit = memberLimit
        }
    }

    fun finish() {
        status = GroupStatus.FINISH
    }

    fun isFinished(): Boolean {
        val today = LocalDate.now()

        return status == GroupStatus.FINISH ||
                (deadline != null && today.isAfter(deadline))
    }

    // Java 호출부(TestDataInit, Java 테스트)가 쓰던 Lombok builder를 대신한다.
    // 호출부가 모두 Kotlin으로 전환되면 이름 붙인 인자로 바꾸고 제거한다.
    class Builder {
        private var title: String = ""
        private var description: String? = null
        private var deadline: LocalDate? = null
        private var penalty: String = ""
        private var password: String = ""
        private var inviteCode: String = ""
        private var memberLimit: Int = 0
        private var status: GroupStatus = GroupStatus.ACTIVE

        fun title(title: String) = apply { this.title = title }
        fun description(description: String?) = apply { this.description = description }
        fun deadline(deadline: LocalDate?) = apply { this.deadline = deadline }
        fun penalty(penalty: String) = apply { this.penalty = penalty }
        fun password(password: String) = apply { this.password = password }
        fun inviteCode(inviteCode: String) = apply { this.inviteCode = inviteCode }
        fun memberLimit(memberLimit: Int) = apply { this.memberLimit = memberLimit }
        fun status(status: GroupStatus) = apply { this.status = status }

        fun build(): Group =
            Group(title, description, deadline, penalty, password, inviteCode, memberLimit, status)
    }

    companion object {

        fun builder(): Builder = Builder()
    }
}