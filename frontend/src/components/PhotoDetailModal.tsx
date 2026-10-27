'use client';

import { useEffect, useRef } from 'react';
import Link from 'next/link';
import type { GroupPhoto } from '@/lib/habitApi';
import { formatShortDate } from '@/lib/photoLibrary';

interface PhotoDetailModalProps {
  groupId: number | string;
  photo: GroupPhoto;
  onClose: () => void;
}

const FOCUSABLE = 'a[href], button:not([disabled])';

// 사진 라이브러리에서 사진을 눌렀을 때 뜨는 상세. Esc로 닫고, Tab 이동은 모달 안에 가둔다.
export function PhotoDetailModal({ groupId, photo, onClose }: PhotoDetailModalProps) {
  const dialogRef = useRef<HTMLDivElement>(null);
  const closeRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    closeRef.current?.focus();

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose();
        return;
      }
      if (event.key !== 'Tab') return;

      const focusable = dialogRef.current?.querySelectorAll<HTMLElement>(FOCUSABLE);
      if (!focusable || focusable.length === 0) return;

      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-slate-900/60 p-4 backdrop-blur-sm"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="photo-detail-title"
        className="my-6 w-full max-w-lg overflow-hidden rounded-3xl border border-slate-100 bg-white shadow-2xl"
      >
        <div className="flex max-h-[60vh] items-center justify-center bg-slate-100">
          <img
            src={photo.imageUrl}
            alt={`${photo.memberNickname}의 ${photo.habitTitle} 인증 사진`}
            className="max-h-[60vh] w-full object-contain"
          />
        </div>

        <div className="space-y-3 px-6 py-5">
          <div className="flex items-center justify-between gap-3">
            <Link
              href={`/group/${groupId}/member/${photo.memberId}`}
              className="flex min-w-0 items-center gap-2 text-sm font-extrabold text-slate-900 hover:text-emerald-600 transition-colors"
            >
              <span className="flex h-7 w-7 flex-none items-center justify-center rounded-lg border border-emerald-100 bg-emerald-50 text-xs font-black text-emerald-600">
                {photo.memberNickname.slice(0, 1)}
              </span>
              <span className="truncate">{photo.memberNickname}</span>
            </Link>
            <span className="flex-none text-xs font-semibold text-slate-400">
              {formatShortDate(photo.verifyDate)}
            </span>
          </div>

          <div>
            <p id="photo-detail-title" className="text-sm font-extrabold text-emerald-600">
              {photo.habitTitle}
            </p>
            <p className="mt-1 whitespace-pre-wrap break-words text-sm leading-6 text-slate-700">
              {photo.description || '작성된 인증 내용이 없습니다.'}
            </p>
          </div>

          <div className="flex justify-end pt-1">
            <button
              ref={closeRef}
              type="button"
              onClick={onClose}
              className="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-2 text-xs font-bold text-slate-700 hover:bg-slate-100 transition cursor-pointer"
            >
              닫기
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
