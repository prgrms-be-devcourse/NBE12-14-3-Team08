package com.back.domain.penaltyverify.service

import com.back.domain.penaltyverify.dto.PenaltyVerifyDetailResponse
import com.back.domain.penaltyverify.repository.PenaltyVerifyCommentRepository
import com.back.domain.penaltyverify.repository.PenaltyVerifyReactionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 벌칙 제출 취소·초기화 + 그 인증에 달린 하트·댓글 정리를 한 트랜잭션으로 묶는다. (docs/adr/0002-verify-reaction-comment-tables.md)
// 벌칙 인증은 지우지 않고 REQUIRED로 되돌려 증빙이 바뀌므로, 이전 증빙에 달린 반응과 댓글은 남기지 않는다.
// PenaltyVerifyService를 건드리지 않고 그 위에 얹어, 기존 서비스의 의존성과 동작은 그대로 둔다.
@Service
class PenaltyVerifyResetService(
    private val penaltyVerifyService: PenaltyVerifyService,
    private val reactionRepository: PenaltyVerifyReactionRepository,
    private val commentRepository: PenaltyVerifyCommentRepository,
) {

    // 권한·상태 검사에 실패하면 예외가 나서 아래 정리는 실행되지 않고, 트랜잭션 전체가 되돌아간다
    @Transactional
    fun cancelOrReset(memberId: Long, id: Long): PenaltyVerifyDetailResponse {
        val response = penaltyVerifyService.cancelOrReset(memberId, id)

        reactionRepository.deleteAllByPenaltyVerifyId(id)
        commentRepository.deleteAllByPenaltyVerifyId(id)

        return response
    }
}
