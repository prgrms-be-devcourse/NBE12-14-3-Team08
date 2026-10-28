'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import PenaltySubmitModal from '@/app/components/modal/PenaltySubmitModal';
import PenaltyDetailModal from '@/app/components/modal/PenaltyDetailModal';

export default function PenaltyPlaygroundTestPage() {
  const [tokenInput, setTokenInput] = useState('');
  const [currentSavedToken, setCurrentSavedToken] = useState('');
  const [groupId, setGroupId] = useState(1);
  const [testHabitId, setTestHabitId] = useState(1);
  const [testPenaltyId, setTestPenaltyId] = useState(1);

  // 모달 제어 상태
  const [isSubmitModalOpen, setIsSubmitModalOpen] = useState(false);
  const [isDetailModalOpen, setIsDetailModalOpen] = useState(false);

  // 로컬 스토리지 토큰 동기화
  useEffect(() => {
    const saved = localStorage.getItem('accessToken') || '';
    setCurrentSavedToken(saved);
    setTokenInput(saved);
  }, []);

  const handleSaveToken = () => {
    if (!tokenInput.trim()) {
      alert('토큰을 입력해주세요.');
      return;
    }
    const cleanToken = tokenInput.replace(/^Bearer\s+/i, '').trim();
    localStorage.setItem('accessToken', cleanToken);
    setCurrentSavedToken(cleanToken);
    alert('토큰이 localStorage에 성공적으로 저장되었습니다!');
  };

  const handleClearToken = () => {
    localStorage.removeItem('accessToken');
    setCurrentSavedToken('');
    setTokenInput('');
    alert('저장된 토큰이 제거되었습니다.');
  };

  return (
      <div className="min-h-screen bg-[#F4F8F6] p-8 text-gray-900 font-sans">
        <div className="mx-auto max-w-3xl rounded-[32px] bg-white p-8 shadow-[0_8px_30px_rgb(0,0,0,0.05)] border border-forest-100">

          {/* 상단 타이틀 */}
          <div className="border-b border-gray-100 pb-6 mb-8">
          <span className="bg-forest-100 text-forest-800 text-xs font-bold px-3 py-1 rounded-full uppercase">
            Dev Test Playground
          </span>
            <h1 className="text-2xl font-black text-forest-950 mt-2">
              벌칙 & 관리자 대시보드 종합 테스트 베드
            </h1>
            <p className="text-xs text-gray-500 mt-1">
              토큰 주입, 벌칙 제출 모달, 상세 조회 모달 및 관리자 승인 대시보드로의 이동을 테스트합니다.
            </p>
          </div>

          {/* 1. 토큰 주입 섹션 */}
          <section className="mb-8 rounded-2xl bg-gray-50 p-5 border border-gray-200">
            <h2 className="text-sm font-bold text-gray-800 mb-2 flex items-center gap-2">
              <span className="h-2 w-2 rounded-full bg-forest-500"></span>
              1. 인증 토큰 설정 (LocalStorage Injection)
            </h2>
            <p className="text-xs text-gray-500 mb-3">
              Swagger에서 발급받은 방장(asd) 또는 팀원의 <code>accessToken</code> 문자열을 넣어주세요.
            </p>
            <div className="flex gap-2 mb-2">
              <input
                  type="text"
                  value={tokenInput}
                  onChange={(e) => setTokenInput(e.target.value)}
                  placeholder="eyJhbGciOi..."
                  className="flex-1 rounded-xl border border-gray-300 px-3 py-2 text-xs outline-none focus:border-forest-500 font-mono"
              />
              <button
                  onClick={handleSaveToken}
                  className="rounded-xl bg-forest-500 px-4 py-2 text-xs font-bold text-lime-400 hover:bg-forest-600 transition"
              >
                저장
              </button>
              <button
                  onClick={handleClearToken}
                  className="rounded-xl border border-gray-300 bg-white px-3 py-2 text-xs font-bold text-gray-600 hover:bg-gray-100 transition"
              >
                비우기
              </button>
            </div>
            <div className="text-[11px] text-gray-400 truncate">
              현재 로컬스토리지 상태:{' '}
              <span className={currentSavedToken ? 'text-forest-600 font-semibold' : 'text-rose-500 font-semibold'}>
              {currentSavedToken ? `토큰 등록됨 (${currentSavedToken.slice(0, 20)}...)` : '토큰 비어있음'}
            </span>
            </div>
          </section>

          {/* 2. 관리자 대시보드 바로가기 */}
          <section className="mb-8 rounded-2xl bg-forest-50/50 p-5 border border-forest-100">
            <h2 className="text-sm font-bold text-forest-950 mb-2 flex items-center gap-2">
              <span className="h-2 w-2 rounded-full bg-forest-500"></span>
              2. 관리자 인증 검토 대시보드 이동 (방장 전용)
            </h2>
            <p className="text-xs text-gray-600 mb-3">
              관리자 대시보드로 이동합니다. 방장(asd) 토큰이 주입되어 있어야 403 없이 접근할 수 있습니다.
            </p>
            <div className="flex items-center gap-3">
              <div className="flex items-center gap-1.5 text-xs text-gray-600 font-medium">
                <span>테스트 Group ID:</span>
                <input
                    type="number"
                    value={groupId}
                    onChange={(e) => setGroupId(Number(e.target.value))}
                    className="w-16 rounded-lg border border-gray-300 bg-white px-2 py-1 text-center font-bold"
                />
              </div>
              <Link
                  href={`/admin/group/${groupId}/verification`}
                  className="rounded-xl bg-forest-600 px-5 py-2 text-xs font-bold text-lime-400 shadow-sm hover:bg-forest-700 transition"
              >
                관리자 페이지(/admin/group/{groupId}/verification) 열기 ↗
              </Link>
            </div>
          </section>

          {/* 3. 모달 컴포넌트 직접 트리거 테스트 */}
          <section className="rounded-2xl bg-white p-5 border border-gray-200">
            <h2 className="text-sm font-bold text-gray-800 mb-2 flex items-center gap-2">
              <span className="h-2 w-2 rounded-full bg-forest-500"></span>
              3. 모달 컴포넌트 단독 동작 테스트
            </h2>
            <p className="text-xs text-gray-500 mb-4">
              실제 유저 시점의 벌칙 제출 모달과 관리자/팀원용 상세 조회 모달을 바로 띄워봅니다.
            </p>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {/* 제출 모달 테스트 */}
              <div className="rounded-2xl border border-gray-200 p-4 bg-gray-50 flex flex-col justify-between">
                <div>
                <span className="text-xs font-bold text-forest-700 bg-forest-100 px-2 py-0.5 rounded-full">
                  일반 팀원 시점
                </span>
                  <h3 className="text-sm font-bold text-gray-800 mt-2">벌칙 인증 제출 모달</h3>
                  <p className="text-[11px] text-gray-500 mt-1 mb-3">
                    습관 실패 후 사진과 소감을 적어 제출하는 모달입니다.
                  </p>
                  <div className="flex items-center gap-1.5 text-xs text-gray-600 font-medium mb-3">
                    <span>Habit ID:</span>
                    <input
                        type="number"
                        value={testHabitId}
                        onChange={(e) => setTestHabitId(Number(e.target.value))}
                        className="w-16 rounded-lg border border-gray-300 bg-white px-2 py-1 text-center font-bold"
                    />
                    <span>Penalty ID:</span>
                    <input
                        type="number"
                        value={testPenaltyId}
                        onChange={(e) => setTestPenaltyId(Number(e.target.value))}
                        className="w-16 rounded-lg border border-gray-300 bg-white px-2 py-1 text-center font-bold"
                    />
                  </div>
                </div>
                <button
                    onClick={() => setIsSubmitModalOpen(true)}
                    className="w-full rounded-xl bg-gray-900 text-white text-xs font-bold py-2.5 hover:bg-black transition"
                >
                  벌칙 제출 모달 띄우기 (PenaltySubmitModal)
                </button>
              </div>

              {/* 상세 보기 모달 테스트 */}
              <div className="rounded-2xl border border-gray-200 p-4 bg-gray-50 flex flex-col justify-between">
                <div>
                <span className="text-xs font-bold text-rose-700 bg-rose-100 px-2 py-0.5 rounded-full">
                  관리자/팀원 뷰
                </span>
                  <h3 className="text-sm font-bold text-gray-800 mt-2">벌칙 상세 보기 모달</h3>
                  <p className="text-[11px] text-gray-500 mt-1 mb-3">
                    제출된 벌칙의 사진과 수행 내용, 날짜를 확인하는 모달입니다.
                  </p>
                </div>
                <button
                    onClick={() => setIsDetailModalOpen(true)}
                    className="w-full rounded-xl border border-gray-300 bg-white text-gray-800 text-xs font-bold py-2.5 hover:bg-gray-100 transition"
                >
                  상세 조회 모달 띄우기 (PenaltyDetailModal)
                </button>
              </div>
            </div>
          </section>

        </div>

        {/* 1. 벌칙 제출 모달 연동 */}
        <PenaltySubmitModal
            isOpen={isSubmitModalOpen}
            onClose={() => setIsSubmitModalOpen(false)}
            penaltyId={testPenaltyId}
            habitId={testHabitId}
            penaltyText="스타벅스 커피 쏘기"
            onSuccess={() => {
              alert('제출 완료 콜백(onSuccess) 정상 동작!');
            }}
        />

        {/* 2. 벌칙 상세 조회 모달 연동 */}
        <PenaltyDetailModal
            isOpen={isDetailModalOpen}
            onClose={() => setIsDetailModalOpen(false)}
            habitTitle="알고리즘 1문제 풀기"
            imageUrl="https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?w=600&auto=format&fit=crop&q=60"
            description="알고리즘 문제 풀이를 깜빡 놓쳐서 약속대로 스타벅스 아메리카노 테이크아웃해서 인증합니다! ㅠㅠ"
            verifyDate="2026.09.23"
            penaltyText="스타벅스 아메리카노 사기"
        />
      </div>
  );
}