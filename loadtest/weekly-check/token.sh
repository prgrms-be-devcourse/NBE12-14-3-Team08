#!/usr/bin/env bash
# 회원 id로 액세스 토큰(JWT)을 만든다. 사용: loadtest/weekly-check/token.sh <회원 id>
# 서명 키는 기본값(local-development-jwt-secret-key-1234567890)이다. 바꿨다면 JWT_SECRET 환경변수로 넘긴다.
set -euo pipefail
ID="${1:?회원 id가 필요합니다}"
SECRET="${JWT_SECRET:-local-development-jwt-secret-key-1234567890}"
b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
NOW=$(date +%s)
HEADER=$(printf '{"alg":"HS256"}' | b64url)
PAYLOAD=$(printf '{"sub":"%s","tokenType":"access","iat":%s,"exp":%s}' "$ID" "$NOW" "$((NOW + 3600))" | b64url)
SIG=$(printf '%s.%s' "$HEADER" "$PAYLOAD" | openssl dgst -sha256 -hmac "$SECRET" -binary | b64url)
printf '%s.%s.%s' "$HEADER" "$PAYLOAD" "$SIG"
