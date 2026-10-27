'use client';

import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { habitApi, type GroupPhoto } from '@/lib/habitApi';
import {
  DEFAULT_PHOTO_COLUMNS,
  PHOTO_GAP,
  PHOTO_LEVELS,
  chunkRows,
  formatShortDate,
  formatYearMonthWeek,
  monthStartRows,
  normalizeDate,
  placeMonthLabels,
  rowRatio,
  stepColumns,
  type PhotoColumns,
} from '@/lib/photoLibrary';
import { PhotoDetailPanel } from './PhotoDetailPanel';

interface GroupPhotoLibraryProps {
  groupId: number | string;
  members: { memberId: number; nickname: string }[];
}

// 상세 패널에서 다음 사진으로 넘기다 끝에 닿기 전에 미리 다음 페이지를 불러올 남은 장 수
const PREFETCH_REMAINING = 6;

interface LoadedPhotos {
  memberId: number | null;
  items: GroupPhoto[];
  nextCursor: string | null;
  error: string | null;
  moreError: string | null;
}

interface ScrollAnchor {
  id: number;
  date: string;
}

const PAGE_SIZE = 60;

// 칸 위에 마우스를 올렸을 때 보이는 글자 크기. 칸이 작을수록 항목을 줄인다.
const WHO_CLASS: Record<PhotoColumns, string> = {
  1: 'text-xl',
  3: 'text-sm',
  5: 'text-xs',
  15: 'text-[7px] leading-[1.1]',
};
const WHAT_CLASS: Record<PhotoColumns, string> = {
  1: 'text-[15px]',
  3: 'text-xs',
  5: 'text-[10px]',
  15: 'hidden',
};
const WHEN_CLASS: Record<PhotoColumns, string> = {
  1: 'text-[13px]',
  3: 'text-[11px]',
  5: 'text-[9px]',
  15: 'hidden',
};

function mergeById(current: GroupPhoto[], incoming: GroupPhoto[]): GroupPhoto[] {
  const seen = new Set(current.map((photo) => photo.id));
  return [...current, ...incoming.filter((photo) => !seen.has(photo.id))];
}

function normalizePhotos(items: GroupPhoto[]): GroupPhoto[] {
  return items.map((photo) => ({ ...photo, verifyDate: normalizeDate(photo.verifyDate) }));
}

function errorText(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback;
}

