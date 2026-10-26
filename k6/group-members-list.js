import http from 'k6/http';
import { check, sleep, fail } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://docker.internal';
const GROUP_ID = __ENV.GROUP_ID || 1;

export const options = {
    stages: [
        { duration: '30s', target: 10 },  // Warm-up 단계
        { duration: '2m',  target: 50 },  // 정상 부하 유지 단계
        { duration: '30s', target: 0 },   // Ramp-down 단계
    ],
    thresholds: {
        http_req_duration: ['p(95)<200'], // 95%의 요청이 200ms 이하일 것
        http_req_failed: ['rate<0.01'],   // 실패율이 1% 미만일 것
    },
    systemTags: ['status', 'method', 'url', 'name', 'error', 'check'],
};

export function setup() {
    const loginUrl = `${BASE_URL}/api/auth/login`;
    console.log(`[Setup] 로그인 시도 중... URL: ${loginUrl}`);

    const payload = JSON.stringify({
        username: 'asd',
        password: '1234'
    });

    const params = {
        headers: { 'Content-Type': 'application/json' }
    };

    const loginRes = http.post(loginUrl, payload, params);

    if (loginRes.status !== 200) {
        console.error(`❌ [Setup 실패] 상태 코드: ${loginRes.status}, 바디: ${loginRes.body}`);
        return { token: null };
    }

    const token = loginRes.json('accessToken');
    console.log(`✅ [Setup 성공] 토큰 발급 완료`);

    return { token };
}

export default function (data) {
    if (!data || !data.token) {
        fail('유효한 JWT 토큰이 없어 테스트를 진행할 수 없습니다.');
    }

    const url = `${BASE_URL}/api/groups/${GROUP_ID}/members`;
    const params = {
        headers: {
            'Authorization': `Bearer ${data.token}`,
            'Content-Type': 'application/json',
        },
        tags: {
            name: 'group_members_list_get',
        },
        timeout: '60s',
    };

    const res = http.get(url, params);

    check(res, {
        'status 200': (r) => r.status === 200,
        'is json array': (r) => {
            const body = r.json();
            return Array.isArray(body) || Array.isArray(r.json('content'));
        },
    });

    sleep(1);
}
