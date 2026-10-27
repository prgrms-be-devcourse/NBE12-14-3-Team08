"use client";

import { useEffect, useLayoutEffect, useRef, useState, useCallback } from "react";
import { createPortal } from "react-dom";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
    ArrowLeft,
    Calendar as CalendarIcon,
    Camera,
    Crown,
    AlertTriangle,
    Upload,
    Plus,
    ChevronLeft,
    ChevronRight,
    X,
} from "lucide-react";
import { useMember } from "@/context/MemberContext";
import { ModalShell, ModalHeader, ModalActions, ImageLightbox } from "@/components/ModalKit";
import {
    habitApi,
    uploadFileToSignedUrl,
    type HabitResponse,
    type HabitVerifyResponse,
    type HabitVerifyStatus,
    type GroupMemberDetail,
    type PenaltyVerifyStatus,
    type PenaltyVerifySummary,
    type PenaltyVerifyDetail,
} from "@/lib/habitApi";
import { readImageSize } from "@/lib/imageSize";
import { groupApi, type GroupDetailResponse } from "@/lib/groupApi";
import { daysSince, formatWeekRange, summarizeThisWeek, type WeekSummary } from "@/lib/habitWeek";

type Props = { groupId: string; memberId: string };

const PENALTY_STATUS_META: Record<PenaltyVerifyStatus, {
    statusLabel: string; // 상태 표시용 텍스트 (동그라미 옆, 모달 안)
    dotClassName: string; // 상태 표시 동그라미 색상
    badgeClassName: string; // 모달 안 상태 배지 색상
    action: "cert" | "detail";
}> = {
    REQUIRED: {
        statusLabel: "미인증",
        dotClassName: "bg-white border border-slate-300",
        badgeClassName: "text-slate-600 bg-slate-100",
        action: "cert",
    },
    PENDING: {
        statusLabel: "인증중",
        dotClassName: "bg-amber-400",
        badgeClassName: "text-amber-700 bg-amber-100",
        action: "detail",
    },
    APPROVED: {
        statusLabel: "인증완료",
        dotClassName: "bg-emerald-500",
        badgeClassName: "text-emerald-700 bg-emerald-100",
        action: "detail",
    },
    REJECTED: {
        statusLabel: "인증실패",
        dotClassName: "bg-rose-500",
        badgeClassName: "text-rose-700 bg-rose-100",
        action: "cert",
    },
};

// 상태와 분리된 버튼: 제출/재제출은 "벌칙 시행하기", 조회는 "벌칙 확인하기"
const PENALTY_ACTION_BUTTON_LABEL: Record<"cert" | "detail", string> = {
    cert: "벌칙 시행하기",
    detail: "벌칙 확인하기",
};

