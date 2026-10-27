"use client";

import { useEffect, useLayoutEffect, useRef, useState, useCallback } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
    ArrowLeft,
    Calendar as CalendarIcon,
    CheckCircle2,
    Camera,
    Crown,
    AlertTriangle,
    Upload,
    Plus,
    ChevronLeft,
    ChevronRight,
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

            const [memberRes, penaltiesRes] = await Promise.all([
                habitApi.getGroupMember(groupId, groupMemberId),
                habitApi.getPenaltiesByGroupMember(groupId, groupMemberId),
            ]);
            setMember(memberRes);
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

    // 아직 제출 전(REQUIRED)이라 날짜가 없는 벌칙은 맨 위로 오도록 미래 날짜 취급
    const rows = [...penalties].sort((a, b) =>
        (b.verifyDate ?? "9999-99-99").localeCompare(a.verifyDate ?? "9999-99-99")
    );

    const openPenaltyDetail = async (penaltyId: number) => {
        try {
            const detail = await habitApi.getPenaltyDetail(penaltyId);
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
        setActivePenalty(penalty);
    };

    return (
        <div className="max-w-4xl mx-auto px-4 sm:px-6 py-6 md:py-8">
            {/* Top Nav */}
            <div className="flex items-center justify-between mb-5">
                <Link
                    href={`/group/${groupId}`}
                    className="flex items-center gap-1.5 text-xs font-bold text-slate-500 hover:text-slate-900 transition-colors"
                >
                    <ArrowLeft className="w-4 h-4" />
                    <span>챌린지 방으로</span>
                </Link>
                <span className="text-xs font-semibold px-2.5 py-1 rounded-full bg-slate-100 text-slate-600">
                    멤버 상세 페이지
                </span>
            </div>

            {/* 프로필 & 누적 벌칙 & 도전 습관 */}
            <div className="bg-white rounded-3xl border border-slate-200/90 shadow-sm p-6 mb-6">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                    <div className="flex items-center gap-3.5">
                        <div className="relative">
                            <div className="w-14 h-14 rounded-full bg-emerald-100 border-2 border-emerald-400 flex items-center justify-center text-lg font-black text-emerald-700">
                                {member.nickname.slice(0, 1)}
                            </div>
                            {isHost && (
                                <div className="absolute -bottom-1 -right-1 bg-amber-400 text-white p-1 rounded-full shadow-2xs">
                                    <Crown className="w-3 h-3 fill-white" />
                                </div>
                            )}
                        </div>
                        <div>
                            <div className="flex items-center gap-2">
                                <h1 className="text-lg font-black text-slate-900">{member.nickname}</h1>
                                {isHost && (
                                    <span className="text-[10px] font-bold px-1.5 py-0.5 bg-amber-100 text-amber-700 rounded-md">
                                        방장
                                    </span>
                                )}
                                {isMe && (
                                    <span className="text-[10px] font-bold px-1.5 py-0.5 bg-emerald-100 text-emerald-700 rounded-md">
                                        나
                                    </span>
                                )}
                            </div>
                        </div>
                    </div>

                    <div className="flex items-center gap-2 shrink-0">
                        {viewerIsOwner && !isMe && !isHost && (
                            <div className="flex items-center gap-1.5">
                                <button
                                    onClick={() => setShowDelegateModal(true)}
                                    className="px-3 py-2.5 rounded-xl font-bold text-xs text-amber-700 bg-amber-50 hover:bg-amber-100 border border-amber-200 transition-colors cursor-pointer"
                                >
                                    방장 위임
                                </button>
                                <button
                                    onClick={() => setShowKickModal(true)}
                                    className="px-3 py-2.5 rounded-xl font-bold text-xs text-rose-600 hover:bg-rose-50 border border-rose-200 transition-colors cursor-pointer"
                                >
                                    추방
                                </button>
                            </div>
                        )}
                        <div className="flex items-center bg-rose-50/80 border border-rose-100 px-4 py-2.5 rounded-2xl">
                            <div>
                                <span className="text-[10px] font-bold text-rose-400 uppercase">누적 벌칙</span>
                                <p className="text-sm font-black text-rose-600">벌칙 횟수 : {member.penaltyCount}회</p>
                            </div>
                        </div>
                    </div>
                </div>

                {/* 도전 습관 정보는 같은 그룹 멤버라면 누구나 조회 가능, 액션 버튼은 본인만 */}
                {habit ? (
                    <div className="mt-5 pt-5 border-t border-slate-100 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                        <div>
                            <span className="text-[11px] font-bold text-emerald-600 uppercase">도전 습관</span>
                            <div className="flex items-center gap-2">
                                <h2 className="text-lg font-extrabold text-slate-900">{habit.title}</h2>
                                <span className="text-[11px] font-bold px-2 py-0.5 rounded-full bg-emerald-50 text-emerald-600 border border-emerald-100">
                                    주 {habit.days}회
                                </span>
                            </div>
                            <p className="text-xs text-slate-500 mt-0.5">
                                {habit.description || "매일매일 실천하고 성공을 기록해보세요!"}
                            </p>
                        </div>

                        {isMe && habit.status === "ACTIVE" && (
                            <div className="flex items-center gap-2 shrink-0">
                                <button
                                    onClick={() => setShowCertifyModal(true)}
                                    className="flex items-center gap-1.5 px-4 py-2.5 rounded-xl font-bold text-xs text-white bg-emerald-500 hover:bg-emerald-600 shadow-md shadow-emerald-500/20 transition-all cursor-pointer"
                                >
                                    <Camera className="w-4 h-4" />
                                    <span>오늘 인증하기</span>
                                </button>
                                <button
                                    onClick={() => setShowFailModal(true)}
                                    className="px-3.5 py-2.5 rounded-xl font-bold text-xs text-rose-600 hover:bg-rose-50 border border-rose-200 transition-colors cursor-pointer"
                                >
                                    습관 포기하기
                                </button>
                            </div>
                        )}
                        {habit.status === "FAILED" && (
                            <span className="text-xs font-bold px-3 py-1.5 rounded-xl bg-slate-100 text-slate-500 shrink-0">
                                실패 처리된 습관입니다
                            </span>
                        )}
                    </div>
                ) : (
                    <div className="mt-5 pt-5 border-t border-slate-100 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                        <div>
                            <span className="text-[11px] font-bold text-emerald-600 uppercase">도전 습관</span>
                            <p className="text-sm text-slate-500 mt-0.5">아직 진행 중인 습관이 없습니다.</p>
                        </div>
                        {isMe && (
                            <button
                                onClick={() => setShowCreateHabitModal(true)}
                                className="flex items-center gap-1.5 px-4 py-2.5 rounded-xl font-bold text-xs text-white bg-emerald-500 hover:bg-emerald-600 shadow-md shadow-emerald-500/20 transition-all cursor-pointer shrink-0"
                            >
                                <Plus className="w-4 h-4" />
                                <span>습관을 추가해주세요</span>
                            </button>
                        )}
                    </div>
                )}
            </div>

            {/* 인증 캘린더 (본인 페이지에서만 표시) */}
            {(habit || verifies.length > 0) && <VerifyCalendar verifies={verifies} onSelectDay={setCertDetail} />}

            {/* 인증 앨범: 한 달씩 보여주고, 달력과는 독립적으로 달을 고른다 */}
            {verifies.length > 0 && <VerifyAlbum verifies={verifies} onSelect={setCertDetail} />}

            {/* 벌칙 기록 (실패한 습관 종류와 무관하게 이 멤버의 전체 벌칙) */}
            <div className="bg-white rounded-3xl border border-slate-200/90 shadow-sm p-6">
                <h3 className="text-sm font-extrabold text-slate-900 mb-4">벌칙 기록</h3>

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

                        if (editingVerify) {
                            await habitApi.resubmitHabitVerification(habit.id, editingVerify.id, {
                                description: description || null,
                                imageUrl,
                            });
                        } else {
                            await habitApi.createHabitVerification(habit.id, {
                                description: description || null,
                                imageUrl,
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
                <CertDetailModal
                    item={certDetail}
                    verifies={verifies}
                    activeHabitId={habit?.id ?? null}
                    habitTitleById={habitTitleById}
                    isMe={isMe}
                    onClose={() => setCertDetail(null)}
                    onEdit={(target) => {
                        setEditingVerify(target);
                        setCertDetail(null);
                    }}
                />
            )}

            {activePenalty && (
                <PenaltyCertModal
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
                <PenaltyDetailModal item={penaltyDetail} onClose={() => setPenaltyDetail(null)} />
            )}
        </div>
    );
}

function CenteredMessage({ text }: { text: string }) {
    return (
        <div className="max-w-4xl mx-auto px-4 py-16 text-center space-y-3">
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

function VerifyCalendar({
    verifies,
    onSelectDay,
}: {
    verifies: HabitVerifyResponse[];
    onSelectDay: (v: HabitVerifyResponse) => void;
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
        verify?: HabitVerifyResponse;
    } | null> = [];

    for (let i = 0; i < startDayOfWeek; i++) cells.push(null);
    for (let day = 1; day <= daysInMonth; day++) {
        const dateStr = `${year}-${pad(month + 1)}-${pad(day)}`;
        cells.push({
            day,
            dateStr,
            isToday: year === todayYear && month === todayMonth && day === todayDate,
            verify: verifyByDate.get(dateStr),
        });
    }

    return (
        <div className="bg-white rounded-3xl border border-slate-200/90 shadow-sm p-6 mb-6">
            <div className="grid grid-cols-3 items-center mb-4">
                <div />
                <div className="flex items-center justify-center gap-2">
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
                <div className="flex items-center justify-end gap-2 text-[11px]">
                    <span className="flex items-center gap-1 text-slate-500">
                        <span className="w-2.5 h-2.5 rounded-full bg-emerald-500 inline-block" />
                        성공
                    </span>
                    <span className="flex items-center gap-1 text-slate-500">
                        <span className="w-2.5 h-2.5 rounded-full bg-amber-400 inline-block" />
                        검토중
                    </span>
                    <span className="flex items-center gap-1 text-slate-500">
                        <span className="w-2.5 h-2.5 rounded-full bg-rose-400 inline-block" />
                        반려
                    </span>
                    <span className="flex items-center gap-1 text-slate-400">
                        <span className="w-2.5 h-2.5 rounded-full bg-slate-200 inline-block" />
                        대기
                    </span>
                </div>
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

                    const { day, isToday, verify } = cell;

                    if (!verify) {
                        return (
                            <div
                                key={idx}
                                className={`relative aspect-[4/3] rounded-xl border select-none ${
                                    isToday
                                        ? "border-2 border-dashed border-amber-400"
                                        : "bg-slate-50 border-slate-200/70"
                                }`}
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
                            className="group relative aspect-[4/3] rounded-xl overflow-hidden bg-slate-100 cursor-pointer select-none transition-transform hover:scale-105 hover:z-10 hover:shadow-lg"
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

    if (!currentMonth) return null;

    const goTo = (next: string | undefined) => {
        if (!next || next === currentMonth) return;
        prevHeightRef.current = gridRef.current?.offsetHeight ?? null;
        // 더 오래된 달로 가면 왼쪽에서, 더 최근 달로 가면 오른쪽에서 들어온다
        setDirection(months.indexOf(next) > index ? "left" : "right");
        setMonth(next);
    };

    const [yearText, monthText] = currentMonth.split("-");

    return (
        <div className="bg-white rounded-3xl border border-slate-200/90 shadow-sm p-6 mb-6">
            <div className="flex items-baseline justify-between mb-3">
                <h3 className="text-sm font-extrabold text-slate-900">앨범</h3>
                <span className="text-xs text-slate-500">
                    {yearText}년 {Number(monthText)}월 · {items.length}장
                </span>
            </div>

            <div className="flex items-center gap-1.5 mb-3.5">
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

            <div ref={gridRef} className="overflow-hidden transition-[height] duration-300 ease-out">
                <div
                    key={currentMonth}
                    className={`grid grid-cols-3 sm:grid-cols-4 gap-1.5 ${
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
    APPROVED: {
        title: "성공 인증",
        titleClass: "text-emerald-600",
        borderClass: "border-emerald-100",
        btnClass: "bg-emerald-500 hover:bg-emerald-600",
    },
    PENDING: {
        title: "검토 대기 중",
        titleClass: "text-amber-600",
        borderClass: "border-amber-100",
        btnClass: "bg-amber-500 hover:bg-amber-600",
    },
    REJECTED: {
        title: "반려된 인증",
        titleClass: "text-rose-600",
        borderClass: "border-rose-100",
        btnClass: "bg-rose-500 hover:bg-rose-600",
    },
} as const;

function CertDetailModal({
    item,
    verifies,
    activeHabitId,
    habitTitleById,
    isMe,
    onClose,
    onEdit,
}: {
    item: HabitVerifyResponse;
    verifies: HabitVerifyResponse[];
    activeHabitId: number | null;
    habitTitleById: Record<number, string>;
    isMe: boolean;
    onClose: () => void;
    onEdit: (item: HabitVerifyResponse) => void;
}) {
    const [currentId, setCurrentId] = useState(item.id);
    const [direction, setDirection] = useState<"left" | "right" | null>(null);
    const [closing, setClosing] = useState(false);
    const [showLightbox, setShowLightbox] = useState(false);

    // 좌우 이동은 지금 보고 있는 인증이 속한 달 안에서만 한다 (최신순 목록)
    const current = verifies.find((v) => v.id === currentId) ?? item;
    const siblings = verifiesOfMonth(verifies, monthOf(current.verifyDate));
    const position = siblings.findIndex((v) => v.id === current.id);
    const older = siblings[position + 1];
    const newer = siblings[position - 1];

    const meta = CERT_STATUS_META[current.status];
    // 재제출은 진행 중인 습관의 반려 인증만 가능하다 (실패한 이전 습관의 인증은 수정할 수 없다)
    const isPreviousHabit = current.habitId !== activeHabitId;
    const canEdit = isMe && current.status === "REJECTED" && !isPreviousHabit;
    const habitTitle = habitTitleById[current.habitId];

    const move = (target: HabitVerifyResponse | undefined, dir: "left" | "right") => {
        if (!target) return;
        setDirection(dir);
        setCurrentId(target.id);
    };

    // 닫을 때 페이드아웃이 끝난 뒤에 실제로 닫는다
    const requestClose = useCallback(() => {
        setClosing(true);
        setTimeout(onClose, 300);
    }, [onClose]);

    useEffect(() => {
        const onKeyDown = (e: KeyboardEvent) => {
            if (showLightbox) return;
            if (e.key === "Escape") requestClose();
            else if (e.key === "ArrowLeft") move(older, "left");
            else if (e.key === "ArrowRight") move(newer, "right");
        };
        document.addEventListener("keydown", onKeyDown);
        return () => document.removeEventListener("keydown", onKeyDown);
    });

    const dateLabel = `${Number(current.verifyDate.slice(5, 7))}월 ${Number(current.verifyDate.slice(8, 10))}일`;

    return (
        <ModalShell borderClass={meta.borderClass} widthClass="max-w-md" animated closing={closing}>
            <ModalHeader
                title={meta.title}
                icon={<CheckCircle2 className="w-5 h-5" />}
                onClose={requestClose}
                titleClass={meta.titleClass}
            />
            <div className="mt-3 overflow-hidden">
                <div
                    key={current.id}
                    className={
                        direction === "left"
                            ? "anim-slide-from-left [--slide-distance:48px]"
                            : direction === "right"
                              ? "anim-slide-from-right [--slide-distance:48px]"
                              : ""
                    }
                >
                    {current.imageUrl && (
                        <button
                            type="button"
                            onClick={() => setShowLightbox(true)}
                            className="block w-full aspect-[4/3] rounded-2xl overflow-hidden bg-slate-100 border border-slate-200 shadow-inner cursor-zoom-in"
                        >
                            {/* eslint-disable-next-line @next/next/no-img-element */}
                            <img
                                src={current.imageUrl}
                                alt="인증 사진"
                                className={`w-full h-full object-cover ${
                                    current.status === "REJECTED" ? "grayscale opacity-75" : ""
                                }`}
                            />
                        </button>
                    )}
                    <div className="mt-3 bg-slate-50 p-3 rounded-xl border border-slate-100">
                        <p className="text-xs font-semibold text-slate-800 leading-relaxed">
                            {current.description || "등록된 내용이 없습니다."}
                        </p>
                    </div>
                    <div className="mt-3 flex items-center justify-between gap-2 text-[11px] text-slate-400">
                        <span className="flex items-center gap-1.5 min-w-0">
                            {habitTitle && <span className="truncate font-bold text-slate-500">{habitTitle}</span>}
                            {isPreviousHabit && (
                                <span className="shrink-0 px-1.5 py-0.5 rounded-full bg-slate-100 text-slate-500 font-bold">
                                    이전 습관
                                </span>
                            )}
                        </span>
                        <span className="shrink-0">
                            인증 일자: {current.verifyDate} ({dateLabel})
                        </span>
                    </div>
                </div>
            </div>

            {/* 이전/다음 인증 이동 버튼은 애니메이션 영역 밖에 두어 항상 위에 보이게 한다 */}
            {(older || newer) && (
                <div className="mt-3 flex items-center justify-between">
                    <button
                        type="button"
                        onClick={() => move(older, "left")}
                        disabled={!older}
                        aria-label="이전 인증"
                        className="flex items-center gap-1 px-3 py-1.5 rounded-full bg-slate-100 text-slate-600 text-xs font-bold disabled:opacity-35 enabled:cursor-pointer enabled:hover:bg-slate-200 transition-colors"
                    >
                        <ChevronLeft className="w-4 h-4" />
                        이전
                    </button>
                    <span className="text-[11px] text-slate-400">
                        {position + 1} / {siblings.length}
                    </span>
                    <button
                        type="button"
                        onClick={() => move(newer, "right")}
                        disabled={!newer}
                        aria-label="다음 인증"
                        className="flex items-center gap-1 px-3 py-1.5 rounded-full bg-slate-100 text-slate-600 text-xs font-bold disabled:opacity-35 enabled:cursor-pointer enabled:hover:bg-slate-200 transition-colors"
                    >
                        다음
                        <ChevronRight className="w-4 h-4" />
                    </button>
                </div>
            )}

            {showLightbox && current.imageUrl && (
                <ImageLightbox src={current.imageUrl} alt="인증 사진" onClose={() => setShowLightbox(false)} />
            )}

            {canEdit ? (
                <div className="mt-4 flex items-center gap-2">
                    <button
                        onClick={requestClose}
                        className="flex-1 py-2.5 text-slate-600 bg-slate-100 hover:bg-slate-200 text-xs font-bold rounded-xl transition-colors"
                    >
                        닫기
                    </button>
                    <button
                        onClick={() => onEdit(current)}
                        className={`flex-1 py-2.5 text-white text-xs font-bold rounded-xl shadow-xs ${meta.btnClass}`}
                    >
                        수정하기
                    </button>
                </div>
            ) : (
                <button
                    onClick={requestClose}
                    className={`mt-4 w-full py-2.5 text-white text-xs font-bold rounded-xl shadow-xs ${meta.btnClass}`}
                >
                    확인
                </button>
            )}
        </ModalShell>
    );
}

// ---------- 모달: 벌칙 인증하기 ----------

function PenaltyCertModal({
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
        <ModalShell borderClass="border-rose-100">
            <ModalHeader
                title={isEditing ? "✏️ 벌칙 인증 수정하기" : "벌칙 인증하기"}
                subtitle={
                    isEditing
                        ? "반려된 내용을 수정해서 다시 제출해주세요."
                        : "벌칙을 수행한 사진과 내용을 남겨주세요."
                }
                onClose={onClose}
                titleClass="text-rose-600"
            />
            <span
                className={`inline-block mt-2 text-xs font-bold px-2.5 py-1 rounded-xl ${PENALTY_STATUS_META[status].badgeClassName}`}
            >
                {PENALTY_STATUS_META[status].statusLabel}
            </span>
            <form onSubmit={handleSubmit} className="mt-4 space-y-4">
                <div>
                    <label className="block text-xs font-bold text-slate-700 mb-1">
                        벌칙 수행 사진 <span className="text-rose-500">*</span>
                    </label>
                    <div className="relative w-full h-48 rounded-2xl overflow-hidden bg-slate-100 border border-rose-200 flex items-center justify-center">
                        {preview ? (
                            // eslint-disable-next-line @next/next/no-img-element
                            <img src={preview} alt="벌칙 인증 미리보기" className="w-full h-full object-cover" />
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
                        placeholder="어떻게 벌칙을 수행했는지 적어주세요."
                        className="w-full px-4 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-rose-400 resize-none"
                    />
                </div>

                {error && <p className="text-xs text-rose-500 font-semibold">{error}</p>}

                <ModalActions
                    onClose={onClose}
                    submitting={submitting}
                    submitLabel={isEditing ? "수정 후 재제출" : "벌칙 인증 등록"}
                    tone="rose"
                />
            </form>
        </ModalShell>
    );
}

// ---------- 모달: 벌칙 수행 상세 ----------

function PenaltyDetailModal({ item, onClose }: { item: PenaltyVerifyDetail; onClose: () => void }) {
    const [showLightbox, setShowLightbox] = useState(false);

    return (
        <ModalShell borderClass="border-rose-100" widthClass="max-w-sm">
            <ModalHeader
                title="벌칙 수행 기록"
                onClose={onClose}
                titleClass="text-rose-600"
            />
            <span
                className={`inline-block mt-2 text-xs font-bold px-2.5 py-1 rounded-xl ${PENALTY_STATUS_META[item.status].badgeClassName}`}
            >
                {PENALTY_STATUS_META[item.status].statusLabel}
            </span>
            <div className="mt-3">
                <p className="text-xs font-bold text-slate-500">{item.habitTitle}</p>
                <p className="text-[11px] text-slate-400 mt-0.5">벌칙: {item.penaltyText}</p>
                {item.imageUrl && (
                    <button
                        type="button"
                        onClick={() => setShowLightbox(true)}
                        className="block mt-2.5 w-full h-56 rounded-2xl overflow-hidden bg-slate-100 border border-rose-200 shadow-inner cursor-zoom-in"
                    >
                        {/* eslint-disable-next-line @next/next/no-img-element */}
                        <img src={item.imageUrl} alt="벌칙 사진" className="w-full h-full object-cover" />
                    </button>
                )}
                {showLightbox && item.imageUrl && (
                    <ImageLightbox src={item.imageUrl} alt="벌칙 사진" onClose={() => setShowLightbox(false)} />
                )}
                <div className="mt-3 bg-rose-50/50 p-3 rounded-xl border border-rose-100">
                    <p className="text-xs font-semibold text-rose-900 leading-relaxed">
                        {item.description || "등록된 내용이 없습니다."}
                    </p>
                </div>
                <p className="mt-3 text-[11px] text-slate-400 text-right">인증 일자: {item.verifyDate ?? "-"}</p>
            </div>
            <button
                onClick={onClose}
                className="mt-4 w-full py-2.5 bg-rose-500 hover:bg-rose-600 text-white text-xs font-bold rounded-xl shadow-xs"
            >
                확인
            </button>
        </ModalShell>
    );
}
