// k6 원본 지표(--out json)를 읽어, 주간 판정이 도는 구간 안과 밖의 요청 지연을 나눠 집계한다.
// 사용: node loadtest/weekly-check/analyze.mjs <k6 원본 json 경로>
import { readFileSync } from 'node:fs';

const file = process.argv[2];
if (!file) {
  console.error('사용: node analyze.mjs <k6 원본 json 경로>');
  process.exit(1);
}

const points = [];
let startedAt = null;
let finishedAt = null;

for (const line of readFileSync(file, 'utf8').split('\n')) {
  if (!line.trim()) continue;
  const row = JSON.parse(line);
  if (row.type !== 'Point') continue;
  const { time, value, tags = {} } = row.data;
  if (row.metric === 'weekly_started_at_ms') startedAt = value;
  else if (row.metric === 'weekly_finished_at_ms') finishedAt = value;
  else if (row.metric === 'http_req_duration' && (tags.scenario === 'read_probe' || tags.scenario === 'lock_probe')) {
    // k6 지표의 시각은 요청이 끝난 시각이다. 판정 중에 막혔다가 판정이 끝난 뒤 응답받은 요청도 "판정 중"으로 세도록 시작 시각으로 분류한다.
    points.push({ scenario: tags.scenario, at: Date.parse(time) - value, ms: value, status: tags.status });
  }
}

function stats(values) {
  if (values.length === 0) return null;
  const sorted = [...values].sort((a, b) => a - b);
  const pick = (p) => sorted[Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1)];
  const avg = values.reduce((a, b) => a + b, 0) / values.length;
  return { n: values.length, avg, p50: pick(50), p95: pick(95), p99: pick(99), max: sorted[sorted.length - 1] };
}

const fmt = (n) => (n === undefined ? '-' : n.toFixed(1).padStart(8));
const row = (label, s) =>
  s
    ? `${label.padEnd(26)} n=${String(s.n).padStart(5)}  avg=${fmt(s.avg)}  p50=${fmt(s.p50)}  p95=${fmt(s.p95)}  p99=${fmt(s.p99)}  max=${fmt(s.max)}`
    : `${label.padEnd(26)} (요청 없음)`;

if (startedAt === null || finishedAt === null) {
  console.log('판정 시작·종료 시각이 없습니다. (MODE=batch 로 실행했는지 확인)');
  process.exit(0);
}

console.log(`판정 구간: ${((finishedAt - startedAt) / 1000).toFixed(1)}초`);
for (const [scenario, label] of [['read_probe', '일반 조회(잠금 없음)'], ['lock_probe', '습관 포기(행 잠금)']]) {
  const all = points.filter((p) => p.scenario === scenario);
  const during = all.filter((p) => p.at >= startedAt && p.at <= finishedAt).map((p) => p.ms);
  const outside = all.filter((p) => p.at < startedAt || p.at > finishedAt).map((p) => p.ms);
  console.log(row(`${label} · 판정 중`, stats(during)));
  console.log(row(`${label} · 판정 밖`, stats(outside)));
}
