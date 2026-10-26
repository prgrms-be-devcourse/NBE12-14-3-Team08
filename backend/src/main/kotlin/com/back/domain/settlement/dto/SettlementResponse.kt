
package com.back.domain.settlement.dto

@JvmRecord
data class SettlementResponse(
    val groupId: Long?,
    val groupTitle: String,
    val personalStats: PersonalStats,
    val groupComparison: GroupComparison
) {

    @JvmRecord
    data class PersonalStats(
        val memberId: Long?,
        val nickname: String,
        val approvedCount: Long,
        val totalVerifyCount: Long,
        val rejectedCount: Long,
        val penaltyCount: Long
    )

    @JvmRecord
    data class Winner(
        val memberId: Long?,
        val nickname: String,
        val value: Long
    )

    @JvmRecord
    data class GroupComparison(
        val mostDescriptionChars: Winner?,
        val mostDeadlineVerifications: Winner?,
        val mostRejectedVerifications: Winner?,
        val longestPenaltyDelay: Winner?
    )
}