export function MemberPage({ groupId, memberId }: Props) {
    const router = useRouter();
    const { currentUser, authReady } = useMember();

    const [member, setMember] = useState<GroupMemberDetail | null>(null);
    const [group, setGroup] = useState<GroupDetailResponse | null>(null);
    const [penalties, setPenalties] = useState<PenaltyVerifySummary[]>([]);
    const [habitDaysById, setHabitDaysById] = useState<Record<number, number>>({});
    const [habitEndDateById, setHabitEndDateById] = useState<Record<number, string>>({});
    const [habit, setHabit] = useState<HabitResponse | null>(null);
    const [verifies, setVerifies] = useState<HabitVerifyResponse[]>([]);
    const [habitTitleById, setHabitTitleById] = useState<Record<number, string>>({});
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<Error | null>(null);

    const [activePenalty, setActivePenalty] = useState<PenaltyVerifySummary | null>(null);
    const [activePenaltyDetail, setActivePenaltyDetail] = useState<PenaltyVerifyDetail | null>(null);
    const [penaltyDetail, setPenaltyDetail] = useState<PenaltyVerifyDetail | null>(null);
    const [expandedHabitId, setExpandedHabitId] = useState<number | null>(null);
    const [showCertifyModal, setShowCertifyModal] = useState(false);
    const [showFailModal, setShowFailModal] = useState(false);
    const [showCreateHabitModal, setShowCreateHabitModal] = useState(false);
    const [certDetail, setCertDetail] = useState<HabitVerifyResponse | null>(null);
    // 상세 패널에서 이전/다음으로 넘길 때의 슬라이드 방향. 달력·앨범에서 직접 고르면 null
    const [certDirection, setCertDirection] = useState<"left" | "right" | null>(null);
    const [editingVerify, setEditingVerify] = useState<HabitVerifyResponse | null>(null);
    const [viewerIsOwner, setViewerIsOwner] = useState(false);
    const [showKickModal, setShowKickModal] = useState(false);
    const [showDelegateModal, setShowDelegateModal] = useState(false);

    const load = useCallback(async () => {
        try {
            setError(null);

            // 백엔드는 groupMemberId 기준으로 조회하므로, 라우트의 memberId를 그룹 멤버 목록에서 찾아 변환한다.
            const groupMembers = await habitApi.getGroupMembers(groupId);
            const target = groupMembers.find((m) => String(m.memberId) === String(memberId));
            if (!target) {
                throw new Error("멤버 정보를 찾을 수 없습니다.");
            }
            const groupMemberId = target.groupMemberId;

            const viewerEntry = groupMembers.find((m) => String(m.memberId) === currentUser?.id);
            setViewerIsOwner(viewerEntry?.role === "OWNER");

            const [memberRes, penaltiesRes, groupRes] = await Promise.all([
                habitApi.getGroupMember(groupId, groupMemberId),
                habitApi.getPenaltiesByGroupMember(groupId, groupMemberId),
                // 이번 주 계산에 방 생성일이 필요하다. 못 불러오면 링만 숨기고 나머지는 그대로 보여준다.
                groupApi.getById(groupId).catch(() => null),
            ]);
            setMember(memberRes);
            setGroup(groupRes);
            setPenalties(penaltiesRes);

            const uniqueHabitIds = Array.from(new Set(penaltiesRes.map((p) => p.habitId)));
            const habitEntries = await Promise.all(
                uniqueHabitIds.map(async (habitId) => {
                    try {
                        const habitRes = await habitApi.getHabit(habitId);
                        return {
                            habitId,
                            days: habitRes.days,
                            endDate: habitRes.modifyDate.slice(0, 10),
                            title: habitRes.title,
                        };
                    } catch {
                        return null;
                    }
                })
            );
            const validHabitEntries = habitEntries.filter((entry): entry is NonNullable<typeof entry> => entry !== null);
            setHabitDaysById(Object.fromEntries(validHabitEntries.map((e) => [e.habitId, e.days])));
            setHabitEndDateById(Object.fromEntries(validHabitEntries.map((e) => [e.habitId, e.endDate])));

            // 활성 습관 여부/식별자는 그룹 멤버 목록에 이미 포함되어 있어, 본인 여부와 무관하게
            // 그룹의 아무 멤버나 대상 멤버의 도전 습관과 인증 캘린더를 조회할 수 있다 (조회만 가능).
            let activeHabit: HabitResponse | null = null;
            let activeVerifies: HabitVerifyResponse[] = [];
            if (target.habitId != null) {
                try {
                    activeHabit = await habitApi.getHabit(target.habitId);
                    activeVerifies = await habitApi.getHabitVerifications(activeHabit.id);
                } catch {
                    activeHabit = null;
                    activeVerifies = [];
                }
            }
            setHabit(activeHabit);

            // 이전 습관의 인증도 앨범·달력에 함께 보여준다.
            // 실패한 습관은 벌칙이 하나씩 생기므로 벌칙 목록의 습관 id가 곧 이 멤버의 이전 습관이다.
            // (그룹의 실패 습관 목록 API는 요청한 본인 것만 주기 때문에 다른 멤버 페이지에서는 쓸 수 없다)
            const previousHabitIds = uniqueHabitIds.filter((id) => id !== activeHabit?.id);
            const previousVerifies = (
                await Promise.all(
                    previousHabitIds.map((id) => habitApi.getHabitVerifications(id).catch(() => [] as HabitVerifyResponse[]))
                )
            ).flat();

            // 같은 날짜에 두 습관의 인증이 있으면 달력은 마지막 것을 보여주므로, 현재 습관의 인증을 뒤에 둔다
            setVerifies([...previousVerifies, ...activeVerifies]);
            setHabitTitleById({
                ...Object.fromEntries(validHabitEntries.map((e) => [e.habitId, e.title])),
                ...(activeHabit ? { [activeHabit.id]: activeHabit.title } : {}),
            });
        } catch (err) {
            setError(err instanceof Error ? err : new Error("알 수 없는 오류가 발생했습니다."));
        } finally {
            setLoading(false);
        }
    }, [groupId, memberId, currentUser]);

    useEffect(() => {
        if (!authReady) return;
        if (!currentUser) {
            router.replace(`/login?redirect=/group/${groupId}/member/${memberId}`);
            return;
        }
        load();
    }, [authReady, currentUser, load, router, groupId, memberId]);

    if (!authReady || !currentUser) {
        return <CenteredMessage text="로그인 정보를 확인하는 중..." />;
    }

    if (loading) {
        return <CenteredMessage text="불러오는 중..." />;
    }

    if (error) {
        return <CenteredMessage text={error.message} />;
    }

    if (!member) {
        return <CenteredMessage text="멤버 정보를 찾을 수 없습니다." />;
    }

    const isMe = currentUser.id === String(member.memberId);
    const isHost = member.role === "OWNER";

    // 이번 주 링: 현재 습관의 인증만 센다. 방 정보를 못 불러왔거나 진행 중인 습관이 없으면 링을 보여주지 않는다.
    const today = new Date();
    const weekSummary =
        habit && habit.status === "ACTIVE" && group
            ? summarizeThisWeek({
                  verifies: verifies.filter((v) => v.habitId === habit.id),
                  goal: habit.days,
                  habitCreateDate: habit.createDate,
                  groupCreateDate: group.createDate,
                  today,
              })
            : null;

    // 아직 제출 전(REQUIRED)이라 날짜가 없는 벌칙은 맨 위로 오도록 미래 날짜 취급
    const rows = [...penalties].sort((a, b) =>
        (b.verifyDate ?? "9999-99-99").localeCompare(a.verifyDate ?? "9999-99-99")
    );

    const openCert = (verify: HabitVerifyResponse) => {
        // 오른쪽 패널은 한 번에 하나만 연다
        setActivePenalty(null);
        setPenaltyDetail(null);
        setCertDetail(verify);
        setCertDirection(null);
    };

    const openPenaltyDetail = async (penaltyId: number) => {
        try {
            const detail = await habitApi.getPenaltyDetail(penaltyId);
            setCertDetail(null);
            setPenaltyDetail(detail);
        } catch {
            // 상세 조회 실패 시 조용히 무시 (요약 정보는 이미 화면에 있음)
        }
    };

    const openPenaltyAction = async (penalty: PenaltyVerifySummary) => {
        const meta = PENALTY_STATUS_META[penalty.status];
        if (meta.action !== "cert") {
            await openPenaltyDetail(penalty.id);
            return;
        }
        if (penalty.status === "REJECTED") {
            // 반려된 벌칙은 기존 제출 내용을 불러와 수정 형태로 다시 제출할 수 있도록 한다.
            try {
                const detail = await habitApi.getPenaltyDetail(penalty.id);
                setActivePenaltyDetail(detail);
            } catch {
                setActivePenaltyDetail(null);
            }
        } else {
            setActivePenaltyDetail(null);
        }
        setCertDetail(null);
        setActivePenalty(penalty);
    };

    return (
        <div className="max-w-5xl mx-auto px-4 sm:px-6 py-6 md:py-8">
            {/* Top Nav */}
            <div className="flex items-center justify-between mb-5">
                <Link
                    href={`/group/${groupId}`}
                    className="flex items-center gap-1.5 text-xs font-bold text-slate-500 hover:text-slate-900 transition-colors"
                >
                    <ArrowLeft className="w-4 h-4" />
                    <span>돌아가기</span>
                </Link>
                <span className="text-xs font-semibold px-2.5 py-1 rounded-full bg-slate-100 text-slate-600">
                    멤버 상세 페이지
                </span>
            </div>

            {/* 요약 카드: 프로필, 이번 주 링, 도전 습관 */}
            <div className="relative overflow-hidden rounded-[32px] bg-gradient-to-br from-[#0f3d2e] to-[#0a2a20] text-white p-6 sm:p-8 mb-6 shadow-[0_18px_40px_rgba(11,20,17,0.28)]">
                <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-4">
                    <div className="flex items-center gap-3.5">
                        <div className="shrink-0 w-[60px] h-[60px] rounded-full p-[3px] bg-[conic-gradient(from_200deg,#c6f24a,#34e6a4,#c6f24a)]">
                            <div className="w-full h-full rounded-full bg-[#0f3d2e] flex items-center justify-center text-xl font-black">
                                {member.nickname.slice(0, 1)}
                            </div>
                        </div>
                        <div>
                            <div className="flex items-center gap-2 flex-wrap">
                                <h1 className="text-[22px] sm:text-2xl font-extrabold tracking-tight">{member.nickname}</h1>
                                {isHost && (
                                    <span className="inline-flex items-center gap-1 text-[11px] font-extrabold px-2.5 py-0.5 rounded-full bg-amber-400/15 text-amber-300">
                                        <Crown className="w-3 h-3 fill-amber-300" />
                                        방장
                                    </span>
                                )}
                                {isMe && (
                                    <span className="text-[11px] font-extrabold px-2.5 py-0.5 rounded-full bg-[#c6f24a]/15 text-[#c6f24a]">
                                        나
                                    </span>
                                )}
                                <span
                                    title="누적 벌칙"
                                    className="inline-flex items-center gap-1.5 text-[11px] font-extrabold px-2.5 py-0.5 rounded-full bg-rose-400/15 text-rose-300"
                                >
                                    <span className="w-1.5 h-1.5 rounded-full bg-rose-400" />
                                    벌칙 {member.penaltyCount}회
                                </span>
                            </div>
                            <p className="text-xs text-white/55 mt-1">
                                {group?.title ?? "챌린지 방"}
                                {habit && habit.status === "ACTIVE" ? ` · 도전 ${daysSince(habit.createDate, today)}일째` : ""}
                            </p>
                        </div>
                    </div>

                    {viewerIsOwner && !isMe && !isHost && (
                        <div className="flex items-center gap-1.5 shrink-0">
                            <button
                                onClick={() => setShowDelegateModal(true)}
                                className="px-3 py-2.5 rounded-xl font-bold text-xs text-amber-200 bg-white/10 hover:bg-white/15 transition-colors cursor-pointer"
                            >
                                방장 위임
                            </button>
                            <button
                                onClick={() => setShowKickModal(true)}
                                className="px-3 py-2.5 rounded-xl font-bold text-xs text-rose-300 bg-white/10 hover:bg-white/15 transition-colors cursor-pointer"
                            >
                                추방
                            </button>
                        </div>
                    )}
                </div>

                {weekSummary && (
                    <>
                        <div className="mt-6 flex flex-wrap items-center justify-center gap-7">
                            <WeekRing summary={weekSummary} />
                            <div className="grid gap-2.5 text-[13px] font-extrabold text-white/70">
                                <p
                                    className="text-[12px] font-bold text-white/55"
                                    title="방을 만든 요일부터 7일씩 한 주로 센다"
                                >
                                    {formatWeekRange(weekSummary.weekStart, weekSummary.weekEnd)}
                                </p>
                                <div className="flex items-center gap-2">
                                    <i className="w-[22px] h-2 rounded-full bg-[#c6f24a]" />
                                    승인 <b className="text-base text-white">{weekSummary.approved}</b>
                                </div>
                                {weekSummary.pending > 0 && (
                                    <div className="flex items-center gap-2">
                                        <i className="w-[22px] h-2 rounded-full bg-[#c6f24a]/40" />
                                        검토중 <b className="text-base text-white">{weekSummary.pending}</b>
                                    </div>
                                )}
                            </div>
                        </div>

                        {weekSummary.isExempt && (
                            <p className="mt-4 mx-auto max-w-md text-center text-[13px] font-bold text-white/85 bg-white/10 ring-1 ring-white/20 rounded-2xl px-4 py-2.5">
                                🌱 습관을 만든 이번 주는 판정하지 않아요
                            </p>
                        )}

                        {weekSummary.goalReached && (
                            <p className="mt-4 text-center text-sm font-extrabold text-[#0c1a13] rounded-2xl px-4 py-3 bg-gradient-to-r from-[#34e6a4] to-[#c6f24a] anim-modal-pop-in">
                                🎉 이번 주 목표 달성! 주 {weekSummary.goal}회 승인을 채웠어요
                            </p>
                        )}
                    </>
                )}

                {/* 도전 습관 정보는 같은 그룹 멤버라면 누구나 조회 가능, 액션 버튼은 본인만 */}
                {habit ? (
                    <div className="mt-6 pt-5 border-t border-white/10 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                        <div>
                            <span className="text-[11px] font-extrabold tracking-wider text-[#c6f24a]">도전 습관</span>
                            <div className="flex items-center gap-2 flex-wrap mt-0.5">
                                <h2 className="text-xl sm:text-2xl font-extrabold tracking-tight">{habit.title}</h2>
                                <span className="text-xs font-extrabold px-2.5 py-0.5 rounded-full bg-white/10">
                                    주 {habit.days}회
                                </span>
                            </div>
                            <p className="text-xs text-white/55 mt-1">
                                {habit.description || "매일매일 실천하고 성공을 기록해보세요!"}
                            </p>
                        </div>

                        {isMe && habit.status === "ACTIVE" && (
                            <div className="flex items-center gap-2 shrink-0">
                                <button
                                    onClick={() => setShowCertifyModal(true)}
                                    className="flex items-center gap-1.5 px-5 py-3 rounded-2xl font-extrabold text-sm text-[#0c1a13] bg-[#c6f24a] hover:-translate-y-0.5 shadow-[0_8px_20px_rgba(198,242,74,0.25)] transition-all cursor-pointer"
                                >
                                    <Camera className="w-4 h-4" />
                                    <span>오늘 인증하기</span>
                                </button>
                                <button
                                    onClick={() => setShowFailModal(true)}
                                    className="px-4 py-3 rounded-2xl font-bold text-sm text-white/75 bg-white/10 hover:bg-white/15 transition-colors cursor-pointer"
                                >
                                    습관 포기하기
                                </button>
                            </div>
                        )}
                        {habit.status === "FAILED" && (
                            <span className="text-xs font-bold px-3 py-1.5 rounded-xl bg-white/10 text-white/70 shrink-0">
                                실패 처리된 습관입니다
                            </span>
                        )}
                    </div>
                ) : (
                    <div className="mt-6 pt-5 border-t border-white/10 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                        <div>
                            <span className="text-[11px] font-extrabold tracking-wider text-[#c6f24a]">도전 습관</span>
                            <p className="text-sm text-white/65 mt-0.5">아직 진행 중인 습관이 없습니다.</p>
                        </div>
                        {isMe && (
                            <button
                                onClick={() => setShowCreateHabitModal(true)}
                                className="flex items-center gap-1.5 px-5 py-3 rounded-2xl font-extrabold text-sm text-[#0c1a13] bg-[#c6f24a] hover:-translate-y-0.5 shadow-[0_8px_20px_rgba(198,242,74,0.25)] transition-all cursor-pointer shrink-0"
                            >
                                <Plus className="w-4 h-4" />
                                <span>습관을 추가해주세요</span>
                            </button>
                        )}
                    </div>
                )}
            </div>

            {/* 인증 기록: 달력과 앨범을 토글로 전환 */}
            {(habit || verifies.length > 0) && (
                <VerifyRecords
                    verifies={verifies}
                    onSelect={openCert}
                    thisWeek={weekSummary ? { start: weekSummary.weekStart, end: weekSummary.weekEnd } : null}
                />
            )}

            {/* 벌칙 기록 (실패한 습관 종류와 무관하게 이 멤버의 전체 벌칙) */}
            <div className="bg-white rounded-[28px] shadow-[0_10px_30px_rgba(14,21,18,0.07),0_1px_2px_rgba(14,21,18,0.05)] p-6">
                <h3 className="text-[17px] font-extrabold text-slate-900 mb-4">벌칙 기록</h3>

                {rows.length === 0 ? (
                    <p className="text-xs text-slate-400">아직 기록이 없습니다.</p>
                ) : (
                    <div className="space-y-3">
                        {rows.map((penalty) => {
                            const isExpanded = expandedHabitId === penalty.habitId;
                            return (
                                <div
                                    key={penalty.id}
                                    className="p-3.5 sm:p-4 rounded-2xl border border-slate-100 bg-slate-50/60"
                                >
                                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                                        <button
                                            type="button"
                                            onClick={() =>
                                                setExpandedHabitId(isExpanded ? null : penalty.habitId)
                                            }
                                            className="text-left flex-1 rounded-xl -m-1.5 p-1.5 hover:bg-slate-100/80 transition-colors cursor-pointer"
                                        >
                                            <div className="flex items-center gap-2">
                                                <span className="text-sm font-semibold text-slate-700">
                                                    {penalty.habitTitle}
                                                </span>
                                                {habitDaysById[penalty.habitId] != null && (
                                                    <span className="text-[10px] font-bold text-slate-400">
                                                        주 {habitDaysById[penalty.habitId]}회
                                                    </span>
                                                )}
                                            </div>
                                            <p className="text-[11px] font-semibold text-slate-400 mt-0.5">
                                                {habitEndDateById[penalty.habitId] ? `~${habitEndDateById[penalty.habitId]}` : "-"}
                                            </p>
                                        </button>
                                        <div className="flex items-center gap-3 shrink-0">
                                            {(() => {
                                                const meta = PENALTY_STATUS_META[penalty.status];
                                                const isActionable = meta.action === "detail" || isMe;

                                                return (
                                                    <>
                                                        <div className="flex items-center gap-1.5">
                                                            <span className={`w-2.5 h-2.5 rounded-full shrink-0 ${meta.dotClassName}`} />
                                                            <span className="text-xs font-bold text-slate-700">
                                                                {meta.statusLabel}
                                                            </span>
                                                        </div>

                                                        {isActionable && (
                                                            <button
                                                                onClick={() => openPenaltyAction(penalty)}
                                                                className="w-[110px] text-center text-xs font-bold text-rose-600 bg-rose-50 border border-rose-300 hover:bg-rose-100 px-2.5 py-1.5 rounded-full transition-colors cursor-pointer"
                                                            >
                                                                {PENALTY_ACTION_BUTTON_LABEL[meta.action]}
                                                            </button>
                                                        )}
                                                    </>
                                                );
                                            })()}
                                        </div>
                                    </div>

                                    {isExpanded && <HabitHistoryHeatmap habitId={penalty.habitId} />}
                                </div>
                            );
                        })}
                    </div>
                )}
            </div>

            {/* 모달들 */}
            {(showCertifyModal || editingVerify) && habit && (
                <CertifyModal
                    initial={
                        editingVerify
                            ? { description: editingVerify.description, imageUrl: editingVerify.imageUrl }
                            : null
                    }
                    onClose={() => {
                        setShowCertifyModal(false);
                        setEditingVerify(null);
                    }}
                    onSubmit={async (file, description, existingImageUrl) => {
                        let imageUrl: string | null = existingImageUrl;
                        if (file) {
                            const { uploadUrl, publicUrl } = await habitApi.getHabitUploadUrl(habit.id, file.name);
                            await uploadFileToSignedUrl(uploadUrl, file);
                            imageUrl = publicUrl;
                        }
                        if (!imageUrl) {
                            throw new Error("습관 인증 사진은 필수입니다.");
                        }

                        // 갤러리가 사진 비율대로 자리를 잡도록 크기를 함께 보낸다. 사진을 바꾸지 않은 재제출은
                        // 서버가 이전 크기를 비우므로 올라가 있는 사진에서 다시 읽는다. 못 읽으면 크기 없이 보낸다.
                        const imageSize = await readImageSize(file ?? imageUrl);

                        if (editingVerify) {
                            await habitApi.resubmitHabitVerification(habit.id, editingVerify.id, {
                                description: description || null,
                                imageUrl,
                                ...imageSize,
                            });
                        } else {
                            await habitApi.createHabitVerification(habit.id, {
                                description: description || null,
                                imageUrl,
                                ...imageSize,
                            });
                        }
                        setShowCertifyModal(false);
                        setEditingVerify(null);
                        await load();
                    }}
                />
            )}

            {showFailModal && habit && (
                <HabitFailModal
                    onClose={() => setShowFailModal(false)}
                    onConfirm={async () => {
                        await habitApi.failHabit(habit.id);
                        setShowFailModal(false);
                        await load();
                    }}
                />
            )}

            {showDelegateModal && member && (
                <ConfirmModal
                    title="방장 권한 위임"
                    message={
                        <>
                            <strong className="text-slate-700">{member.nickname}</strong>님에게 방장 권한을
                            위임할까요?
                            <br />
                            <span className="text-amber-600 font-semibold">
                                (위임 후에는 본인은 일반 멤버가 됩니다.)
                            </span>
                        </>
                    }
                    confirmLabel="위임 확정"
                    confirmClassName="bg-amber-500 hover:bg-amber-600 shadow-amber-500/20"
                    onClose={() => setShowDelegateModal(false)}
                    onConfirm={async () => {
                        await habitApi.transferOwner(groupId, member.groupMemberId);
                        setShowDelegateModal(false);
                        await load();
                    }}
                />
            )}

            {showKickModal && member && (
                <ConfirmModal
                    title="멤버 추방"
                    message={
                        <>
                            <strong className="text-slate-700">{member.nickname}</strong>님을 그룹에서
                            추방할까요?
                            <br />
                            <span className="text-rose-500 font-semibold">
                                (해당 멤버의 습관·인증·벌칙 기록이 모두 삭제됩니다.)
                            </span>
                        </>
                    }
                    confirmLabel="추방 확정"
                    confirmClassName="bg-rose-500 hover:bg-rose-600 shadow-rose-500/20"
                    onClose={() => setShowKickModal(false)}
                    onConfirm={async () => {
                        await habitApi.kickMember(groupId, member.groupMemberId);
                        setShowKickModal(false);
                        router.push(`/group/${groupId}`);
                    }}
                />
            )}

            {showCreateHabitModal && (
                <CreateHabitModal
                    onClose={() => setShowCreateHabitModal(false)}
                    onSubmit={async (title, description, days) => {
                        await habitApi.createHabit(groupId, { title, description: description || null, days });
                        setShowCreateHabitModal(false);
                        await load();
                    }}
                />
            )}

            {certDetail && (
                <CertDetailPanel
                    item={certDetail}
                    verifies={verifies}
                    activeHabitId={habit?.id ?? null}
                    habitTitleById={habitTitleById}
                    isMe={isMe}
                    direction={certDirection}
                    onNavigate={(target, dir) => {
                        setCertDetail(target);
                        setCertDirection(dir);
                    }}
                    onClose={() => setCertDetail(null)}
                    onEdit={(target) => {
                        setEditingVerify(target);
                        setCertDetail(null);
                    }}
                />
            )}

            {activePenalty && (
                <PenaltyCertPanel
                    status={activePenalty.status}
                    initial={
                        activePenaltyDetail
                            ? { description: activePenaltyDetail.description, imageUrl: activePenaltyDetail.imageUrl }
                            : null
                    }
                    onClose={() => {
                        setActivePenalty(null);
                        setActivePenaltyDetail(null);
                    }}
                    onSubmit={async (file, description, existingImageUrl) => {
                        let imageUrl = existingImageUrl;
                        if (file) {
                            const { uploadUrl, publicUrl } = await habitApi.getPenaltyUploadUrl(activePenalty.id, file.name);
                            await uploadFileToSignedUrl(uploadUrl, file);
                            imageUrl = publicUrl;
                        }
                        if (!imageUrl) {
                            throw new Error("벌칙 인증 사진은 필수입니다.");
                        }

                        if (activePenalty.status === "REJECTED") {
                            await habitApi.resubmitPenalty(activePenalty.id, {
                                description: description || null,
                                imageUrl,
                            });
                        } else {
                            await habitApi.submitPenalty(activePenalty.habitId, {
                                description: description || null,
                                imageUrl,
                            });
                        }
                        setActivePenalty(null);
                        setActivePenaltyDetail(null);
                        await load();
                    }}
                />
            )}

            {penaltyDetail && (
                <PenaltyDetailPanel item={penaltyDetail} onClose={() => setPenaltyDetail(null)} />
            )}
        </div>
    );
}

