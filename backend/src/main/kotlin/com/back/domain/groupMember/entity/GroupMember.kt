
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

    companion object {

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