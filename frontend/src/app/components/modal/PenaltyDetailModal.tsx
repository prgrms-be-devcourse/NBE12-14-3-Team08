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
            className="relative my-6 w-full max-w-md rounded-[28px] bg-white p-6 shadow-[0_30px_70px_rgba(0,0,0,0.3)]"
        >
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
              <h2 id="penalty-detail-title" className="text-lg font-extrabold tracking-tight text-slate-900">
                {habitTitle}
              </h2>
              <p className="mt-0.5 text-xs text-slate-500">
                {penaltyText === '습관 수행 인증' ? penaltyText : `벌칙: ${penaltyText || '벌칙 미지정'}`}
              </p>
            </div>
            <button
                type="button"
                onClick={onClose}
                aria-label="상세 모달 닫기"
                className="rounded-[10px] px-2 py-1 text-xl leading-none text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-900 cursor-pointer"
            >
              ×
            </button>
          </div>

          <div className="my-4 flex items-center justify-center overflow-hidden rounded-[22px] bg-slate-50">
            {imageUrl ? (
                <img
                    src={imageUrl}
                    alt="인증 사진"
                    className="max-h-80 w-full object-cover"
                />
            ) : (
                <div className="flex h-56 w-full items-center justify-center">
                  <span className="text-sm text-slate-400">등록된 인증 사진이 없습니다.</span>
                </div>
            )}
          </div>

          <div className="mb-2.5 rounded-2xl bg-slate-50 px-3.5 py-3 text-xs">
            <b className="mb-0.5 block text-[11px] font-bold text-slate-400">인증 내용</b>
            <span className="whitespace-pre-wrap text-slate-800">
              {description || '작성된 인증 내용이 없습니다.'}
            </span>
          </div>
          <div className="rounded-2xl bg-slate-50 px-3.5 py-3 text-xs">
            <b className="mb-0.5 block text-[11px] font-bold text-slate-400">인증 날짜</b>
            <span className="text-slate-800">{verifyDate}</span>
          </div>

          {(onApprove || onReject) && (
              <div className="mt-4 flex items-center gap-2.5">
                {onApprove && (
                    <button
                        type="button"
                        onClick={() => {
                          onApprove();
                          onClose();
                        }}
                        className="flex-1 cursor-pointer rounded-2xl bg-forest-500 py-3 text-sm font-extrabold text-lime-400 transition-colors hover:bg-forest-800"
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
                        className="flex-1 cursor-pointer rounded-2xl border border-slate-200 bg-white py-3 text-sm font-bold text-slate-500 transition-colors hover:border-rose-200 hover:bg-rose-50 hover:text-rose-600"
                    >
                      거절
                    </button>
                )}
              </div>
          )}
        </div>
      </div>
  );
}