function CenteredMessage({ text }: { text: string }) {
    return (
        <div className="max-w-5xl mx-auto px-4 py-16 text-center space-y-3">
            <p className="text-slate-500">{text}</p>
        </div>
    );
}

// ---------- 캘린더 ----------

const VERIFY_STATUS_LABEL: Record<HabitVerifyStatus, string> = {
    APPROVED: "성공",
    PENDING: "검토중",
    REJECTED: "반려",
};

// 인증 상태 점 (달력, 앨범 공통). 사진 위에서도 보이도록 옅은 그림자를 둔다.
const STATUS_DOT_CLASS: Record<HabitVerifyStatus, string> = {
    APPROVED: "bg-emerald-500",
    PENDING: "bg-amber-400",
    REJECTED: "bg-rose-400",
};

function StatusDot({ status, className = "" }: { status: HabitVerifyStatus; className?: string }) {
    return (
        <span
            className={`w-3 h-3 rounded-full shadow-[0_0_2px_rgba(0,0,0,0.35)] ${STATUS_DOT_CLASS[status]} ${className}`}
        />
    );
}

// 인증 사진 썸네일. 반려된 사진은 흑백으로 흐리게 보여준다.
function VerifyThumb({ verify }: { verify: HabitVerifyResponse }) {
    const [failed, setFailed] = useState(false);

    // 사진이 없거나 불러오지 못하면 깨진 이미지 아이콘 대신 회색 칸을 보여준다
    if (!verify.imageUrl || failed) {
        return <span className="absolute inset-0 bg-slate-200" />;
    }
    return (
        // eslint-disable-next-line @next/next/no-img-element
        <img
            src={verify.imageUrl}
            alt=""
            loading="lazy"
            onError={() => setFailed(true)}
            className={`absolute inset-0 w-full h-full object-cover ${
                verify.status === "REJECTED" ? "grayscale opacity-70" : ""
            }`}
        />
    );
}

