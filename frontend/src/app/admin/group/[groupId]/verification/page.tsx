'use client';

import React, { useState, useEffect, use } from 'react';
import Link from 'next/link';
import { ArrowLeft } from 'lucide-react';
import PenaltyDetailModal from '@/app/components/modal/PenaltyDetailModal';
import { Eyebrow, HeroCard, HeroTile, HeroTiles, cardClass } from '@/components/DesignKit';
import { apiRequest } from '@/lib/memberApi';

interface VerifyCardItem {
  id: number;
  date: string;
  imageUrl: string;
  habitTitle: string;
  memberName: string;
  description: string;
  penaltyText: string;
}

interface HabitVerifyCardItem {
  id: number;
  date: string;
  imageUrl: string;
  habitTitle: string;
  memberName: string;
  description: string;
}

export default function AdminVerificationPage({
                                                params,
                                              }: {
  params: Promise<{ groupId: string }> | { groupId: string };
}) {
  const resolvedParams = params instanceof Promise ? use(params) : params;
  const groupId = Number(resolvedParams?.groupId) || 1;

  const [penaltyList, setPenaltyList] = useState<VerifyCardItem[]>([]);
  const [selectedPenaltyIds, setSelectedPenaltyIds] = useState<number[]>([]);
  const [selectedPenalty, setSelectedPenalty] = useState<VerifyCardItem | null>(null);

  const [habitVerifyList, setHabitVerifyList] = useState<HabitVerifyCardItem[]>([]);
  const [selectedHabitVerifyIds, setSelectedHabitVerifyIds] = useState<number[]>([]);
  const [selectedHabitVerify, setSelectedHabitVerify] = useState<HabitVerifyCardItem | null>(null);

  useEffect(() => {
    const fetchPendingPenalties = async () => {
      try {
        const data = await apiRequest<any[]>(`/groups/${groupId}/penalties/pending`, {}, true);
        const formatted: VerifyCardItem[] = (data || []).map((item: any) => ({
          id: item.id,
          date: item.verifyDate || '',
          imageUrl: item.imageUrl || '',
          habitTitle: item.habitTitle || '',
          memberName: item.memberNickname || '',
          description: item.description || '',
          penaltyText: item.penaltyText || '',
        }));

        setPenaltyList(formatted);
      } catch (err) {
        console.error('벌칙 대기 목록 fetch 에러:', err);
      }
    };

    const fetchPendingHabitVerifications = async () => {
      try {
        const data = await apiRequest<any[]>(`/groups/${groupId}/habits/verifications/pending`, {}, true);
        const formatted: HabitVerifyCardItem[] = (data || []).map((item: any) => ({
          id: item.id,
          date: item.verifyDate || '',
          imageUrl: item.imageUrl || '',
          habitTitle: item.habitTitle || '',
          memberName: item.memberNickname || '',
          description: item.description || '',
        }));

        setHabitVerifyList(formatted);
      } catch (err) {
        console.error('습관 대기 목록 fetch 에러:', err);
      }
    };

    fetchPendingPenalties();
    fetchPendingHabitVerifications();
  }, [groupId]);

  const handleSingleHabitAction = async (
      e: React.MouseEvent | null,
      id: number,
      action: 'approve' | 'reject'
  ) => {
    if (e) e.stopPropagation();
    const actionKo = action === 'approve' ? '승인' : '거절';
    if (!confirm(`이 습관 인증을 ${actionKo}하시겠습니까?`)) return;

    try {
      await apiRequest<void>(`/habits/verifications/${id}/${action}`, { method: 'PATCH' }, true);

      alert(`습관 인증이 ${actionKo}되었습니다.`);
      setHabitVerifyList((prev) => prev.filter((item) => item.id !== id));
      setSelectedHabitVerifyIds((prev) => prev.filter((selectedId) => selectedId !== id));
      if (selectedHabitVerify?.id === id) {
        setSelectedHabitVerify(null);
      }
    } catch (err: any) {
      alert(err.message || '처리에 실패했습니다.');
    }
  };

  const handleBulkHabitAction = async (action: 'approve' | 'reject') => {
    if (selectedHabitVerifyIds.length === 0) {
      alert('선택된 습관 인증 항목이 없습니다.');
      return;
    }

    const actionKo = action === 'approve' ? '일괄 승인' : '일괄 거절';
    if (!confirm(`선택한 ${selectedHabitVerifyIds.length}건을 ${actionKo}하시겠습니까?`)) return;

    try {
      await apiRequest<void>(
          `/groups/${groupId}/habits/verifications/bulk-${action}`,
          {
            method: 'PATCH',
            body: JSON.stringify({ ids: selectedHabitVerifyIds }),
          },
          true
      );

      alert(`${actionKo} 처리가 완료되었습니다.`);
      setHabitVerifyList((prev) => prev.filter((h) => !selectedHabitVerifyIds.includes(h.id)));
      setSelectedHabitVerifyIds([]);
    } catch (err: any) {
      alert(err.message || '일괄 처리에 실패했습니다.');
    }
  };

  const toggleSelectAllHabits = () => {
    if (selectedHabitVerifyIds.length === habitVerifyList.length) {
      setSelectedHabitVerifyIds([]);
    } else {
      setSelectedHabitVerifyIds(habitVerifyList.map((h) => h.id));
    }
  };

  const handleSinglePenaltyAction = async (
      e: React.MouseEvent | null,
      id: number,
      action: 'approve' | 'reject'
  ) => {
    if (e) e.stopPropagation();
    const actionKo = action === 'approve' ? '승인' : '거절';
    if (!confirm(`이 벌칙 인증을 ${actionKo}하시겠습니까?`)) return;

    try {
      await apiRequest<void>(`/penalties/${id}/${action}`, { method: 'PATCH' }, true);

      alert(`벌칙 인증이 ${actionKo}되었습니다.`);
      setPenaltyList((prev) => prev.filter((item) => item.id !== id));
      setSelectedPenaltyIds((prev) => prev.filter((selectedId) => selectedId !== id));
      if (selectedPenalty?.id === id) {
        setSelectedPenalty(null);
      }
    } catch (err: any) {
      alert(err.message || '처리에 실패했습니다.');
    }
  };

  const handleBulkPenaltyAction = async (action: 'approve' | 'reject') => {
    if (selectedPenaltyIds.length === 0) {
      alert('선택된 항목이 없습니다.');
      return;
    }

    const actionKo = action === 'approve' ? '일괄 승인' : '일괄 거절';
    if (!confirm(`선택한 ${selectedPenaltyIds.length}건을 ${actionKo}하시겠습니까?`)) return;

    try {
      await apiRequest<void>(
          `/groups/${groupId}/penalties/bulk-${action}`,
          {
            method: 'PATCH',
            body: JSON.stringify({ ids: selectedPenaltyIds }),
          },
          true
      );

      alert(`${actionKo} 처리가 완료되었습니다.`);
      setPenaltyList((prev) => prev.filter((p) => !selectedPenaltyIds.includes(p.id)));
      setSelectedPenaltyIds([]);
    } catch (err: any) {
      alert(err.message || '일괄 처리에 실패했습니다.');
    }
  };

  const toggleSelectAllPenalties = () => {
    if (selectedPenaltyIds.length === penaltyList.length) {
      setSelectedPenaltyIds([]);
    } else {
      setSelectedPenaltyIds(penaltyList.map((p) => p.id));
    }
  };

  return (
      <main className="mx-auto max-w-5xl px-4 pb-8 pt-1 sm:px-6 sm:pb-10 sm:pt-2">
        <Link
            href={`/group/${groupId}`}
            className="mb-3 flex w-fit items-center sm:mb-4 gap-1.5 text-xs font-bold text-slate-500 transition-colors hover:text-slate-900"
        >
          <ArrowLeft className="h-4 w-4" />
          <span>그룹으로 돌아가기</span>
        </Link>

        <HeroCard>
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div>
              <Eyebrow>ADMIN</Eyebrow>
              <h1 className="mt-2 text-[26px] font-extrabold leading-tight tracking-tight sm:text-[28px]">
                미인증 내역 관리
              </h1>
            </div>
          </div>
          <HeroTiles>
            <HeroTile label="습관 인증 대기" value={habitVerifyList.length} unit="건" />
            <HeroTile label="벌칙 인증 대기" value={penaltyList.length} unit="건" />
          </HeroTiles>
        </HeroCard>

        <PendingSection
            title="습관 인증 대기"
            accent="forest"
            imageAlt="습관 인증 사진"
            emptyText="대기 중인 습관 인증이 없습니다."
            items={habitVerifyList}
            selectedIds={selectedHabitVerifyIds}
            onToggle={(id) =>
                setSelectedHabitVerifyIds((prev) =>
                    prev.includes(id) ? prev.filter((value) => value !== id) : [...prev, id]
                )
            }
            onToggleAll={toggleSelectAllHabits}
            onBulk={handleBulkHabitAction}
            onSingle={handleSingleHabitAction}
            onOpen={(item) => setSelectedHabitVerify(item)}
        />

        <PendingSection
            title="벌칙 인증 대기"
            accent="rose"
            imageAlt="인증 사진"
            emptyText="대기 중인 벌칙 인증이 없습니다."
            items={penaltyList}
            selectedIds={selectedPenaltyIds}
            onToggle={(id) =>
                setSelectedPenaltyIds((prev) =>
                    prev.includes(id) ? prev.filter((value) => value !== id) : [...prev, id]
                )
            }
            onToggleAll={toggleSelectAllPenalties}
            onBulk={handleBulkPenaltyAction}
            onSingle={handleSinglePenaltyAction}
            onOpen={(item) => setSelectedPenalty(item)}
        />

        {selectedPenalty && (
            <PenaltyDetailModal
                isOpen={!!selectedPenalty}
                onClose={() => setSelectedPenalty(null)}
                habitTitle={selectedPenalty.habitTitle}
                penaltyText={selectedPenalty.penaltyText}
                imageUrl={selectedPenalty.imageUrl}
                description={selectedPenalty.description}
                verifyDate={selectedPenalty.date}
                onApprove={() => handleSinglePenaltyAction(null, selectedPenalty.id, 'approve')}
                onReject={() => handleSinglePenaltyAction(null, selectedPenalty.id, 'reject')}
            />
        )}

        {selectedHabitVerify && (
            <PenaltyDetailModal
                isOpen={!!selectedHabitVerify}
                onClose={() => setSelectedHabitVerify(null)}
                habitTitle={selectedHabitVerify.habitTitle}
                penaltyText="습관 수행 인증"
                imageUrl={selectedHabitVerify.imageUrl}
                description={selectedHabitVerify.description}
                verifyDate={selectedHabitVerify.date}
                onApprove={() => handleSingleHabitAction(null, selectedHabitVerify.id, 'approve')}
                onReject={() => handleSingleHabitAction(null, selectedHabitVerify.id, 'reject')}
            />
        )}
      </main>
  );
}

