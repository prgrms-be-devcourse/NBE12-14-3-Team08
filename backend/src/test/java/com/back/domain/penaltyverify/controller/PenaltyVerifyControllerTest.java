package com.back.domain.penaltyverify.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.domain.group.entity.Group;
import com.back.domain.group.repository.GroupRepository;
import com.back.domain.groupMember.entity.GroupMember;
import com.back.domain.groupMember.entity.GroupMemberRole;
import com.back.domain.groupMember.repository.GroupMemberRepository;
import com.back.domain.habit.entity.Habit;
import com.back.domain.habit.repository.HabitRepository;
import com.back.domain.member.entity.Member;
import com.back.domain.member.repository.MemberRepository;
import com.back.domain.member.service.AuthTokenService;
import com.back.domain.penaltyverify.dto.BulkActionRequest;
import com.back.domain.penaltyverify.dto.SubmitPenaltyVerifyRequest;
import com.back.domain.penaltyverify.entity.PenaltyVerify;
import com.back.domain.penaltyverify.entity.PenaltyVerifyStatus;
import com.back.domain.penaltyverify.repository.PenaltyVerifyRepository;
import com.back.global.exception.StorageUnavailableException;
import com.back.global.storage.StorageService;
import com.back.global.storage.UploadUrlRequest;
import com.back.global.storage.UploadUrlResponse;
import com.back.global.storage.VerificationImageType;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PenaltyVerifyControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private MemberRepository memberRepository;
    @Autowired private AuthTokenService authTokenService;
    @Autowired private GroupRepository groupRepository;
    @Autowired private GroupMemberRepository groupMemberRepository;
    @Autowired private HabitRepository habitRepository;
    @Autowired private PenaltyVerifyRepository penaltyVerifyRepository;

    // 인증 사진 URL 검사(Supabase 호출)는 StorageServiceTest에서 확인하므로 여기서는 통과시킨다
    @MockitoBean private StorageService storageService;

    private Long ownerId;
    private String ownerToken;
    private Long memberId;
    private String memberToken;

    private Long groupId;
    private Long memberGroupMemberId;
    private Long memberHabitId;
    private Long pendingPenaltyId;
    private Long rejectedPenaltyId;

    @BeforeEach
    void setUp() {
        Group group = groupRepository.save(
            Group.builder()
                .title("테스트 그룹")
                .penalty("커피 사기")
                .password("1234")
                .inviteCode("TESTCODE")
                .memberLimit(5)
                .build()
        );
        groupId = group.getId();

        Member owner = memberRepository.save(Member.create("방장", "owner", "password"));
        ownerId = owner.getId();
        ownerToken = authTokenService.createAccessToken(ownerId);
        groupMemberRepository.save(GroupMember.create(group, owner, GroupMemberRole.OWNER));

        Member member = memberRepository.save(Member.create("일반회원", "member", "password"));
        memberId = member.getId();
        memberToken = authTokenService.createAccessToken(memberId);
        GroupMember normalGroupMember = groupMemberRepository.save(
            GroupMember.create(group, member, GroupMemberRole.MEMBER)
        );
        memberGroupMemberId = normalGroupMember.getId();

        Habit habit = habitRepository.save(
            Habit.create(normalGroupMember, "아침 운동", "30분 러닝", 30)
        );
        memberHabitId = habit.getId();
        penaltyVerifyRepository.save(PenaltyVerify.create(normalGroupMember, habit));

        Habit habitForPending = habitRepository.save(
            Habit.create(normalGroupMember, "책 읽기", "하루 10페이지", 30)
        );
        PenaltyVerify pendingPv = PenaltyVerify.create(normalGroupMember, habitForPending);
        pendingPv.submit(LocalDate.now(), "책 읽었습니다.", "https://example.com/book.jpg");
        pendingPenaltyId = penaltyVerifyRepository.save(pendingPv).getId();

        Habit habitForRejected = habitRepository.save(
            Habit.create(normalGroupMember, "물 마시기", "하루 2L", 30)
        );
        PenaltyVerify rejectedPv = PenaltyVerify.create(normalGroupMember, habitForRejected);
        rejectedPv.submit(LocalDate.now(), "물 인증", "https://example.com/water.jpg");
        rejectedPv.reject();
        rejectedPenaltyId = penaltyVerifyRepository.save(rejectedPv).getId();
    }

    @Test
    @DisplayName("벌칙 최초 제출 성공 시 201 Created 및 PENDING 반환")
    void submit_성공() throws Exception {
        SubmitPenaltyVerifyRequest request = new SubmitPenaltyVerifyRequest(
            "오늘 러닝 완료", "https://example.com/run.jpg"
        );

        mockMvc.perform(post("/api/habits/{habitId}/penalties", memberHabitId)
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.description").value("오늘 러닝 완료"))
            .andExpect(jsonPath("$.imageUrl").value("https://example.com/run.jpg"));
    }

    @Test
    @DisplayName("반려된 벌칙 재제출 성공 시 PENDING 상태로 갱신")
    void resubmit_성공() throws Exception {
        SubmitPenaltyVerifyRequest request = new SubmitPenaltyVerifyRequest(
            "물 다시 마시고 재인증합니다.", "https://example.com/water2.jpg"
        );

        mockMvc.perform(patch("/api/penalties/{id}/resubmit", rejectedPenaltyId)
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.description").value("물 다시 마시고 재인증합니다."));
    }

    @Test
    @DisplayName("벌칙 단건 상세 조회 성공")
    void getDetail_성공() throws Exception {
        mockMvc.perform(get("/api/penalties/{id}", pendingPenaltyId)
                .header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(pendingPenaltyId))
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("그룹 멤버별 벌칙 목록 최신순 조회 성공")
    void getPenaltyList_성공() throws Exception {
        mockMvc.perform(get("/api/groups/{groupId}/members/{groupMemberId}/penalties", groupId, memberGroupMemberId)
                .header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    @DisplayName("방장의 벌칙 단건 승인 성공 시 APPROVED 반환")
    void approve_성공() throws Exception {
        mockMvc.perform(patch("/api/penalties/{id}/approve", pendingPenaltyId)
                .header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @DisplayName("방장의 벌칙 단건 거절 성공 시 REJECTED 반환")
    void reject_성공() throws Exception {
        mockMvc.perform(patch("/api/penalties/{id}/reject", pendingPenaltyId)
                .header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    @DisplayName("방장의 그룹 내 벌칙 승인 대기 목록 조회 성공")
    void getPending_성공() throws Exception {
        mockMvc.perform(get("/api/groups/{groupId}/penalties/pending", groupId)
                .header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(pendingPenaltyId));
    }

    @Test
    @DisplayName("방장의 벌칙 일괄 승인 성공")
    void bulkApprove_성공() throws Exception {
        BulkActionRequest request = new BulkActionRequest(List.of(pendingPenaltyId));

        mockMvc.perform(patch("/api/groups/{groupId}/penalties/bulk-approve", groupId)
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].status").value("APPROVED"));
    }

    @Test
    @DisplayName("벌칙 작성자가 PENDING 벌칙을 제출 취소하면 200 OK 및 REQUIRED 반환, 기록은 남는다")
    void cancel_성공() throws Exception {
        mockMvc.perform(patch("/api/penalties/{id}/cancel", pendingPenaltyId)
                .header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REQUIRED"))
            .andExpect(jsonPath("$.imageUrl").doesNotExist());

        assertThat(penaltyVerifyRepository.findById(pendingPenaltyId)).isPresent();
    }

    @Nested
    @DisplayName("벌칙 인증 제출 실패 POST /api/habits/{habitId}/penalties")
    class SubmitFailure {

        private ResultActions submit(String token, Long habitId, SubmitPenaltyVerifyRequest request) throws Exception {
            return mockMvc.perform(post("/api/habits/{habitId}/penalties", habitId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)));
        }

        @Test
        @DisplayName("사진 없이 제출하면 400이다 (컨트롤러가 @Valid로 사진 필수 검사를 한다)")
        void 사진_없으면_400() throws Exception {
            submit(memberToken, memberHabitId, new SubmitPenaltyVerifyRequest("오늘 러닝 완료", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("인증 사진은 필수입니다."));
        }

        @Test
        @DisplayName("같은 방 방장이 남의 벌칙을 제출하면 403이다")
        void 남의_벌칙_403() throws Exception {
            submit(ownerToken, memberHabitId, new SubmitPenaltyVerifyRequest("대신 제출", "https://example.com/run.jpg"))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("벌칙이 없는 습관이면 404다")
        void 벌칙_없는_습관_404() throws Exception {
            GroupMember memberGroupMember = groupMemberRepository.findById(memberGroupMemberId).orElseThrow();
            Long habitWithoutPenaltyId = habitRepository.save(
                Habit.create(memberGroupMember, "일찍 자기", "11시 전 취침", 30)
            ).getId();

            submit(memberToken, habitWithoutPenaltyId, new SubmitPenaltyVerifyRequest("제출", "https://example.com/run.jpg"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("해당 습관에 대한 벌칙 정보가 존재하지 않습니다."));
        }

        @Test
        @DisplayName("사진을 확인하는 중 Supabase와 통신하지 못하면 502다")
        void 사진_저장소_장애_502() throws Exception {
            willThrow(new StorageUnavailableException("사진 저장소에 연결할 수 없습니다."))
                .given(storageService).validateImageUrl(any(), anyLong(), anyLong(), any(), any());

            submit(memberToken, memberHabitId, new SubmitPenaltyVerifyRequest("오늘 러닝 완료", "https://example.com/run.jpg"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("사진 저장소에 연결할 수 없습니다."));
        }
    }

    @Nested
    @DisplayName("벌칙 인증 사진 업로드 URL 발급 POST /api/penalties/{id}/upload-url")
    class UploadUrl {

        private ResultActions requestUploadUrl(String token, Long penaltyId) throws Exception {
            return mockMvc.perform(post("/api/penalties/{id}/upload-url", penaltyId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(new UploadUrlRequest("penalty.png"))));
        }

        @Test
        @DisplayName("재제출할 수 있는(반려된) 내 벌칙이면 200이고 이 회원·이 벌칙 기준으로 발급한 URL을 돌려준다")
        void 내_벌칙_200() throws Exception {
            given(storageService.createUploadUrl(VerificationImageType.PENALTY, memberId, rejectedPenaltyId, "penalty.png"))
                .willReturn(new UploadUrlResponse("https://storage/upload-signed", "https://storage/public.png"));

            requestUploadUrl(memberToken, rejectedPenaltyId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").value("https://storage/upload-signed"))
                .andExpect(jsonPath("$.publicUrl").value("https://storage/public.png"));
        }

        @Test
        @DisplayName("이미 승인된 벌칙이면 409다")
        void 승인된_벌칙_409() throws Exception {
            penaltyVerifyRepository.findById(pendingPenaltyId).orElseThrow().approve();

            requestUploadUrl(memberToken, pendingPenaltyId)
                .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("같은 방 방장이 남의 벌칙에 요청하면 403이다")
        void 남의_벌칙_403() throws Exception {
            requestUploadUrl(ownerToken, rejectedPenaltyId)
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("파일명이 비어 있으면 400이다 (UploadUrlRequest의 @NotBlank 검사)")
        void 파일명_없으면_400() throws Exception {
            mockMvc.perform(post("/api/penalties/{id}/upload-url", rejectedPenaltyId)
                    .header("Authorization", "Bearer " + memberToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(new UploadUrlRequest(" "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("입력값 검증에 실패했습니다."));
        }
    }
}