const monthOf = (verifyDate: string) => verifyDate.slice(0, 7); // "YYYY-MM"

// 같은 달의 인증을 최신순으로 정렬한다 (앨범 표시와 상세 보기 이동에 함께 쓴다)
const verifiesOfMonth = (verifies: HabitVerifyResponse[], month: string) =>
    verifies
        .filter((v) => monthOf(v.verifyDate) === month)
        .sort((a, b) => b.verifyDate.localeCompare(a.verifyDate) || b.id - a.id);

// 달력에서 링이 세는 이번 주 칸: 링과 같은 라임색의 얇은 후광과 옅은 그림자
const THIS_WEEK_OUTLINE =
    "ring-2 ring-[#c6f24a] ring-offset-2 shadow-[0_6px_14px_-6px_rgba(110,160,0,0.5)]";

function VerifyLegend({ showThisWeek = false }: { showThisWeek?: boolean }) {
    return (
        <div className="flex items-center justify-end gap-x-2.5 whitespace-nowrap text-[11px] font-bold text-slate-500">
            <span className="flex items-center gap-1">
                <span className="w-2.5 h-2.5 rounded-full bg-emerald-500 inline-block" />
                성공
            </span>
            <span className="flex items-center gap-1">
                <span className="w-2.5 h-2.5 rounded-full bg-amber-400 inline-block" />
                검토중
            </span>
            <span className="flex items-center gap-1">
                <span className="w-2.5 h-2.5 rounded-full bg-rose-400 inline-block" />
                반려
            </span>
            <span className="flex items-center gap-1 text-slate-400">
                <span className="w-2.5 h-2.5 rounded-full bg-slate-100 ring-1 ring-slate-300 inline-block" />
                미인증
            </span>
            {showThisWeek && (
                <span className="flex items-center gap-1">
                    <span className="w-3 h-2.5 rounded-[3px] bg-white ring-2 ring-[#c6f24a] inline-block" />
                    이번 주
                </span>
            )}
        </div>
    );
}

// 인증 기록 카드: 달력과 앨범을 토글로 전환한다
function VerifyRecords({
    verifies,
    onSelect,
    thisWeek,
}: {
    verifies: HabitVerifyResponse[];
    onSelect: (v: HabitVerifyResponse) => void;
    thisWeek: { start: string; end: string } | null;
}) {
    const [view, setView] = useState<"calendar" | "album">("calendar");

    return (
        <div className="bg-white rounded-[28px] shadow-[0_10px_30px_rgba(14,21,18,0.07),0_1px_2px_rgba(14,21,18,0.05)] p-6 mb-6">
            <div className="flex items-center justify-between mb-4">
                <h3 className="text-[17px] font-extrabold text-slate-900">인증 기록</h3>
                <div role="tablist" aria-label="인증 기록 보기" className="flex bg-slate-100 rounded-full p-[3px]">
                    {(["calendar", "album"] as const).map((v) => (
                        <button
                            key={v}
                            type="button"
                            role="tab"
                            aria-selected={view === v}
                            onClick={() => setView(v)}
                            className={`px-4 py-1.5 rounded-full text-[13px] font-extrabold cursor-pointer transition-colors ${
                                view === v ? "bg-slate-900 text-white" : "text-slate-500 hover:text-slate-700"
                            }`}
                        >
                            {v === "calendar" ? "달력" : "앨범"}
                        </button>
                    ))}
                </div>
            </div>

            <div key={view} className="anim-modal-pop-in">
                {view === "calendar" ? (
                    <VerifyCalendar verifies={verifies} onSelectDay={onSelect} thisWeek={thisWeek} />
                ) : (
                    <VerifyAlbum verifies={verifies} onSelect={onSelect} />
                )}
            </div>
        </div>
    );
}

// ---------- 이번 주 링 ----------

const RING_RADIUS = 92;
const RING_LENGTH = 2 * Math.PI * RING_RADIUS;

function WeekRing({ summary }: { summary: WeekSummary }) {
    const fraction = (count: number) => Math.min(1, count / summary.goal);
    const arcStyle = (count: number) =>
        ({
            strokeDasharray: RING_LENGTH,
            strokeDashoffset: RING_LENGTH * (1 - fraction(count)),
            "--ring-len": RING_LENGTH,
        }) as React.CSSProperties;

    return (
        <div
            role="img"
            aria-label={`이번 주 인증 ${summary.approved}회, 목표 ${summary.goal}회`}
            className="relative w-[180px] h-[180px] sm:w-[240px] sm:h-[240px] shrink-0"
        >
            <svg viewBox="0 0 210 210" className="w-full h-full -rotate-90">
                <circle cx="105" cy="105" r={RING_RADIUS} fill="none" stroke="rgba(255,255,255,0.14)" strokeWidth="17" />
                {summary.pending > 0 && (
                    <circle
                        cx="105"
                        cy="105"
                        r={RING_RADIUS}
                        fill="none"
                        stroke="#c6f24a"
                        strokeOpacity="0.4"
                        strokeWidth="17"
                        strokeLinecap="round"
                        className="anim-ring-fill"
                        style={arcStyle(summary.approved + summary.pending)}
                    />
                )}
                {summary.approved > 0 && (
                    <circle
                        cx="105"
                        cy="105"
                        r={RING_RADIUS}
                        fill="none"
                        stroke="#c6f24a"
                        strokeWidth="17"
                        strokeLinecap="round"
                        className="anim-ring-fill"
                        style={arcStyle(summary.approved)}
                    />
                )}
            </svg>
            {summary.goalReached && <span className="absolute inset-0 rounded-full anim-ring-pulse pointer-events-none" />}
            <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
                <span className="text-xs sm:text-sm font-extrabold text-white/55">이번 주</span>
                <b className="text-[52px] sm:text-[68px] leading-none font-extrabold tracking-tight text-[#c6f24a] tabular-nums">
                    {summary.approved}
                    <em className="not-italic text-[24px] sm:text-[32px] opacity-60 ml-0.5">/{summary.goal}</em>
                </b>
            </div>
        </div>
    );
}

function VerifyCalendar({
    verifies,
    onSelectDay,
    thisWeek,
}: {
    verifies: HabitVerifyResponse[];
    onSelectDay: (v: HabitVerifyResponse) => void;
    // 링이 세는 이번 주 기간. 달력에서 같은 칸들을 표시해 링 숫자와 맞춰 볼 수 있게 한다.
    thisWeek: { start: string; end: string } | null;
}) {
    const now = new Date();
    const todayYear = now.getFullYear();
    const todayMonth = now.getMonth();
    const todayDate = now.getDate();

    const [year, setYear] = useState(todayYear);
    const [month, setMonth] = useState(todayMonth); // 0-indexed

    const goPrevMonth = () => {
        if (month === 0) {
            setYear((y) => y - 1);
            setMonth(11);
        } else {
            setMonth((m) => m - 1);
        }
    };

    const goNextMonth = () => {
        if (month === 11) {
            setYear((y) => y + 1);
            setMonth(0);
        } else {
            setMonth((m) => m + 1);
        }
    };

    const daysInMonth = new Date(year, month + 1, 0).getDate();
    const startDayOfWeek = new Date(year, month, 1).getDay();
    const weekDays = ["일", "월", "화", "수", "목", "금", "토"];

    // 상태와 무관하게 그 날짜에 제출된 인증이 있으면 그대로 보여준다 (승인된 것만 걸러내지 않음).
    const verifyByDate = new Map<string, HabitVerifyResponse>();
    verifies.forEach((v) => verifyByDate.set(v.verifyDate, v));

    const pad = (n: number) => n.toString().padStart(2, "0");
    const cells: Array<{
        day: number;
        dateStr: string;
        isToday: boolean;
        inThisWeek: boolean;
        verify?: HabitVerifyResponse;
    } | null> = [];

    for (let i = 0; i < startDayOfWeek; i++) cells.push(null);
    for (let day = 1; day <= daysInMonth; day++) {
        const dateStr = `${year}-${pad(month + 1)}-${pad(day)}`;
        cells.push({
            day,
            dateStr,
            isToday: year === todayYear && month === todayMonth && day === todayDate,
            inThisWeek: !!thisWeek && dateStr >= thisWeek.start && dateStr <= thisWeek.end,
            verify: verifyByDate.get(dateStr),
        });
    }

    return (
        <div>
            <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2 mb-4">
                <div className="flex items-center gap-2">
                    <button
                        onClick={goPrevMonth}
                        className="p-1 rounded-lg hover:bg-slate-100 transition-colors cursor-pointer"
                        aria-label="이전 달"
                    >
                        <ChevronLeft className="w-4 h-4 text-slate-500" />
                    </button>
                    <CalendarIcon className="w-4 h-4 text-emerald-600" />
                    <h3 className="text-sm font-extrabold text-slate-900">
                        {year}년 {month + 1}월
                    </h3>
                    <button
                        onClick={goNextMonth}
                        className="p-1 rounded-lg hover:bg-slate-100 transition-colors cursor-pointer"
                        aria-label="다음 달"
                    >
                        <ChevronRight className="w-4 h-4 text-slate-500" />
                    </button>
                </div>
                <VerifyLegend showThisWeek={!!thisWeek} />
            </div>

            <div className="grid grid-cols-7 gap-2 mb-2 text-center text-xs font-bold text-slate-400">
                {weekDays.map((d, i) => (
                    <div key={i} className={i === 0 ? "text-rose-400" : i === 6 ? "text-blue-400" : ""}>
                        {d}
                    </div>
                ))}
            </div>

            <div className="grid grid-cols-7 gap-2">
                {cells.map((cell, idx) => {
                    if (!cell) return <div key={idx} className="aspect-[4/3] rounded-xl bg-transparent" />;

                    const { day, isToday, inThisWeek, verify } = cell;

                    if (!verify) {
                        return (
                            <div
                                key={idx}
                                className={`relative aspect-[4/3] rounded-xl border select-none ${
                                    isToday
                                        ? "border-2 border-dashed border-amber-400"
                                        : inThisWeek
                                          ? "bg-[#f6fbe2] border-transparent"
                                          : "bg-slate-50 border-slate-200/70"
                                } ${inThisWeek ? THIS_WEEK_OUTLINE : ""}`}
                            >
                                <span
                                    className={`absolute top-1 left-1.5 text-[11px] font-bold ${
                                        isToday ? "text-amber-700" : "text-slate-400"
                                    }`}
                                >
                                    {day}
                                </span>
                                {isToday && (
                                    <span className="absolute inset-0 flex items-center justify-center text-[10px] font-bold text-amber-600">
                                        오늘
                                    </span>
                                )}
                            </div>
                        );
                    }

                    return (
                        <button
                            key={idx}
                            type="button"
                            onClick={() => onSelectDay(verify)}
                            aria-label={`${month + 1}월 ${day}일 인증 ${VERIFY_STATUS_LABEL[verify.status]}`}
                            className={`group relative aspect-[4/3] rounded-xl overflow-hidden bg-slate-100 cursor-pointer select-none transition-transform hover:scale-105 hover:z-10 hover:shadow-lg ${inThisWeek ? THIS_WEEK_OUTLINE : ""}`}
                        >
                            <VerifyThumb verify={verify} />
                            <span className="absolute inset-x-0 top-0 h-1/2 bg-gradient-to-b from-black/35 to-transparent" />
                            <span className="absolute top-1 left-1.5 text-[11px] font-bold text-white drop-shadow">
                                {day}
                            </span>
                            <StatusDot status={verify.status} className="absolute right-1.5 bottom-1.5" />
                        </button>
                    );
                })}
            </div>
        </div>
    );
}

