'use client';

import { useEffect } from 'react';

interface HabitVerifyDetailModalProps {
  isOpen: boolean;
  onClose: () => void;
  verification: {
    id: number;
    habitId: number;
    habitTitle: string;
    verifyDate: string;
    status: 'PENDING' | 'APPROVED' | 'REJECTED';
    description: string;
    imageUrl: string;
  };
  onApprove?: () => void;
  onReject?: () => void;
}

export function HabitVerifyDetailModal({
                                         isOpen,
                                         onClose,
                                         verification,
                                         onApprove,
                                         onReject,
                                       }: HabitVerifyDetailModalProps) {
  useEffect(() => {
    if (!isOpen) return;

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose();
      }
    };

    document.addEventListener('keydown', handleKeyDown);

    return () => {
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [isOpen, onClose]);

  if (!isOpen) {
    return null;
  }

  return (
      <div
          className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-slate-900/55 p-4 backdrop-blur-sm"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) {
              onClose();
            }
          }}
      >
        <div
            role="dialog"
            aria-modal="true"
            aria-labelledby="habit-verify-detail-title"
            className="my-6 w-full max-w-lg rounded-3xl border border-slate-100 bg-white px-8 py-7 shadow-2xl sm:px-10 sm:py-8"
        >
          <div className="flex items-center justify-end">
            <button
                type="button"
                onClick={onClose}
                aria-label="인증 상세 모달 닫기"
                className="rounded-full px-2 text-2xl leading-none text-slate-400 hover:bg-slate-100 transition-colors"
            >
              ×
            </button>
          </div>

          <div className="mt-2 space-y-7">
            <p
                id="habit-verify-detail-title"
                className="text-center text-xl font-bold leading-8 text-slate-800"
            >
              {verification.habitTitle}
            </p>

            <div className="flex items-center justify-center overflow-hidden rounded-2xl border border-slate-200 bg-slate-50">
              {verification.imageUrl ? (
                  <img
                      src={verification.imageUrl}
                      alt="습관 인증 사진"
                      className="max-h-80 w-full object-cover"
                  />
              ) : (
                  <div className="flex h-72 w-full items-center justify-center">
                <span className="text-sm text-slate-400">
                  등록된 인증 사진이 없습니다.
                </span>
                  </div>
              )}
            </div>

            <p className="px-2 text-center text-lg font-semibold leading-8 text-slate-700">
              {verification.description || '작성된 인증 내용이 없습니다.'}
            </p>

            <p className="pt-2 text-center text-base font-semibold text-slate-700">
              인증 날짜: {verification.verifyDate}
            </p>

            {(onApprove || onReject) && (
                <div className="flex items-center gap-3 pt-4">
                  {onApprove && (
                      <button
                          type="button"
                          onClick={() => {
                            onApprove();
                            onClose();
                          }}
                          className="flex-1 py-3 rounded-xl bg-forest-500 hover:bg-forest-600 text-lime-400 font-bold text-sm shadow-md shadow-forest-500/20 transition-colors cursor-pointer"
                      >
                        승인
                      </button>
                  )}
                  {onReject && (
                      <button
                          type="button"
                          onClick={() => {
                            onReject();
                            onClose();
                          }}
                          className="flex-1 py-3 rounded-xl border border-rose-200 bg-rose-50 hover:bg-rose-100 text-rose-600 font-bold text-sm transition-colors cursor-pointer"
                      >
                        거절
                      </button>
                  )}
                </div>
            )}
          </div>
        </div>
      </div>
  );
}