
/**
 * k6 부하 테스트 스크립트
 * 대상: POST /api/groups
 *
 * 시나리오:
 * - Ramp-up: 1분 동안 VU를 50명까지 증가
 * - Hold: 3분 동안 VU 50명 유지
 * - Ramp-down: 1분 동안 VU를 0명으로 감소
 *
 * 실행 전 확인:
 * - Spring Boot 서버가 실행 중이어야 합니다.
 * - Docker 컨테이너에서 호스트 서버에 접근할 때
 *   BASE_URL에 http://host.docker.internal:8080을 사용합니다.
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const errorRate = new Rate('errors');

export const options = {
  stages: [
    { duration: '1m', target: 50 },
    { duration: '3m', target: 50 },
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],
    errors: ['rate<0.01'],
  },
};

function createGroup() {
  const baseUrl = __ENV.BASE_URL || 'http://host.docker.internal:8080';
  const url = `${baseUrl}/api/groups`;

  const payload = JSON.stringify({
    title: `test-group-${__VU}-${__ITER}`,
    description: 'Load-test generated group',
    deadline: '2026-12-31',
    penalty: '테스트 벌칙',
    password: 'test1234',
    memberLimit: 5,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
    timeout: '60s',
  };

  const res = http.post(url, payload, params);

  let responseId;

  // 연결 실패 또는 응답 본문이 없는 경우 JSON 파싱을 시도하지 않습니다.
  if (res.status > 0 && res.body) {
    try {
      const body = res.json();
      responseId = body.id;
    } catch (_) {
      responseId = undefined;
    }
  }

  const success = check(res, {
    'status is 201': (r) => r.status === 201,
    'response has id': () => responseId !== undefined,
  });

  errorRate.add(!success);

  sleep(1);
}

export default function () {
  createGroup();
}