// ---------- 인증 앨범: 한 달씩, 월 선택 ----------

function VerifyAlbum({
    verifies,
    onSelect,
}: {
    verifies: HabitVerifyResponse[];
    onSelect: (v: HabitVerifyResponse) => void;
}) {
    // 인증이 있는 달만 최신순으로 선택할 수 있다
    const months = Array.from(new Set(verifies.map((v) => monthOf(v.verifyDate)))).sort((a, b) => b.localeCompare(a));

    const [month, setMonth] = useState<string | null>(months[0] ?? null);
    const [direction, setDirection] = useState<"left" | "right" | null>(null);
    const gridRef = useRef<HTMLDivElement>(null);
    const prevHeightRef = useRef<number | null>(null);

    // 선택한 달에 인증이 없어졌다면(수정 후 재조회 등) 가장 최근 달로 되돌린다
    const currentMonth = month !== null && months.includes(month) ? month : (months[0] ?? null);
    const items = currentMonth ? verifiesOfMonth(verifies, currentMonth) : [];
    const index = currentMonth ? months.indexOf(currentMonth) : -1;

    // 달이 바뀔 때 앨범 높이가 점프하지 않고 부드럽게 변하도록 이전 높이에서 새 높이로 옮긴다
    useLayoutEffect(() => {
        const el = gridRef.current;
        const from = prevHeightRef.current;
        prevHeightRef.current = null;
        if (!el || from === null) return;

        const to = el.scrollHeight;
        el.style.height = `${from}px`;
        void el.offsetHeight;
        el.style.height = `${to}px`;
        const timer = setTimeout(() => {
            el.style.height = "";
        }, 340);
        return () => clearTimeout(timer);
    }, [currentMonth]);

    if (!currentMonth) {
        return <p className="py-10 text-center text-sm text-slate-400">아직 인증 사진이 없어요</p>;
    }

    const goTo = (next: string | undefined) => {
        if (!next || next === currentMonth) return;
        prevHeightRef.current = gridRef.current?.offsetHeight ?? null;
        // 더 오래된 달로 가면 왼쪽에서, 더 최근 달로 가면 오른쪽에서 들어온다
        setDirection(months.indexOf(next) > index ? "left" : "right");
        setMonth(next);
    };

    return (
        <div>
            <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2 mb-3.5">
            <div className="flex items-center gap-1.5 min-w-0 flex-1">
                <button
                    type="button"
                    onClick={() => goTo(months[index + 1])}
                    disabled={index >= months.length - 1}
                    aria-label="이전 달 앨범"
                    className="shrink-0 w-7 h-7 rounded-full bg-slate-100 text-slate-500 flex items-center justify-center disabled:opacity-35 enabled:cursor-pointer"
                >
                    <ChevronLeft className="w-4 h-4" />
                </button>
                <div className="flex gap-1.5 overflow-x-auto flex-1 [scrollbar-width:none]">
                    {months.map((m) => (
                        <button
                            key={m}
                            type="button"
                            onClick={() => goTo(m)}
                            className={`shrink-0 px-3.5 py-1.5 rounded-full border text-[13px] font-bold whitespace-nowrap cursor-pointer transition-colors ${
                                m === currentMonth
                                    ? "bg-slate-900 border-slate-900 text-white"
                                    : "bg-white border-slate-200 text-slate-500 hover:bg-slate-50"
                            }`}
                        >
                            {Number(m.slice(5))}월
                        </button>
                    ))}
                </div>
                <button
                    type="button"
                    onClick={() => goTo(months[index - 1])}
                    disabled={index <= 0}
                    aria-label="다음 달 앨범"
                    className="shrink-0 w-7 h-7 rounded-full bg-slate-100 text-slate-500 flex items-center justify-center disabled:opacity-35 enabled:cursor-pointer"
                >
                    <ChevronRight className="w-4 h-4" />
                </button>
            </div>
            <VerifyLegend />
            </div>

            <div ref={gridRef} className="overflow-hidden transition-[height] duration-300 ease-out">
                <div
                    key={currentMonth}
                    className={`grid grid-cols-3 sm:grid-cols-4 lg:grid-cols-5 gap-1.5 ${
                        direction === "left"
                            ? "anim-slide-children-from-left"
                            : direction === "right"
                              ? "anim-slide-children-from-right"
                              : ""
                    }`}
                >
                    {items.map((v) => (
                        <button
                            key={v.id}
                            type="button"
                            onClick={() => onSelect(v)}
                            aria-label={`${v.verifyDate} 인증 ${VERIFY_STATUS_LABEL[v.status]}`}
                            className="relative aspect-[4/3] rounded-[10px] overflow-hidden bg-slate-100 cursor-pointer transition-transform hover:scale-[1.03] hover:z-10 hover:shadow-lg"
                        >
                            <VerifyThumb verify={v} />
                            <span className="absolute inset-x-0 top-0 h-1/2 bg-gradient-to-b from-black/30 to-transparent" />
                            <span className="absolute top-1.5 left-1.5 text-[11px] font-extrabold text-white drop-shadow">
                                {Number(v.verifyDate.slice(5, 7))}/{Number(v.verifyDate.slice(8, 10))}
                            </span>
                            <StatusDot status={v.status} className="absolute right-1.5 bottom-1.5" />
                        </button>
                    ))}
                </div>
            </div>
        </div>
    );
}

// ---------- 벌칙 기록: 습관 인증 히스토리 히트맵 ----------

const HEATMAP_STATUS_LABEL: Record<HabitVerifyStatus, string> = {
    APPROVED: "성공",
    PENDING: "검토중",
    REJECTED: "반려",
};

function HabitHistoryHeatmap({ habitId }: { habitId: number }) {
    const [habit, setHabit] = useState<HabitResponse | null>(null);
    const [verifies, setVerifies] = useState<HabitVerifyResponse[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        let cancelled = false;
        (async () => {
            try {
                setLoading(true);
                setError(null);
                const [habitRes, verifiesRes] = await Promise.all([
                    habitApi.getHabit(habitId),
                    habitApi.getHabitVerifications(habitId),
                ]);
                if (!cancelled) {
                    setHabit(habitRes);
                    setVerifies(verifiesRes);
                }
            } catch (err) {
                if (!cancelled) {
                    setError(err instanceof Error ? err.message : "히스토리를 불러오지 못했습니다.");
                }
            } finally {
                if (!cancelled) setLoading(false);
            }
        })();
        return () => {
            cancelled = true;
        };
    }, [habitId]);

    if (loading) {
        return <p className="mt-3 pt-3 border-t border-slate-100 text-[11px] text-slate-400">불러오는 중...</p>;
    }

    if (error || !habit) {
        return (
            <p className="mt-3 pt-3 border-t border-slate-100 text-[11px] text-rose-500">
                {error || "습관 정보를 찾을 수 없습니다."}
            </p>
        );
    }

    const pad = (n: number) => n.toString().padStart(2, "0");
    const toDateStr = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;

    const verifyByDate = new Map(verifies.map((v) => [v.verifyDate, v]));
    const habitStartStr = habit.createDate.slice(0, 10);
    const todayStr = toDateStr(new Date());

    const start = new Date(habitStartStr);
    start.setDate(start.getDate() - start.getDay());
    const end = new Date();
    end.setDate(end.getDate() + (6 - end.getDay()));

    const weeks: string[][] = [];
    const cursor = new Date(start);
    while (cursor <= end) {
        const week: string[] = [];
        for (let i = 0; i < 7; i++) {
            week.push(toDateStr(cursor));
            cursor.setDate(cursor.getDate() + 1);
        }
        weeks.push(week);
    }

    const colorFor = (dateStr: string) => {
        if (dateStr < habitStartStr || dateStr > todayStr) return "bg-transparent";
        const status = verifyByDate.get(dateStr)?.status;
        if (status === "APPROVED") return "bg-emerald-500";
        if (status === "PENDING") return "bg-amber-400";
        if (status === "REJECTED") return "bg-rose-400";
        return "bg-slate-200/70";
    };

    const DAY_LABELS = ["일", "월", "화", "수", "목", "금", "토"];
    const SHOWN_DAY_INDEXES = new Set([1, 3, 5]); // 월, 수, 금만 표시

    const monthLabels = weeks.map((week, wi) => {
        const month = Number(week[0].slice(5, 7));
        const prevMonth = wi > 0 ? Number(weeks[wi - 1][0].slice(5, 7)) : null;
        return month !== prevMonth ? `${month}월` : null;
    });

    return (
        <div className="mt-3 pt-3 border-t border-slate-100">
            <div className="flex flex-wrap items-center justify-between gap-2 mb-2">
                <span className="text-[11px] text-slate-500">{habit.title} 인증 기록</span>
                <div className="flex items-center gap-2 text-[10px] text-slate-400">
                    <span className="flex items-center gap-1">
                        <span className="w-2.5 h-2.5 rounded-sm bg-emerald-500 inline-block" />
                        성공
                    </span>
                    <span className="flex items-center gap-1">
                        <span className="w-2.5 h-2.5 rounded-sm bg-amber-400 inline-block" />
                        미검토
                    </span>
                    <span className="flex items-center gap-1">
                        <span className="w-2.5 h-2.5 rounded-sm bg-rose-400 inline-block" />
                        반려
                    </span>
                    <span className="flex items-center gap-1">
                        <span className="w-2.5 h-2.5 rounded-sm bg-slate-200/70 inline-block" />
                        미제출
                    </span>
                </div>
            </div>
            <div className="flex overflow-x-auto pb-1">
                <div className="flex flex-col gap-[3px] mr-1.5 shrink-0">
                    <div className="h-3 mb-1" />
                    {DAY_LABELS.map((label, di) => (
                        <div key={di} className="w-4 h-3 text-[9px] leading-3 text-slate-400 text-right">
                            {SHOWN_DAY_INDEXES.has(di) ? label : ""}
                        </div>
                    ))}
                </div>
                <div className="flex gap-[3px]">
                    {weeks.map((week, wi) => (
                        <div key={wi} className="flex flex-col gap-[3px]">
                            <div className="h-3 mb-1 w-3 relative shrink-0">
                                {monthLabels[wi] && (
                                    <span className="absolute left-0 top-0 text-[9px] leading-3 text-slate-400 whitespace-nowrap">
                                        {monthLabels[wi]}
                                    </span>
                                )}
                            </div>
                            {week.map((dateStr) => {
                                const verify = verifyByDate.get(dateStr);
                                const title = verify ? `${dateStr} · ${HEATMAP_STATUS_LABEL[verify.status]}` : dateStr;
                                return (
                                    <div
                                        key={dateStr}
                                        title={title}
                                        className={`w-3 h-3 rounded-sm ${colorFor(dateStr)}`}
                                    />
                                );
                            })}
                        </div>
                    ))}
                </div>
            </div>
        </div>
    );
}

