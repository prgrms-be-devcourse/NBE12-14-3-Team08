
package com.back.domain.groupMember.entity

import com.back.domain.group.entity.Group
import com.back.domain.member.entity.Member
import com.back.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "group_member",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_group_member",
            columnNames = ["group_id", "member_id"]
        )
    ]
)
class GroupMember protected constructor() : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    lateinit var group: Group
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    lateinit var member: Member
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    lateinit var role: GroupMemberRole
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: GroupMemberStatus = GroupMemberStatus.ACTIVE
        protected set

    fun changeRole(role: GroupMemberRole?) {
        if (role != null) {
            this.role = role
        }
    }

    fun leave() {
        status = GroupMemberStatus.LEFT
    }

    // Java 호출부(TestDataInit)가 쓰던 Lombok builder를 대신한다.
    // 호출부가 모두 Kotlin으로 전환되면 create()로 바꾸고 제거한다.
    class Builder {
        private lateinit var group: Group
        private lateinit var member: Member
        private lateinit var role: GroupMemberRole
        private var status: GroupMemberStatus = GroupMemberStatus.ACTIVE

        fun group(group: Group) = apply { this.group = group }
        fun member(member: Member) = apply { this.member = member }
        fun role(role: GroupMemberRole) = apply { this.role = role }
        fun status(status: GroupMemberStatus) = apply { this.status = status }

        fun build(): GroupMember =
            GroupMember().also {
                it.group = group
                it.member = member
                it.role = role
                it.status = status
            }
    }

    companion object {
        @JvmStatic
        fun builder(): Builder = Builder()

        @JvmStatic
        fun create(
            group: Group,
            member: Member,
            role: GroupMemberRole
        ): GroupMember {
            return GroupMember().apply {
                this.group = group
                this.member = member
                this.role = role
                this.status = GroupMemberStatus.ACTIVE
            }
        }
    }
}