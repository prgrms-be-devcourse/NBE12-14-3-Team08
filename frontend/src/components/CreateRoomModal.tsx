'use client';

import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import type { CreateGroupRequest } from '../lib/groupApi';

const initialForm: CreateGroupRequest = {
  title: '',
  description: '',
  deadline: '',
  penalty: '',
  password: '',
  memberLimit: 2,
};

const inputClassName = 'w-full rounded-xl border border-slate-200 px-4 py-2.5 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-forest-500';

export function CreateRoomModal({
  isOpen,
  onClose,
  onCreate,
}: {
  isOpen: boolean;
  onClose: () => void;
  onCreate: (request: CreateGroupRequest) => Promise<void>;
}) {
  const [form, setForm] = useState(initialForm);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!isOpen) return;
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !isSubmitting) onClose();
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, isSubmitting, onClose]);

  if (!isOpen) return null;

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError('');
    setIsSubmitting(true);
    try {
      await onCreate(form);
      setForm(initialForm);
      onClose();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : '방을 만들지 못했습니다.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const today = new Date().toISOString().slice(0, 10);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-slate-900/55 p-4 backdrop-blur-sm"
      onMouseDown={(event) => { if (event.target === event.currentTarget && !isSubmitting) onClose(); }}>
      <div role="dialog" aria-modal="true" aria-labelledby="create-room-title"
        className="my-6 w-full max-w-lg rounded-3xl border border-slate-100 bg-white p-6 shadow-2xl sm:p-8">
        <div className="flex items-center justify-between border-b border-slate-100 pb-4">
          <h2 id="create-room-title" className="text-xl font-extrabold text-slate-900">새 방 만들기</h2>
          <button type="button" onClick={onClose} disabled={isSubmitting} aria-label="방 만들기 닫기"
            className="rounded-full p-1 text-2xl leading-none text-slate-400 hover:bg-slate-100">×</button>
        </div>

        <form onSubmit={handleSubmit} className="mt-5 space-y-4">
          <Field label="방 이름" required>
            <input autoFocus required value={form.title} onChange={(event) => setForm({ ...form, title: event.target.value })}
              className={inputClassName} placeholder="함께 도전할 방 이름" />
          </Field>
          <Field label="방 설명">
            <textarea value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })}
              className={`${inputClassName} min-h-20 resize-none`} placeholder="방의 목표를 소개해주세요" />
          </Field>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Field label="종료일" required>
              <input type="date" required min={today} value={form.deadline}
                onChange={(event) => setForm({ ...form, deadline: event.target.value })} className={inputClassName} />
            </Field>
            <Field label="최대 인원" required>
              <input type="number" required min={1} value={form.memberLimit}
                onChange={(event) => setForm({ ...form, memberLimit: Number(event.target.value) })} className={inputClassName} />
            </Field>
          </div>
          <Field label="실패 시 벌칙">
            <input value={form.penalty} onChange={(event) => setForm({ ...form, penalty: event.target.value })}
              className={inputClassName} placeholder="예: 커피 사기" />
          </Field>
          <Field label="방 비밀번호" required>
            <input type="password" required autoComplete="new-password" value={form.password}
              onChange={(event) => setForm({ ...form, password: event.target.value })}
              className={inputClassName} placeholder="초대받은 사람이 입력할 비밀번호" />
          </Field>

          {error && <p role="alert" className="text-sm font-semibold text-rose-600">{error}</p>}
          <div className="flex justify-end gap-2 pt-3">
            <button type="button" onClick={onClose} disabled={isSubmitting}
              className="rounded-xl px-5 py-2.5 text-sm font-bold text-slate-600 hover:bg-slate-100">취소</button>
            <button type="submit" disabled={isSubmitting}
              className="rounded-xl bg-forest-500 px-6 py-2.5 text-sm font-bold text-lime-400 shadow-md shadow-forest-500/20 hover:bg-forest-600 disabled:cursor-wait disabled:opacity-60">
              {isSubmitting ? '만드는 중...' : '방 만들기'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

function Field({ label, required = false, children }: { label: string; required?: boolean; children: React.ReactNode }) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-sm font-bold text-slate-700">
        {label}{required && <span className="ml-1 text-rose-500">*</span>}
      </span>
      {children}
    </label>
  );
}
