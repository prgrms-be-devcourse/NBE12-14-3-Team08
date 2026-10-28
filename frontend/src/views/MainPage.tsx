'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { MemberNavigation } from '../components/MemberNavigation';
import { CreateRoomModal } from '../components/CreateRoomModal';
import { useMember } from '../context/MemberContext';
import { groupApi } from '../lib/groupApi';
import type { CreateGroupRequest, GroupStatus, GroupSummary } from '../lib/groupApi';

const tabs: Array<{ status: GroupStatus; label: string }> = [
  { status: 'ACTIVE', label: '진행 중인 방' },
  { status: 'FINISH', label: '완료된 방' },
];

export function MainPage() {
  const router = useRouter();
  const { currentUser, authReady } = useMember();
  const [status, setStatus] = useState<GroupStatus>('ACTIVE');
  const [groups, setGroups] = useState<GroupSummary[]>([]);
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
    groupApi.getMyGroups(status)
      .then((data) => { if (!cancelled) setGroups(data); })
      .catch((caught) => {
        if (!cancelled) setError(caught instanceof Error ? caught.message : '방 목록을 불러오지 못했습니다.');
      })
      .finally(() => { if (!cancelled) setIsLoading(false); });

    return () => { cancelled = true; };
  }, [authReady, currentUser, refreshKey, router, status]);

  const handleCreate = async (request: CreateGroupRequest) => {
    const created = await groupApi.create(request);
    router.push(`/group/${created.id}`);
  };

  if (!authReady || (authReady && !currentUser)) {
    return <div className="px-4 py-24 text-center text-sm text-slate-500">로그인 정보를 확인하는 중...</div>;
  }

  return (
    <main className="mx-auto max-w-5xl px-4 py-8 sm:px-6 sm:py-12">
      <section className="rounded-3xl border border-slate-100 bg-white p-6 shadow-xl shadow-slate-200/60 sm:p-10">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="text-xs font-bold text-forest-600">내기? 내기!</p>
            <h1 className="mt-2 text-2xl font-extrabold tracking-tight text-slate-900">메인페이지</h1>
          </div>
          <MemberNavigation embedded />
        </div>

        <div className="mt-8 flex justify-end">
          <button type="button" onClick={() => setShowCreateModal(true)}
            className="shrink-0 rounded-2xl bg-forest-500 px-5 py-3 text-sm font-extrabold text-lime-400 shadow-lg shadow-forest-500/25 hover:bg-forest-600">
            ＋ 새 방 만들기
          </button>
        </div>

        <div className="mt-8 flex border-b border-slate-200" role="tablist" aria-label="방 상태">
          {tabs.map((tab) => (
            <button key={tab.status} type="button" role="tab" aria-selected={status === tab.status}
              onClick={() => setStatus(tab.status)}
              className={`relative px-5 py-3 text-sm font-bold ${
                status === tab.status ? 'text-forest-700' : 'text-slate-400 hover:text-slate-600'
              }`}>
              {tab.label}
              {status === tab.status && <span className="absolute inset-x-0 bottom-0 h-0.5 rounded-full bg-forest-500" />}
            </button>
          ))}
        </div>

        <div className="mt-6">
          {isLoading ? (
            <div className="py-16 text-center text-sm text-slate-400">방 목록을 불러오는 중...</div>
          ) : error ? (
            <div className="rounded-2xl border border-rose-100 bg-rose-50 px-5 py-10 text-center">
              <p className="text-sm font-semibold text-rose-600">{error}</p>
              <button type="button" onClick={() => setRefreshKey((current) => current + 1)}
                className="mt-3 text-xs font-bold text-rose-600 underline">다시 불러오기</button>
            </div>
          ) : groups.length === 0 ? (
            <div className="rounded-3xl border border-dashed border-slate-200 py-16 text-center">
              <p className="text-sm font-medium text-slate-400">
                {status === 'ACTIVE' ? '진행 중인 방이 없습니다.' : '완료된 방이 없습니다.'}
              </p>
              {status === 'ACTIVE' && (
                <button type="button" onClick={() => setShowCreateModal(true)}
                  className="mt-3 text-xs font-bold text-forest-600 hover:underline">새로운 방을 만들어보세요!</button>
              )}
            </div>
          ) : (
            <div className="grid grid-cols-1 gap-5 md:grid-cols-2">
              {groups.map((group) => (
                <button
                  key={group.id}
                  type="button"
                  onClick={() => router.push(status === 'FINISH' ? `/group/${group.id}/settlement` : `/group/${group.id}`)}
                  className="group rounded-3xl border border-slate-200/80 bg-white p-6 text-left transition-all hover:border-forest-400 hover:shadow-xl hover:shadow-forest-500/5">
                  <div className="flex items-center justify-between gap-3">
                    <span className={`rounded-full px-2.5 py-1 text-[11px] font-bold ${
                      status === 'ACTIVE' ? 'bg-forest-100 text-forest-700' : 'bg-slate-100 text-slate-500'
                    }`}>{status === 'ACTIVE' ? '진행 중' : '완료'}</span>
                    <span className="text-xs font-semibold text-slate-400">👥 {group.currentMemberCount} / {group.memberLimit}명</span>
                  </div>
                  <h3 className="mt-4 flex items-center gap-1.5 text-lg font-extrabold text-slate-900 transition-colors group-hover:text-forest-600">
                    {group.role === 'OWNER' && (
                      <span role="img" aria-label="방장" title="방장">👑</span>
                    )}
                    <span>{group.title}</span>
                  </h3>
                  <p className="mt-2 min-h-10 text-xs leading-5 text-slate-500 line-clamp-2">{group.description || '함께 목표를 달성해보세요!'}</p>
                  <div className="mt-5 flex items-center justify-end border-t border-slate-100 pt-4 text-xs font-bold text-forest-600">
                    {status === 'ACTIVE' ? '방 입장하기' : '기록 확인하기'} <span className="ml-1">→</span>
                  </div>
                </button>
              ))}
            </div>
          )}
        </div>
      </section>

      <CreateRoomModal isOpen={showCreateModal} onClose={() => setShowCreateModal(false)} onCreate={handleCreate} />
    </main>
  );
}
