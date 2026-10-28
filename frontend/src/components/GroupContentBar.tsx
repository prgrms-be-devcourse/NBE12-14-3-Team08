'use client';

import type { KeyboardEvent, ReactNode } from 'react';

export type GroupView = 'photos' | 'members';

interface GroupContentBarProps {
  view: GroupView;
  onViewChange: (view: GroupView) => void;
  memberCount: number;
  // 아이콘 탭 왼쪽·오른쪽에 놓을 내용. 없으면 아이콘 탭만 가운데에 남는다.
  left?: ReactNode;
  right?: ReactNode;
}

// 탭 구분선 위에 겹쳐 보이는 활성 표시(인스타그램 프로필 탭과 같은 방식)
const tabClass = (active: boolean) =>
  `relative flex h-14 w-24 cursor-pointer items-center justify-center transition-colors @max-[560px]:w-auto @max-[560px]:flex-1 after:absolute after:-bottom-px after:left-1/2 after:h-0.5 after:-translate-x-1/2 after:rounded-full after:bg-slate-900 after:transition-all ${
    active ? 'text-slate-900 after:w-16' : 'text-slate-400 after:w-0 hover:text-slate-500'
  }`;

// 그룹 메인 카드의 맨 윗줄. 가운데에 인증 사진/멤버를 고르는 아이콘 탭이 있고,
// 사진 보기에서는 왼쪽에 지금 보는 년/월/주, 오른쪽에 멤버 필터가 놓인다.
// 좁은 카드에서는 아이콘 탭이 윗줄 전체를 차지하고 나머지는 아랫줄로 내려간다.
// (부모에 @container를 줘야 카드 폭에 맞춰 바뀐다)
export function GroupContentBar({ view, onViewChange, memberCount, left, right }: GroupContentBarProps) {
  const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
    event.preventDefault();
    const next: GroupView = view === 'photos' ? 'members' : 'photos';
    onViewChange(next);
    event.currentTarget.querySelector<HTMLElement>(`[data-view="${next}"]`)?.focus();
  };

  return (
    <div className="grid grid-cols-[1fr_auto_1fr] items-center border-b border-slate-200 px-4 @max-[560px]:grid-cols-[1fr_auto] @max-[560px]:border-b-0 @max-[560px]:px-0">
      {left && (
        <div className="col-start-1 row-start-1 min-w-0 @max-[560px]:row-start-2 @max-[560px]:px-3.5 @max-[560px]:py-2.5">
          {left}
        </div>
      )}

      <div
        role="tablist"
        aria-label="보기 전환"
        onKeyDown={handleKeyDown}
        className="col-start-2 row-start-1 flex justify-self-center @max-[560px]:col-span-full @max-[560px]:col-start-1 @max-[560px]:w-full @max-[560px]:border-b @max-[560px]:border-slate-200"
      >
        <button
          type="button"
          role="tab"
          data-view="members"
          aria-selected={view === 'members'}
          aria-controls="group-view-panel"
          tabIndex={view === 'members' ? 0 : -1}
          aria-label={`멤버 (${memberCount})`}
          title={`멤버 (${memberCount})`}
          onClick={() => onViewChange('members')}
          className={tabClass(view === 'members')}
        >
          {/* 사람이 들어 있는 사각 프레임 아이콘 */}
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.9"
            strokeLinecap="round"
            strokeLinejoin="round"
            className="h-6 w-6"
            aria-hidden="true"
          >
            <rect x="3.5" y="3.5" width="17" height="17" rx="4" />
            <circle cx="12" cy="10" r="2.8" />
            <path d="M6.8 19c.7-2.7 2.7-4.2 5.2-4.2s4.5 1.5 5.2 4.2" />
          </svg>
        </button>

        <button
          type="button"
          role="tab"
          data-view="photos"
          aria-selected={view === 'photos'}
          aria-controls="group-view-panel"
          tabIndex={view === 'photos' ? 0 : -1}
          aria-label="인증 사진"
          title="인증 사진"
          onClick={() => onViewChange('photos')}
          className={tabClass(view === 'photos')}
        >
          {/* 격자(3×3) 아이콘 */}
          <svg viewBox="0 0 24 24" fill="currentColor" className="h-6 w-6" aria-hidden="true">
            <path d="M3 3h5v5H3zM9.5 3h5v5h-5zM16 3h5v5h-5zM3 9.5h5v5H3zM9.5 9.5h5v5h-5zM16 9.5h5v5h-5zM3 16h5v5H3zM9.5 16h5v5h-5zM16 16h5v5h-5z" />
          </svg>
        </button>
      </div>

      {right && (
        <div className="col-start-3 row-start-1 justify-self-end @max-[560px]:col-start-2 @max-[560px]:row-start-2 @max-[560px]:px-3.5 @max-[560px]:py-2.5">
          {right}
        </div>
      )}
    </div>
  );
}
