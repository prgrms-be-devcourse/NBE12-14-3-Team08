package com.back.domain.habitVerify.controller;

import static org.mockito.BDDMockito.given;
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
import com.back.domain.habitVerify.dto.HabitVerifyRequest;
import com.back.domain.habitVerify.entity.HabitVerify;
import com.back.domain.habitVerify.repository.HabitVerifyRepository;
import com.back.domain.member.entity.Member;
import com.back.domain.member.repository.MemberRepository;
import com.back.domain.member.service.AuthTokenService;
import com.back.global.storage.StorageService;
import com.back.global.storage.UploadUrlRequest;
import com.back.global.storage.UploadUrlResponse;
import com.back.global.storage.VerificationImageType;
import java.time.LocalDate;
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
class HabitVerifyControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private AuthTokenService authTokenService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private GroupRepository groupRepository;
    @Autowired private GroupMemberRepository groupMemberRepository;
    @Autowired private HabitRepository habitRepository;
    @Autowired private HabitVerifyRepository habitVerifyRepository;

    // 인증 사진 URL 검사·업로드 URL 발급(Supabase 호출)은 StorageServiceTest에서 확인하므로 여기서는 통과시킨다
    @MockitoBean private StorageService storageService;

    private String ownerToken;
    private Long memberId;
    private String memberToken;
    private String outsiderToken;
    private Habit memberHabit;
    private Long memberHabitId;

    @BeforeEach
    void setUp() {
        Group group = groupRepository.save(Group.builder()
            .title("아침 기상 챌린지")
            .penalty("커피 1잔 쏘기")
            .password("1234")
            .inviteCode("HABITCODE")
            .memberLimit(10)
            .build());

        Member owner = memberRepository.save(Member.create("방장", "owner", "pass1234"));
        groupMemberRepository.save(GroupMember.create(group, owner, GroupMemberRole.OWNER));
        ownerToken = authTokenService.createAccessToken(owner.getId());

        Member member = memberRepository.save(Member.create("일반회원", "member", "pass1234"));
        GroupMember memberGroupMember = groupMemberRepository.save(
            GroupMember.create(group, member, GroupMemberRole.MEMBER)
        );
        memberId = member.getId();
        memberToken = authTokenService.createAccessToken(member.getId());

        memberHabit = habitRepository.save(
            Habit.create(memberGroupMember, "기상 후 스트레칭", "10분 스트레칭", 3)
        );
        memberHabitId = memberHabit.getId();

        Member outsider = memberRepository.save(Member.create("다른방회원", "outsider", "pass1234"));
        outsiderToken = authTokenService.createAccessToken(outsider.getId());
    }

    private ResultActions postVerification(String token, Long habitId, HabitVerifyRequest request) throws Exception {
        return mockMvc.perform(post("/api/habits/{habitId}/verifications", habitId)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonMapper.writeValueAsString(request)));
    }

    @Nested
    @DisplayName("습관 인증 등록 POST /api/habits/{habitId}/verifications")
    class Create {

        @Test
        @DisplayName("내 습관에 사진과 함께 인증하면 201이고 검토 대기(PENDING)로 등록된다")
        void 등록_성공_201() throws Exception {
            postVerification(memberToken, memberHabitId,
                new HabitVerifyRequest("스트레칭 완료", "https://example.com/stretch.png"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.habitId").value(memberHabitId))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.description").value("스트레칭 완료"))
                .andExpect(jsonPath("$.imageUrl").value("https://example.com/stretch.png"));
        }

        @Test
        @DisplayName("사진 없이 인증하면 400이다 (컨트롤러가 @Valid로 사진 필수 검사를 한다)")
        void 사진_없으면_400() throws Exception {
            postVerification(memberToken, memberHabitId, new HabitVerifyRequest("스트레칭 완료", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("인증 사진은 필수입니다."));
        }

        @Test
        @DisplayName("같은 방이어도 남의 습관에 인증하면 403이다")
        void 남의_습관_403() throws Exception {
            postVerification(ownerToken, memberHabitId,
                new HabitVerifyRequest("대신 인증", "https://example.com/stretch.png"))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("같은 습관을 같은 날 두 번 인증하면 두 번째는 400이다")
        void 같은_날_두_번_400() throws Exception {
            HabitVerifyRequest request = new HabitVerifyRequest("스트레칭 완료", "https://example.com/stretch.png");
            postVerification(memberToken, memberHabitId, request).andExpect(status().isCreated());

            postVerification(memberToken, memberHabitId, request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("해당 날짜에 이미 인증 기록이 있습니다."));
        }

        @Test
        @DisplayName("포기해서 실패(FAILED)한 습관에 인증하면 409다")
        void 실패한_습관_409() throws Exception {
            memberHabit.fail();

            postVerification(memberToken, memberHabitId,
                new HabitVerifyRequest("스트레칭 완료", "https://example.com/stretch.png"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("진행 중인 습관에만 인증을 등록할 수 있습니다. 현재 상태: FAILED"));
        }
    }

    @Nested
    @DisplayName("습관 인증 목록 GET /api/habits/{habitId}/verifications")
    class FindAll {

        private ResultActions getVerifications(String token, Long habitId) throws Exception {
            return mockMvc.perform(get("/api/habits/{habitId}/verifications", habitId)
                .header("Authorization", "Bearer " + token));
        }

        @Test
        @DisplayName("같은 방 멤버는 남의 습관 인증 목록도 200으로 받는다")
        void 같은_방_멤버_200() throws Exception {
            habitVerifyRepository.save(
                HabitVerify.create(memberHabit, LocalDate.now(), "스트레칭 완료", "https://example.com/stretch.png")
            );

            getVerifications(ownerToken, memberHabitId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].description").value("스트레칭 완료"));
        }

        @Test
        @DisplayName("다른 방 사람이 조회하면 403이다")
        void 다른_방_사람_403() throws Exception {
            getVerifications(outsiderToken, memberHabitId)
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("없는 습관이면 404다")
        void 없는_습관_404() throws Exception {
            getVerifications(memberToken, Long.MAX_VALUE)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 습관입니다."));
        }
    }

    @Nested
    @DisplayName("반려된 습관 인증 재제출 PATCH /api/habits/{habitId}/verifications/{verificationId}/resubmit")
    class Resubmit {

        private Long saveVerify(boolean rejected) {
            HabitVerify habitVerify = HabitVerify.create(
                memberHabit, LocalDate.now(), "처음 인증", "https://example.com/old.png"
            );
            if (rejected) {
                habitVerify.reject();
            }
            return habitVerifyRepository.save(habitVerify).getId();
        }

        private ResultActions resubmit(Long verificationId, HabitVerifyRequest request) throws Exception {
            return mockMvc.perform(patch("/api/habits/{habitId}/verifications/{verificationId}/resubmit",
                    memberHabitId, verificationId)
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)));
        }

        @Test
        @DisplayName("반려된 인증을 다시 내면 200이고 검토 대기(PENDING)로 돌아간다")
        void 반려_재제출_200() throws Exception {
            Long verificationId = saveVerify(true);

            resubmit(verificationId, new HabitVerifyRequest("다시 인증", "https://example.com/new.png"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.description").value("다시 인증"))
                .andExpect(jsonPath("$.imageUrl").value("https://example.com/new.png"));
        }

        @Test
        @DisplayName("반려되지 않은 인증(검토 대기)을 다시 내면 409다")
        void 반려_아니면_409() throws Exception {
            Long verificationId = saveVerify(false);

            resubmit(verificationId, new HabitVerifyRequest("다시 인증", "https://example.com/new.png"))
                .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("사진 없이 다시 내면 400이다 (컨트롤러가 @Valid로 사진 필수 검사를 한다)")
        void 사진_없으면_400() throws Exception {
            Long verificationId = saveVerify(true);

            resubmit(verificationId, new HabitVerifyRequest("다시 인증", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("인증 사진은 필수입니다."));
        }

        @Test
        @DisplayName("없는 인증이면 404다")
        void 없는_인증_404() throws Exception {
            resubmit(Long.MAX_VALUE, new HabitVerifyRequest("다시 인증", "https://example.com/new.png"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 인증 기록입니다."));
        }
    }

    @Nested
    @DisplayName("습관 인증 사진 업로드 URL 발급 POST /api/storage/habits/{habitId}/upload-url")
    class UploadUrl {

        private ResultActions requestUploadUrl(String token, Long habitId) throws Exception {
            return mockMvc.perform(post("/api/storage/habits/{habitId}/upload-url", habitId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(new UploadUrlRequest("today.png"))));
        }

        @Test
        @DisplayName("내 습관이면 200이고 이 회원·이 습관 기준으로 발급한 URL을 돌려준다")
        void 내_습관_200() throws Exception {
            given(storageService.createUploadUrl(VerificationImageType.HABIT, memberId, memberHabitId, "today.png"))
                .willReturn(new UploadUrlResponse("https://storage/upload-signed", "https://storage/public.png"));

            requestUploadUrl(memberToken, memberHabitId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").value("https://storage/upload-signed"))
                .andExpect(jsonPath("$.publicUrl").value("https://storage/public.png"));
        }

        @Test
        @DisplayName("같은 방이어도 남의 습관이면 403이다")
        void 남의_습관_403() throws Exception {
            requestUploadUrl(ownerToken, memberHabitId)
                .andExpect(status().isForbidden());
        }
    }
}
