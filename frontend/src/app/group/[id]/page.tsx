'use client';

import React, { useState, useEffect, use, useCallback } from 'react';
import Link from 'next/link';
import { ArrowLeft } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useMember } from '@/context/MemberContext';
import { apiRequest } from '@/lib/memberApi';
import { isGroupFinished } from '@/lib/groupApi';
import { EditRoomModal } from '@/components/EditRoomModal';
import { MemberHabitHeatmap } from '@/components/MemberHabitHeatmap';
import { GroupContentBar } from '@/components/GroupContentBar';
import { memberHandle } from '@/lib/memberDisplay';
import type { MemberProvider } from '@/lib/memberApi';
import { GroupStatistics } from '@/components/GroupStatistics';
import { GroupPhotoLibrary } from '@/components/GroupPhotoLibrary';
import { OverlayScrollArea } from '@/components/OverlayScrollArea';
import { habitApi } from '@/lib/habitApi';
import { summarizeThisWeek, type WeekSummary } from '@/lib/habitWeek';
import { DarkPill, HeroCard, HeroTile, HeroTiles, btnGhost, btnLime } from '@/components/DesignKit';

interface RankingItem {
    rank: number;
    memberId: number;
    nickname: string | null;
    count: number;
}

interface GroupRankings {
    habitVerifyTop3: RankingItem[];
    penaltyVerifyTop3: RankingItem[];
    habitSuccessTop3: RankingItem[];
}

interface GroupDetailData {
    id: number;
    title: string;
    description: string;
    status?: string;
    createDate?: string;
    startDate?: string;
    deadline?: string;
    penalty?: string;
    inviteCode?: string;
    memberLimit: number;
    inviteLink?: string;
    currentMemberCount: number;
    rankings: GroupRankings | null;
}

interface GroupMemberItem {
    id: number;
    memberId: number;
    nickname: string;
    username: string;
    provider: MemberProvider;
    role: 'OWNER' | 'MEMBER';
    habitId: number | null;
    habitTitle: string | null;
    habitDescription: string | null;
}