// ---------- 모달: 오늘 인증하기 ----------

function CertifyModal({
    initial,
    onClose,
    onSubmit,
}: {
    initial?: { description: string | null; imageUrl: string | null } | null;
    onClose: () => void;
    onSubmit: (file: File | null, description: string, existingImageUrl: string | null) => Promise<void>;
}) {
    const isEditing = !!initial;
    const [file, setFile] = useState<File | null>(null);
    const [preview, setPreview] = useState<string | null>(initial?.imageUrl ?? null);
    const [description, setDescription] = useState(initial?.description ?? "");
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const handleFile = (e: React.ChangeEvent<HTMLInputElement>) => {
        const f = e.target.files?.[0] ?? null;
        setFile(f);
        setPreview(f ? URL.createObjectURL(f) : (initial?.imageUrl ?? null));
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setSubmitting(true);
        setError(null);
        try {
            await onSubmit(file, description, initial?.imageUrl ?? null);
        } catch (err) {
            setError(err instanceof Error ? err.message : "인증 등록에 실패했습니다.");
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <ModalShell>
            <ModalHeader
                title={isEditing ? "✏️ 인증 수정하기" : "📸 오늘 인증하기"}
                subtitle={
                    isEditing
                        ? "반려된 내용을 수정해서 다시 제출해주세요."
                        : "오늘 실천한 내용을 사진과 함께 남겨보세요."
                }
                onClose={onClose}
            />
            <form onSubmit={handleSubmit} className="mt-4 space-y-4">
                <div>
                    <label className="block text-xs font-bold text-slate-700 mb-1">인증 사진 (선택)</label>
                    <div className="relative w-full h-48 rounded-2xl overflow-hidden bg-slate-100 border border-slate-200 flex items-center justify-center">
                        {preview ? (
                            // eslint-disable-next-line @next/next/no-img-element
                            <img src={preview} alt="인증 미리보기" className="w-full h-full object-cover" />
                        ) : (
                            <span className="text-xs text-slate-400">사진을 선택해주세요</span>
                        )}
                        <label className="absolute bottom-2 right-2 cursor-pointer px-3 py-1.5 bg-white/90 hover:bg-white text-slate-800 text-xs font-bold rounded-lg shadow-md flex items-center gap-1.5">
                            <Upload className="w-3.5 h-3.5" />
                            <span>{isEditing ? "사진 변경" : "사진 업로드"}</span>
                            <input type="file" accept="image/*" onChange={handleFile} className="hidden" />
                        </label>
                    </div>
                </div>

                <div>
                    <label className="block text-xs font-bold text-slate-700 mb-1">인증 내용</label>
                    <textarea
                        rows={3}
                        value={description}
                        onChange={(e) => setDescription(e.target.value)}
                        placeholder="오늘 어떻게 실천했는지 적어주세요."
                        className="w-full px-4 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500 resize-none"
                    />
                </div>

                {error && <p className="text-xs text-rose-500 font-semibold">{error}</p>}

                <ModalActions
                    onClose={onClose}
                    submitting={submitting}
                    submitLabel={isEditing ? "수정 후 재제출" : "인증 등록"}
                />
            </form>
        </ModalShell>
    );
}

// ---------- 모달: 습관 추가 ----------

function CreateHabitModal({
    onClose,
    onSubmit,
}: {
    onClose: () => void;
    onSubmit: (title: string, description: string, days: number) => Promise<void>;
}) {
    const [title, setTitle] = useState("");
    const [description, setDescription] = useState("");
    const [days, setDays] = useState(3);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!title.trim()) {
            setError("습관 제목은 필수입니다.");
            return;
        }
        setSubmitting(true);
        setError(null);
        try {
            await onSubmit(title.trim(), description.trim(), days);
        } catch (err) {
            setError(err instanceof Error ? err.message : "습관 생성에 실패했습니다.");
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <ModalShell>
            <ModalHeader title="🌱 습관 추가하기" subtitle="새로 도전할 습관을 등록해보세요." onClose={onClose} />
            <form onSubmit={handleSubmit} className="mt-4 space-y-4">
                <div>
                    <label className="block text-xs font-bold text-slate-700 mb-1">
                        습관 제목 <span className="text-rose-500">*</span>
                    </label>
                    <input
                        type="text"
                        value={title}
                        onChange={(e) => setTitle(e.target.value)}
                        placeholder="예: 매일 30분 운동하기"
                        className="w-full px-4 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500"
                    />
                </div>

                <div>
                    <label className="block text-xs font-bold text-slate-700 mb-1">습관 설명</label>
                    <textarea
                        rows={3}
                        value={description}
                        onChange={(e) => setDescription(e.target.value)}
                        placeholder="습관에 대한 설명을 적어주세요."
                        className="w-full px-4 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500 resize-none"
                    />
                </div>

                <div>
                    <label className="block text-xs font-bold text-slate-700 mb-1">주간 목표 횟수 (1~7)</label>
                    <input
                        type="number"
                        min={1}
                        max={7}
                        value={days}
                        onChange={(e) => setDays(Number(e.target.value))}
                        className="w-full px-4 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500"
                    />
                </div>

                {error && <p className="text-xs text-rose-500 font-semibold">{error}</p>}

                <ModalActions onClose={onClose} submitting={submitting} submitLabel="습관 생성" />
            </form>
        </ModalShell>
    );
}

// ---------- 모달: 습관 포기 확인 ----------

function HabitFailModal({ onClose, onConfirm }: { onClose: () => void; onConfirm: () => Promise<void> }) {
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const handleConfirm = async () => {
        setSubmitting(true);
        setError(null);
        try {
            await onConfirm();
        } catch (err) {
            setError(err instanceof Error ? err.message : "처리에 실패했습니다.");
            setSubmitting(false);
        }
    };

    return (
        <ModalShell borderClass="border-rose-200" widthClass="max-w-sm">
            <div className="text-center">
                <div className="w-12 h-12 rounded-full bg-rose-100 text-rose-500 flex items-center justify-center mx-auto mb-3">
                    <AlertTriangle className="w-6 h-6" />
                </div>
                <h3 className="text-base font-bold text-slate-900">습관 포기 확정</h3>
                <p className="text-xs text-slate-500 mt-2 leading-relaxed">
                    이 습관을 실패로 종료할까요?
                    <br />
                    <span className="text-rose-500 font-semibold">(벌칙 수행 기록이 추가됩니다.)</span>
                </p>
                {error && <p className="text-xs text-rose-500 font-semibold mt-2">{error}</p>}
                <div className="mt-6 flex items-center justify-center gap-2">
                    <button
                        onClick={onClose}
                        disabled={submitting}
                        className="px-4 py-2.5 rounded-xl text-xs font-bold text-slate-600 bg-slate-100 hover:bg-slate-200 transition-colors disabled:opacity-50"
                    >
                        취소
                    </button>
                    <button
                        onClick={handleConfirm}
                        disabled={submitting}
                        className="px-4 py-2.5 rounded-xl text-xs font-bold text-white bg-rose-500 hover:bg-rose-600 shadow-md shadow-rose-500/20 transition-colors disabled:opacity-50"
                    >
                        {submitting ? "처리 중..." : "포기 확정"}
                    </button>
                </div>
            </div>
        </ModalShell>
    );
}

// ---------- 모달: 범용 확인(방장 위임 / 추방 등) ----------

