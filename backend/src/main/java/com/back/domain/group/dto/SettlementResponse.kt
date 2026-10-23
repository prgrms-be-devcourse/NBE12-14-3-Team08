
package com.back.domain.group.dto

data class SettlementResponse(
    val groupId: Long?,
    val groupTitle: String,
    val personalStats: PersonalStats,
    val groupComparison: GroupComparison
) {

    data class PersonalStats(
        val memberId: Long?,
        val nickname: String,
        val approvedCount: Long,
        val totalVerifyCount: Long,
        val rejectedCount: Long,
        val penaltyCount: Long
    )

    data class Winner(
        val memberId: Long?,
        val nickname: String,
        val value: Long
    )

    data class GroupComparison(
        val mostDescriptionChars: Winner?,
        val mostDeadlineVerifications: Winner?,
        val mostRejectedVerifications: Winner?,
        val longestPenaltyDelay: Winner?
    )
}