'use client';

import { useEffect } from 'react';

interface PenaltyDetailModalProps {
  isOpen: boolean;
  onClose: () => void;
  habitTitle: string;
  penaltyText: string;
  imageUrl: string;
  description: string;
  verifyDate: string;
  onApprove?: () => void;
  onReject?: () => void;
}

export default function PenaltyDetailModal({
                                             isOpen,
                                             onClose,
                                             habitTitle,
                                             penaltyText,
                                             imageUrl,
                                             description,
                                             verifyDate,
                                             onApprove,
                                             onReject,
                                           }: PenaltyDetailModalProps) {
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

  if (!isOpen) return null;

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
            aria-labelledby="penalty-detail-title"
            className="relative my-6 w-full max-w-lg rounded-3xl border border-slate-100 bg-white px-8 py-7 shadow-2xl sm:px-10 sm:py-8"
        >
          <button
              type="button"
              onClick={onClose}
              aria-label="벌칙 상세 모달 닫기"
              className="absolute right-6 top-6 rounded-full px-2 text-2xl leading-none text-slate-400 hover:bg-slate-100 transition-colors cursor-pointer"
          >
            ×
          </button>

          <div className="flex justify-center pt-1">
            <div className="inline-flex items-center gap-1.5 rounded-full bg-rose-50 px-3 py-1 border border-rose-200/60">
              <span className="text-xs font-bold text-rose-600">벌칙 :</span>
              <span className="text-xs font-extrabold text-rose-900">{penaltyText || '벌칙 미지정'}</span>
            </div>
          </div>

          <div className="mt-4 space-y-7">
            <p
                id="penalty-detail-title"
                className="text-center text-xl font-bold leading-8 text-slate-800"
            >
              {habitTitle}
            </p>

            <div className="flex items-center justify-center overflow-hidden rounded-2xl border border-slate-200 bg-slate-50">
              {imageUrl ? (
                  <img
                      src={imageUrl}
                      alt="벌칙 인증 사진"
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

            <p className="px-2 text-center text-lg font-semibold leading-8 text-slate-700 whitespace-pre-wrap">
              {description || '작성된 벌칙 수행 내용이 없습니다.'}
            </p>

            <p className="pt-2 text-center text-base font-semibold text-slate-700">
              인증 날짜: {verifyDate}
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