// 인증 사진을 아이폰 사진 앱처럼 모아 보는 라이브러리.
// 날짜 내림차순(최신이 위)이고, 한 줄에 1·3·5·15장 단계를 오가며 스크롤하면 다음 페이지를 불러온다.
export function GroupPhotoLibrary({ groupId, members }: GroupPhotoLibraryProps) {
  const [filter, setFilter] = useState<number | null>(null);
  const [columns, setColumns] = useState<PhotoColumns>(DEFAULT_PHOTO_COLUMNS);
  const [loaded, setLoaded] = useState<LoadedPhotos | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);
  const [periodLabel, setPeriodLabel] = useState('');
  const [selected, setSelected] = useState<GroupPhoto | null>(null);

  const frameRef = useRef<HTMLDivElement>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  const gridRef = useRef<HTMLDivElement>(null);
  const sentinelRef = useRef<HTMLDivElement>(null);
  const labelRefs = useRef<(HTMLSpanElement | null)[]>([]);
  const anchorRef = useRef<ScrollAnchor | null>(null);
  const loadingMoreRef = useRef(false);
  const lastFocusRef = useRef<HTMLElement | null>(null);

  // 갤러리 밖에 따로 그리는 스크롤바. 평소엔 숨기고, 마우스를 올리거나 스크롤할 때만 보인다.
  const trackRef = useRef<HTMLDivElement>(null);
  const thumbRef = useRef<HTMLDivElement>(null);
  const contentRef = useRef<HTMLDivElement>(null);
  const dragRef = useRef<{ startY: number; startTop: number } | null>(null);
  const hideTimerRef = useRef<number | null>(null);
  const [scrollActive, setScrollActive] = useState(false);
  const [dragging, setDragging] = useState(false);

  // 필터를 바꾸면 새 목록이 올 때까지 loaded.memberId와 filter가 달라 "불러오는 중"으로 본다.
  const current = loaded !== null && loaded.memberId === filter ? loaded : null;
  const items = useMemo(() => current?.items ?? [], [current]);
  const rows = useMemo(() => chunkRows(items, columns), [items, columns]);
  const monthStarts = useMemo(() => (columns === 15 ? monthStartRows(rows) : []), [columns, rows]);
  const hasMore = current !== null && current.nextCursor !== null && current.moreError === null;
  const selectedIndex = selected ? items.findIndex((photo) => photo.id === selected.id) : -1;

  // 첫 페이지: 그룹이나 멤버 필터가 바뀔 때마다 처음부터 다시 불러온다.
  useEffect(() => {
    let cancelled = false;

    habitApi
      .getGroupPhotos(groupId, { size: PAGE_SIZE, memberId: filter })
      .then((page) => {
        if (cancelled) return;
        setLoaded({
          memberId: filter,
          items: normalizePhotos(page.items),
          nextCursor: page.nextCursor,
          error: null,
          moreError: null,
        });
      })
      .catch((error) => {
        if (cancelled) return;
        setLoaded({
          memberId: filter,
          items: [],
          nextCursor: null,
          error: errorText(error, '인증 사진을 불러오지 못했습니다.'),
          moreError: null,
        });
      });

    return () => {
      cancelled = true;
    };
  }, [groupId, filter, reloadKey]);

  const loadMore = useCallback(async () => {
    if (loadingMoreRef.current || !current || !current.nextCursor || current.moreError) return;

    const requestFilter = filter;
    const cursor = current.nextCursor;
    loadingMoreRef.current = true;
    setLoadingMore(true);

    try {
      const page = await habitApi.getGroupPhotos(groupId, {
        size: PAGE_SIZE,
        cursor,
        memberId: requestFilter,
      });
      setLoaded((prev) =>
        prev && prev.memberId === requestFilter
          ? { ...prev, items: mergeById(prev.items, normalizePhotos(page.items)), nextCursor: page.nextCursor }
          : prev
      );
    } catch (error) {
      setLoaded((prev) =>
        prev && prev.memberId === requestFilter
          ? { ...prev, moreError: errorText(error, '인증 사진을 더 불러오지 못했습니다.') }
          : prev
      );
    } finally {
      loadingMoreRef.current = false;
      setLoadingMore(false);
    }
  }, [groupId, filter, current]);

  // 맨 아래 표식이 보이면 다음 페이지를 불러온다.
  useEffect(() => {
    const sentinel = sentinelRef.current;
    const root = scrollRef.current;
    if (!hasMore || !sentinel || !root) return;

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting) void loadMore();
      },
      { root, rootMargin: '400px' }
    );
    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [hasMore, loadMore, items.length]);

  // 화면 맨 위에 걸친 사진. 단계나 필터를 바꿔도 이 날짜 근처를 계속 보여 주기 위해 기억한다.
  const captureAnchor = useCallback((): ScrollAnchor | null => {
    const scroller = scrollRef.current;
    if (!scroller) return null;

    const top = scroller.getBoundingClientRect().top;
    const cells = scroller.querySelectorAll<HTMLElement>('[data-photo-id]');
    for (const cell of cells) {
      if (cell.getBoundingClientRect().bottom > top + 4) {
        return { id: Number(cell.dataset.photoId), date: cell.dataset.date ?? '' };
      }
    }
    return null;
  }, []);

  const updatePeriod = useCallback(() => {
    const anchor = captureAnchor();
    setPeriodLabel(anchor ? formatYearMonthWeek(anchor.date) : '');
  }, [captureAnchor]);

  // 단계나 필터가 바뀐 뒤, 기억해 둔 사진(없으면 그 날짜 근처)이 화면 맨 위에 오도록 스크롤한다.
  // 칸이 작아져 불러온 사진이 한 화면에 다 들어가면 아직 갈 수 없으므로, 위치에 닿거나 더 불러올 게 없을 때까지 기억해 둔다.
  const loadedMemberId = loaded?.memberId;
  useLayoutEffect(() => {
    const scroller = scrollRef.current;
    const anchor = anchorRef.current;

    if (scroller && anchor && items.length > 0) {
      const cells = scroller.querySelectorAll<HTMLElement>('[data-photo-id]');
      let target: HTMLElement | null = null;
      for (const cell of cells) {
        if (Number(cell.dataset.photoId) === anchor.id) {
          target = cell;
          break;
        }
      }
      if (!target) {
        // 같은 사진이 없으면 그 날짜 이전(같거나 더 오래된) 첫 사진으로 간다.
        for (const cell of cells) {
          if ((cell.dataset.date ?? '') <= anchor.date) {
            target = cell;
            break;
          }
        }
      }
      const row = target?.closest<HTMLElement>('[data-row]') ?? target;
      let reached = false;
      if (row) {
        scroller.scrollTop += row.getBoundingClientRect().top - scroller.getBoundingClientRect().top;
        reached = Math.abs(row.getBoundingClientRect().top - scroller.getBoundingClientRect().top) < 2;
      }
      if (reached || !hasMore) anchorRef.current = null;
    }

    updatePeriod();
  }, [columns, loadedMemberId, items.length, hasMore, updatePeriod]);

  // 사용자가 직접 스크롤하기 시작하면 기억해 둔 위치로 되돌리지 않는다.
  useEffect(() => {
    const scroller = scrollRef.current;
    if (!scroller) return;

    const forget = () => {
      anchorRef.current = null;
    };
    scroller.addEventListener('wheel', forget, { passive: true });
    scroller.addEventListener('touchstart', forget, { passive: true });
    scroller.addEventListener('pointerdown', forget, { passive: true });
    return () => {
      scroller.removeEventListener('wheel', forget);
      scroller.removeEventListener('touchstart', forget);
      scroller.removeEventListener('pointerdown', forget);
    };
  }, []);

  // 스크롤 위치와 내용 길이에 맞춰 막대(thumb)의 길이와 위치를 정한다. 스크롤할 내용이 없으면 막대를 숨긴다.
  const updateThumb = useCallback(() => {
    const scroller = scrollRef.current;
    const track = trackRef.current;
    const thumb = thumbRef.current;
    if (!scroller || !track || !thumb) return;

    const { scrollTop, scrollHeight, clientHeight } = scroller;
    const overflow = scrollHeight - clientHeight;
    if (overflow <= 1) {
      track.style.display = 'none';
      return;
    }

    track.style.display = '';
    const trackHeight = track.clientHeight;
    const thumbHeight = Math.max(40, Math.round((clientHeight / scrollHeight) * trackHeight));
    const top = Math.round((scrollTop / overflow) * (trackHeight - thumbHeight));
    thumb.style.height = `${thumbHeight}px`;
    thumb.style.transform = `translateY(${top}px)`;
  }, []);

  // 스크롤바를 보이고, 잠시 뒤 다시 숨긴다.
  const showScrollbar = useCallback(() => {
    setScrollActive(true);
    if (hideTimerRef.current !== null) window.clearTimeout(hideTimerRef.current);
    hideTimerRef.current = window.setTimeout(() => {
      hideTimerRef.current = null;
      setScrollActive(false);
    }, 900);
  }, []);

  useEffect(
    () => () => {
      if (hideTimerRef.current !== null) window.clearTimeout(hideTimerRef.current);
    },
    []
  );

  // 스크롤하면 좌상단의 현재 시기와 스크롤바를 갱신한다.
  useEffect(() => {
    const scroller = scrollRef.current;
    if (!scroller) return;

    let ticking = false;
    const handleScroll = () => {
      updateThumb();
      showScrollbar();
      if (ticking) return;
      ticking = true;
      requestAnimationFrame(() => {
        ticking = false;
        updatePeriod();
      });
    };

    scroller.addEventListener('scroll', handleScroll, { passive: true });
    return () => scroller.removeEventListener('scroll', handleScroll);
  }, [updatePeriod, updateThumb, showScrollbar]);

  // 사진을 더 불러오거나 단계·창 크기가 바뀌어 내용 길이가 달라지면 막대도 다시 맞춘다.
  useLayoutEffect(() => {
    const scroller = scrollRef.current;
    const content = contentRef.current;
    if (!scroller || !content) return;

    const observer = new ResizeObserver(updateThumb);
    observer.observe(scroller);
    observer.observe(content);
    return () => observer.disconnect();
  }, [updateThumb]);

  // 막대를 끌어 스크롤한다.
  const handleThumbPointerDown = (event: React.PointerEvent<HTMLDivElement>) => {
    const scroller = scrollRef.current;
    if (!scroller) return;
    event.preventDefault();
    event.stopPropagation();
    anchorRef.current = null;
    event.currentTarget.setPointerCapture(event.pointerId);
    dragRef.current = { startY: event.clientY, startTop: scroller.scrollTop };
    setDragging(true);
  };

  const handleThumbPointerMove = (event: React.PointerEvent<HTMLDivElement>) => {
    const drag = dragRef.current;
    const scroller = scrollRef.current;
    const track = trackRef.current;
    const thumb = thumbRef.current;
    if (!drag || !scroller || !track || !thumb) return;

    const travel = track.clientHeight - thumb.offsetHeight;
    if (travel <= 0) return;
    const overflow = scroller.scrollHeight - scroller.clientHeight;
    scroller.scrollTop = drag.startTop + ((event.clientY - drag.startY) / travel) * overflow;
  };

  const handleThumbPointerEnd = () => {
    if (!dragRef.current) return;
    dragRef.current = null;
    setDragging(false);
    showScrollbar();
  };

  // 막대 바깥의 빈 곳을 누르면 한 화면씩 그 방향으로 넘긴다.
  const handleTrackPointerDown = (event: React.PointerEvent<HTMLDivElement>) => {
    const scroller = scrollRef.current;
    const thumb = thumbRef.current;
    if (!scroller || !thumb || event.target !== event.currentTarget) return;
    anchorRef.current = null;
    const direction = event.clientY < thumb.getBoundingClientRect().top ? -1 : 1;
    scroller.scrollBy({ top: direction * scroller.clientHeight * 0.9, behavior: 'smooth' });
  };

  // 15열의 월 라벨은 그 달이 시작되는 행의 높이에 맞춰 왼쪽 끝에 두고, 겹치면 아래로 민다.
  const positionLabels = useCallback(() => {
    const grid = gridRef.current;
    if (!grid) return;

    const rowEls = grid.querySelectorAll<HTMLElement>('[data-row]');
    const labels = labelRefs.current.slice(0, monthStarts.length);
    const tops = placeMonthLabels(
      monthStarts.map((start) => rowEls[start.rowIndex]?.offsetTop ?? 0),
      labels.map((label) => label?.offsetHeight ?? 0)
    );
    labels.forEach((label, index) => {
      if (label) label.style.top = `${tops[index]}px`;
    });
  }, [monthStarts]);

  useLayoutEffect(() => {
    positionLabels();

    const grid = gridRef.current;
    if (!grid || columns !== 15) return;
    const observer = new ResizeObserver(positionLabels);
    observer.observe(grid);
    return () => observer.disconnect();
  }, [positionLabels, columns, rows.length]);

  const stepLevel = useCallback(
    (delta: 1 | -1) => {
      anchorRef.current = captureAnchor();
      setColumns((current) => stepColumns(current, delta));
    },
    [captureAnchor]
  );

  // Ctrl+휠과 두 손가락 핀치로 단계를 바꾼다. (한 번의 동작에 한 단계)
  useEffect(() => {
    const frame = frameRef.current;
    if (!frame) return;

    let wheelLocked = false;
    const handleWheel = (event: WheelEvent) => {
      if (!event.ctrlKey) return;
      event.preventDefault();
      if (wheelLocked) return;
      wheelLocked = true;
      setTimeout(() => {
        wheelLocked = false;
      }, 350);
      stepLevel(event.deltaY < 0 ? 1 : -1);
    };

    let startDistance = 0;
    let pinchDone = false;
    const distance = (touches: TouchList) =>
      Math.hypot(touches[0].clientX - touches[1].clientX, touches[0].clientY - touches[1].clientY);
    const handleTouchStart = (event: TouchEvent) => {
      if (event.touches.length === 2) {
        startDistance = distance(event.touches);
        pinchDone = false;
      }
    };
    const handleTouchMove = (event: TouchEvent) => {
      if (event.touches.length !== 2 || !startDistance) return;
      event.preventDefault();
      if (pinchDone) return;
      const ratio = distance(event.touches) / startDistance;
      if (ratio > 1.3) {
        pinchDone = true;
        stepLevel(1);
      } else if (ratio < 0.77) {
        pinchDone = true;
        stepLevel(-1);
      }
    };
    const handleTouchEnd = (event: TouchEvent) => {
      if (event.touches.length < 2) startDistance = 0;
    };

    frame.addEventListener('wheel', handleWheel, { passive: false });
    frame.addEventListener('touchstart', handleTouchStart, { passive: true });
    frame.addEventListener('touchmove', handleTouchMove, { passive: false });
    frame.addEventListener('touchend', handleTouchEnd, { passive: true });
    return () => {
      frame.removeEventListener('wheel', handleWheel);
      frame.removeEventListener('touchstart', handleTouchStart);
      frame.removeEventListener('touchmove', handleTouchMove);
      frame.removeEventListener('touchend', handleTouchEnd);
    };
  }, [stepLevel]);

  const selectFilter = (memberId: number | null) => {
    if (memberId === filter) return;
    anchorRef.current = captureAnchor();
    setFilter(memberId);
  };

  // 사진을 고른다. 끝에 가까워지면 다음 페이지를 미리 불러와 계속 넘겨 볼 수 있게 한다.
  const selectPhoto = (photo: GroupPhoto) => {
    setSelected(photo);
    const index = items.findIndex((item) => item.id === photo.id);
    if (hasMore && index >= items.length - PREFETCH_REMAINING) void loadMore();
  };

  const openPhoto = (photo: GroupPhoto) => {
    if (!selected) lastFocusRef.current = document.activeElement as HTMLElement | null;
    selectPhoto(photo);
  };

  const closePhoto = () => {
    setSelected(null);

    // 넘겨 보다 닫았다면 마지막으로 보던 사진 칸으로 포커스를 돌려준다.
    const cell = selected
      ? scrollRef.current?.querySelector<HTMLElement>(`[data-photo-id="${selected.id}"]`)
      : null;
    (cell ?? lastFocusRef.current)?.focus({ preventScroll: true });
  };

  // 앞뒤로 넘긴 사진이 갤러리 안에서 잘려 보이면 갤러리만 스크롤해 보이게 한다. (페이지는 움직이지 않는다)
  const revealPhoto = (photo: GroupPhoto) => {
    const scroller = scrollRef.current;
    const cell = scroller?.querySelector<HTMLElement>(`[data-photo-id="${photo.id}"]`);
    if (!scroller || !cell) return;

    const view = scroller.getBoundingClientRect();
    const box = cell.getBoundingClientRect();
    if (box.top < view.top) scroller.scrollTop -= view.top - box.top;
    else if (box.bottom > view.bottom) scroller.scrollTop += Math.min(box.bottom - view.bottom, box.top - view.top);
  };

  const stepPhoto = (photo: GroupPhoto) => {
    selectPhoto(photo);
    revealPhoto(photo);
  };

  const levelIndex = PHOTO_LEVELS.indexOf(columns);
  const gap = PHOTO_GAP[columns];

  let body: React.ReactNode;
  if (current === null) {
    body = <div className="py-24 text-center text-sm font-medium text-slate-400">인증 사진을 불러오는 중...</div>;
  } else if (current.error) {
    body = (
      <div className="flex flex-col items-center gap-3 px-4 py-24 text-center">
        <p className="text-sm font-medium text-rose-600">{current.error}</p>
        <button
          type="button"
          onClick={() => {
            setLoaded(null);
            setReloadKey((key) => key + 1);
          }}
          className="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-2 text-xs font-bold text-slate-700 hover:bg-slate-100 transition cursor-pointer"
        >
          다시 시도
        </button>
      </div>
    );
  } else if (items.length === 0) {
    body = (
      <div className="px-4 py-24 text-center text-sm font-medium text-slate-400">
        {filter === null ? '아직 승인된 인증 사진이 없습니다.' : '이 멤버의 승인된 인증 사진이 없습니다.'}
      </div>
    );
  } else {
    body = (
      <div
        ref={gridRef}
        className={`relative flex flex-col pb-[84px] ${columns === 1 ? 'px-2 pt-2' : ''}`}
        style={{ gap }}
      >
        {rows.map((row, rowIndex) => {
          const ratio = rowRatio(row, columns);
          return (
            <div
              key={row[0].id}
              data-row={rowIndex}
              className="grid"
              style={{ gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))`, gap }}
            >
              {row.map((photo) => (
                <button
                  key={photo.id}
                  type="button"
                  data-photo-id={photo.id}
                  data-date={photo.verifyDate}
                  onClick={() => openPhoto(photo)}
                  aria-label={`${photo.memberNickname} ${photo.habitTitle} ${formatShortDate(photo.verifyDate)}`}
                  aria-current={selected?.id === photo.id ? 'true' : undefined}
                  className={`group/cell relative block w-full overflow-hidden bg-slate-200 p-0 cursor-pointer ${
                    columns === 1 ? 'rounded-[18px]' : ''
                  }`}
                  style={{ aspectRatio: `1 / ${ratio}` }}
                >
                  <img
                    src={photo.imageUrl}
                    alt=""
                    loading="lazy"
                    decoding="async"
                    className="absolute inset-0 h-full w-full object-cover"
                    onError={(event) => {
                      event.currentTarget.style.visibility = 'hidden';
                    }}
                  />
                  <span
                    className="absolute inset-0 flex flex-col items-center justify-center gap-0.5 overflow-hidden p-1 text-center text-white opacity-0 transition-opacity group-hover/cell:bg-slate-900/60 group-hover/cell:opacity-100 group-focus-visible/cell:bg-slate-900/60 group-focus-visible/cell:opacity-100 [@media(hover:none)]:justify-end [@media(hover:none)]:bg-linear-to-t [@media(hover:none)]:from-slate-900/55 [@media(hover:none)]:to-transparent [@media(hover:none)]:opacity-100"
                  >
                    <span className={`max-w-full truncate font-extrabold ${WHO_CLASS[columns]}`}>
                      {photo.memberNickname}
                    </span>
                    <span
                      className={`line-clamp-2 max-w-full opacity-90 [@media(hover:none)]:hidden ${WHAT_CLASS[columns]}`}
                    >
                      {photo.habitTitle}
                    </span>
                    <span className={`opacity-75 [@media(hover:none)]:hidden ${WHEN_CLASS[columns]}`}>
                      {formatShortDate(photo.verifyDate)}
                    </span>
                  </span>
                  {selected?.id === photo.id && (
                    <span
                      className={`pointer-events-none absolute inset-0 z-[1] ring-inset ring-emerald-500 ${
                        columns === 15 ? 'ring-2' : 'ring-4'
                      }`}
                    />
                  )}
                </button>
              ))}
            </div>
          );
        })}

        {monthStarts.map((start, index) => (
          <span
            key={`${start.rowIndex}-${start.label}`}
            ref={(element) => {
              labelRefs.current[index] = element;
            }}
            className="pointer-events-none absolute left-1.5 top-1.5 z-[3] whitespace-nowrap rounded-[14px] bg-white/90 px-3 py-px text-[clamp(16px,3.2vw,28px)] font-extrabold leading-[1.35] text-slate-900 shadow-md"
          >
            {start.label}
          </span>
        ))}

        <div ref={sentinelRef} className="py-4 text-center text-xs font-medium text-slate-400">
          {current.moreError ? (
            <span>
              {current.moreError}{' '}
              <button
                type="button"
                onClick={() => {
                  setLoaded((prev) => (prev ? { ...prev, moreError: null } : prev));
                }}
                className="font-bold text-emerald-600 hover:underline cursor-pointer"
              >
                다시 시도
              </button>
            </span>
          ) : current.nextCursor ? (
            loadingMore ? '불러오는 중...' : ''
          ) : (
            '모든 인증 사진을 불러왔습니다.'
          )}
        </div>
      </div>
    );
  }

  return (
    <section aria-label="인증 사진">
      {/* 갤러리 위 줄: 왼쪽에 지금 보는 년/월/주, 오른쪽에 멤버 필터 */}
      <div className="mb-3 flex flex-wrap items-start justify-between gap-x-3 gap-y-2 px-1">
        <p
          aria-live="polite"
          className="min-h-[28px] text-lg font-extrabold tabular-nums leading-7 tracking-tight text-slate-900 [@media(max-width:400px)]:text-base"
        >
          {periodLabel && current !== null ? periodLabel : ''}
        </p>

      <div
        className="flex min-w-0 flex-1 basis-60 flex-wrap items-center justify-end gap-1.5"
        role="group"
        aria-label="멤버 필터"
      >
        <button
          type="button"
          aria-pressed={filter === null}
          onClick={() => selectFilter(null)}
          className={`rounded-full border px-3 py-1 text-xs font-bold transition cursor-pointer ${
            filter === null
              ? 'border-emerald-200 bg-emerald-50 text-emerald-600'
              : 'border-slate-200 bg-white text-slate-500 hover:bg-slate-50'
          }`}
        >
          전체
        </button>
        {members.map((member) => (
          <button
            key={member.memberId}
            type="button"
            aria-pressed={filter === member.memberId}
            onClick={() => selectFilter(member.memberId)}
            className={`rounded-full border px-3 py-1 text-xs font-bold transition cursor-pointer ${
              filter === member.memberId
                ? 'border-emerald-200 bg-emerald-50 text-emerald-600'
                : 'border-slate-200 bg-white text-slate-500 hover:bg-slate-50'
            }`}
          >
            {member.nickname}
          </button>
        ))}
      </div>
      </div>

      <div className="group/frame relative">
      <div
        ref={frameRef}
        className="relative h-[min(720px,78vh)] overflow-hidden rounded-3xl border border-slate-200 bg-white"
      >
        <div
          ref={scrollRef}
          className="relative h-full touch-pan-y overflow-y-auto overscroll-contain [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
        >
          <div ref={contentRef}>{body}</div>
        </div>

        <div className="absolute bottom-3.5 left-1/2 z-[6] flex -translate-x-1/2 items-center gap-1.5 whitespace-nowrap rounded-full border border-white/80 bg-white/70 p-1.5 shadow-xl backdrop-blur-xl">
          <button
            type="button"
            onClick={() => stepLevel(-1)}
            disabled={levelIndex === 0}
            aria-label="축소 (사진이 작아지고 더 많이 보임)"
            title="축소"
            className="h-[34px] w-[34px] rounded-full bg-slate-900/5 text-base font-extrabold text-slate-700 disabled:opacity-35 disabled:cursor-default cursor-pointer"
          >
            −
          </button>
          <button
            type="button"
            onClick={() => stepLevel(1)}
            disabled={levelIndex === PHOTO_LEVELS.length - 1}
            aria-label="확대 (사진이 커지고 적게 보임)"
            title="확대"
            className="h-[34px] w-[34px] rounded-full bg-slate-900/5 text-base font-extrabold text-slate-700 disabled:opacity-35 disabled:cursor-default cursor-pointer"
          >
            +
          </button>
          <button
            type="button"
            onClick={() => scrollRef.current?.scrollTo({ top: 0, behavior: 'smooth' })}
            aria-label="맨 위(최신)로"
            title="맨 위(최신)로"
            className="h-[34px] w-[34px] rounded-full bg-slate-900/5 text-base font-extrabold text-slate-700 cursor-pointer"
          >
            ↑
          </button>
        </div>
      </div>

      {/* 갤러리 프레임 오른쪽 바깥에 따로 놓는 스크롤바. 평소엔 숨기고 마우스를 올리거나 스크롤할 때만 나타난다. */}
      <div
        ref={trackRef}
        aria-hidden="true"
        onPointerDown={handleTrackPointerDown}
        className={`absolute bottom-4 left-full top-4 ml-1.5 w-3 rounded-full transition-opacity duration-200 ${
          scrollActive || dragging ? 'opacity-100' : 'opacity-0 group-hover/frame:opacity-100'
        }`}
      >
        <div
          ref={thumbRef}
          onPointerDown={handleThumbPointerDown}
          onPointerMove={handleThumbPointerMove}
          onPointerUp={handleThumbPointerEnd}
          onPointerCancel={handleThumbPointerEnd}
          className={`absolute left-[3px] top-0 w-1.5 touch-none rounded-full transition-colors ${
            dragging ? 'bg-slate-600' : 'bg-slate-400 hover:bg-slate-500'
          }`}
        />
      </div>
      </div>

      <p className="mt-2 text-center text-xs font-medium text-slate-400">
        −/+ 또는 Ctrl+휠(핀치)로 축소/확대 · 한 줄 {columns}장
      </p>

      {selected && (
        <PhotoDetailPanel
          groupId={groupId}
          photo={selected}
          onPrev={selectedIndex > 0 ? () => stepPhoto(items[selectedIndex - 1]) : null}
          onNext={
            selectedIndex >= 0 && selectedIndex < items.length - 1 ? () => stepPhoto(items[selectedIndex + 1]) : null
          }
          onClose={closePhoto}
        />
      )}
    </section>
  );
}
