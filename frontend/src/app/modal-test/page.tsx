'use client';

import { useState } from 'react';
import { HabitVerifyModal } from '@/components/HabitVerifyModal';
import { HabitVerifyDetailModal } from '@/components/HabitVerifyDetailModal';

const testVerification = {
  id: 1,
  habitId: 1,
  habitTitle: '기상 후 10분 운동하고 샤워하기',
  verifyDate: '2026-09-27',
  status: 'APPROVED' as const,
  description: '기상 후 운동을 완료하고 샤워했습니다.',
  imageUrl:
    'https://images.unsplash.com/photo-1517836357463-d25dfeac3438',
};

export default function ModalTestPage() {
  const [isVerifyModalOpen, setIsVerifyModalOpen] = useState(false);
  const [isDetailModalOpen, setIsDetailModalOpen] = useState(false);

  const handleVerifySubmit = async (data: {
    description: string;
    imageUrl: string;
    imageWidth?: number;
    imageHeight?: number;
  }) => {
    console.log('인증 등록 테스트:', data);
  };

  return (
    <main className="min-h-screen bg-slate-100 p-8">
      <div className="mx-auto max-w-2xl">
        <h1 className="mb-8 text-2xl font-bold text-slate-900">
          습관 인증 모달 테스트
        </h1>

        <div className="flex flex-col gap-4">
          {/* 인증 등록 모달 */}
          <button
            type="button"
            onClick={() => setIsVerifyModalOpen(true)}
            className="rounded-xl bg-forest-500 px-6 py-3 font-bold text-lime-400 hover:bg-forest-600"
          >
            오늘 인증하기
          </button>

          {/* 인증 상세 모달 */}
          <button
            type="button"
            onClick={() => setIsDetailModalOpen(true)}
            className="rounded-xl bg-slate-800 px-6 py-3 font-bold text-white hover:bg-slate-700"
          >
            인증 상세 보기
          </button>
        </div>
      </div>

      {/* 인증 등록 모달 */}
      <HabitVerifyModal
        isOpen={isVerifyModalOpen}
        onClose={() => setIsVerifyModalOpen(false)}
        habitId={1}
        onSubmit={handleVerifySubmit}
      />

      {/* 인증 상세 모달 */}
      <HabitVerifyDetailModal
        isOpen={isDetailModalOpen}
        onClose={() => setIsDetailModalOpen(false)}
        verification={testVerification}
      />
    </main>
  );
}