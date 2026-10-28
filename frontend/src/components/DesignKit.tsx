// 멤버 상세 페이지에서 쓰던 디자인(진한 숲색 히어로 + 흰 카드 + 라임 포인트)을 다른 페이지에서도 쓰기 위한 공통 조각

/** 어두운 면 위의 주요 버튼 */
export const btnLime =
  'inline-flex items-center justify-center gap-1.5 rounded-2xl bg-lime-400 px-5 py-3 text-sm font-extrabold text-forest-900 shadow-lg shadow-lime-400/25 transition hover:-translate-y-0.5 cursor-pointer';

/** 밝은 면 위의 주요 버튼 */
export const btnForest =
  'inline-flex items-center justify-center gap-1.5 rounded-2xl bg-forest-500 px-5 py-3 text-sm font-extrabold text-lime-400 transition hover:bg-forest-800 cursor-pointer disabled:opacity-50 disabled:pointer-events-none';

/** 밝은 면 위의 보조 버튼 */
export const btnSoft =
  'inline-flex items-center justify-center gap-1.5 rounded-2xl bg-slate-100 px-5 py-3 text-sm font-bold text-slate-600 transition hover:bg-slate-200 cursor-pointer';

/** 어두운 면 위의 보조 버튼 */
export const btnGhost =
  'inline-flex items-center justify-center gap-1.5 rounded-xl bg-white/10 px-3.5 py-2.5 text-xs font-bold text-white/80 transition hover:bg-white/15 cursor-pointer';

/** 흰 카드 */
export const cardClass =
  'rounded-[28px] bg-white p-6 shadow-[0_10px_30px_rgba(14,21,18,0.07),0_1px_2px_rgba(14,21,18,0.05)] sm:p-8';

/** 입력창: 포커스 시 숲색 링 */
export const inputClass =
  'w-full rounded-2xl border border-slate-200 bg-white px-4 py-3 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-forest-500 focus:ring-4 focus:ring-forest-500/10';

export function HeroCard({ children, className = '' }: { children: React.ReactNode; className?: string }) {
  return (
    <section
      className={`relative overflow-hidden rounded-[32px] bg-gradient-to-br from-forest-500 to-forest-800 p-6 text-white shadow-[0_18px_40px_rgba(11,20,17,0.28)] sm:p-9 ${className}`}
    >
      <div className="relative">{children}</div>
    </section>
  );
}

export function Eyebrow({ children }: { children: React.ReactNode }) {
  return <p className="text-[11px] font-extrabold uppercase tracking-[0.2em] text-lime-400">{children}</p>;
}

export function HeroTiles({ children }: { children: React.ReactNode }) {
  return <div className="mt-6 flex flex-wrap gap-3">{children}</div>;
}

export function HeroTile({
  label,
  value,
  unit,
  title,
  compact = false,
}: {
  label: string;
  // 라벨이 이모지뿐일 때 마우스를 올리면 보이고 스크린리더가 읽는 설명
  title?: string;
  value: React.ReactNode;
  unit?: string;
  // 값 글씨를 줄인 작은 타일 (이모지 라벨용)
  compact?: boolean;
}) {
  return (
    <div className="min-w-[140px] flex-1 rounded-[18px] bg-white/10 px-4 py-3" title={title}>
      <p
        className={
          title
            ? `${compact ? 'text-base' : 'text-xl'} leading-none`
            : 'text-[11px] font-bold text-white/55'
        }
        aria-label={title}
      >
        {label}
      </p>
      <p className={`mt-1 ${compact ? 'text-sm' : 'text-xl'} font-extrabold tracking-tight`}>
        {value}
        {unit && <span className="text-lime-400">{unit}</span>}
      </p>
    </div>
  );
}

/** 진한 면 위의 알약. tone: lime(성공·진행), amber(방장), rose(주의), neutral */
export function DarkPill({
  tone = 'neutral',
  children,
}: {
  tone?: 'lime' | 'amber' | 'rose' | 'neutral';
  children: React.ReactNode;
}) {
  const tones = {
    lime: 'bg-lime-400/15 text-lime-400',
    amber: 'bg-amber-400/15 text-amber-300',
    rose: 'bg-rose-400/15 text-rose-300',
    neutral: 'bg-white/10 text-white/85',
  };
  return (
    <span className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-[11px] font-extrabold ${tones[tone]}`}>
      {children}
    </span>
  );
}
