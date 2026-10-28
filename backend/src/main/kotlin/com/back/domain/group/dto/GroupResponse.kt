
package com.back.domain.group.dto

import com.back.domain.group.entity.Group
import com.back.domain.group.entity.GroupStatus
import java.time.LocalDate
import java.time.LocalDateTime

interface GroupResponse {

     
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
        val currentMemberCount: Long,
        val rankings: Rankings? = null
    ) {
        companion object {


            fun from(
                group: Group,
                baseInviteUrl: String,
                isJoined: Boolean,
                currentMemberCount: Long,
                rankings: Rankings? = null
            ): Detail {
                val createdAt = requireNotNull(group.createDate) {
                    "그룹 생성 일시는 필수입니다."
                }

                val currentStatus =
                    if (
                        group.status == GroupStatus.ACTIVE &&
                        group.deadline?.let {
                            LocalDate.now().isAfter(it)
                        } == true
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
                    currentMemberCount = currentMemberCount,
                    rankings = rankings
                )
            }
        }
    }

     
    data class Simple(
        val id: Long?,
        val title: String,
        val description: String?,
        val memberLimit: Int,
        val currentMemberCount: Long?,
        val status: String,
        val role: String
    )

     
    data class Rankings(
        val habitVerifyTop3: List<Ranking>,
        val penaltyVerifyTop3: List<Ranking>,
        val habitSuccessTop3: List<Ranking>
    )

     
    data class Ranking(
        val rank: Int,
        val memberId: Long,
        val nickname: String?,
        val count: Int
    )
}