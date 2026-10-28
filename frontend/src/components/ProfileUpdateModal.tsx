'use client';

import { useEffect, useRef } from 'react';

export function ProfileUpdateModal({
  isOpen,
  type,
  message,
  onClose,
}: {
  isOpen: boolean;
  type: 'success' | 'error';
  message: string;
  onClose: () => void;
}) {
  const closeButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!isOpen) return;
    closeButtonRef.current?.focus();
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  const isSuccess = type === 'success';

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-sm"
      onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}
    >
      <div
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="profile-update-result-title"
        aria-describedby="profile-update-result-message"
        className="w-full max-w-sm rounded-3xl border border-slate-100 bg-white p-7 text-center shadow-2xl"
      >
        <div className={`mx-auto flex h-12 w-12 items-center justify-center rounded-full text-xl font-black ${
          isSuccess ? 'bg-forest-100 text-forest-600' : 'bg-rose-100 text-rose-600'
        }`}>
          {isSuccess ? '✓' : '!'}
        </div>
        <h2 id="profile-update-result-title" className="mt-4 text-lg font-extrabold text-slate-900">
          {isSuccess ? '저장 완료' : '저장 실패'}
        </h2>
        <p id="profile-update-result-message" className="mt-2 text-sm leading-6 text-slate-600">{message}</p>
        <button
          ref={closeButtonRef}
          type="button"
          onClick={onClose}
          className={`mt-6 w-full rounded-xl px-5 py-3 text-sm font-bold ${
            isSuccess ? 'bg-forest-500 text-lime-400 hover:bg-forest-600' : 'bg-rose-500 text-white hover:bg-rose-600'
          }`}
        >
          확인
        </button>
      </div>
    </div>
  );
}
