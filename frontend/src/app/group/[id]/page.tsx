'use client';

import React, { useState, useEffect, use, useCallback } from 'react';
import Link from 'next/link';
import { ArrowLeft } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useMember } from '@/context/MemberContext';
import { apiRequest } from '@/lib/memberApi';
import { EditRoomModal } from '@/components/EditRoomModal';
import { MemberHabitHeatmap } from '@/components/MemberHabitHeatmap';
import { GroupPhotoLibrary } from '@/components/GroupPhotoLibrary';

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
}

interface GroupMemberItem {
    id: number;
    memberId: number;
    nickname: string;
    username: string;
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
            });

            try {
                const rawMembers = await apiRequest<any[]>(`/groups/${groupId}/members`, {}, true);

                const formattedMembers: GroupMemberItem[] = (rawMembers || []).map((m: any) => ({
                    id: m.groupMemberId || m.id,
                    memberId: m.memberId,
                    nickname: m.nickname || m.memberName || '',
                    username: m.username,
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
        <main className="mx-auto max-w-5xl px-4 py-6 sm:px-6 md:py-8">
            {/* Top Nav */}
            <div className="mb-5 flex items-center justify-between">
                <Link
                    href="/main"
                    className="flex items-center gap-1.5 text-xs font-bold text-slate-500 transition-colors hover:text-slate-900"
                >
                    <ArrowLeft className="h-4 w-4" />
                    <span>방 목록으로</span>
                </Link>
            </div>

            <div className="rounded-3xl border border-slate-100 bg-white p-6 shadow-xl shadow-slate-200/60 sm:p-10">
                {/* 그룹 기본 정보 카드 */}
                <section className="rounded-3xl border border-slate-200/80 bg-white p-6 sm:p-8 transition-all">
                    <div className="flex flex-wrap items-start justify-between gap-4">
                        <div className="space-y-1.5">
                            <div className="flex items-center gap-2">
                                <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">
                                    {group.title}
                                </h1>
                                {currentRole === 'OWNER' && (
                                    <span
                                        className="text-base"
                                        role="img"
                                        aria-label="방장"
                                        title="방장"
                                    >
                                        👑
                                    </span>
                                )}
                            </div>
                            <p className="text-xs leading-5 text-slate-500 max-w-xl">
                                {group.description || '함께 목표를 실천해보세요!'}
                            </p>
                        </div>

                        <button
                            onClick={handleLeaveGroup}
                            className="rounded-xl border border-rose-200 bg-rose-50 px-3.5 py-1.5 text-xs font-bold text-rose-600 hover:bg-rose-100 transition cursor-pointer"
                        >
                            방 탈퇴
                        </button>
                    </div>

                    <div className="mt-6 flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-4 text-xs font-semibold text-slate-500">
                        <div className="flex items-center gap-2">
                            <span>👥 참여 인원</span>
                            <span className="font-bold text-slate-700">
                                {group.currentMemberCount} / {group.memberLimit}명
                            </span>
                        </div>
                        <div className="flex items-center gap-2">
                            <span>📅 마감 기한</span>
                            <span className="font-bold text-slate-700">
                                {group.deadline ? `${group.deadline}까지` : '상시 진행'}
                            </span>
                        </div>
                    </div>

                    <div className="mt-4 flex items-center gap-2 text-xs">
                        <span className="font-bold text-slate-400">벌칙</span>
                        <span className="rounded-full bg-rose-50 border border-rose-100 px-3 py-1 font-bold text-rose-600">
                            {group.penalty || '벌칙 없음'}
                        </span>
                    </div>

                    {/* 컨트롤 버튼부 */}
                    <div className="mt-6 flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-5">
                        <button
                            onClick={handleCopyInvite}
                            className="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-2 text-xs font-bold text-slate-700 hover:bg-slate-100 transition cursor-pointer"
                        >
                            초대 코드 복사
                        </button>

                        {currentRole === 'OWNER' && (
                            <div className="flex items-center gap-2">
                                <button
                                    onClick={() => setIsEditModalOpen(true)}
                                    className="rounded-2xl border border-slate-200 bg-white px-4 py-2 text-xs font-bold text-slate-700 hover:bg-slate-50 hover:border-slate-300 transition cursor-pointer"
                                >
                                    방 설정
                                </button>
                                <Link
                                    href={`/admin/group/${rawId}/verification`}
                                    className="rounded-2xl bg-emerald-500 px-4 py-2 text-xs font-extrabold text-white shadow-md shadow-emerald-500/20 hover:bg-emerald-600 transition text-center"
                                >
                                    미인증 내역 관리
                                </Link>
                            </div>
                        )}
                    </div>
                </section>

                {/* 인증 사진 라이브러리 / 멤버별 습관 목록 전환 */}
                <section className="mt-8">
                    <div
                        className="mb-4 inline-flex rounded-2xl border border-slate-200 bg-slate-50 p-1"
                        role="group"
                        aria-label="보기 전환"
                    >
                        <button
                            type="button"
                            aria-pressed={view === 'photos'}
                            onClick={() => setView('photos')}
                            className={`rounded-xl px-4 py-1.5 text-sm font-extrabold transition cursor-pointer ${
                                view === 'photos' ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-400 hover:text-slate-700'
                            }`}
                        >
                            인증 사진
                        </button>
                        <button
                            type="button"
                            aria-pressed={view === 'members'}
                            onClick={() => setView('members')}
                            className={`rounded-xl px-4 py-1.5 text-sm font-extrabold transition cursor-pointer ${
                                view === 'members' ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-400 hover:text-slate-700'
                            }`}
                        >
                            도전 중인 멤버 <span className="text-emerald-600 text-xs font-bold">({group.currentMemberCount})</span>
                        </button>
                    </div>

                    {view === 'photos' && (
                        <GroupPhotoLibrary
                            groupId={group.id}
                            members={members.map((m) => ({ memberId: m.memberId, nickname: m.nickname }))}
                        />
                    )}

                    {view === 'members' && (
                    <div className="grid grid-cols-1 gap-3.5">
                        {members.map((m, index) => {
                            const linkHref = `/group/${rawId}/member/${m.memberId}`;

                            return (
                                <div
                                    key={`member-${m.id}-${index}`}
                                    onClick={() => router.push(linkHref)}
                                    className="group flex flex-col rounded-3xl border border-slate-200/80 bg-white p-5 transition-all hover:border-emerald-400 hover:shadow-lg hover:shadow-emerald-500/5 cursor-pointer"
                                >
                                    <div className="flex items-center justify-between w-full">
                                        <div className="flex items-center gap-3.5">
                                            <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-emerald-50 text-sm font-black text-emerald-600 border border-emerald-100">
                                                {m.nickname.slice(0, 1)}
                                            </div>
                                            <div>
                                                <div className="flex items-center gap-1.5">
                                                    <span className="text-sm font-extrabold text-slate-900 group-hover:text-emerald-600 transition-colors">
                                                        {m.nickname}
                                                    </span>
                                                    {m.role === 'OWNER' && <span className="text-xs">👑</span>}
                                                </div>
                                                <span className="text-[11px] font-semibold text-slate-400">
                                                    {m.username}
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
                                            <span className="text-sm font-bold text-slate-300 group-hover:text-emerald-500 transition-colors">
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
                    )}
                </section>
            </div>

            {group && (
                <EditRoomModal
                    isOpen={isEditModalOpen}
                    onClose={() => setIsEditModalOpen(false)}
                    groupId={group.id}
                    initialData={{
                        title: group.title,
                        description: group.description,
                        startDate: group.startDate || new Date().toISOString().slice(0, 10),
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