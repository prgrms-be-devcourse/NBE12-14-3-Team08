
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
    title: String = "",
    description: String? = null,
    deadline: LocalDate? = null,
    penalty: String = "",
    password: String = "",
    inviteCode: String = "",
    memberLimit: Int = 0,
    status: GroupStatus = GroupStatus.ACTIVE,
) : BaseEntity() {

    @Column(nullable = false)
    var title: String = title
        protected set

    var description: String? = description
        protected set

    var deadline: LocalDate? = deadline
        protected set

    @Column(nullable = false)
    var penalty: String = penalty
        protected set

    @Column(nullable = false)
    var password: String = password
        protected set

    @Column(nullable = false, unique = true)
    var inviteCode: String = inviteCode
        protected set

    var memberLimit: Int = memberLimit
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: GroupStatus = status
        protected set

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
}