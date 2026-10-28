'use client';

import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import type { PointerEvent, ReactNode } from 'react';

// 높이가 고정된 스크롤 영역. 기본 스크롤바는 숨기고, 인증 사진 앨범과 같은 모양의 막대를 영역 오른쪽 바깥에 따로 그린다.
// 막대는 평소엔 숨겨 두고 마우스를 올리거나 스크롤할 때만 나타난다.
export function OverlayScrollArea({
  id,
  role,
  label,
  className = '',
  children,
}: {
  id?: string;
  role?: string;
  label?: string;
  className?: string;
  children: ReactNode;
}) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const contentRef = useRef<HTMLDivElement>(null);
  const trackRef = useRef<HTMLDivElement>(null);
  const thumbRef = useRef<HTMLDivElement>(null);
  const dragRef = useRef<{ startY: number; startTop: number } | null>(null);
  const hideTimerRef = useRef<number | null>(null);
  const [scrollActive, setScrollActive] = useState(false);
  const [dragging, setDragging] = useState(false);

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

  useEffect(() => {
    const scroller = scrollRef.current;
    if (!scroller) return;

    const handleScroll = () => {
      updateThumb();
      showScrollbar();
    };

    scroller.addEventListener('scroll', handleScroll, { passive: true });
    return () => scroller.removeEventListener('scroll', handleScroll);
  }, [updateThumb, showScrollbar]);

  // 내용 길이나 영역 크기가 바뀌면 막대도 다시 맞춘다.
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
  const handleThumbPointerDown = (event: PointerEvent<HTMLDivElement>) => {
    const scroller = scrollRef.current;
    if (!scroller) return;
    event.preventDefault();
    event.stopPropagation();
    event.currentTarget.setPointerCapture(event.pointerId);
    dragRef.current = { startY: event.clientY, startTop: scroller.scrollTop };
    setDragging(true);
  };

  const handleThumbPointerMove = (event: PointerEvent<HTMLDivElement>) => {
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
  const handleTrackPointerDown = (event: PointerEvent<HTMLDivElement>) => {
    const scroller = scrollRef.current;
    const thumb = thumbRef.current;
    if (!scroller || !thumb || event.target !== event.currentTarget) return;
    const direction = event.clientY < thumb.getBoundingClientRect().top ? -1 : 1;
    scroller.scrollBy({ top: direction * scroller.clientHeight * 0.9, behavior: 'smooth' });
  };

  return (
    <div id={id} role={role} aria-label={label} className="group/frame relative h-[min(720px,78vh)]">
      <div className="relative h-full overflow-hidden">
        <div
          ref={scrollRef}
          className="relative h-full touch-pan-y overflow-y-auto overscroll-contain [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
        >
          <div ref={contentRef} className={className}>
            {children}
          </div>
        </div>
      </div>

      <div
        ref={trackRef}
        aria-hidden="true"
        onPointerDown={handleTrackPointerDown}
        className={`absolute bottom-4 left-full top-4 ml-0.5 w-3 rounded-full transition-opacity duration-200 sm:ml-1.5 ${
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
  );
}
