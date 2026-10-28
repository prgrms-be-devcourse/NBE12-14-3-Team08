'use client';

import { useEffect, useRef, useState } from 'react';

export function DeleteAccountModal({
  isOpen,
  onClose,
  onConfirm,
}: {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => Promise<void>;
}) {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');
  const cancelButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!isOpen) return;
    const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    cancelButtonRef.current?.focus();
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !isSubmitting) onClose();
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      previousFocus?.focus();
    };
  }, [isOpen, isSubmitting, onClose]);

  if (!isOpen) return null;

  const handleConfirm = async () => {
    setError('');
    setIsSubmitting(true);
    try {
      await onConfirm();
      onClose();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : '회원탈퇴를 완료하지 못했습니다.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/55 p-4 backdrop-blur-sm"
      onMouseDown={(event) => { if (event.target === event.currentTarget && !isSubmitting) onClose(); }}>
      <div role="dialog" aria-modal="true" aria-labelledby="delete-account-title"
        aria-describedby="delete-account-description"
        className="w-full max-w-lg rounded-3xl border border-slate-200 bg-white p-7 shadow-2xl sm:p-9">
        <p className="text-center text-2xl font-black tracking-tight text-slate-900">내기? 내기!</p>
        <h2 id="delete-account-title" className="mt-8 text-center text-lg font-bold text-slate-900 sm:text-xl">
          <span className="text-rose-600">회원탈퇴</span>를 진행하시겠습니까?
        </h2>
        <div id="delete-account-description" className="mt-5 text-sm leading-6 text-slate-600">
          <p className="text-center">회원탈퇴를 하면 계정을 복구할 수 없습니다.</p>
          <ul className="mt-4 list-disc space-y-1.5 rounded-2xl bg-slate-50 py-4 pl-9 pr-5 text-left text-xs leading-5">
            <li>아이디, 비밀번호, 연결된 구글 계정 정보는 삭제됩니다.</li>
            <li>같은 아이디나 구글 계정으로 다시 가입할 수 있지만, 이전 기록은 이어지지 않는 새 계정이 만들어집니다.</li>
            <li>참여했던 방의 인증 사진·내용과 벌칙 기록, 닉네임은 방 기록을 위해 남습니다.</li>
          </ul>
        </div>
        {error && <p role="alert" className="mt-4 text-center text-sm font-semibold text-rose-600">{error}</p>}
        <div className="mt-9 flex items-center justify-between gap-3">
          <button type="button" onClick={handleConfirm} disabled={isSubmitting}
            className="rounded-xl border border-rose-200 bg-rose-50 px-5 py-2.5 text-sm font-bold text-rose-600 transition-colors hover:bg-rose-100 disabled:cursor-wait disabled:opacity-60">
            {isSubmitting ? '처리 중...' : '회원 탈퇴'}
          </button>
          <button ref={cancelButtonRef} type="button" onClick={onClose} disabled={isSubmitting}
            className="rounded-xl bg-emerald-500 px-5 py-2.5 text-sm font-bold text-white shadow-sm transition-colors hover:bg-emerald-600 disabled:opacity-60">
            돌아가기
          </button>
        </div>
      </div>
    </div>
  );
}
