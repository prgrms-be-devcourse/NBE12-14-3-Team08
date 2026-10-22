package com.back.domain.penaltyverify.controller;

import com.back.domain.penaltyverify.dto.BulkActionRequest;
import com.back.domain.penaltyverify.dto.PenaltyVerifyDetailResponse;
import com.back.domain.penaltyverify.dto.PenaltyVerifySummaryResponse;
import com.back.domain.penaltyverify.dto.SubmitPenaltyVerifyRequest;
import com.back.domain.penaltyverify.service.PenaltyVerifyService;
import com.back.global.security.LoginMemberId;
import com.back.global.storage.UploadUrlRequest;
import com.back.global.storage.UploadUrlResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "벌칙 인증 API", description = "유저의 벌칙 제출/재제출/스토리지 업로드 URL 발급 및 방장의 벌칙 단건/일괄 심사")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PenaltyVerifyController {

    private final PenaltyVerifyService penaltyVerifyService;

    @Operation(
            summary = "벌칙 인증 등록",
            description = "특정 습관의 실패로 인해 부과된 벌칙 이행 기록을 새로 등록합니다. (그룹 가입자만 가능)\n\n" +
                    "- 벌칙 실천 내용 및 증빙 사진 URL을 첨부하여 검토 대기(PENDING) 상태로 제출합니다."
    )
    @PostMapping("/habits/{habitId}/penalties")
    public ResponseEntity<PenaltyVerifyDetailResponse> submit(
        @LoginMemberId Long memberId,
        @PathVariable Long habitId,
        @Valid @RequestBody SubmitPenaltyVerifyRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(penaltyVerifyService.submit(memberId, habitId, request));
    }

    @Operation(
            summary = "반려된 벌칙 인증 재제출",
            description = "방장에게 반려(REJECTED)된 특정 벌칙 인증 기록을 보완하여 재제출합니다. (본인만 가능)\n\n" +
                    "- 재제출 시 인증 상태가 다시 검토 대기(PENDING) 상태로 전환됩니다."
    )
    @PatchMapping("/penalties/{id}/resubmit")
    public PenaltyVerifyDetailResponse resubmit(
        @LoginMemberId Long memberId,
        @PathVariable Long id,
        @Valid @RequestBody SubmitPenaltyVerifyRequest request
    ) {
        return penaltyVerifyService.resubmit(memberId, id, request);
    }

    @Operation(
            summary = "벌칙 인증 사진 업로드용 서명 URL 발급",
            description = "벌칙 증빙 사진을 스토리지(supabase)에 바로 업로드할 수 있는 Signed URL을 발급합니다.\n\n" +
                    "- 본인의 벌칙 중 제출(REQUIRED) 또는 재제출(REJECTED)할 수 있는 건에만 발급합니다.\n" +
                    "- 종료된 방에서는 발급할 수 없습니다.\n" +
                    "- 허용 확장자: jpg, jpeg, png, webp"
    )
    @PostMapping("/penalties/{id}/upload-url")
    public UploadUrlResponse getUploadUrl(
        @LoginMemberId Long memberId,
        @PathVariable Long id,
        @Valid @RequestBody UploadUrlRequest request
    ) {
        return penaltyVerifyService.createUploadUrl(memberId, id, request.getFilename());
    }

    @Operation(
            summary = "벌칙 인증 상세 조회",
            description = "특정 벌칙 인증 기록의 상세 정보 및 검토 상태를 확인합니다. (그룹 가입자만 조회 가능)"
    )
    @GetMapping("/penalties/{id}")
    public PenaltyVerifyDetailResponse getDetail(
        @LoginMemberId Long memberId,
        @PathVariable Long id
    ) {
        return penaltyVerifyService.getDetail(memberId, id);
    }

    @Operation(
            summary = "그룹 내 누적 벌칙 개수 조회",
            description = "특정 그룹에서 발생한 전체 벌칙의 누적 개수를 조회합니다. (그룹 가입자만 조회 가능)"
    )
    @GetMapping("/groups/{groupId}/penalties/count")
    public ResponseEntity<Map<String, Long>> getCount(
        @PathVariable Long groupId,
        @LoginMemberId Long memberId
    ) {
        long count = penaltyVerifyService.getCount(groupId, memberId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @Operation(
            summary = "특정 멤버의 벌칙 목록 조회",
            description = "특정 그룹 내 지정한 멤버가 부여받은 벌칙 및 인증 목록을 전체 조회합니다. (그룹 가입자만 조회 가능)"
    )
    @GetMapping("/groups/{groupId}/members/{groupMemberId}/penalties")
    public List<PenaltyVerifySummaryResponse> getPenaltyList(
        @LoginMemberId Long memberId,
        @PathVariable Long groupId,
        @PathVariable Long groupMemberId
    ) {
        return penaltyVerifyService.getPenaltiesByGroupMember(memberId, groupId, groupMemberId);
    }

    @Operation(
            summary = "단건 벌칙 인증 승인",
            description = "멤버가 제출한 특정 벌칙 인증 요청을 개별 승인합니다. (그룹 방장만 가능)\n\n" +
                    "- 승인 완료(APPROVED) 상태로 변경되며, 해당 유저의 주간 정산 패널티가 최종 해제됩니다."
    )
    @PatchMapping("/penalties/{id}/approve")
    public PenaltyVerifyDetailResponse approve(
        @LoginMemberId Long memberId,
        @PathVariable Long id
    ) {
        return penaltyVerifyService.approve(memberId, id);
    }

    @Operation(
            summary = "단건 벌칙 인증 반려",
            description = "멤버가 제출한 특정 벌칙 인증 요청을 개별 반려(거절)합니다. (그룹 방장만 가능)\n\n" +
                    "- 반려 완료(REJECTED) 상태로 변경되며, 유저는 내용을 보완하여 재제출해야 합니다."
    )
    @PatchMapping("/penalties/{id}/reject")
    public PenaltyVerifyDetailResponse reject(
        @LoginMemberId Long memberId,
        @PathVariable Long id
    ) {
        return penaltyVerifyService.reject(memberId, id);
    }

    @Operation(
            summary = "검토 대기 중인 벌칙 목록 조회",
            description = "특정 그룹에서 멤버들이 제출한 벌칙 요청 중 검토 대기(PENDING) 상태인 목록을 조회합니다. (그룹 방장만 조회 가능)\n\n" +
                    "- 방장이 승인/반려를 결정해야 하는 대기 건들을 리스트로 반환합니다."
    )
    @GetMapping("/groups/{groupId}/penalties/pending")
    public List<PenaltyVerifySummaryResponse> getPending(
        @LoginMemberId Long memberId,
        @PathVariable Long groupId
    ) {
        return penaltyVerifyService.getPendingByGroup(memberId, groupId);
    }

    @Operation(
            summary = "벌칙 목록 일괄 승인",
            description = "특정 그룹 내 여러 건의 검토 대기 벌칙 요청을 한 번에 일괄 승인 처리합니다. (그룹 방장만 가능)\n\n" +
                    "- 요청 바디(BulkActionRequest)에 포함된 벌칙 인증 ID 목록 전체를 APPROVED 상태로 일괄 변경합니다."
    )
    @PatchMapping("/groups/{groupId}/penalties/bulk-approve")
    public List<PenaltyVerifyDetailResponse> bulkApprove(
        @LoginMemberId Long memberId,
        @PathVariable Long groupId,
        @RequestBody BulkActionRequest request
    ) {
        return penaltyVerifyService.bulkApprove(memberId, groupId, request);
    }

    @Operation(
            summary = "벌칙 목록 일괄 반려",
            description = "특정 그룹 내 여러 건의 검토 대기 벌칙 요청을 한 번에 일괄 반려 처리합니다. (그룹 방장만 가능)\n\n" +
                    "- 요청 바디(BulkActionRequest)에 포함된 벌칙 인증 ID 목록 전체를 REJECTED 상태로 일괄 변경합니다."
    )
    @PatchMapping("/groups/{groupId}/penalties/bulk-reject")
    public List<PenaltyVerifyDetailResponse> bulkReject(
        @LoginMemberId Long memberId,
        @PathVariable Long groupId,
        @RequestBody BulkActionRequest request
    ) {
        return penaltyVerifyService.bulkReject(memberId, groupId, request);
    }

    @Operation(
            summary = "벌칙 인증 제출 취소 / 초기화",
            description = "벌칙 기록은 결산을 위해 삭제하지 않고, 제출 내용(설명, 사진, 제출일)을 비워 REQUIRED 상태로 되돌립니다.\n\n" +
                    "- 작성자: 방장 검토 전(PENDING)인 벌칙만 제출 취소할 수 있습니다.\n" +
                    "- 방장: PENDING / APPROVED / REJECTED 벌칙을 초기화할 수 있습니다. (본인 벌칙 포함)\n" +
                    "- 그 외 사용자는 403, 종료된 방은 400을 반환합니다."
    )
    @PatchMapping("/penalties/{id}/cancel")
    public PenaltyVerifyDetailResponse cancelOrReset(
        @LoginMemberId Long memberId,
        @PathVariable Long id
    ) {
        return penaltyVerifyService.cancelOrReset(memberId, id);
    }
}