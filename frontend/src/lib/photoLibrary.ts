import type { GroupPhoto } from './habitApi';

// 한 줄에 놓이는 사진 수. 뒤로 갈수록 사진이 커지고 적게 보인다.
export type PhotoColumns = 1 | 3 | 5 | 15;
export const PHOTO_LEVELS: PhotoColumns[] = [15, 5, 3, 1];
export const DEFAULT_PHOTO_COLUMNS: PhotoColumns = 3;

// 칸 사이 간격(px)
export const PHOTO_GAP: Record<PhotoColumns, number> = { 15: 1, 5: 3, 3: 3, 1: 8 };

// 행 높이 비율(세로/가로)의 범위. 1장 단계에서 아주 길거나 납작한 사진이 화면을 독차지하지 않게 한다.
const MIN_RATIO = 0.35;
const MAX_SINGLE_RATIO = 3;

// 사진의 세로/가로 비율. 크기를 모르면(저장 전 인증) 정사각형으로 본다.
export function photoRatio(photo: Pick<GroupPhoto, 'imageWidth' | 'imageHeight'>): number {
  const { imageWidth, imageHeight } = photo;
  if (!imageWidth || !imageHeight || imageWidth <= 0 || imageHeight <= 0) return 1;
  return imageHeight / imageWidth;
}

export function chunkRows<T>(items: T[], columns: number): T[][] {
  const rows: T[][] = [];
  for (let i = 0; i < items.length; i += columns) {
    rows.push(items.slice(i, i + columns));
  }
  return rows;
}

// 행 높이 비율(세로/가로).
// - 1장 단계는 사진 원본 비율을 따른다.
// - 여러 장 단계는 그 행에서 가장 세로로 긴 사진을 기준으로 하되 정사각형까지만 키운다.
//   (가로 사진만 있는 행은 낮고, 세로·정사각 사진이 섞이면 정사각형이 된다)
export function rowRatio(row: GroupPhoto[], columns: PhotoColumns): number {
  if (row.length === 0) return 1;
  if (columns === 1) {
    return Math.min(MAX_SINGLE_RATIO, Math.max(MIN_RATIO, photoRatio(row[0])));
  }
  return Math.max(MIN_RATIO, Math.min(1, Math.max(...row.map(photoRatio))));
}

// "2026-10-26" 같은 날짜 문자열에서 연·월·일을 읽는다. (배열 [2026, 10, 26] 형태도 받는다)
export function parseYmd(value: unknown): { year: number; month: number; day: number } | null {
  if (Array.isArray(value) && value.length >= 3) {
    return { year: Number(value[0]), month: Number(value[1]), day: Number(value[2]) };
  }
  if (typeof value === 'string') {
    const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(value);
    if (match) return { year: Number(match[1]), month: Number(match[2]), day: Number(match[3]) };
  }
  return null;
}

// 사진 날짜를 "2026-10-26" 문자열로 맞춘다. 날짜를 읽을 수 없으면 원래 값을 문자열로 돌려준다.
export function normalizeDate(value: unknown): string {
  const parsed = parseYmd(value);
  if (!parsed) return String(value ?? '');
  const mm = String(parsed.month).padStart(2, '0');
  const dd = String(parsed.day).padStart(2, '0');
  return `${parsed.year}-${mm}-${dd}`;
}

export function formatShortDate(value: string): string {
  const parsed = parseYmd(value);
  if (!parsed) return value;
  return `${parsed.year}.${String(parsed.month).padStart(2, '0')}.${String(parsed.day).padStart(2, '0')}`;
}

// "2026년 10월"
export function formatYearMonth(value: string): string {
  const parsed = parseYmd(value);
  return parsed ? `${parsed.year}년 ${parsed.month}월` : '';
}

export function yearMonthKey(value: string): number {
  const parsed = parseYmd(value);
  return parsed ? parsed.year * 100 + parsed.month : 0;
}

// 사진 목록에서 월이 바뀌는 첫 사진의 id → 그 사진이 속한 행 번호.
// 목록이 날짜 내림차순이라 월이 바뀌는 곳이 곧 "그 달의 가장 늦은 날짜"가 시작되는 자리다.
export function monthStartRows(rows: GroupPhoto[][]): { rowIndex: number; label: string }[] {
  const starts: { rowIndex: number; label: string }[] = [];
  let previous: number | null = null;

  rows.forEach((row, rowIndex) => {
    for (const photo of row) {
      const key = yearMonthKey(photo.verifyDate);
      if (key !== previous) {
        starts.push({ rowIndex, label: formatYearMonth(photo.verifyDate) });
        previous = key;
      }
    }
  });

  return starts;
}

// 행 번호별로 월 라벨의 위치(px)를 정한다. 짧은 달이 이어져 라벨이 겹치면 바로 아래로 밀어 낸다.
// rowTops는 각 행의 위쪽 위치, labelHeights는 각 라벨의 높이다.
export function placeMonthLabels(
  rowTops: number[],
  labelHeights: number[],
  gap = 4,
  inset = 6
): number[] {
  const tops: number[] = [];
  let previousBottom = Number.NEGATIVE_INFINITY;

  rowTops.forEach((rowTop, index) => {
    const top = Math.max(rowTop + inset, previousBottom + gap);
    tops.push(top);
    previousBottom = top + (labelHeights[index] ?? 0);
  });

  return tops;
}

// 다음 단계로 이동한다. 범위를 벗어나면 그대로 둔다. delta가 +1이면 사진이 커진다.
export function stepColumns(current: PhotoColumns, delta: 1 | -1): PhotoColumns {
  const index = PHOTO_LEVELS.indexOf(current) + delta;
  return PHOTO_LEVELS[Math.min(PHOTO_LEVELS.length - 1, Math.max(0, index))];
}
