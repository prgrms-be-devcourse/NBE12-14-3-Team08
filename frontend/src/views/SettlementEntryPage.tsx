'use client';

import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { useRouter } from 'next/navigation';
import { ProfileAvatar } from '../components/ProfileAvatar';
import { useMember } from '../context/MemberContext';
import {
  settlementApi,
  type SettlementGroupComparison,
  type SettlementPersonalStats,
  type SettlementResponse,
  type SettlementWinner,
} from '../lib/settlementApi';

interface SettlementEntryPageProps {
  groupId: string;
  preview?: boolean;
}

const previewSettlement: SettlementResponse = {
  groupId: 1,
  groupTitle: '아침 6시 기상챌린지 ⏰',
  personalStats: {
    memberId: 1,
    nickname: '정우',
    approvedCount: 18,
    totalVerifyCount: 21,
    rejectedCount: 3,
    penaltyCount: 2,
  },
  groupComparison: {
    mostDescriptionChars: { memberId: 1, nickname: '정우', value: 1284 },
    mostDeadlineVerifications: { memberId: 2, nickname: '서연', value: 7 },
    mostRejectedVerifications: { memberId: 3, nickname: '지훈', value: 5 },
    longestPenaltyDelay: { memberId: 4, nickname: '민수', value: 9 },
  },
};

interface ComparisonCard {
  eyebrow: string;
  icon: string;
  title: string;
  winner: string;
  value: string;
  detail: string;
  gradient: string;
}

const toneClasses = {
  emerald: 'bg-emerald-50 text-emerald-700 ring-emerald-100',
  sky: 'bg-sky-50 text-sky-700 ring-sky-100',
  rose: 'bg-rose-50 text-rose-700 ring-rose-100',
  amber: 'bg-amber-50 text-amber-700 ring-amber-100',
};

function winnerName(winner: SettlementWinner | null) {
  return winner?.nickname ?? '기록 없음';
}

function winnerValue(winner: SettlementWinner | null, prefix: string, unit: string) {
  return winner ? `${prefix}${winner.value.toLocaleString()}${unit}` : '기록 없음';
}

function createComparisonCards(comparison: SettlementGroupComparison): ComparisonCard[] {
  return [
    {
      eyebrow: '이번 방의 수다왕',
      icon: '💬',
      title: '인증 설명을 가장 정성껏 남겼어요',
      winner: winnerName(comparison.mostDescriptionChars),
      value: winnerValue(comparison.mostDescriptionChars, '총 ', '자'),
      detail: '인증할 때 남긴 설명의 글자 수를 모두 더했어요.',
      gradient: 'from-violet-500 to-fuchsia-500',
    },
    {
      eyebrow: '마감일의 주인공',
      icon: '⏰',
      title: '마지막 날까지 포기하지 않았어요',
      winner: winnerName(comparison.mostDeadlineVerifications),
      value: winnerValue(comparison.mostDeadlineVerifications, '마감일 인증 ', '회'),
      detail: '방 생성일을 기준으로 나눈 주간 마감일의 승인된 인증 횟수예요.',
      gradient: 'from-orange-400 to-rose-500',
    },
    {
      eyebrow: '아쉬운 거절왕',
      icon: '🥲',
      title: '다음에는 더 완벽하게 인증해봐요',
      winner: winnerName(comparison.mostRejectedVerifications),
      value: winnerValue(comparison.mostRejectedVerifications, '인증 거절 ', '회'),
      detail: '거절된 인증도 다시 도전한 소중한 기록이에요.',
      gradient: 'from-forest-600 to-forest-800',
    },
    {
      eyebrow: '느긋한 벌칙왕',
      icon: '🐢',
      title: '벌칙을 가장 오래 고민했어요',
      winner: winnerName(comparison.longestPenaltyDelay),
      value: winnerValue(comparison.longestPenaltyDelay, '최장 ', '일'),
      detail: '벌칙이 생긴 날부터 제출한 날까지의 기간을 비교했어요.',
      gradient: 'from-forest-500 to-forest-800',
    },
  ];
}

