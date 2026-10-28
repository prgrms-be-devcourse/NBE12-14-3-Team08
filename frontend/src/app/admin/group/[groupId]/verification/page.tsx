'use client';

import React, { useState, useEffect, use } from 'react';
import Link from 'next/link';
import PenaltyDetailModal from '@/app/components/modal/PenaltyDetailModal';
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
      <main className="mx-auto max-w-5xl px-4 py-8 sm:px-6 sm:py-12">
        <div className="rounded-3xl border border-slate-100 bg-white p-6 shadow-xl shadow-slate-200/60 sm:p-10">
          <div className="mb-8 border-b border-slate-100 pb-5">
            <Link
                href={`/group/${groupId}`}
                className="inline-flex items-center gap-1.5 text-xs font-bold text-slate-400 hover:text-slate-700 transition mb-3"
            >
              <span>←</span> 그룹으로 돌아가기
            </Link>
            <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">관리자 페이지</h1>
            <p className="mt-1.5 text-sm text-slate-500">멤버가 올린 인증을 확인하고 승인하거나 반려해요.</p>
          </div>

          <section className="mb-12">
            <div className="flex items-center justify-between mb-5 flex-wrap gap-3">
              <div className="flex items-center gap-2">
                <div className="w-1.5 h-5 bg-emerald-500 rounded-full"></div>
                <span className="text-lg font-extrabold text-slate-900">습관 인증 대기</span>
                <span className="bg-emerald-100 text-emerald-700 text-xs font-bold px-2 py-0.5 rounded-full ml-1">
                {habitVerifyList.length}
              </span>
              </div>

              <div className="flex items-center gap-2">
                <button
                    onClick={toggleSelectAllHabits}
                    className="rounded-full border border-slate-200 bg-white px-3.5 py-1.5 text-xs font-bold text-slate-600 hover:bg-slate-50 transition-colors cursor-pointer"
                >
                  {selectedHabitVerifyIds.length === habitVerifyList.length && habitVerifyList.length > 0
                      ? '선택 해제'
                      : '전체 선택'}
                </button>
                <button
                    onClick={() => handleBulkHabitAction('approve')}
                    className="rounded-full bg-emerald-50 px-4 py-1.5 text-xs font-bold text-emerald-700 hover:bg-emerald-100 transition-colors cursor-pointer"
                >
                  선택 승인
                </button>
                <button
                    onClick={() => handleBulkHabitAction('reject')}
                    className="rounded-full bg-rose-50 px-4 py-1.5 text-xs font-bold text-rose-600 hover:bg-rose-100 transition-colors cursor-pointer"
                >
                  선택 거절
                </button>
              </div>
            </div>

            <div className="flex gap-5 overflow-x-auto pb-6 px-1">
              {habitVerifyList.map((item) => {
                const isChecked = selectedHabitVerifyIds.includes(item.id);
                return (
                    <div
                        key={item.id}
                        onClick={() => setSelectedHabitVerify(item)}
                        className={`relative flex h-[280px] w-48 flex-shrink-0 flex-col justify-between rounded-[24px] bg-white p-4 border transition-all duration-200 cursor-pointer hover:-translate-y-1 hover:shadow-lg ${
                            isChecked ? 'border-emerald-400 shadow-md ring-1 ring-emerald-400' : 'border-slate-100 shadow-sm'
                        }`}
                    >
                      <div className="flex items-center justify-between mb-2">
                        <input
                            type="checkbox"
                            checked={isChecked}
                            onClick={(e) => e.stopPropagation()}
                            onChange={() =>
                                setSelectedHabitVerifyIds((prev) =>
                                    prev.includes(item.id) ? prev.filter((id) => id !== item.id) : [...prev, item.id]
                                )
                            }
                            className="h-4 w-4 rounded border-slate-300 text-emerald-500 focus:ring-emerald-400 cursor-pointer"
                        />
                        <span className="text-xs font-semibold text-slate-400 bg-slate-50 px-2 py-0.5 rounded-full">
                      {item.date}
                    </span>
                      </div>

                      <div className="relative mb-3 flex h-28 w-full items-center justify-center rounded-2xl bg-emerald-50/50 border border-emerald-50 overflow-hidden group">
                        {item.imageUrl ? (
                            <img
                                src={item.imageUrl}
                                alt="습관 인증 사진"
                                className="h-full w-full object-cover transition-transform duration-300 group-hover:scale-105"
                            />
                        ) : (
                            <span className="text-xs font-medium text-emerald-300">사진 없음</span>
                        )}
                      </div>

                      <div className="text-center mb-3">
                        <div className="truncate text-[13px] font-extrabold text-slate-800 mb-0.5">
                          {item.habitTitle}
                        </div>
                        <div className="text-[11px] font-medium text-slate-400">{item.memberName}</div>
                      </div>

                      <div className="flex gap-2 pt-3 border-t border-slate-50 mt-auto">
                        <button
                            onClick={(e) => handleSingleHabitAction(e, item.id, 'approve')}
                            className="flex-1 py-1.5 rounded-xl bg-emerald-500 text-white hover:bg-emerald-600 text-[12px] font-bold shadow-sm transition-all cursor-pointer"
                        >
                          승인
                        </button>
                        <button
                            onClick={(e) => handleSingleHabitAction(e, item.id, 'reject')}
                            className="flex-1 py-1.5 rounded-xl bg-white text-slate-500 hover:bg-rose-50 hover:text-rose-600 border border-slate-200 hover:border-rose-200 text-[12px] font-bold transition-all cursor-pointer"
                        >
                          거절
                        </button>
                      </div>
                    </div>
                );
              })}

              {habitVerifyList.length === 0 && (
                  <div className="w-full py-16 text-center text-sm font-medium text-slate-400 rounded-3xl border border-dashed border-slate-200">
                    대기 중인 습관 인증이 없습니다.
                  </div>
              )}
            </div>
          </section>

          <section>
            <div className="flex items-center justify-between mb-5 flex-wrap gap-3">
              <div className="flex items-center gap-2">
                <div className="w-1.5 h-5 bg-rose-400 rounded-full"></div>
                <span className="text-lg font-extrabold text-slate-900">벌칙 인증 대기</span>
                <span className="bg-rose-100 text-rose-700 text-xs font-bold px-2 py-0.5 rounded-full ml-1">
                {penaltyList.length}
              </span>
              </div>

              <div className="flex items-center gap-2">
                <button
                    onClick={toggleSelectAllPenalties}
                    className="rounded-full border border-slate-200 bg-white px-3.5 py-1.5 text-xs font-bold text-slate-600 hover:bg-slate-50 transition-colors cursor-pointer"
                >
                  {selectedPenaltyIds.length === penaltyList.length && penaltyList.length > 0 ? '선택 해제' : '전체 선택'}
                </button>
                <button
                    onClick={() => handleBulkPenaltyAction('approve')}
                    className="rounded-full bg-emerald-50 px-4 py-1.5 text-xs font-bold text-emerald-700 hover:bg-emerald-100 transition-colors cursor-pointer"
                >
                  선택 승인
                </button>
                <button
                    onClick={() => handleBulkPenaltyAction('reject')}
                    className="rounded-full bg-rose-50 px-4 py-1.5 text-xs font-bold text-rose-600 hover:bg-rose-100 transition-colors cursor-pointer"
                >
                  선택 거절
                </button>
              </div>
            </div>

            <div className="flex gap-5 overflow-x-auto pb-6 px-1">
              {penaltyList.map((item) => {
                const isChecked = selectedPenaltyIds.includes(item.id);
                return (
                    <div
                        key={item.id}
                        onClick={() => setSelectedPenalty(item)}
                        className={`relative flex h-[280px] w-48 flex-shrink-0 flex-col justify-between rounded-[24px] bg-white p-4 border transition-all duration-200 cursor-pointer hover:-translate-y-1 hover:shadow-lg ${
                            isChecked ? 'border-emerald-400 shadow-md ring-1 ring-emerald-400' : 'border-slate-100 shadow-sm'
                        }`}
                    >
                      <div className="flex items-center justify-between mb-2">
                        <input
                            type="checkbox"
                            checked={isChecked}
                            onClick={(e) => e.stopPropagation()}
                            onChange={() =>
                                setSelectedPenaltyIds((prev) =>
                                    prev.includes(item.id) ? prev.filter((id) => id !== item.id) : [...prev, item.id]
                                )
                            }
                            className="h-4 w-4 rounded border-slate-300 text-emerald-500 focus:ring-emerald-400 cursor-pointer"
                        />
                        <span className="text-xs font-semibold text-slate-400 bg-slate-50 px-2 py-0.5 rounded-full">
                      {item.date}
                    </span>
                      </div>

                      <div className="relative mb-3 flex h-28 w-full items-center justify-center rounded-2xl bg-emerald-50/50 border border-emerald-50 overflow-hidden group">
                        {item.imageUrl ? (
                            <img
                                src={item.imageUrl}
                                alt="인증 사진"
                                className="h-full w-full object-cover transition-transform duration-300 group-hover:scale-105"
                            />
                        ) : (
                            <span className="text-xs font-medium text-emerald-300">사진 없음</span>
                        )}
                      </div>

                      <div className="text-center mb-3">
                        <div className="truncate text-[13px] font-extrabold text-slate-800 mb-0.5">
                          {item.habitTitle}
                        </div>
                        <div className="text-[11px] font-medium text-slate-400">{item.memberName}</div>
                      </div>

                      <div className="flex gap-2 pt-3 border-t border-slate-50 mt-auto">
                        <button
                            onClick={(e) => handleSinglePenaltyAction(e, item.id, 'approve')}
                            className="flex-1 py-1.5 rounded-xl bg-emerald-500 text-white hover:bg-emerald-600 text-[12px] font-bold shadow-sm transition-all cursor-pointer"
                        >
                          승인
                        </button>
                        <button
                            onClick={(e) => handleSinglePenaltyAction(e, item.id, 'reject')}
                            className="flex-1 py-1.5 rounded-xl bg-white text-slate-500 hover:bg-rose-50 hover:text-rose-600 border border-slate-200 hover:border-rose-200 text-[12px] font-bold transition-all cursor-pointer"
                        >
                          거절
                        </button>
                      </div>
                    </div>
                );
              })}

              {penaltyList.length === 0 && (
                  <div className="w-full py-16 text-center text-sm font-medium text-slate-400 rounded-3xl border border-dashed border-slate-200">
                    대기 중인 벌칙 인증이 없습니다.
                  </div>
              )}
            </div>
          </section>
        </div>

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