package com.back.domain.settlement.controller

import com.back.domain.settlement.dto.SettlementResponse
import com.back.domain.settlement.service.SettlementService
import com.back.global.security.LoginMemberId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(
    name = "결산 API",
    description = "마감된 챌린지 그룹의 개인 통계와 그룹 내 비교 결산"
)
@RestController
@RequestMapping("/api/groups/{groupId}/settlement")
class SettlementController(
    private val settlementService: SettlementService
) {

    @Operation(
        summary = "그룹 결산 데이터 조회",
        description = """
            챌린지가 마감된 특정 그룹의 최종 정산 데이터를 확인합니다. (그룹 가입자만 조회 가능)

            - 해당 방의 전체 챌린지 성공률, 멤버별 달성 통계, 최종 정산 내역을 종합하여 반환합니다.
        """
    )
    @GetMapping
    fun getSettlement(
        @PathVariable groupId: Long,
        @LoginMemberId memberId: Long
    ): ResponseEntity<SettlementResponse> {
        val response = settlementService.getSettlement(groupId, memberId)
        return ResponseEntity.ok(response)
    }
}
