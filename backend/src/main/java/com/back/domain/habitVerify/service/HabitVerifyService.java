package com.back.domain.habitVerify.service;

import com.back.domain.groupMember.entity.GroupMember;
import com.back.domain.groupMember.entity.GroupMemberRole;
import com.back.domain.groupMember.repository.GroupMemberRepository;
import com.back.domain.habit.entity.Habit;
import com.back.domain.habit.entity.HabitStatus;
import com.back.domain.habit.repository.HabitRepository;
import com.back.global.exception.BusinessRuleException;
import com.back.global.exception.EntityNotFoundException;
import com.back.domain.habitVerify.dto.HabitVerifyRequest;
import com.back.domain.habitVerify.dto.HabitVerifyResponse;
import com.back.domain.habitVerify.dto.HabitVerifySummaryResponse;
import com.back.domain.habitVerify.entity.HabitVerify;
import com.back.domain.habitVerify.repository.HabitVerifyRepository;
import com.back.global.exception.ForbiddenException;
import com.back.global.storage.StorageService;
import com.back.global.storage.UploadUrlResponse;
import com.back.global.storage.VerificationImageType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.back.domain.habitVerify.dto.BulkActionRequest;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HabitVerifyService {

    private final HabitRepository habitRepository;
    private final HabitVerifyRepository habitVerifyRepository;
    private final StorageService storageService;
    private final GroupMemberRepository groupMemberRepository;

    // 습관 인증 사진 업로드 URL 발급: 내 습관 → 진행 중인 방 → 활성 습관 순으로 확인
    // 반려 건 재제출에도 새 사진이 필요하므로 "오늘 이미 인증했는지"는 보지 않는다.
    @Transactional(readOnly = true)
    public UploadUrlResponse createUploadUrl(Long memberId, Long habitId, String filename) {
        Habit habit = habitRepository.findByIdAndGroupMember_Member_Id(habitId, memberId)
                .orElseThrow(() -> new ForbiddenException("본인의 습관에만 사진을 올릴 수 있습니다."));

        if (habit.getGroupMember().getGroup().isFinished()) {
            throw new BusinessRuleException("이미 종료된 그룹에서는 습관 인증 사진을 올릴 수 없습니다.");
        }

        if (habit.getStatus() != HabitStatus.ACTIVE) {
            throw new IllegalStateException("진행 중인 습관에만 사진을 올릴 수 있습니다. 현재 상태: " + habit.getStatus());
        }

        return storageService.createUploadUrl(VerificationImageType.HABIT, memberId, habitId, filename);
    }

    @Transactional
    public HabitVerifyResponse create(
            Long habitId,
            HabitVerifyRequest request,
            Long memberId

    ) {

        Habit habit = habitRepository.findByIdAndGroupMember_Member_Id(habitId, memberId)
                .orElseThrow(() ->
                        new ForbiddenException(
                                "본인의 습관에만 인증을 등록할 수 있습니다."
                        )
                );

        if (habit.getGroupMember().getGroup().isFinished()) {
            throw new BusinessRuleException("이미 종료된 그룹에서는 습관 인증을 등록할 수 없습니다.");
        }

        if (habit.getStatus() != HabitStatus.ACTIVE) {
            throw new IllegalStateException("진행 중인 습관에만 인증을 등록할 수 있습니다. 현재 상태: " + habit.getStatus());
        }

        LocalDate verifyDate = LocalDate.now();

        boolean alreadyExists =
                habitVerifyRepository.existsByHabitIdAndVerifyDate(
                        habitId,
                        verifyDate
                );

        if (alreadyExists) {
            throw new IllegalArgumentException(
                    "해당 날짜에 이미 인증 기록이 있습니다."
            );
        }

        validateHabitImage(memberId, habitId, request.imageUrl(), null);

        HabitVerify habitVerify = HabitVerify.create(
                habit, verifyDate,
                request.description(),
                request.imageUrl()
        );

        HabitVerify savedHabitVerify =
                habitVerifyRepository.save(habitVerify);

        return HabitVerifyResponse.from(savedHabitVerify);
    }

    @Transactional(readOnly = true)
    public List<HabitVerifyResponse> findAll(
            Long habitId, Long memberId
    ){
        Habit habit = habitRepository.findById(habitId)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 습관입니다."));

        Long groupId = habit.getGroupMember().getGroup().getId();
        boolean isGroupMember = groupMemberRepository.existsByGroupIdAndMemberId(groupId, memberId);

        if (!isGroupMember) {
            throw new ForbiddenException("같은 그룹의 멤버만 조회할 수 있습니다.");
        }

        return habitVerifyRepository
                .findAllByHabitIdOrderByVerifyDateDesc(habitId)
                .stream()
                .map(HabitVerifyResponse::from)
                .toList();
    }

    @Transactional
    public HabitVerifyResponse resubmit(
            Long habitId,
            Long verificationId,
            HabitVerifyRequest request,
            Long memberId
    ) {
        Habit habit = habitRepository.findByIdAndGroupMember_Member_Id(
                habitId,
                memberId
        ).orElseThrow(() ->
                new ForbiddenException(
                        "본인의 습관 인증만 재제출할 수 있습니다."
                )
        );

        if (habit.getGroupMember().getGroup().isFinished()) {
            throw new BusinessRuleException("이미 종료된 그룹의 습관 인증은 수정할 수 없습니다.");
        }

        if (habit.getStatus() != HabitStatus.ACTIVE) {
            throw new IllegalStateException(
                    "이미 종료된 습관의 인증은 수정할 수 없습니다."
            );
        }

        HabitVerify habitVerify =
                habitVerifyRepository
                        .findByIdAndHabitId(
                                verificationId,
                                habitId
                        )
                        .orElseThrow(() ->
                                new EntityNotFoundException(
                                        "존재하지 않는 인증 기록입니다."
                                )
                        );

        LocalDateTime deadline = calculateEntryDeadline(habit, habitVerify);
        if (!LocalDateTime.now().isBefore(deadline)) {
            throw new IllegalStateException(
                    "마감일이 지나 더 이상 수정할 수 없습니다."
            );
        }

        habitVerify.validateResubmittable();
        validateHabitImage(memberId, habitId, request.imageUrl(), habitVerify.getImageUrl());

        habitVerify.resubmit(
                request.description(),
                request.imageUrl()
        );

        return HabitVerifyResponse.from(habitVerify);
    }

    // 사진 필수 여부는 HabitVerifyRequest의 @NotBlank가 막는다.
    // Supabase 호출이 있으므로 다른 검사가 모두 끝난 뒤 마지막에 확인한다.
    private void validateHabitImage(Long memberId, Long habitId, String imageUrl, String currentImageUrl) {
        storageService.validateImageUrl(
                VerificationImageType.HABIT,
                memberId,
                habitId,
                imageUrl,
                currentImageUrl
        );
    }

    // 이 인증 건이 제출된 주간 구간의 마감 시각(다음 마감 요일 00시)을 계산한다.
    // 방 생성 요일 기준 주간 판정 로직(HabitService.evaluateWeeklyResult)과 동일한 규칙을 사용해,
    // 반려된 인증은 그 주간 판정이 시작되기 전까지만 수정할 수 있도록 한다.
    private LocalDateTime calculateEntryDeadline(Habit habit, HabitVerify habitVerify) {
        DayOfWeek deadlineDayOfWeek = habit.getGroupMember().getGroup().getCreateDate().getDayOfWeek();
        LocalDateTime submittedAt = habitVerify.getCreateDate();

        int daysSincePeriodStart =
                (submittedAt.getDayOfWeek().getValue() - deadlineDayOfWeek.getValue() + 7) % 7;
        LocalDateTime periodStart = submittedAt.toLocalDate()
                .minusDays(daysSincePeriodStart)
                .atStartOfDay();

        return periodStart.plusWeeks(1);
    }

    @Transactional(readOnly = true)
    public List<HabitVerifySummaryResponse> getPendingByGroup(
            Long memberId,
            Long groupId
    ) {
        GroupMember groupMember =
                groupMemberRepository
                        .findByGroupIdAndMemberId(groupId, memberId)
                        .orElseThrow(() ->
                                new AccessDeniedException(
                                        "해당 그룹의 멤버가 아닙니다."
                                )
                        );

        if (groupMember.getRole() != GroupMemberRole.OWNER) {
            throw new AccessDeniedException(
                    "방장만 대기 목록을 조회할 수 있습니다."
            );
        }

        return habitVerifyRepository
                .findPendingByGroupId(groupId)
                .stream()
                .map(HabitVerifySummaryResponse::from)
                .toList();
    }
    @Transactional
    public HabitVerifyResponse approve(
            Long memberId,
            Long verificationId
    ) {
        HabitVerify habitVerify =
                habitVerifyRepository
                        .findById(verificationId)
                        .orElseThrow(() ->
                                new EntityNotFoundException(
                                        "존재하지 않는 인증 기록입니다."
                                )
                        );

        Long groupId = habitVerify
                .getHabit()
                .getGroupMember()
                .getGroup()
                .getId();

        validateOwner(memberId, groupId);

        habitVerify.approve();

        return HabitVerifyResponse.from(habitVerify);
    }
    @Transactional
    public HabitVerifyResponse reject(
            Long memberId,
            Long verificationId
    ) {
        HabitVerify habitVerify =
                habitVerifyRepository
                        .findById(verificationId)
                        .orElseThrow(() ->
                                new EntityNotFoundException(
                                        "존재하지 않는 인증 기록입니다."
                                )
                        );

        Long groupId = habitVerify
                .getHabit()
                .getGroupMember()
                .getGroup()
                .getId();

        validateOwner(memberId, groupId);

        habitVerify.reject();

        return HabitVerifyResponse.from(habitVerify);
    }

    @Transactional
    public List<HabitVerifyResponse> bulkApprove(
            Long memberId,
            Long groupId,
            BulkActionRequest request
    ) {
        validateOwner(memberId, groupId);

        List<HabitVerify> targets =
                habitVerifyRepository.findAllByIdInAndGroupId(
                        request.ids(),
                        groupId
                );

        targets.forEach(HabitVerify::approve);

        return targets.stream()
                .map(HabitVerifyResponse::from)
                .toList();
    }

    @Transactional
    public List<HabitVerifyResponse> bulkReject(
            Long memberId,
            Long groupId,
            BulkActionRequest request
    ) {
        validateOwner(memberId, groupId);

        List<HabitVerify> targets =
                habitVerifyRepository.findAllByIdInAndGroupId(
                        request.ids(),
                        groupId
                );

        targets.forEach(HabitVerify::reject);

        return targets.stream()
                .map(HabitVerifyResponse::from)
                .toList();
    }
    private void validateOwner(
            Long memberId,
            Long groupId
    ) {
        GroupMember groupMember =
                groupMemberRepository
                        .findByGroupIdAndMemberId(
                                groupId,
                                memberId
                        )
                        .orElseThrow(() ->
                                new AccessDeniedException(
                                        "승인/거절 권한이 없습니다."
                                )
                        );

        if (groupMember.getRole() != GroupMemberRole.OWNER) {
            throw new AccessDeniedException(
                    "승인/거절 권한이 없습니다."
            );
        }

        if (groupMember.getGroup().isFinished()) {
            throw new BusinessRuleException(
                    "이미 종료된 그룹은 인증을 검토할 수 없습니다."
            );
        }
    }
}