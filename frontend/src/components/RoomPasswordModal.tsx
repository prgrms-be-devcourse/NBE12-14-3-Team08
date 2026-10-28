'use client';

import { useEffect, useRef, useState } from 'react';

export function RoomPasswordModal({
  isOpen,
  onClose,
  roomTitle,
  roomDescription,
  onSubmit,
}: {
  isOpen: boolean;
  onClose: () => void;
  roomTitle: string;
  roomDescription: string;
  onSubmit: (password: string) => Promise<void>;
}) {
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!isOpen) {
      setPassword('');
      setError('');
      return;
    }
    const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    inputRef.current?.focus();
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

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError('');
    setIsSubmitting(true);
    try {
      await onSubmit(password);
      onClose();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : '인증하지 못했습니다.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-xs"
      onMouseDown={(event) => { if (event.target === event.currentTarget && !isSubmitting) onClose(); }}>
      <div role="dialog" aria-modal="true" aria-labelledby="invite-password-title"
        className="w-full max-w-sm rounded-3xl border border-slate-100 bg-white p-6 shadow-2xl">
        <div className="flex items-center justify-between border-b border-slate-100 pb-3">
          <h2 id="invite-password-title" className="text-base font-bold text-slate-900">인증 비밀번호 입력</h2>
          <button type="button" onClick={onClose} disabled={isSubmitting} aria-label="모달 닫기"
            className="rounded-full p-1 text-xl leading-none text-slate-400 hover:bg-slate-100">×</button>
        </div>
        <form onSubmit={handleSubmit} className="mt-4 space-y-4">
          <div className="rounded-2xl border border-slate-100 bg-slate-50 py-3 text-center">
            <p className="text-sm font-bold text-slate-800">{roomTitle}</p>
            <p className="mt-0.5 text-xs text-slate-500">{roomDescription}</p>
          </div>
          <div>
          <label htmlFor="invite-room-password" className="mb-1 block text-xs font-bold text-slate-700">인증 비밀번호</label>
          <input ref={inputRef} id="invite-room-password" type="password" autoComplete="off" required
            value={password} onChange={(event) => { setPassword(event.target.value); setError(''); }}
            placeholder="비밀번호를 입력하세요"
            aria-invalid={Boolean(error)} aria-describedby={error ? 'invite-room-password-error' : undefined}
            className="w-full rounded-xl border border-slate-200 px-4 py-2.5 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-forest-500" />
          {error && <p id="invite-room-password-error" role="alert" className="mt-1 text-xs font-semibold text-rose-500">{error}</p>}
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <button type="button" onClick={onClose} disabled={isSubmitting}
              className="rounded-xl px-4 py-2 text-xs font-bold text-slate-600 hover:bg-slate-100">취소</button>
            <button type="submit" disabled={isSubmitting}
              className="rounded-xl bg-forest-500 px-5 py-2 text-xs font-bold text-lime-400 shadow-sm hover:bg-forest-600 disabled:cursor-wait disabled:opacity-60">
              {isSubmitting ? '확인 중...' : '인증하기'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
