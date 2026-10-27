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
  approved: number;
  pending: number;
  goal: number;
  // 습관을 만든 주는 판정하지 않는다
  isFirstWeek: boolean;
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
  const weekStart = toDateKey(weekStartOf(today, groupCreateDate));
  const todayKey = toDateKey(today);
  const inWeek = verifies.filter((v) => v.verifyDate >= weekStart && v.verifyDate <= todayKey);

  const approved = inWeek.filter((v) => v.status === 'APPROVED').length;
  const pending = inWeek.filter((v) => v.status === 'PENDING').length;
  const habitWeekStart = toDateKey(weekStartOf(parseDate(habitCreateDate), groupCreateDate));

  return {
    approved,
    pending,
    goal,
    isFirstWeek: habitWeekStart === weekStart,
    goalReached: approved >= goal,
  };
}

// 습관을 만든 날부터 오늘까지 며칠째인지 (만든 날이 1일째)
export function daysSince(date: string, today: Date): number {
  const start = parseDate(date);
  const end = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  return Math.round((end.getTime() - start.getTime()) / 86_400_000) + 1;
}
