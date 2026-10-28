'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Eyebrow, HeroCard, HeroTile, HeroTiles, btnLime } from '../components/DesignKit';
import { CreateRoomModal } from '../components/CreateRoomModal';
import { useMember } from '../context/MemberContext';
import { groupApi } from '../lib/groupApi';
import type { CreateGroupRequest, GroupStatus, GroupSummary } from '../lib/groupApi';

const tabs: Array<{ status: GroupStatus; label: string }> = [
  { status: 'ACTIVE', label: '진행 중인 방' },
  { status: 'FINISH', label: '완료된 방' },
];

const cardShadow = 'shadow-[0_10px_30px_rgba(14,21,18,0.07),0_1px_2px_rgba(14,21,18,0.05)]';

export function MainPage() {
  const router = useRouter();
  const { currentUser, authReady } = useMember();
  const [status, setStatus] = useState<GroupStatus>('ACTIVE');
  // 두 탭의 목록을 한 번에 불러와 히어로의 숫자와 탭 전환에 같이 쓴다
  const [lists, setLists] = useState<Record<GroupStatus, GroupSummary[]>>({ ACTIVE: [], FINISH: [] });
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [refreshKey, setRefreshKey] = useState(0);
  const [showCreateModal, setShowCreateModal] = useState(false);

  useEffect(() => {
    if (!authReady) return;
    if (!currentUser) {
      router.replace('/login');
      return;
    }

    let cancelled = false;
    setIsLoading(true);
    setError('');
    Promise.all([groupApi.getMyGroups('ACTIVE'), groupApi.getMyGroups('FINISH')])
      .then(([active, finish]) => { if (!cancelled) setLists({ ACTIVE: active, FINISH: finish }); })
      .catch((caught) => {
        if (!cancelled) setError(caught instanceof Error ? caught.message : '방 목록을 불러오지 못했습니다.');
      })
      .finally(() => { if (!cancelled) setIsLoading(false); });

    return () => { cancelled = true; };
  }, [authReady, currentUser, refreshKey, router]);

  const handleCreate = async (request: CreateGroupRequest) => {
    const created = await groupApi.create(request);
    router.push(`/group/${created.id}`);
  };

  if (!authReady || (authReady && !currentUser)) {
    return <div className="px-4 py-24 text-center text-sm text-slate-500">로그인 정보를 확인하는 중...</div>;
  }

  const groups = lists[status];
  const finished = status === 'FINISH';
  const ownedCount = lists.ACTIVE.filter((group) => group.role === 'OWNER').length;
  const count = (value: number) => (isLoading ? '-' : value);

  return (
    <main className="mx-auto max-w-5xl px-4 py-8 sm:px-6 sm:py-10">
      <HeroCard>
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <Eyebrow>MY ROOMS</Eyebrow>
            <h1 className="mt-2 text-[26px] font-extrabold leading-tight tracking-tight sm:text-[28px]">
              안녕하세요, {currentUser?.name}님 👋
              <br />
              오늘도 한 번 인증해 볼까요?
            </h1>
            <p className="mt-2 text-[13px] text-white/60">참여 중인 방을 한눈에 확인하고 입장해 보세요.</p>
          </div>
          <button type="button" onClick={() => setShowCreateModal(true)} className={btnLime}>
            <span aria-hidden="true">＋</span> 새 방 만들기
          </button>
        </div>
        <HeroTiles>
          <HeroTile label="진행 중인 방" value={count(lists.ACTIVE.length)} unit="개" />
          <HeroTile label="내가 방장인 방" value={count(ownedCount)} unit="개" />
          <HeroTile label="완료된 방" value={count(lists.FINISH.length)} unit="개" />
        </HeroTiles>
      </HeroCard>

      <div className="mb-4 mt-6 flex flex-wrap items-center justify-between gap-3">
        <div className="inline-flex gap-0.5 rounded-2xl bg-slate-100 p-1" role="tablist" aria-label="방 상태">
          {tabs.map((tab) => (
            <button key={tab.status} type="button" role="tab" aria-selected={status === tab.status}
              onClick={() => setStatus(tab.status)}
              className={`rounded-xl px-4 py-2 text-[13px] font-extrabold transition cursor-pointer ${
                status === tab.status
                  ? 'bg-white text-forest-500 shadow-[0_2px_8px_rgba(14,21,18,0.08)]'
                  : 'text-slate-500 hover:text-slate-800'
              }`}>
              {tab.label}
            </button>
          ))}
        </div>
        <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-600">
          {isLoading ? '-' : groups.length}개
        </span>
      </div>

      <div>
        {isLoading ? (
          <div className="py-16 text-center text-sm text-slate-400">방 목록을 불러오는 중...</div>
        ) : error ? (
          <div className="rounded-[28px] border border-rose-100 bg-rose-50 px-5 py-10 text-center">
            <p className="text-sm font-semibold text-rose-600">{error}</p>
            <button type="button" onClick={() => setRefreshKey((current) => current + 1)}
              className="mt-3 text-xs font-bold text-rose-600 underline cursor-pointer">다시 불러오기</button>
          </div>
        ) : groups.length === 0 ? (
          <div className="rounded-[28px] border border-dashed border-slate-300 bg-white py-16 text-center">
            <p className="text-sm font-medium text-slate-400">
              {finished ? '완료된 방이 없습니다.' : '진행 중인 방이 없습니다.'}
            </p>
            {!finished && (
              <button type="button" onClick={() => setShowCreateModal(true)}
                className="mt-3 text-xs font-bold text-forest-600 hover:underline cursor-pointer">새로운 방을 만들어보세요!</button>
            )}
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-5 md:grid-cols-2">
            {groups.map((group) => {
              return (
                <button
                  key={group.id}
                  type="button"
                  onClick={() => router.push(finished ? `/group/${group.id}/settlement` : `/group/${group.id}`)}
                  className={`group block w-full rounded-[28px] border border-transparent bg-white p-[22px] text-left ${cardShadow} transition-all hover:-translate-y-[3px] hover:border-lime-400 hover:shadow-[0_16px_36px_rgba(14,21,18,0.12)] cursor-pointer`}>
                  <div className="flex items-center justify-between">
                    {finished ? (
                      <span className="rounded-full bg-slate-100 px-2.5 py-0.5 text-[11px] font-extrabold text-slate-500">완료</span>
                    ) : (
                      <span className="inline-flex items-center gap-1.5 rounded-full bg-lime-50 px-2.5 py-0.5 text-[11px] font-extrabold text-forest-500">
                        <span aria-hidden="true" className="h-1.5 w-1.5 rounded-full bg-lime-500" />진행 중
                      </span>
                    )}
                    <span className="text-xs font-bold text-slate-400">👥 {group.currentMemberCount} / {group.memberLimit}명</span>
                  </div>
                  <h3 className="mt-3.5 flex items-center gap-1.5 text-lg font-extrabold tracking-tight text-slate-900">
                    {group.role === 'OWNER' && (
                      <span role="img" aria-label="방장" title="방장">👑</span>
                    )}
                    <span>{group.title}</span>
                  </h3>
                  <p className="mt-1.5 min-h-9 text-xs leading-[1.5] text-slate-500 line-clamp-2">{group.description || '함께 목표를 달성해보세요!'}</p>
                  <div className="mt-4 flex items-center justify-between border-t border-slate-100 pt-3.5 text-xs font-extrabold text-forest-500">
                    <span>{finished ? '기록 확인하기' : '방 입장하기'}</span>
                    <span aria-hidden="true" className="transition-transform group-hover:translate-x-0.5">→</span>
                  </div>
                </button>
              );
            })}
          </div>
        )}
      </div>

      <CreateRoomModal isOpen={showCreateModal} onClose={() => setShowCreateModal(false)} onCreate={handleCreate} />
    </main>
  );
}