interface PendingSectionProps<T extends HabitVerifyCardItem> {
  title: string;
  // 제목 옆 막대와 건수 알약 색: 습관은 숲색, 벌칙은 장미색
  accent: 'forest' | 'rose';
  imageAlt: string;
  emptyText: string;
  items: T[];
  selectedIds: number[];
  onToggle: (id: number) => void;
  onToggleAll: () => void;
  onBulk: (action: 'approve' | 'reject') => void;
  onSingle: (event: React.MouseEvent | null, id: number, action: 'approve' | 'reject') => void;
  onOpen: (item: T) => void;
}

// 대기 중인 인증을 카드로 보여 주고 선택·승인·거절하는 영역. 습관 인증과 벌칙 인증이 같이 쓴다.
function PendingSection<T extends HabitVerifyCardItem>({
                                                         title,
                                                         accent,
                                                         imageAlt,
                                                         emptyText,
                                                         items,
                                                         selectedIds,
                                                         onToggle,
                                                         onToggleAll,
                                                         onBulk,
                                                         onSingle,
                                                         onOpen,
                                                       }: PendingSectionProps<T>) {
  const allSelected = selectedIds.length === items.length && items.length > 0;

  return (
      <section className={`mt-6 ${cardClass}`}>
        <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2.5">
            <span className={`h-5 w-1.5 rounded-full ${accent === 'rose' ? 'bg-rose-400' : 'bg-forest-500'}`} />
            <h2 className="text-[17px] font-extrabold tracking-tight text-slate-900">{title}</h2>
            <span
                className={`rounded-full px-2.5 py-0.5 text-[11px] font-extrabold ${
                    accent === 'rose' ? 'bg-rose-50 text-rose-600' : 'bg-lime-50 text-forest-500'
                }`}
            >
              {items.length}
            </span>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <button
                onClick={onToggleAll}
                className="rounded-full border border-slate-200 bg-white px-3.5 py-1.5 text-xs font-bold text-slate-600 transition-colors hover:bg-slate-50 cursor-pointer"
            >
              {allSelected ? '선택 해제' : '전체 선택'}
            </button>
            <button
                onClick={() => onBulk('approve')}
                className="rounded-full bg-lime-50 px-3.5 py-1.5 text-xs font-extrabold text-forest-500 transition-colors hover:bg-lime-100 cursor-pointer"
            >
              선택 승인
            </button>
            <button
                onClick={() => onBulk('reject')}
                className="rounded-full bg-rose-50 px-3.5 py-1.5 text-xs font-bold text-rose-600 transition-colors hover:bg-rose-100 cursor-pointer"
            >
              선택 거절
            </button>
          </div>
        </div>

        <div className="-mx-1 flex gap-4 overflow-x-auto px-1 pb-4 pt-1">
          {items.map((item) => {
            const isChecked = selectedIds.includes(item.id);
            return (
                <div
                    key={item.id}
                    onClick={() => onOpen(item)}
                    className={`relative flex h-[286px] w-48 flex-shrink-0 cursor-pointer flex-col rounded-3xl border bg-white p-3.5 transition-all duration-200 hover:-translate-y-[3px] hover:shadow-[0_14px_30px_rgba(14,21,18,0.1)] ${
                        isChecked ? 'border-forest-500 shadow-[0_0_0_2px_rgba(15,61,46,0.12)]' : 'border-slate-200'
                    }`}
                >
                  <div className="flex items-center justify-between">
                    <input
                        type="checkbox"
                        checked={isChecked}
                        aria-label="선택"
                        onClick={(e) => e.stopPropagation()}
                        onChange={() => onToggle(item.id)}
                        className="h-[18px] w-[18px] cursor-pointer rounded border-slate-300 accent-forest-500"
                    />
                    <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-bold text-slate-400">
                      {item.date}
                    </span>
                  </div>

                  <div className="group relative my-3 flex h-28 w-full items-center justify-center overflow-hidden rounded-[18px] bg-forest-50/60">
                    {item.imageUrl ? (
                        <img
                            src={item.imageUrl}
                            alt={imageAlt}
                            className="h-full w-full object-cover transition-transform duration-300 group-hover:scale-105"
                        />
                    ) : (
                        <span className="text-xs font-medium text-forest-300">사진 없음</span>
                    )}
                  </div>

                  <div className="text-center">
                    <div className="truncate text-[13px] font-extrabold text-slate-900">{item.habitTitle}</div>
                    <div className="mt-0.5 text-[11px] font-semibold text-slate-400">{item.memberName}</div>
                  </div>

                  <div className="mt-auto flex gap-2 border-t border-slate-100 pt-3">
                    <button
                        onClick={(e) => onSingle(e, item.id, 'approve')}
                        className="flex-1 cursor-pointer rounded-xl bg-forest-500 py-2 text-xs font-extrabold text-lime-400 transition-colors hover:bg-forest-800"
                    >
                      승인
                    </button>
                    <button
                        onClick={(e) => onSingle(e, item.id, 'reject')}
                        className="flex-1 cursor-pointer rounded-xl border border-slate-200 bg-white py-2 text-xs font-bold text-slate-500 transition-all hover:border-rose-200 hover:bg-rose-50 hover:text-rose-600"
                    >
                      거절
                    </button>
                  </div>
                </div>
            );
          })}

          {items.length === 0 && (
              <div className="w-full rounded-[22px] border border-dashed border-slate-200 py-12 text-center text-sm font-medium text-slate-400">
                {emptyText}
              </div>
          )}
        </div>
      </section>
  );
}