export function SettlementEntryPage({ groupId, preview = false }: SettlementEntryPageProps) {
  const router = useRouter();
  const { currentUser, authReady } = useMember();
  const [settlement, setSettlement] = useState<SettlementResponse | null>(preview ? previewSettlement : null);
  const [isLoading, setIsLoading] = useState(!preview);
  const [error, setError] = useState('');
  const [retryKey, setRetryKey] = useState(0);
  const [step, setStep] = useState(0);

  useEffect(() => {
    if (preview) return;
    if (!authReady) return;
    if (!currentUser) {
      router.replace('/login');
      return;
    }

    let cancelled = false;

    settlementApi.getSettlement(groupId)
      .then((response) => {
        if (!cancelled) setSettlement(response);
      })
      .catch((caught) => {
        if (!cancelled) {
          setError(caught instanceof Error ? caught.message : '결산 기록을 불러오지 못했습니다.');
        }
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [authReady, currentUser, groupId, preview, retryKey, router]);

  if ((!preview && !authReady) || isLoading) {
    return <SettlementStatus message="결산 기록을 불러오는 중..." />;
  }

  if (error || !settlement) {
    return (
      <SettlementStatus
        message={error || '결산 기록을 찾을 수 없습니다.'}
        actionLabel="다시 불러오기"
        onAction={() => {
          setSettlement(null);
          setError('');
          setIsLoading(true);
          setRetryKey((current) => current + 1);
        }}
        onBack={() => router.push('/main')}
      />
    );
  }

  const nickname = settlement.personalStats.nickname || currentUser?.name || '참가자';
  const comparisonCards = createComparisonCards(settlement.groupComparison);
  const lastStep = comparisonCards.length + 2;
  const progress = ((step + 1) / (lastStep + 1)) * 100;

  const goNext = () => setStep((current) => Math.min(current + 1, lastStep));
  const goPrevious = () => setStep((current) => Math.max(current - 1, 0));

  return (
    // 헤더(65px)를 뺀 화면 높이에 맞춰 스크롤 없이 한 화면에 담는다
    <main className="h-[calc(100svh-65px)] overflow-hidden px-4 py-3 sm:px-6 sm:py-5">
      <section className="relative mx-auto h-full max-w-5xl overflow-hidden rounded-3xl border border-slate-100 bg-white p-4 text-slate-900 shadow-xl shadow-slate-200/60 sm:p-6">
        <div aria-hidden="true" className="absolute -left-32 top-12 h-72 w-72 rounded-full bg-forest-200/50 blur-3xl" />
        <div aria-hidden="true" className="absolute -right-32 bottom-10 h-80 w-80 rounded-full bg-violet-200/40 blur-3xl" />

        <div className="relative mx-auto flex h-full max-w-4xl flex-col">
        <header className="grid grid-cols-3 items-center gap-4">
          <button
            type="button"
            onClick={() => router.push('/main')}
            className="justify-self-start rounded-full border border-slate-200 bg-white px-4 py-2 text-xs font-bold text-slate-600 shadow-sm transition hover:bg-forest-50 hover:text-forest-700"
          >
            ← 방 목록
          </button>
          <div className="text-center">
            <p className="text-xs font-black tracking-[0.24em] text-forest-600">내기? 내기!</p>
            <p className="mt-1 text-[10px] font-semibold text-slate-400">{settlement.groupTitle} 결산</p>
          </div>
          <span aria-hidden="true" />
        </header>

        <div className="mt-3 h-1 overflow-hidden rounded-full bg-slate-100">
          <div
            className="h-full rounded-full bg-gradient-to-r from-forest-500 to-lime-400 transition-all duration-500"
            style={{ width: `${progress}%` }}
          />
        </div>

        <FitToHeight>
          {step === 0 && <IntroSlide nickname={nickname} onStart={goNext} />}
          {step === 1 && <PersonalSlide stats={settlement.personalStats} />}
          {step >= 2 && step < lastStep && (
            <ComparisonSlide
              card={comparisonCards[step - 2]}
              current={step - 1}
              total={comparisonCards.length}
            />
          )}
          {step === lastStep && <ClosingSlide nickname={nickname} />}
        </FitToHeight>

        {step > 0 && (
          <footer className="flex items-center justify-between border-t border-slate-100 pt-3">
            <button
              type="button"
              onClick={goPrevious}
              className="rounded-xl px-4 py-2.5 text-sm font-bold text-slate-500 transition hover:bg-slate-50 hover:text-slate-900"
            >
              이전
            </button>
            <div className="flex gap-1.5" aria-label="결산 진행 단계">
              {Array.from({ length: lastStep + 1 }, (_, index) => (
                <span
                  key={index}
                  className={`h-1.5 rounded-full transition-all ${index === step ? 'w-6 bg-forest-500' : 'w-1.5 bg-slate-200'}`}
                />
              ))}
            </div>
            {step < lastStep ? (
              <button
                type="button"
                onClick={goNext}
                className="rounded-xl bg-forest-500 px-5 py-2.5 text-sm font-black text-lime-400 transition hover:bg-forest-600"
              >
                다음 →
              </button>
            ) : (
              <button
                type="button"
                onClick={() => router.push(`/group/${groupId}`)}
                className="rounded-xl bg-forest-500 px-5 py-2.5 text-sm font-black text-lime-400 transition hover:bg-forest-600"
              >
                방으로 이동
              </button>
            )}
          </footer>
        )}
        </div>
      </section>
    </main>
  );
}

// 남은 높이에 슬라이드를 맞춘다. 내용이 더 크면 비율을 줄여 스크롤 없이 한 화면에 담는다.
function FitToHeight({ children }: { children: ReactNode }) {
  const boxRef = useRef<HTMLDivElement>(null);
  const innerRef = useRef<HTMLDivElement>(null);
  const [scale, setScale] = useState(1);

  useLayoutEffect(() => {
    const box = boxRef.current;
    const inner = innerRef.current;
    if (!box || !inner) return;

    const update = () => {
      // offsetHeight는 transform의 영향을 받지 않아 원래 크기를 그대로 준다
      const next = Math.min(1, box.clientHeight / inner.offsetHeight);
      setScale(Math.max(next, 0.4));
    };
    update();

    const observer = new ResizeObserver(update);
    observer.observe(box);
    observer.observe(inner);
    return () => observer.disconnect();
  }, []);

  return (
    <div ref={boxRef} className="relative min-h-0 flex-1 overflow-hidden">
      <div
        ref={innerRef}
        className="absolute left-0 top-1/2 flex w-full justify-center"
        style={{ transform: `translateY(-50%) scale(${scale})`, transformOrigin: 'center' }}
      >
        {children}
      </div>
    </div>
  );
}

function SettlementStatus({
  message,
  actionLabel,
  onAction,
  onBack,
}: {
  message: string;
  actionLabel?: string;
  onAction?: () => void;
  onBack?: () => void;
}) {
  return (
    <main className="h-[calc(100svh-65px)] overflow-hidden px-4 py-3 sm:px-6 sm:py-5">
      <section className="mx-auto flex h-full max-w-5xl items-center justify-center rounded-3xl border border-slate-100 bg-white p-6 text-slate-900 shadow-xl shadow-slate-200/60">
      <div className="w-full max-w-md rounded-3xl border border-slate-200 bg-slate-50 p-8 text-center">
        <p className="text-sm font-bold text-slate-600">{message}</p>
        <div className="mt-6 flex justify-center gap-3">
          {onBack && (
            <button
              type="button"
              onClick={onBack}
              className="rounded-xl border border-slate-200 bg-white px-4 py-2 text-xs font-bold text-slate-600 transition hover:bg-slate-100"
            >
              방 목록
            </button>
          )}
          {onAction && actionLabel && (
            <button
              type="button"
              onClick={onAction}
              className="rounded-xl bg-forest-500 px-4 py-2 text-xs font-black text-lime-400 transition hover:bg-forest-600"
            >
              {actionLabel}
            </button>
          )}
        </div>
      </div>
      </section>
    </main>
  );
}

function IntroSlide({ nickname, onStart }: { nickname: string; onStart: () => void }) {
  return (
    <div className="w-full text-center">
      <div className="mx-auto flex h-20 w-20 items-center justify-center rounded-[1.75rem] bg-gradient-to-br from-lime-300 to-lime-400 text-4xl shadow-2xl shadow-forest-500/20">
        🎉
      </div>
      <p className="mt-5 text-sm font-black tracking-[0.3em] text-forest-600">HABIT RECAP</p>
      <h1 className="mt-3 text-3xl font-black leading-tight tracking-tight sm:text-5xl">
        함께 만든 습관의<br />마지막 장면이에요
      </h1>
      <p className="mx-auto mt-4 max-w-lg text-sm leading-6 text-slate-500 sm:text-base">
        {nickname} 님과 방 멤버들이 쌓아온 인증과 벌칙 기록을<br className="hidden sm:block" /> 재미있는 결산으로 확인해보세요.
      </p>
      <button
        type="button"
        onClick={onStart}
        className="mt-6 rounded-2xl bg-forest-500 px-8 py-3.5 text-sm font-black text-lime-400 shadow-xl shadow-forest-500/20 transition hover:-translate-y-0.5 hover:bg-forest-600"
      >
        내 결산 시작하기 →
      </button>
    </div>
  );
}

function PersonalSlide({ stats }: { stats: SettlementPersonalStats }) {
  const personalStats = [
    { icon: '✅', label: '인증 승인', value: `${stats.approvedCount}회`, tone: 'emerald' as const },
    { icon: '📸', label: '전체 인증', value: `${stats.totalVerifyCount}회`, tone: 'sky' as const },
    { icon: '↩️', label: '인증 거절', value: `${stats.rejectedCount}회`, tone: 'rose' as const },
    { icon: '🚨', label: '벌칙', value: `${stats.penaltyCount}회`, tone: 'amber' as const },
  ];

  return (
    <div className="w-full max-w-3xl">
      <div className="flex flex-col items-center text-center">
        <ProfileAvatar nickname={stats.nickname} />
        <p className="mt-3 text-xs font-black tracking-[0.2em] text-forest-600">MY RECORD</p>
        <h2 className="mt-1.5 text-2xl font-black sm:text-3xl">{stats.nickname} 님의 개인 결산</h2>
        <p className="mt-1.5 text-sm text-slate-500">이 방에서 남긴 기록을 한눈에 모았어요.</p>
      </div>

      <div className="mt-5 grid grid-cols-2 gap-3 sm:grid-cols-4">
        {personalStats.map((stat) => (
          <article key={stat.label} className="rounded-3xl border border-slate-200 bg-slate-50/80 p-3.5 text-center">
            <div className={`mx-auto flex h-11 w-11 items-center justify-center rounded-2xl text-xl ring-1 ${toneClasses[stat.tone]}`}>
              {stat.icon}
            </div>
            <p className="mt-3 text-xs font-bold text-slate-400">{stat.label}</p>
            <p className="mt-0.5 text-xl font-black text-slate-900">{stat.value}</p>
          </article>
        ))}
      </div>

      <div className="mt-4 rounded-3xl border border-forest-200 bg-forest-50 p-4 text-center">
        <p className="text-sm font-bold text-forest-800">총 {stats.totalVerifyCount}번의 인증으로 꾸준함을 보여줬어요!</p>
        <p className="mt-1 text-xs text-forest-600">작은 반복이 멋진 습관을 만들었습니다.</p>
      </div>
    </div>
  );
}

function ComparisonSlide({
  card,
  current,
  total,
}: {
  card: ComparisonCard;
  current: number;
  total: number;
}) {
  return (
    <div className="w-full max-w-2xl text-center">
      <p className="text-xs font-black tracking-[0.22em] text-slate-400">GROUP RECORD {current} / {total}</p>
      <div className={`mx-auto mt-4 flex h-20 w-20 items-center justify-center rounded-[1.75rem] bg-gradient-to-br ${card.gradient} text-4xl shadow-2xl`}>
        {card.icon}
      </div>
      <p className="mt-5 text-sm font-black text-forest-600">{card.eyebrow}</p>
      <h2 className="mt-1.5 text-2xl font-black tracking-tight sm:text-3xl">{card.title}</h2>

      <div className="mx-auto mt-5 max-w-md rounded-3xl border border-slate-200 bg-slate-50 p-5">
        <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-slate-900 text-xl font-black text-white">
          {card.winner === '기록 없음' ? '-' : card.winner.charAt(0)}
        </div>
        <p className="mt-3 text-lg font-black">{card.winner}</p>
        <p className="mt-1 bg-gradient-to-r from-forest-600 to-lime-600 bg-clip-text text-2xl font-black text-transparent">
          {card.value}
        </p>
        <p className="mt-3 text-xs leading-5 text-slate-500">{card.detail}</p>
      </div>
    </div>
  );
}

function ClosingSlide({ nickname }: { nickname: string }) {
  return (
    <div className="w-full max-w-2xl text-center">
      <p className="text-5xl">🏁</p>
      <p className="mt-4 text-sm font-black tracking-[0.25em] text-forest-600">RECAP COMPLETE</p>
      <h2 className="mt-2 text-3xl font-black leading-tight sm:text-4xl">
        {nickname} 님,<br />이번 습관도 수고했어요!
      </h2>
      <p className="mx-auto mt-4 max-w-md text-sm leading-6 text-slate-500">
        함께한 기록은 완료된 방에서 언제든 다시 볼 수 있어요. 다음 습관에서도 즐거운 도전을 이어가세요.
      </p>
      <div className="mt-6 rounded-3xl border border-slate-200 bg-slate-50 p-4">
        <p className="text-xs font-bold text-slate-400">이번 방의 한 줄 결산</p>
        <p className="mt-2 text-lg font-black text-slate-900">“혼자보다 함께여서 더 꾸준했던 시간”</p>
      </div>
    </div>
  );
}
