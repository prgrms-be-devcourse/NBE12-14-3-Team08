/**
 * 주간 판정 스케줄러 부하 테스트 (#93)
 *
 * 준비: loadtest/weekly-check/seed.sh <습관 수> 로 시드를 넣고, 앱을 --spring.profiles.active=loadtest 로 실행한다.
 * 회원 id = 습관 id = 1..HABITS 라서 습관 i의 주인은 회원 i다.
 *
 * MODE
 *  - baseline: 판정을 돌리지 않고 일반 요청(습관 조회)의 지연만 잰다. (비교 기준)
 *  - batch   : 시작 RUN_DELAY 뒤에 판정을 한 번 실행하고, 그동안 일반 요청과 습관 포기 요청(행 잠금을 쓰는 요청)을 같이 보낸다.
 *
 * 환경변수: BASE_URL, MODE, HABITS, PROBE_RATE(초당 조회 수), PROBE_DURATION, RUN_DELAY, LOCK_PROBES, LOCK_RATE,
 *           RUN_MODE(single=현재 방식, chunked=묶음 트랜잭션), CHUNK(묶음 크기)
 *
 * 판정이 도는 구간(시작·종료 시각)은 weekly_started_at_ms / weekly_finished_at_ms 로 남긴다.
 * loadtest/weekly-check/run-case.sh 가 --out json 으로 받은 원본 지표를 analyze.mjs 로 구간 안팎을 나눠 집계한다.
 *
 * 실행 예 (프로젝트 루트):
 *   docker compose -f docker-compose.monitoring.yml run --rm -e MODE=batch -e HABITS=1000 k6 run /k6/weekly-check.js
 */
import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { Trend, Counter, Gauge } from 'k6/metrics';
import { authHeaders } from './lib/jwt.js';

const baseUrl = (__ENV.BASE_URL || 'http://host.docker.internal:8080').replace(/\/$/, '');
const mode = __ENV.MODE || 'batch';
const habits = parseInt(__ENV.HABITS || '1000', 10);
const runMode = __ENV.RUN_MODE || 'single';
const chunk = parseInt(__ENV.CHUNK || '100', 10);
const probeRate = parseInt(__ENV.PROBE_RATE || '20', 10);
const probeDuration = __ENV.PROBE_DURATION || '60s';
const runDelay = __ENV.RUN_DELAY || '10s';
const lockProbes = parseInt(__ENV.LOCK_PROBES || '100', 10);
const lockRate = parseInt(__ENV.LOCK_RATE || '5', 10);

const readLatency = new Trend('probe_read_ms', true);
const lockLatency = new Trend('probe_lock_ms', true);
const lockStatus = new Counter('probe_lock_status');
const weeklyCheckMs = new Trend('weekly_check_ms', true);
const weeklyStartedAt = new Gauge('weekly_started_at_ms');
const weeklyFinishedAt = new Gauge('weekly_finished_at_ms');

const scenarios = {
  // 일반 요청: 판정이 돌든 말든 계속 보내는 습관 상세 조회. 잠금을 쓰지 않는 요청이다.
  read_probe: {
    executor: 'constant-arrival-rate',
    rate: probeRate,
    timeUnit: '1s',
    duration: probeDuration,
    preAllocatedVUs: 20,
    maxVUs: 300,
    exec: 'readProbe',
  },
};

if (mode === 'batch') {
  // 판정 실행: 한 번만. 끝날 때까지 응답을 기다린다.
  scenarios.trigger = {
    executor: 'per-vu-iterations',
    vus: 1,
    iterations: 1,
    startTime: runDelay,
    maxDuration: '30m',
    exec: 'trigger',
  };
  // 습관 포기 요청: 습관 행을 잠그는 요청. 판정이 같은 행을 쥐고 있으면 끝날 때까지 기다린다.
  // 판정 시작 직후부터 일정한 속도로 서로 다른 습관에 보낸다.
  scenarios.lock_probe = {
    executor: 'constant-arrival-rate',
    rate: lockRate,
    timeUnit: '1s',
    duration: `${Math.ceil(lockProbes / lockRate)}s`,
    preAllocatedVUs: 20,
    maxVUs: 500,
    startTime: runDelay,
    exec: 'lockProbe',
  };
}

export const options = {
  scenarios,
  thresholds: {
    'http_req_failed{scenario:read_probe}': ['rate<0.01'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function readProbe() {
  const id = 1 + Math.floor(Math.random() * habits);
  const res = http.get(`${baseUrl}/api/habits/${id}`, { headers: authHeaders(id), tags: { name: 'GET /api/habits/{id}' } });
  readLatency.add(res.timings.duration);
  check(res, { 'habit detail 200': (r) => r.status === 200 });
}

// 서로 다른 습관을 고르기 위해 반복 번호를 습관 id 전체에 고르게 퍼뜨린다. (시드가 id 순서로 판정되므로 앞뒤에 골고루 걸린다)
export function lockProbe() {
  const step = Math.max(1, Math.floor(habits / lockProbes));
  const id = 1 + ((exec.scenario.iterationInTest * step) % habits);
  const res = http.post(`${baseUrl}/api/habits/fail/${id}`, null, { headers: authHeaders(id), tags: { name: 'POST /api/habits/fail/{id}' } });
  lockLatency.add(res.timings.duration);
  lockStatus.add(1, { status: String(res.status) });
  check(res, { 'no server error': (r) => r.status < 500 });
}

export function trigger() {
  const res = http.post(`${baseUrl}/loadtest/weekly-check/run?mode=${runMode}&chunk=${chunk}`, null, { headers: authHeaders(1), timeout: '1800s', tags: { name: 'POST /loadtest/weekly-check/run' } });
  const ok = check(res, { 'weekly check 200': (r) => r.status === 200 });
  if (ok) {
    const body = res.json();
    weeklyCheckMs.add(body.elapsedMs);
    weeklyStartedAt.add(body.startedAtMs);
    weeklyFinishedAt.add(body.finishedAtMs);
    console.log(`WEEKLY_CHECK ${JSON.stringify(body)}`);
  }
}

export function handleSummary(data) {
  const name = `/k6/results/weekly-check-${mode}-${runMode}-${habits}.json`;
  return { stdout: textSummary(data), [name]: JSON.stringify(data, null, 2) };
}

// 필요한 지표만 간단히 보여 준다.
function textSummary(data) {
  const lines = [`\n=== 주간 판정 부하 테스트 (MODE=${mode}, RUN_MODE=${runMode}, HABITS=${habits}) ===`];
  const show = (label, metric) => {
    const m = data.metrics[metric];
    if (!m || !m.values) return;
    const v = m.values;
    lines.push(`${label}: avg=${fmt(v.avg)} p95=${fmt(v['p(95)'])} p99=${fmt(v['p(99)'])} max=${fmt(v.max)} (ms, n=${v.count !== undefined ? v.count : '-'})`);
  };
  show('일반 조회 지연', 'probe_read_ms');
  show('습관 포기(행 잠금) 지연', 'probe_lock_ms');
  show('판정 1회 소요', 'weekly_check_ms');
  return lines.join('\n') + '\n';
}

function fmt(n) {
  return n === undefined ? '-' : n.toFixed(1);
}
