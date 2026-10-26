#!/usr/bin/env bash
# 주간 판정 부하 테스트 시드(초기화 겸용). 사용: loadtest/weekly-check/seed.sh [습관 수=1000]
# docker-compose.yml 의 postgres 컨테이너(naegi-postgres)에서 부하 테스트 전용 DB(naegi_load)에 실행한다.
set -euo pipefail
HABITS="${1:-1000}"
if (( HABITS % 10 != 0 )); then echo "습관 수는 10의 배수여야 합니다: $HABITS" >&2; exit 1; fi
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
docker exec -i "${PG_CONTAINER:-naegi-postgres}" psql -U naegi -d naegi_load -v habits="$HABITS" -q -f - < "$DIR/seed.sql"