function ConfirmModal({
    title,
    message,
    confirmLabel,
    confirmClassName,
    onClose,
    onConfirm,
}: {
    title: string;
    message: React.ReactNode;
    confirmLabel: string;
    confirmClassName: string;
    onClose: () => void;
    onConfirm: () => Promise<void>;
}) {
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const handleConfirm = async () => {
        setSubmitting(true);
        setError(null);
        try {
            await onConfirm();
        } catch (err) {
            setError(err instanceof Error ? err.message : "처리에 실패했습니다.");
            setSubmitting(false);
        }
    };

    return (
        <ModalShell borderClass="border-rose-200" widthClass="max-w-sm">
            <div className="text-center">
                <div className="w-12 h-12 rounded-full bg-rose-100 text-rose-500 flex items-center justify-center mx-auto mb-3">
                    <AlertTriangle className="w-6 h-6" />
                </div>
                <h3 className="text-base font-bold text-slate-900">{title}</h3>
                <p className="text-xs text-slate-500 mt-2 leading-relaxed">{message}</p>
                {error && <p className="text-xs text-rose-500 font-semibold mt-2">{error}</p>}
                <div className="mt-6 flex items-center justify-center gap-2">
                    <button
                        onClick={onClose}
                        disabled={submitting}
                        className="px-4 py-2.5 rounded-xl text-xs font-bold text-slate-600 bg-slate-100 hover:bg-slate-200 transition-colors disabled:opacity-50"
                    >
                        취소
                    </button>
                    <button
                        onClick={handleConfirm}
                        disabled={submitting}
                        className={`px-4 py-2.5 rounded-xl text-xs font-bold text-white shadow-md transition-colors disabled:opacity-50 ${confirmClassName}`}
                    >
                        {submitting ? "처리 중..." : confirmLabel}
                    </button>
                </div>
            </div>
        </ModalShell>
    );
}

// ---------- 모달: 성공 인증 상세 ----------

const CERT_STATUS_META = {
    APPROVED: { chipClass: "bg-emerald-50 text-emerald-700", dotClass: "bg-emerald-500" },
    PENDING: { chipClass: "bg-amber-50 text-amber-700", dotClass: "bg-amber-400" },
    REJECTED: { chipClass: "bg-rose-50 text-rose-700", dotClass: "bg-rose-400" },
} as const;

const PANEL_ICON_BUTTON =
    "flex h-11 w-11 items-center justify-center rounded-xl text-slate-600 transition hover:bg-slate-100 disabled:cursor-default disabled:opacity-35 disabled:hover:bg-transparent cursor-pointer";

