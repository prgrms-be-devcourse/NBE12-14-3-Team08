'use client';

import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react';
import { Check, ChevronDown, Users } from 'lucide-react';

interface MemberFilterDropdownProps {
  members: { memberId: number; nickname: string }[];
  // 선택한 멤버의 id. null이면 전체
  value: number | null;
  onChange: (memberId: number | null) => void;
}

// 인증 사진을 멤버별로 거르는 드롭다운. "전체"와 멤버 목록이 한 목록에 들어 있다.
// 목록은 ↑ ↓ Home End로 오가고 Enter/Space로 고르며 Esc나 바깥을 누르면 닫힌다.
export function MemberFilterDropdown({ members, value, onChange }: MemberFilterDropdownProps) {
  const options: { id: number | null; label: string }[] = [
    { id: null, label: '전체' },
    ...members.map((member) => ({ id: member.memberId, label: member.nickname })),
  ];
  const selectedIndex = Math.max(0, options.findIndex((option) => option.id === value));

  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(selectedIndex);
  const rootRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const listRef = useRef<HTMLUListElement>(null);
  const listId = useId();

  // 열리면 목록으로 포커스를 옮겨 키보드로 바로 고를 수 있게 한다.
  useEffect(() => {
    if (open) listRef.current?.focus({ preventScroll: true });
  }, [open]);

  // 열려 있는 동안 현재 항목이 목록 안에서 보이게 한다.
  useEffect(() => {
    if (!open) return;
    listRef.current?.querySelector<HTMLElement>(`[data-index="${activeIndex}"]`)?.scrollIntoView({ block: 'nearest' });
  }, [open, activeIndex]);

  // 목록 바깥을 누르면 닫는다.
  useEffect(() => {
    if (!open) return;
    const handlePointerDown = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener('pointerdown', handlePointerDown);
    return () => document.removeEventListener('pointerdown', handlePointerDown);
  }, [open]);

  const openMenu = () => {
    setActiveIndex(selectedIndex);
    setOpen(true);
  };

  const close = (restoreFocus: boolean) => {
    setOpen(false);
    if (restoreFocus) buttonRef.current?.focus({ preventScroll: true });
  };

  const choose = (index: number) => {
    onChange(options[index].id);
    close(true);
  };

  const handleButtonKeyDown = (event: KeyboardEvent<HTMLButtonElement>) => {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      openMenu();
    }
  };

  const handleListKeyDown = (event: KeyboardEvent<HTMLUListElement>) => {
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        setActiveIndex((index) => Math.min(options.length - 1, index + 1));
        break;
      case 'ArrowUp':
        event.preventDefault();
        setActiveIndex((index) => Math.max(0, index - 1));
        break;
      case 'Home':
        event.preventDefault();
        setActiveIndex(0);
        break;
      case 'End':
        event.preventDefault();
        setActiveIndex(options.length - 1);
        break;
      case 'Enter':
      case ' ':
        event.preventDefault();
        choose(activeIndex);
        break;
      case 'Escape':
        event.preventDefault();
        event.stopPropagation();
        close(true);
        break;
      case 'Tab':
        close(false);
        break;
    }
  };

  const filtered = value !== null;

  return (
    <div ref={rootRef} className="relative">
      <button
        ref={buttonRef}
        type="button"
        aria-label="멤버 필터"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={open ? listId : undefined}
        onClick={() => (open ? close(false) : openMenu())}
        onKeyDown={handleButtonKeyDown}
        className={`flex max-w-[11rem] items-center gap-1.5 rounded-full border px-3 py-1.5 text-xs font-bold transition cursor-pointer ${
          filtered
            ? 'border-forest-200 bg-forest-50 text-forest-600'
            : 'border-slate-200 bg-white text-slate-600 hover:bg-slate-50'
        }`}
      >
        <Users className="h-3.5 w-3.5 flex-none" />
        <span className="truncate">{options[selectedIndex].label}</span>
        <ChevronDown className={`h-3.5 w-3.5 flex-none transition-transform ${open ? 'rotate-180' : ''}`} />
      </button>

      {open && (
        <ul
          ref={listRef}
          id={listId}
          role="listbox"
          aria-label="멤버 선택"
          aria-activedescendant={`${listId}-${activeIndex}`}
          tabIndex={-1}
          onKeyDown={handleListKeyDown}
          className="absolute right-0 top-full z-30 mt-1.5 max-h-64 w-48 overflow-y-auto rounded-2xl border border-slate-200 bg-white p-1 shadow-xl outline-none"
        >
          {options.map((option, index) => (
            <li
              key={option.id ?? 'all'}
              id={`${listId}-${index}`}
              data-index={index}
              role="option"
              aria-selected={index === selectedIndex}
              onClick={() => choose(index)}
              onMouseMove={() => setActiveIndex(index)}
              className={`flex cursor-pointer items-center justify-between gap-2 rounded-xl px-3 py-2 text-xs font-bold ${
                index === activeIndex ? 'bg-slate-100' : ''
              } ${index === selectedIndex ? 'text-forest-600' : 'text-slate-700'}`}
            >
              <span className="truncate">{option.label}</span>
              {index === selectedIndex && <Check className="h-3.5 w-3.5 flex-none" />}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