export default function GroupDetailPage({
                                            params,
                                        }: {
    params: Promise<{ id: string }> | { id: string };
}) {
    const router = useRouter();
    const { currentUser, authReady } = useMember();
    const resolvedParams = params instanceof Promise ? use(params) : params;
    const rawId = resolvedParams?.id;

    const [group, setGroup] = useState<GroupDetailData | null>(null);
    const [members, setMembers] = useState<GroupMemberItem[]>([]);
    const [loading, setLoading] = useState(true);
    const [currentRole, setCurrentRole] = useState<'OWNER' | 'MEMBER'>('MEMBER');
    const [isEditModalOpen, setIsEditModalOpen] = useState(false);
    const [view, setView] = useState<'photos' | 'members'>('members');
    // 멤버 뷰의 주간 게이지: 습관 id별 이번 주 승인 수와 목표
    const [weekByHabit, setWeekByHabit] = useState<Record<number, WeekSummary>>({});

    const fetchGroupData = useCallback(async () => {
        if (!rawId || isNaN(Number(rawId))) return;
        const groupId = Number(rawId);

        try {
            const groupData = await apiRequest<any>(`/groups/${groupId}`, {}, true);
            setGroup({
                id: groupData.id || groupData.groupId || groupId,
                title: groupData.title || '',
                description: groupData.description || '',
                status: groupData.status || (groupData.finished ? 'FINISH' : 'ACTIVE'),
                createDate: groupData.createDate,
                startDate: groupData.startDate,
                deadline: groupData.deadline,
                penalty: groupData.penalty || '',
                inviteCode: groupData.inviteCode || '',
                memberLimit: groupData.memberLimit ?? 0,
                inviteLink: groupData.inviteLink,
                currentMemberCount: groupData.currentMemberCount ?? 0,
                rankings: groupData.rankings ?? null,
            });

            try {
                const rawMembers = await apiRequest<any[]>(`/groups/${groupId}/members`, {}, true);

                const formattedMembers: GroupMemberItem[] = (rawMembers || []).map((m: any) => ({
                    id: m.groupMemberId || m.id,
                    memberId: m.memberId,
                    nickname: m.nickname || m.memberName || '',
                    username: m.username,
                    provider: m.provider || 'LOCAL',
                    role: m.role || 'MEMBER',
                    habitId: m.habitId || null,
                    habitTitle: m.habitTitle || null,
                    habitDescription: m.habitDescription || null,
                }));

                setMembers(formattedMembers);

                if (currentUser) {
                    const me = formattedMembers.find(
                        (m) => String(m.memberId) === String(currentUser.id)
                    );
                    setCurrentRole(me?.role ?? 'MEMBER');
                }
            } catch (memberErr: any) {
                console.error('멤버 목록 조회 실패:', memberErr);
            }
        } catch (err: any) {
            console.error('그룹 정보 조회 에러:', err);
            alert(err.message || '그룹 정보를 불러오지 못했습니다.');
        } finally {
            setLoading(false);
        }
    }, [rawId, currentUser]);

    useEffect(() => {
        if (!authReady) return;
        if (!currentUser) {
            router.replace('/login');
            return;
        }

        if (!rawId || isNaN(Number(rawId))) {
            alert('유효하지 않은 그룹 접근입니다.');
            router.replace('/main');
            return;
        }

        setLoading(true);
        fetchGroupData();
    }, [rawId, authReady, currentUser, router, fetchGroupData]);

    const groupCreateDate = group?.createDate;
    const groupRunning = !!group && group.status !== 'FINISH' && !(group.deadline && new Date(group.deadline) < new Date());
    useEffect(() => {
        if (view !== 'members' || !groupCreateDate || !groupRunning) return;
        const targets = members.filter((m) => m.habitId !== null);
        if (targets.length === 0) return;

        let cancelled = false;
        Promise.all(
            targets.map(async (m) => {
                try {
                    const habitId = m.habitId as number;
                    const [habit, verifies] = await Promise.all([habitApi.getHabit(habitId), habitApi.getHabitVerifications(habitId)]);
                    if (habit.status !== 'ACTIVE') return null;
                    const week = summarizeThisWeek({
                        verifies: verifies.filter((v) => v.habitId === habit.id),
                        goal: habit.days,
                        habitCreateDate: habit.createDate,
                        groupCreateDate,
                        today: new Date(),
                    });
                    return [habitId, week] as const;
                } catch {
                    return null;
                }
            })
        ).then((entries) => {
            if (cancelled) return;
            setWeekByHabit(Object.fromEntries(entries.filter((entry) => entry !== null)));
        });

        return () => {
            cancelled = true;
        };
    }, [view, members, groupCreateDate, groupRunning]);

    const handleCopyInvite = () => {
        const inviteText = group?.inviteLink || group?.inviteCode || '';
        if (!inviteText) return;

        if (navigator.clipboard) {
            navigator.clipboard.writeText(inviteText);
            alert('초대 코드가 클립보드에 복사되었습니다: ' + inviteText);
        } else {
            prompt('초대 코드를 복사하세요:', inviteText);
        }
    };

    const handleLeaveGroup = async () => {
        const memberCount = group?.currentMemberCount || 0;

        if (currentRole === 'OWNER' && memberCount > 1) {
            alert('방장은 다른 멤버가 남아있는 상태에서 바로 탈퇴할 수 없습니다.\n방장 권한을 위임해 주세요.');
            return;
        }

        if (!confirm('정말 이 방에서 탈퇴하시겠습니까?')) return;

        try {
            await apiRequest<void>(`/groups/${rawId}/leave`, { method: 'DELETE' }, true);
            alert('그룹에서 탈퇴했습니다.');
            router.push('/main');
        } catch (err: any) {
            alert(err.message || '방 탈퇴 처리에 실패했습니다.');
        }
    };

    if (loading) {
        return (
            <div className="min-h-screen flex items-center justify-center text-sm text-slate-400">
                그룹 정보를 불러오는 중...
            </div>
        );
    }

    if (!group) {
        return (
            <div className="min-h-screen flex items-center justify-center text-sm text-slate-500">
                그룹 정보를 찾을 수 없습니다.
            </div>
        );
    }

    const isRoomFinished = group.status === 'FINISH' || (group.deadline && new Date(group.deadline) < new Date());

    return (
        <main className="mx-auto max-w-5xl px-4 pb-8 pt-1 sm:px-6 sm:pb-10 sm:pt-2">
            {/* Top Nav */}
            <div className="mb-3 flex items-center justify-between sm:mb-4">
                <Link
                    href="/main"
                    className="flex items-center gap-1.5 text-xs font-bold text-slate-500 transition-colors hover:text-slate-900"
                >
                    <ArrowLeft className="h-4 w-4" />
                    <span>방 목록으로</span>
                </Link>
            </div>

            {/* 방 정보: 숲색 히어로 */}
            <HeroCard>
                <div className="flex flex-wrap items-start justify-between gap-4">
                    <div className="min-w-0 flex-1">
                        <div className="flex flex-wrap items-center gap-2">
                            <DarkPill tone={isRoomFinished ? 'neutral' : 'lime'}>
                                {isRoomFinished ? '완료' : '● 진행 중'}
                            </DarkPill>
                            {currentRole === 'OWNER' && (
                                <span className="text-xl" role="img" aria-label="방장" title="방장">👑</span>
                            )}
                        </div>
                        <h1 className="mt-3 text-[26px] font-extrabold leading-tight tracking-tight sm:text-[28px]">
                            {group.title}
                        </h1>
                        <p className="mt-2 max-w-2xl text-[13px] leading-6 text-white/60">
                            {group.description || '함께 목표를 실천해보세요!'}
                        </p>
                    </div>

                    <div className="flex shrink-0 items-center gap-2">
                        {currentRole === 'OWNER' && (
                            <button onClick={() => setIsEditModalOpen(true)} className={btnGhost}>
                                ⚙ 방 설정
                            </button>
                        )}
                        <button
                            onClick={handleLeaveGroup}
                            className="inline-flex items-center rounded-xl bg-rose-400/15 px-3.5 py-2.5 text-xs font-bold text-rose-300 transition hover:bg-rose-400/25 cursor-pointer"
                        >
                            방 탈퇴
                        </button>
                    </div>
                </div>

                <HeroTiles>
                    <HeroTile label="👥" title="참여 인원" compact value={group.currentMemberCount} unit={` / ${group.memberLimit}명`} />
                    <HeroTile label="📅" title="마감 기한" compact value={group.deadline ? `${group.deadline}까지` : '상시 진행'} />
                    <HeroTile label="☕" title="벌칙" compact value={group.penalty || '벌칙 없음'} />
                </HeroTiles>

                {/* 마감된 방은 새로 초대하거나 인증을 검토할 일이 없어 버튼을 숨긴다 */}
                {!isRoomFinished && (
                    <div className="mt-5 flex flex-wrap items-center gap-2.5 border-t border-white/10 pt-5">
                        <button onClick={handleCopyInvite} className={btnLime}>
                            초대 코드 복사
                        </button>
                        {currentRole === 'OWNER' && (
                            <Link href={`/admin/group/${rawId}/verification`} className={btnGhost}>
                                미인증 내역 관리
                            </Link>
                        )}
                    </div>
                )}
            </HeroCard>

            {/* 인증 사진 / 멤버 목록 */}
            <section className="mt-6 rounded-[28px] bg-white shadow-[0_10px_30px_rgba(14,21,18,0.07),0_1px_2px_rgba(14,21,18,0.05)]">

                {group.status === 'FINISH' && group.rankings && (
                    <div className="-mt-4 px-6 pb-6 sm:px-8">
                        <GroupStatistics rankings={group.rankings} />
                    </div>
                )}

                {/* 인증 사진 / 멤버 목록: 아이콘 탭이 정보 바로 아래에서 갤러리와 이어진다 */}
                <div>
                {view === 'photos' && (
                    <GroupPhotoLibrary
                        groupId={group.id}
                        members={members.map((m) => ({ memberId: m.memberId, nickname: m.nickname }))}
                        view={view}
                        onViewChange={setView}
                        memberCount={group.currentMemberCount}
                        finished={isGroupFinished({ status: group.status === 'FINISH' ? 'FINISH' : 'ACTIVE', deadline: group.deadline })}
                    />
                )}

                {view === 'members' && (
                    <div className="@container">
                        <GroupContentBar view={view} onViewChange={setView} memberCount={group.currentMemberCount} />
                        {/* 높이는 앨범과 같게 고정하고, 인원이 많으면 앨범과 같은 모양의 스크롤바로 내린다 */}
                        <OverlayScrollArea id="group-view-panel" role="tabpanel" label="멤버" className="grid grid-cols-1 gap-3.5 p-4 sm:p-5">
                        {members.map((m, index) => {
                            const linkHref = `/group/${rawId}/member/${m.memberId}`;

                            const isMe = currentUser !== null && String(m.memberId) === String(currentUser.id);
                            const week = m.habitId !== null ? weekByHabit[m.habitId] : undefined;

                            return (
                                <div
                                    key={`member-${m.id}-${index}`}
                                    onClick={() => router.push(linkHref)}
                                    className="group flex flex-col rounded-[22px] border border-slate-100 bg-white p-4 transition-all duration-200 hover:-translate-y-0.5 hover:border-lime-400 hover:shadow-[0_10px_24px_rgba(14,21,18,0.08)] cursor-pointer"
                                >
                                    <div className="flex w-full items-center gap-3.5">
                                        {/* 아바타: 나는 라임-민트 링, 다른 멤버는 연한 링 */}
                                        <div
                                            className={`h-[46px] w-[46px] flex-none rounded-full p-[3px] ${
                                                isMe ? '' : 'bg-[#e7efe9]'
                                            }`}
                                            style={isMe ? { background: 'conic-gradient(from 200deg, #c6f24a, #34e6a4, #c6f24a)' } : undefined}
                                        >
                                            <div
                                                className={`grid h-full w-full place-items-center rounded-full text-[15px] font-black ${
                                                    isMe ? 'bg-forest-500 text-white' : 'bg-white text-forest-500'
                                                }`}
                                            >
                                                {m.nickname.slice(0, 1)}
                                            </div>
                                        </div>

                                        <div className="min-w-0">
                                            <div className="flex flex-wrap items-center gap-x-1.5 gap-y-0.5">
                                                <span className="text-sm font-extrabold text-slate-900">{m.nickname}</span>
                                                <span className="text-[11px] font-semibold text-slate-400">
                                                    {memberHandle(m.provider, m.username)}
                                                </span>
                                                {isMe && (
                                                    <span className="rounded-full bg-lime-50 px-2 py-0.5 text-[11px] font-extrabold text-forest-500">
                                                        나
                                                    </span>
                                                )}
                                                {m.role === 'OWNER' && <span className="text-xs">👑</span>}
                                            </div>
                                            <div className="mt-0.5 truncate text-xs text-slate-400" title={m.habitDescription || undefined}>
                                                {m.habitTitle || '아직 진행 중인 습관이 없어요'}
                                                {week && ` · 주 ${week.goal}회`}
                                            </div>
                                        </div>

                                        <div className="ml-auto flex items-center gap-3 text-right">
                                            {isRoomFinished ? (
                                                m.habitDescription && (
                                                    <div className="hidden max-w-[200px] truncate text-[11px] text-slate-400 sm:block">
                                                        {m.habitDescription}
                                                    </div>
                                                )
                                            ) : week ? (
                                                <div className="min-w-[110px] sm:min-w-[120px]" title={`이번 주 승인 ${week.approved}회 / 목표 ${week.goal}회`}>
                                                    <b className="text-xs font-extrabold tabular-nums text-slate-900">
                                                        {week.approved} / {week.goal}회
                                                    </b>
                                                    <div className="mt-1.5 h-1.5 overflow-hidden rounded-full bg-[#eef2f0]" aria-hidden="true">
                                                        <div
                                                            className="h-full rounded-full bg-gradient-to-r from-[#34e6a4] to-lime-400"
                                                            style={{ width: `${week.goal > 0 ? Math.min(100, (week.approved / week.goal) * 100) : 0}%` }}
                                                        />
                                                    </div>
                                                </div>
                                            ) : m.habitId === null ? (
                                                <span className="rounded-full bg-slate-100 px-2.5 py-0.5 text-[11px] font-extrabold text-slate-500">
                                                    습관 없음
                                                </span>
                                            ) : null}
                                            <span className="text-sm font-extrabold text-slate-300 transition-colors group-hover:text-forest-500">
                                                →
                                            </span>
                                        </div>
                                    </div>

                                    {isRoomFinished && m.habitId && (
                                        <MemberHabitHeatmap
                                            habitId={m.habitId}
                                            startDate={group.startDate}
                                            deadline={group.deadline}
                                        />
                                    )}
                                </div>
                            );
                        })}

                        {members.length === 0 && (
                            <div className="w-full py-16 text-center text-sm font-medium text-slate-400 rounded-3xl border border-dashed border-slate-200">
                                참여 중인 멤버가 없습니다.
                            </div>
                        )}
                        </OverlayScrollArea>
                    </div>
                )}
                </div>
            </section>

            {group && (
                <EditRoomModal
                    isOpen={isEditModalOpen}
                    onClose={() => setIsEditModalOpen(false)}
                    groupId={group.id}
                    initialData={{
                        title: group.title,
                        description: group.description,
                        deadline: group.deadline || '',
                        memberLimit: group.memberLimit,
                        penalty: group.penalty || '',
                    }}
                    onSuccess={fetchGroupData}
                />
            )}
        </main>
    );
}