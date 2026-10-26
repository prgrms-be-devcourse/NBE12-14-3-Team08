#!/usr/bin/env bash
# 주간 판정 부하 테스트 한 케이스를 실행한다: 시드 초기화 → k6(판정 + 동시 요청) → 구간별 집계.
# 사용: loadtest/weekly-check/run-case.sh <습관 수> <single|chunked> [묶음 크기=100] [프로브 시간(초)=60] [잠금 프로브 수=100]
# 사전 조건: 앱을 loadtest 프로파일로 실행, naegi-postgres 컨테이너 실행, Docker 실행
set -euo pipefail
HABITS="${1:?습관 수가 필요합니다}"
RUN_MODE="${2:?single 또는 chunked 가 필요합니다}"
CHUNK="${3:-100}"
DURATION="${4:-60}"
LOCK_PROBES="${5:-100}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

"$ROOT/loadtest/weekly-check/seed.sh" "$HABITS" > /dev/null
RAW="/k6/results/raw-${RUN_MODE}-${HABITS}.json"
# Git Bash(Windows)가 /k6/... 를 윈도우 경로로 바꾸지 않도록 MSYS_NO_PATHCONV=1 을 둔다.
MSYS_NO_PATHCONV=1 docker compose -f docker-compose.monitoring.yml run --rm --no-deps \
  -e MODE=batch -e HABITS="$HABITS" -e RUN_MODE="$RUN_MODE" -e CHUNK="$CHUNK" \
  -e PROBE_DURATION="${DURATION}s" -e LOCK_PROBES="$LOCK_PROBES" \
  k6 run --quiet --out "json=$RAW" /k6/weekly-check.js 2>&1 | grep -E "WEEKLY_CHECK|===|지연|소요" || true
echo "--- 구간별 집계 (ms)"
node "$ROOT/loadtest/weekly-check/analyze.mjs" "$ROOT/k6/results/raw-${RUN_MODE}-${HABITS}.json"
