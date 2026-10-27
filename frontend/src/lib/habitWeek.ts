import type { HabitVerifyResponse } from './habitApi';

// 주는 방을 만든 요일을 시작으로 7일씩 끊는다. 서버의 주간 판정(HabitService.evaluateWeeklyResult)과 같은 규칙이다.

const pad = (n: number) => n.toString().padStart(2, '0');

export const toDateKey = (date: Date) => `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;

// "2026-10-25T11:21:55" 같은 서버 시각에서 날짜만 지역 날짜로 바꾼다
const parseDate = (value: string) => {
  const [year, month, day] = value.slice(0, 10).split('-').map(Number);
  return new Date(year, month - 1, day);
};

// 기준 날짜가 속한 주의 시작 날짜
export function weekStartOf(date: Date, groupCreateDate: string): Date {
  const anchorDay = parseDate(groupCreateDate).getDay();
  const back = (date.getDay() - anchorDay + 7) % 7;
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() - back);
}

export interface WeekSummary {
  // 이번 주의 시작과 끝 날짜 (YYYY-MM-DD, 양쪽 모두 포함)
  weekStart: string;
  weekEnd: string;
  approved: number;
  pending: number;
  goal: number;
  // 습관을 만든 뒤 처음 맞는 부분 주라 판정하지 않는다 (서버 규칙: 이번 주의 시작 요일 다음 날부터 만든 습관)
  isExempt: boolean;
  goalReached: boolean;
}

interface WeekSummaryInput {
  // 현재 습관의 인증만 넣는다
  verifies: HabitVerifyResponse[];
  goal: number;
  habitCreateDate: string;
  groupCreateDate: string;
  today: Date;
}

export function summarizeThisWeek({ verifies, goal, habitCreateDate, groupCreateDate, today }: WeekSummaryInput): WeekSummary {
  const weekStartDate = weekStartOf(today, groupCreateDate);
  const weekStart = toDateKey(weekStartDate);
  const weekEnd = toDateKey(new Date(weekStartDate.getFullYear(), weekStartDate.getMonth(), weekStartDate.getDate() + 6));
  const todayKey = toDateKey(today);
  const inWeek = verifies.filter((v) => v.verifyDate >= weekStart && v.verifyDate <= todayKey);

  const approved = inWeek.filter((v) => v.status === 'APPROVED').length;
  const pending = inWeek.filter((v) => v.status === 'PENDING').length;
  // 이번 주 도중(시작 요일 다음 날부터)에 만든 습관은 부분 주라서 판정하지 않는다.
  // 시작 요일 당일에 만들었다면 한 주를 온전히 쓸 수 있어 그 주에 판정한다.
  const habitCreateKey = toDateKey(parseDate(habitCreateDate));

  return {
    weekStart,
    weekEnd,
    approved,
    pending,
    goal,
    isExempt: habitCreateKey > weekStart && habitCreateKey <= weekEnd,
    goalReached: approved >= goal,
  };
}

// 습관을 만든 날부터 오늘까지 며칠째인지 (만든 날이 1일째)
export function daysSince(date: string, today: Date): number {
  const start = parseDate(date);
  const end = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  return Math.round((end.getTime() - start.getTime()) / 86_400_000) + 1;
}

const DAY_NAMES = ['일', '월', '화', '수', '목', '금', '토'];

// "10/24(토) ~ 10/30(금)"
export function formatWeekRange(weekStart: string, weekEnd: string): string {
  const format = (key: string) => {
    const date = parseDate(key);
    return `${date.getMonth() + 1}/${date.getDate()}(${DAY_NAMES[date.getDay()]})`;
  };
  return `${format(weekStart)} ~ ${format(weekEnd)}`;
}