// 인증 상세: 오른쪽에서 열리는 패널 (그룹 사진 라이브러리의 상세 패널과 같은 구조).
// 넓은 화면(lg 이상)에서는 본문을 밀지 않고 겹쳐 열려서 패널 밖의 달력·앨범을 계속 눌러 고를 수 있다.
// 좁은 화면에서는 화면 전체를 덮는다. ← → 로 같은 달의 이전/다음 인증, Esc로 닫기, 사진을 누르면 크게 본다.
function CertDetailPanel({
    item,
    verifies,
    activeHabitId,
    habitTitleById,
    isMe,
    direction,
    onNavigate,
    onClose,
    onEdit,
}: {
    item: HabitVerifyResponse;
    verifies: HabitVerifyResponse[];
    activeHabitId: number | null;
    habitTitleById: Record<number, string>;
    isMe: boolean;
    direction: "left" | "right" | null;
    onNavigate: (target: HabitVerifyResponse, dir: "left" | "right") => void;
    onClose: () => void;
    onEdit: (item: HabitVerifyResponse) => void;
}) {
    const [expanded, setExpanded] = useState(false);
    const closeRef = useRef<HTMLButtonElement>(null);
    const bodyRef = useRef<HTMLDivElement>(null);

    // 좌우 이동은 지금 보고 있는 인증이 속한 달 안에서만 한다 (최신순 목록)
    const current = verifies.find((v) => v.id === item.id) ?? item;
    const siblings = verifiesOfMonth(verifies, monthOf(current.verifyDate));
    const position = siblings.findIndex((v) => v.id === current.id);
    const older = siblings[position + 1];
    const newer = siblings[position - 1];

    const meta = CERT_STATUS_META[current.status];
    // 재제출은 진행 중인 습관의 반려 인증만 가능하다 (실패한 이전 습관의 인증은 수정할 수 없다)
    const isPreviousHabit = current.habitId !== activeHabitId;
    const canEdit = isMe && current.status === "REJECTED" && !isPreviousHabit;
    const habitTitle = habitTitleById[current.habitId];
    const dateLabel = `${Number(current.verifyDate.slice(5, 7))}월 ${Number(current.verifyDate.slice(8, 10))}일`;

    useEffect(() => {
        closeRef.current?.focus({ preventScroll: true });
    }, []);

    // 다른 인증으로 넘기면 글 내용을 맨 위부터 보여 준다.
    useEffect(() => {
        bodyRef.current?.scrollTo({ top: 0 });
    }, [current.id]);

    // 화면 전체를 덮는 좁은 화면에서는 뒤의 페이지가 같이 스크롤되지 않게 한다.
    useEffect(() => {
        if (!window.matchMedia("(max-width: 1023px)").matches) return;
        const previous = document.body.style.overflow;
        document.body.style.overflow = "hidden";
        return () => {
            document.body.style.overflow = previous;
        };
    }, []);

    useEffect(() => {
        const onKeyDown = (e: KeyboardEvent) => {
            if (e.defaultPrevented || e.metaKey || e.ctrlKey || e.altKey) return;

            // 글을 입력하는 중의 방향키·Esc는 건드리지 않는다.
            const target = e.target;
            if (target instanceof HTMLElement && (target.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(target.tagName))) {
                return;
            }

            if (e.key === "Escape") {
                if (expanded) setExpanded(false);
                else onClose();
            } else if (e.key === "ArrowLeft" && older) {
                e.preventDefault();
                onNavigate(older, "left");
            } else if (e.key === "ArrowRight" && newer) {
                e.preventDefault();
                onNavigate(newer, "right");
            }
        };
        document.addEventListener("keydown", onKeyDown);
        return () => document.removeEventListener("keydown", onKeyDown);
    }, [expanded, older, newer, onClose, onNavigate]);

    const slideClass =
        direction === "left"
            ? "anim-slide-from-left [--slide-distance:48px]"
            : direction === "right"
              ? "anim-slide-from-right [--slide-distance:48px]"
              : "";

    return (
        <aside
            aria-label="인증 상세"
            className="anim-panel-in fixed inset-0 z-[60] flex flex-col bg-white lg:inset-auto lg:bottom-0 lg:right-0 lg:top-16 lg:z-40 lg:w-[520px] xl:w-[600px] lg:border-l lg:border-slate-200 lg:shadow-[-16px_0_32px_-12px_rgba(15,23,42,0.12)]"
        >
            <div className="flex h-14 shrink-0 items-center border-b border-slate-100 pl-2 pr-2">
                <button
                    type="button"
                    onClick={() => older && onNavigate(older, "left")}
                    disabled={!older}
                    aria-label="이전 인증"
                    className={PANEL_ICON_BUTTON}
                >
                    <ChevronLeft className="h-5 w-5" strokeWidth={2.5} />
                </button>
                <button
                    type="button"
                    onClick={() => newer && onNavigate(newer, "right")}
                    disabled={!newer}
                    aria-label="다음 인증"
                    className={PANEL_ICON_BUTTON}
                >
                    <ChevronRight className="h-5 w-5" strokeWidth={2.5} />
                </button>
                <span className="ml-2 text-xs font-semibold text-slate-400">
                    {position + 1} / {siblings.length}
                </span>
                <span className="flex-1" />
                <button ref={closeRef} type="button" onClick={onClose} aria-label="상세 닫기" className={PANEL_ICON_BUTTON}>
                    <X className="h-5 w-5" strokeWidth={2.5} />
                </button>
            </div>

            <div key={current.id} className={`flex min-h-0 flex-1 flex-col overflow-hidden ${slideClass}`}>
                <div className="h-[min(52vh,480px)] shrink-0 bg-slate-100">
                    {current.imageUrl ? (
                        <button
                            type="button"
                            onClick={() => setExpanded(true)}
                            aria-label="사진 크게 보기"
                            className="flex h-full w-full cursor-zoom-in items-center justify-center"
                        >
                            {/* eslint-disable-next-line @next/next/no-img-element */}
                            <img
                                src={current.imageUrl}
                                alt={`${dateLabel} 인증 사진`}
                                className={`max-h-full max-w-full object-contain ${
                                    current.status === "REJECTED" ? "grayscale opacity-75" : ""
                                }`}
                            />
                        </button>
                    ) : (
                        <div className="flex h-full items-center justify-center text-sm font-semibold text-slate-400">
                            사진이 없어요
                        </div>
                    )}
                </div>

                <div ref={bodyRef} className="min-h-0 flex-1 overflow-y-auto px-6 py-5">
                    <div className="flex items-center justify-between gap-3">
                        <span
                            className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-extrabold ${meta.chipClass}`}
                        >
                            <span className={`h-2 w-2 rounded-full ${meta.dotClass}`} />
                            {VERIFY_STATUS_LABEL[current.status]}
                        </span>
                        <span className="flex-none text-xs font-semibold text-slate-500">
                            {current.verifyDate} ({dateLabel})
                        </span>
                    </div>

                    <div className="mt-4 flex items-center gap-2 min-w-0">
                        {habitTitle && (
                            <h2 className="truncate text-sm font-extrabold text-[#0f3d2e]">{habitTitle}</h2>
                        )}
                        {isPreviousHabit && (
                            <span className="shrink-0 rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-bold text-slate-500">
                                이전 습관
                            </span>
                        )}
                    </div>
                    <p
                        className={`mt-1.5 whitespace-pre-wrap break-words text-sm leading-6 ${
                            current.description ? "text-slate-700" : "text-slate-500"
                        }`}
                    >
                        {current.description || "등록된 내용이 없습니다."}
                    </p>
                </div>
            </div>

            {canEdit && (
                <div className="shrink-0 border-t border-slate-100 px-6 py-4">
                    <button
                        type="button"
                        onClick={() => onEdit(current)}
                        className="flex h-11 w-full items-center justify-center rounded-2xl bg-[#0f3d2e] text-sm font-extrabold text-[#c6f24a] transition hover:bg-[#0a2a20] cursor-pointer"
                    >
                        수정하기
                    </button>
                </div>
            )}

            {expanded &&
                current.imageUrl &&
                createPortal(
                    <ImageLightbox src={current.imageUrl} alt={`${dateLabel} 인증 사진`} onClose={() => setExpanded(false)} />,
                    document.body,
                )}
        </aside>
    );
}

// ---------- 오른쪽 패널: 벌칙 인증하기 ----------

// 오른쪽 패널의 공통 틀: 제목과 닫기 버튼이 있는 윗줄, 스크롤되는 본문, 선택적인 아랫줄(버튼).
// 인증 상세 패널과 같은 위치·크기·모션을 쓰고, Esc로 닫는다(입력 중에는 건드리지 않는다).
function SidePanel({
    label,
    title,
    onClose,
    onEscape,
    footer,
    children,
}: {
    label: string;
    title: string;
    onClose: () => void;
    // Esc를 눌렀을 때의 동작. 기본은 패널 닫기 (크게 보기를 먼저 닫아야 할 때 바꾼다)
    onEscape?: () => void;
    footer?: React.ReactNode;
    children: React.ReactNode;
}) {
    const closeRef = useRef<HTMLButtonElement>(null);

    useEffect(() => {
        closeRef.current?.focus({ preventScroll: true });
    }, []);

    // 화면 전체를 덮는 좁은 화면에서는 뒤의 페이지가 같이 스크롤되지 않게 한다.
    useEffect(() => {
        if (!window.matchMedia("(max-width: 1023px)").matches) return;
        const previous = document.body.style.overflow;
        document.body.style.overflow = "hidden";
        return () => {
            document.body.style.overflow = previous;
        };
    }, []);

    useEffect(() => {
        const onKeyDown = (e: KeyboardEvent) => {
            if (e.defaultPrevented || e.metaKey || e.ctrlKey || e.altKey || e.key !== "Escape") return;
            const target = e.target;
            if (target instanceof HTMLElement && (target.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(target.tagName))) {
                return;
            }
            (onEscape ?? onClose)();
        };
        document.addEventListener("keydown", onKeyDown);
        return () => document.removeEventListener("keydown", onKeyDown);
    }, [onEscape, onClose]);

    return (
        <aside
            aria-label={label}
            className="anim-panel-in fixed inset-0 z-[60] flex flex-col bg-white lg:inset-auto lg:bottom-0 lg:right-0 lg:top-16 lg:z-40 lg:w-[520px] xl:w-[600px] lg:border-l lg:border-slate-200 lg:shadow-[-16px_0_32px_-12px_rgba(15,23,42,0.12)]"
        >
            <div className="flex h-14 shrink-0 items-center border-b border-slate-100 pl-6 pr-2">
                <h2 className="flex-1 text-[15px] font-extrabold text-slate-900">{title}</h2>
                <button ref={closeRef} type="button" onClick={onClose} aria-label="닫기" className={PANEL_ICON_BUTTON}>
                    <X className="h-5 w-5" strokeWidth={2.5} />
                </button>
            </div>
            <div className="min-h-0 flex-1 overflow-y-auto">{children}</div>
            {footer && <div className="shrink-0 border-t border-slate-100 px-6 py-4">{footer}</div>}
        </aside>
    );
}

function PenaltyCertPanel({
    status,
    initial,
    onClose,
    onSubmit,
}: {
    status: PenaltyVerifyStatus;
    initial?: { description: string | null; imageUrl: string | null } | null;
    onClose: () => void;
    onSubmit: (file: File | null, description: string, existingImageUrl: string | null) => Promise<void>;
}) {
    const isEditing = !!initial;
    const [file, setFile] = useState<File | null>(null);
    const [preview, setPreview] = useState<string | null>(initial?.imageUrl ?? null);
    const [description, setDescription] = useState(initial?.description ?? "");
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const handleFile = (e: React.ChangeEvent<HTMLInputElement>) => {
        const f = e.target.files?.[0] ?? null;
        setFile(f);
        setPreview(f ? URL.createObjectURL(f) : (initial?.imageUrl ?? null));
        setError(null);
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!file && !initial?.imageUrl) {
            setError("벌칙 수행 사진은 필수입니다.");
            return;
        }
        setSubmitting(true);
        setError(null);
        try {
            await onSubmit(file, description, initial?.imageUrl ?? null);
        } catch (err) {
            setError(err instanceof Error ? err.message : "벌칙 인증 등록에 실패했습니다.");
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <SidePanel
            label={isEditing ? "벌칙 인증 수정" : "벌칙 인증"}
            title={isEditing ? "✏️ 벌칙 인증 수정하기" : "벌칙 인증하기"}
            onClose={onClose}
            footer={
                <div className="flex items-center gap-2">
                    <button
                        type="button"
                        onClick={onClose}
                        disabled={submitting}
                        className="h-11 flex-1 rounded-2xl bg-slate-100 text-sm font-extrabold text-slate-600 transition hover:bg-slate-200 disabled:opacity-50 cursor-pointer"
                    >
                        취소
                    </button>
                    <button
                        type="submit"
                        form="penalty-cert-form"
                        disabled={submitting}
                        className="h-11 flex-[2] rounded-2xl bg-[#0f3d2e] text-sm font-extrabold text-[#c6f24a] transition hover:bg-[#0a2a20] disabled:opacity-60 cursor-pointer"
                    >
                        {submitting ? "처리 중..." : isEditing ? "수정 후 재제출" : "벌칙 인증 등록"}
                    </button>
                </div>
            }
        >
            <form id="penalty-cert-form" onSubmit={handleSubmit} className="px-6 py-5 space-y-5">
                <div>
                    <span
                        className={`inline-block text-xs font-bold px-2.5 py-1 rounded-xl ${PENALTY_STATUS_META[status].badgeClassName}`}
                    >
                        {PENALTY_STATUS_META[status].statusLabel}
                    </span>
                    <p className="mt-2 text-sm text-slate-500">
                        {isEditing ? "반려된 내용을 수정해서 다시 제출해주세요." : "벌칙을 수행한 사진과 내용을 남겨주세요."}
                    </p>
                </div>

                <div>
                    <label className="block text-xs font-bold text-slate-700 mb-1.5">
                        벌칙 수행 사진 <span className="text-rose-500">*</span>
                    </label>
                    <div className="relative w-full aspect-[4/3] rounded-2xl overflow-hidden bg-slate-100 flex items-center justify-center">
                        {preview ? (
                            // eslint-disable-next-line @next/next/no-img-element
                            <img src={preview} alt="벌칙 인증 미리보기" className="max-h-full max-w-full object-contain" />
                        ) : (
                            <span className="text-xs text-slate-400">사진을 선택해주세요</span>
                        )}
                        <label className="absolute bottom-3 right-3 cursor-pointer px-3 py-1.5 bg-white/90 hover:bg-white text-slate-800 text-xs font-bold rounded-lg shadow-md flex items-center gap-1.5">
                            <Upload className="w-3.5 h-3.5" />
                            <span>{isEditing ? "사진 변경" : "사진 업로드"}</span>
                            <input type="file" accept="image/*" onChange={handleFile} className="hidden" />
                        </label>
                    </div>
                </div>

                <div>
                    <label htmlFor="penalty-description" className="block text-xs font-bold text-slate-700 mb-1.5">
                        인증 내용
                    </label>
                    <textarea
                        id="penalty-description"
                        rows={5}
                        value={description}
                        onChange={(e) => setDescription(e.target.value)}
                        placeholder="어떻게 벌칙을 수행했는지 적어주세요."
                        className="w-full px-4 py-3 rounded-xl border border-slate-200 text-sm leading-6 focus:outline-none focus:ring-2 focus:ring-[#0f3d2e]/40 resize-none"
                    />
                </div>

                {error && <p className="text-xs text-rose-500 font-semibold">{error}</p>}
            </form>
        </SidePanel>
    );
}

// ---------- 오른쪽 패널: 벌칙 수행 상세 ----------

function PenaltyDetailPanel({ item, onClose }: { item: PenaltyVerifyDetail; onClose: () => void }) {
    const [expanded, setExpanded] = useState(false);
    const altText = "벌칙 수행 사진";

    return (
        <SidePanel
            label="벌칙 수행 기록"
            title="벌칙 수행 기록"
            onClose={onClose}
            onEscape={() => (expanded ? setExpanded(false) : onClose())}
        >
            {item.imageUrl && (
                <div className="h-[min(52vh,480px)] bg-slate-100">
                    <button
                        type="button"
                        onClick={() => setExpanded(true)}
                        aria-label="사진 크게 보기"
                        className="flex h-full w-full cursor-zoom-in items-center justify-center"
                    >
                        {/* eslint-disable-next-line @next/next/no-img-element */}
                        <img src={item.imageUrl} alt={altText} className="max-h-full max-w-full object-contain" />
                    </button>
                </div>
            )}

            <div className="px-6 py-5">
                <div className="flex items-center justify-between gap-3">
                    <span
                        className={`inline-block text-xs font-bold px-2.5 py-1 rounded-xl ${PENALTY_STATUS_META[item.status].badgeClassName}`}
                    >
                        {PENALTY_STATUS_META[item.status].statusLabel}
                    </span>
                    <span className="flex-none text-xs font-semibold text-slate-500">{item.verifyDate ?? "-"}</span>
                </div>

                <h3 className="mt-4 text-sm font-extrabold text-[#0f3d2e]">{item.habitTitle}</h3>
                <p className="mt-0.5 text-xs text-slate-500">벌칙: {item.penaltyText}</p>
                <p
                    className={`mt-3 whitespace-pre-wrap break-words text-sm leading-6 ${
                        item.description ? "text-slate-700" : "text-slate-500"
                    }`}
                >
                    {item.description || "등록된 내용이 없습니다."}
                </p>
            </div>

            {expanded &&
                item.imageUrl &&
                createPortal(<ImageLightbox src={item.imageUrl} alt={altText} onClose={() => setExpanded(false)} />, document.body)}
        </SidePanel>
    );
}
