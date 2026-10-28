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
    const [view, setView] = useState<'photos' | 'members'>('photos');

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
                startDate: groupData.startDate,
                deadline: groupData.deadline,
                penalty: groupData.penalty || '',
                inviteCode: groupData.inviteCode || '',
                memberLimit: groupData.memberLimit || 10,
                inviteLink: groupData.inviteLink,
                currentMemberCount: groupData.currentMemberCount || 1,
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
                    <HeroTile label="🎁" title="벌칙" compact value={group.penalty || '벌칙 없음'} />
                </HeroTiles>

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
                        <div id="group-view-panel" role="tabpanel" aria-label="멤버" className="grid grid-cols-1 gap-3.5 p-4 sm:p-5">
                        {members.map((m, index) => {
                            const linkHref = `/group/${rawId}/member/${m.memberId}`;

                            return (
                                <div
                                    key={`member-${m.id}-${index}`}
                                    onClick={() => router.push(linkHref)}
                                    className="group flex flex-col rounded-3xl border border-slate-200/80 bg-white p-5 transition-all hover:border-forest-400 hover:shadow-lg hover:shadow-forest-500/5 cursor-pointer"
                                >
                                    <div className="flex items-center justify-between w-full">
                                        <div className="flex items-center gap-3.5">
                                            <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-forest-50 text-sm font-black text-forest-600 border border-forest-100">
                                                {m.nickname.slice(0, 1)}
                                            </div>
                                            <div>
                                                <div className="flex items-center gap-1.5">
                                                    <span className="text-sm font-extrabold text-slate-900 group-hover:text-forest-600 transition-colors">
                                                        {m.nickname}
                                                    </span>
                                                    {m.role === 'OWNER' && <span className="text-xs">👑</span>}
                                                </div>
                                                <span className="text-[11px] font-semibold text-slate-400">
                                                    {memberHandle(m.provider, m.username)}
                                                </span>
                                            </div>
                                        </div>

                                        <div className="flex items-center gap-4 text-right">
                                            <div className="hidden sm:block">
                                                <div className="text-xs font-bold text-slate-800">
                                                    {m.habitTitle || '등록된 습관 없음'}
                                                </div>
                                                <div className="text-[11px] text-slate-400 truncate max-w-[200px]">
                                                    {m.habitDescription || '습관 설명이 없습니다.'}
                                                </div>
                                            </div>
                                            <span className="text-sm font-bold text-slate-300 group-hover:text-forest-500 transition-colors">
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
                        </div>
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