
package com.back.domain.group.dto

import com.back.domain.group.entity.Group
import com.back.domain.group.entity.GroupStatus
import java.time.LocalDate
import java.time.LocalDateTime

interface GroupResponse {

    @JvmRecord
    data class Detail(
        val id: Long?,
        val title: String,
        val description: String?,
        val startDate: LocalDate,
        val deadline: LocalDate?,
        val penalty: String,
        val inviteCode: String,
        val memberLimit: Int,
        val createDate: LocalDateTime,
        val inviteLink: String,
        val status: GroupStatus,
        val isJoined: Boolean,
        val currentMemberCount: Long
    ) {
        companion object {

            @JvmStatic
            fun from(
                group: Group,
                baseInviteUrl: String,
                isJoined: Boolean,
                currentMemberCount: Long
            ): Detail {
                val createdAt = requireNotNull(group.createDate) {
                    "그룹 생성 일시는 필수입니다."
                }

                val currentStatus =
                    if (
                        group.status == GroupStatus.ACTIVE &&
                        group.deadline?.let { LocalDate.now().isAfter(it) } == true
                    ) {
                        GroupStatus.FINISH
                    } else {
                        group.status
                    }

                return Detail(
                    id = group.id,
                    title = group.title,
                    description = group.description,
                    startDate = createdAt.toLocalDate(),
                    deadline = group.deadline,
                    penalty = group.penalty,
                    inviteCode = group.inviteCode,
                    memberLimit = group.memberLimit,
                    createDate = createdAt,
                    inviteLink = baseInviteUrl + group.inviteCode,
                    status = currentStatus,
                    isJoined = isJoined,
                    currentMemberCount = currentMemberCount
                )
            }
        }
    }

    @JvmRecord
    data class Simple(
        val id: Long?,
        val title: String,
        val description: String?,
        val memberLimit: Int,
        val currentMemberCount: Long?,
        val status: String,
        val role: String
    )
}