'use client';

import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import Link from 'next/link';
import { ArrowRight, ChevronLeft, ChevronRight, X } from 'lucide-react';
import type { GroupPhoto } from '@/lib/habitApi';
import { formatShortDate } from '@/lib/photoLibrary';
import { useMember } from '@/context/MemberContext';
import { ImageLightbox } from './ModalKit';
import { ReactionCommentSection, type EngagementSnapshot } from './ReactionCommentSection';

interface PhotoDetailPanelProps {
  groupId: number | string;
  photo: GroupPhoto;
  // 앞뒤 사진으로 갈 수 없으면 null이고, 그 버튼은 비활성이 된다.
  onPrev: (() => void) | null;
  onNext: (() => void) | null;
  onClose: () => void;
  // 종료된 방에서는 false: 하트·댓글을 읽기만 할 수 있다.
  writable: boolean;
  onEngagementChange?: (snapshot: EngagementSnapshot) => void;
}

const iconButtonClass =
  'flex h-11 w-11 items-center justify-center rounded-xl text-slate-600 transition hover:bg-slate-100 disabled:cursor-default disabled:opacity-35 disabled:hover:bg-transparent cursor-pointer';

// 사진 라이브러리에서 사진을 눌렀을 때 오른쪽에서 열리는 상세 패널.
// 넓은 화면(lg 이상)에서는 페이지 본문을 밀지 않고 그 위에 겹쳐 오른쪽에 붙으며, 패널 밖의
// 사진은 계속 눌러 고를 수 있다. 좁은 화면에서는 화면 전체를 덮는다. ← → 로 앞뒤 사진, Esc로 닫기,
// 사진을 누르면 크게 본다.
export function PhotoDetailPanel({ groupId, photo, onPrev, onNext, onClose, writable, onEngagementChange }: PhotoDetailPanelProps) {
  const { currentUser } = useMember();
  const [expanded, setExpanded] = useState(false);
  const closeRef = useRef<HTMLButtonElement>(null);
  const bodyRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    closeRef.current?.focus({ preventScroll: true });
  }, []);

  // 사진을 넘기면 글 내용을 맨 위부터 보여 준다.
  useEffect(() => {
    bodyRef.current?.scrollTo({ top: 0 });
  }, [photo.id]);

  // 화면 전체를 덮는 좁은 화면에서는 뒤의 페이지가 같이 스크롤되지 않게 한다.
  useEffect(() => {
    if (!window.matchMedia('(max-width: 1023px)').matches) return;
    const previous = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = previous;
    };
  }, []);

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.defaultPrevented || event.metaKey || event.ctrlKey || event.altKey) return;

      // 글을 입력하는 중의 방향키·Esc는 건드리지 않는다.
      const target = event.target;
      if (target instanceof HTMLElement && (target.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(target.tagName))) {
        return;
      }

      if (event.key === 'Escape') {
        if (expanded) setExpanded(false);
        else onClose();
      } else if (event.key === 'ArrowLeft' && onPrev) {
        event.preventDefault();
        onPrev();
      } else if (event.key === 'ArrowRight' && onNext) {
        event.preventDefault();
        onNext();
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [expanded, onClose, onPrev, onNext]);

  const altText = `${photo.memberNickname}의 ${photo.habitTitle} 인증 사진`;

  return (
    <aside
      aria-label="인증 사진 상세"
      className="anim-panel-in fixed inset-0 z-[60] flex flex-col bg-white lg:inset-auto lg:bottom-0 lg:right-0 lg:top-16 lg:z-40 lg:w-[520px] xl:w-[600px] lg:border-l lg:border-slate-200 lg:shadow-[-16px_0_32px_-12px_rgba(15,23,42,0.12)]"
    >
      <div className="flex h-14 shrink-0 items-center border-b border-slate-100 pl-2 pr-2">
        <button type="button" onClick={onPrev ?? undefined} disabled={!onPrev} aria-label="이전 사진" className={iconButtonClass}>
          <ChevronLeft className="h-5 w-5" strokeWidth={2.5} />
        </button>
        <button type="button" onClick={onNext ?? undefined} disabled={!onNext} aria-label="다음 사진" className={iconButtonClass}>
          <ChevronRight className="h-5 w-5" strokeWidth={2.5} />
        </button>
        <span className="flex-1" />
        <button ref={closeRef} type="button" onClick={onClose} aria-label="상세 닫기" className={iconButtonClass}>
          <X className="h-5 w-5" strokeWidth={2.5} />
        </button>
      </div>

      <div className="h-[min(52vh,480px)] shrink-0 bg-slate-100">
        <button
          type="button"
          onClick={() => setExpanded(true)}
          aria-label="사진 크게 보기"
          className="flex h-full w-full cursor-zoom-in items-center justify-center"
        >
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img key={photo.id} src={photo.imageUrl} alt={altText} className="max-h-full max-w-full object-contain" />
        </button>
      </div>

      <div ref={bodyRef} className="min-h-0 flex-1 overflow-y-auto px-6 py-5">
        <div className="flex items-center justify-between gap-3">
          <span className="flex min-w-0 items-center gap-2 text-sm font-extrabold text-slate-900">
            <span className="flex h-7 w-7 flex-none items-center justify-center rounded-lg border border-forest-100 bg-forest-50 text-xs font-black text-forest-700">
              {photo.memberNickname.slice(0, 1)}
            </span>
            <span className="truncate">{photo.memberNickname}</span>
          </span>
          <span className="flex-none text-xs font-semibold text-slate-500">{formatShortDate(photo.verifyDate)}</span>
        </div>

        <h2 className="mt-4 text-sm font-extrabold text-forest-700">{photo.habitTitle}</h2>
        <p className={`mt-1.5 whitespace-pre-wrap break-words text-sm leading-6 ${photo.description ? 'text-slate-700' : 'text-slate-500'}`}>
          {photo.description || '작성된 인증 내용이 없습니다.'}
        </p>

        <div className="-mx-6 mt-5">
          <ReactionCommentSection
            kind="habit"
            targetId={photo.id}
            isOwnVerify={currentUser?.id === String(photo.memberId)}
            writable={writable}
            onChange={onEngagementChange}
          />
        </div>
      </div>

      <div className="shrink-0 border-t border-slate-100 px-6 py-4">
        <Link
          href={`/group/${groupId}/member/${photo.memberId}`}
          className="flex h-11 items-center justify-center gap-1.5 rounded-2xl border border-slate-200 bg-slate-50 text-xs font-extrabold text-slate-700 transition hover:bg-slate-100"
        >
          {photo.memberNickname}님의 인증 더 보기
          <ArrowRight className="h-4 w-4" />
        </Link>
      </div>

      {expanded &&
        createPortal(<ImageLightbox src={photo.imageUrl} alt={altText} onClose={() => setExpanded(false)} />, document.body)}
    </aside>
  );
}
