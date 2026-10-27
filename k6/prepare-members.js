// 스트레스 테스트용 회원을 미리 만들어 토큰을 파일로 저장한다. (k6/join-stress.js가 읽는다)
// 회원 수천 명의 가입·로그인을 측정마다 하면 BCrypt 때문에 몇 분씩 걸리고,
// 토큰 수천 개를 setup에서 넘기면 k6가 가상 사용자마다 복사해서 메모리가 모자라다.
// 그래서 한 번 만들어 파일로 두고, 측정 때는 모든 가상 사용자가 한 벌을 같이 읽는다.
//
// 실행 예: k6 run -e COUNT=12000 -e OUT=k6/results/stress/tokens.json k6/prepare-members.js
// 서버는 JWT_ACCESS_EXPIRATION을 길게(예: 86400000, 하루) 켜야 측정하는 동안 토큰이 만료되지 않는다.
// 토큰 파일은 로컬 테스트 회원 것이지만 커밋하지 않는다. (k6/results/는 git에서 제외)
import http from 'k6/http';
import { fail } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const COUNT = parseInt(__ENV.COUNT || '100', 10);
const OUT = __ENV.OUT || 'tokens.json';
const MEMBER_PASSWORD = 'loadtest1234';
const JSON_HEADERS = { 'Content-Type': 'application/json' };
const BATCH_SIZE = 20;

export const options = {
    setupTimeout: '30m',
    batch: BATCH_SIZE,
    batchPerHost: BATCH_SIZE,
    // setup만 쓰고 본 공연은 없다
    scenarios: { noop: { executor: 'shared-iterations', vus: 1, iterations: 1, exec: 'noop' } },
};

function request(path, username) {
    return {
        method: 'POST',
        url: `${BASE_URL}${path}`,
        body: JSON.stringify(path === '/api/members'
            ? { nickname: username, username, password: MEMBER_PASSWORD }
            : { username, password: MEMBER_PASSWORD }),
        params: { headers: JSON_HEADERS },
    };
}

export function setup() {
    const runId = Date.now();
    const tokens = [];
    for (let i = 0; i < COUNT; i += BATCH_SIZE) {
        const chunk = Array.from({ length: Math.min(BATCH_SIZE, COUNT - i) }, (_, j) => `st-${runId}-${i + j + 1}`);
        http.batch(chunk.map((u) => request('/api/members', u))).forEach((res, idx) => {
            if (res.status !== 201 && res.status !== 200) fail(`회원가입 실패 ${chunk[idx]}: ${res.status} ${res.body}`);
        });
        http.batch(chunk.map((u) => request('/api/auth/login', u))).forEach((res, idx) => {
            if (res.status !== 200) fail(`로그인 실패 ${chunk[idx]}: ${res.status} ${res.body}`);
            tokens.push(res.json('accessToken'));
        });
        if ((i / BATCH_SIZE) % 50 === 0) console.log(`${i + chunk.length}/${COUNT}`);
    }
    return { tokens };
}

export function noop() {}

export function handleSummary(data) {
    const tokens = data.setup_data ? data.setup_data.tokens : [];
    console.log(`토큰 ${tokens.length}개 저장: ${OUT}`);
    return { [OUT]: JSON.stringify(tokens) };
}
