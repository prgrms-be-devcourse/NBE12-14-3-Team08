// 방 참여 동시성 부하 테스트
// 정원이 정해진 방에 회원 여러 명이 동시에 참여할 때
//   1) 정원이 지켜지는지 (성공 = 빈자리 수, 나머지 400, 500은 0)
//   2) 응답 시간이 어떻게 되는지
// 를 잰다.
//
// 실행 예: k6 run -e USERS=10 -e LIMIT=5 k6/join-concurrency.js
// 환경 변수
//   BASE_URL     서버 주소 (기본 http://localhost:8080, Docker k6라면 http://host.docker.internal:8080)
//   USERS        동시에 참여 요청을 보내는 회원 수 (기본 10)
//   LIMIT        방 정원, 방장 포함 (기본 5)
//   MODE         join   : 처음 참여하는 회원들이 동시에 참여 (기본)
//                rejoin : 이미 멤버인 회원들이 동시에 다시 참여 (비밀번호 확인을 건너뛰는 경로, LIMIT은 USERS+1 이상으로)
//                idle   : 참여 요청 없이 BYSTANDER만 실행 (다른 API의 평소 응답 시간 기준값)
//                same   : 아직 멤버가 아닌 회원 1명이 참여 요청을 USERS번 동시에 보냄 (연타)
//   WRONG_RATIO  틀린 비밀번호로 참여하는 회원 비율 0~1 (기본 0)
//   BYSTANDER    1이면 참여와 상관없는 회원 1명이 그동안 0.2초마다 내 정보 조회(GET /api/members/me)
import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const USERS = parseInt(__ENV.USERS || '10', 10);
const LIMIT = parseInt(__ENV.LIMIT || '5', 10);
const MODE = __ENV.MODE || 'join';
const WRONG_RATIO = parseFloat(__ENV.WRONG_RATIO || '0');
const BYSTANDER = __ENV.BYSTANDER === '1' || MODE === 'idle';
const WRONG_USERS = Math.round(USERS * WRONG_RATIO);
const MEMBER_PASSWORD = 'loadtest1234';
const ROOM_PASSWORD = 'room1234';
const WRONG_ROOM_PASSWORD = 'wrong-room-password';
const JSON_HEADERS = { 'Content-Type': 'application/json' };
// 준비 단계 회원가입·로그인을 몇 명씩 묶어 보낼지 (BCrypt가 느려서 하나씩 보내면 오래 걸린다)
const SETUP_BATCH_SIZE = 20;
// 내 정보 조회를 참여 요청이 다 끝날 때까지 계속하도록, 한 명당 약 0.1초 줄 서기 기준으로 넉넉히 잡는다
const BYSTANDER_DURATION = MODE === 'idle' ? '10s' : `${Math.ceil(USERS * 0.1) + 5}s`;

const joinSuccess = new Counter('join_success_200');
const joinRejected = new Counter('join_rejected_400');
const joinWrongPassword = new Counter('join_wrong_password_403');
const joinServerError = new Counter('join_server_error_5xx');
const joinOther = new Counter('join_other_status');

const scenarios = {};
if (MODE !== 'idle') {
    // 회원 한 명당 참여 요청 1번, 모두 동시에 출발
    scenarios.join = { executor: 'per-vu-iterations', vus: USERS, iterations: 1, maxDuration: '2m', exec: 'join' };
}
if (BYSTANDER) {
    scenarios.bystander = { executor: 'constant-vus', vus: 1, duration: BYSTANDER_DURATION, exec: 'bystander' };
}

// 준비 단계 요청도 http_req_duration에 섞이므로, 요청 종류별 응답 시간을 따로 본다.
// (k6는 threshold를 건 태그별 지표만 요약에 따로 보여 줘서, 항상 통과하는 조건을 건다)
const thresholds = { join_server_error_5xx: ['count==0'] };
if (MODE !== 'idle') thresholds['http_req_duration{name:join}'] = ['max>=0'];
if (BYSTANDER) thresholds['http_req_duration{name:me}'] = ['max>=0'];

export const options = {
    setupTimeout: '10m',
    scenarios,
    summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
    thresholds,
};

function post(path, body, token, name) {
    const headers = token ? { ...JSON_HEADERS, Authorization: `Bearer ${token}` } : JSON_HEADERS;
    return http.post(`${BASE_URL}${path}`, JSON.stringify(body), { headers, tags: { name } });
}

function signupRequest(username) {
    return {
        method: 'POST',
        url: `${BASE_URL}/api/members`,
        body: JSON.stringify({ nickname: username, username, password: MEMBER_PASSWORD }),
        params: { headers: JSON_HEADERS, tags: { name: 'setup' } },
    };
}

function loginRequest(username) {
    return {
        method: 'POST',
        url: `${BASE_URL}/api/auth/login`,
        body: JSON.stringify({ username, password: MEMBER_PASSWORD }),
        params: { headers: JSON_HEADERS, tags: { name: 'setup' } },
    };
}

// 회원 여러 명을 가입시키고 로그인해 토큰을 돌려준다
function createMembersWithTokens(usernames) {
    const tokens = [];
    for (let i = 0; i < usernames.length; i += SETUP_BATCH_SIZE) {
        const chunk = usernames.slice(i, i + SETUP_BATCH_SIZE);

        http.batch(chunk.map(signupRequest)).forEach((res, idx) => {
            if (res.status !== 201 && res.status !== 200) {
                fail(`회원가입 실패 ${chunk[idx]}: ${res.status} ${res.body}`);
            }
        });

        http.batch(chunk.map(loginRequest)).forEach((res, idx) => {
            if (res.status !== 200) {
                fail(`로그인 실패 ${chunk[idx]}: ${res.status} ${res.body}`);
            }
            tokens.push(res.json('accessToken'));
        });
    }
    return tokens;
}

// 측정 전 준비: 방장 → 방 만들기 → 참여할 회원들 가입·로그인 (이 시간은 결과에 들어가지 않는다)
export function setup() {
    const runId = Date.now();
    const [ownerToken] = createMembersWithTokens([`lt-${runId}-owner`]);

    const roomRes = post(
        '/api/groups',
        { title: `부하테스트 ${runId}`, penalty: '커피 쏘기', password: ROOM_PASSWORD, memberLimit: LIMIT },
        ownerToken,
        'setup',
    );
    if (roomRes.status !== 201) {
        fail(`방 만들기 실패: ${roomRes.status} ${roomRes.body}`);
    }
    const inviteCode = roomRes.json('inviteCode');

    const userCount = MODE === 'idle' ? 0 : MODE === 'same' ? 1 : USERS;
    const usernames = Array.from({ length: userCount }, (_, i) => `lt-${runId}-${i + 1}`);
    const tokens = createMembersWithTokens(usernames);

    // rejoin: 측정 전에 모두 한 명씩 먼저 참여시켜 둔다
    if (MODE === 'rejoin') {
        tokens.forEach((token, idx) => {
            const res = post(`/api/groups/join/${inviteCode}`, { password: ROOM_PASSWORD }, token, 'setup');
            if (res.status !== 200) {
                fail(`미리 참여 실패 ${usernames[idx]}: ${res.status} ${res.body}`);
            }
        });
    }

    const [bystanderToken] = BYSTANDER ? createMembersWithTokens([`lt-${runId}-bystander`]) : [null];

    console.log(`준비 완료: 모드 ${MODE}, 방 ${roomRes.json('id')}, 초대코드 ${inviteCode}, 회원 ${tokens.length}명, 틀린 비밀번호 ${WRONG_USERS}명`);
    return { roomId: roomRes.json('id'), inviteCode, ownerToken, tokens, bystanderToken };
}

// 측정: 가상 사용자마다 자기 토큰으로 참여 요청 1번
export function join(data) {
    // __VU는 시나리오끼리 이어서 매겨져 BYSTANDER와 같이 돌면 어긋나므로, 참여 시나리오 안의 순번(0부터)을 쓴다
    const index = exec.scenario.iterationInTest;
    const token = MODE === 'same' ? data.tokens[0] : data.tokens[index];
    const wrongPassword = index < WRONG_USERS;
    const password = wrongPassword ? WRONG_ROOM_PASSWORD : ROOM_PASSWORD;
    const res = post(`/api/groups/join/${data.inviteCode}`, { password }, token, 'join');

    if (res.status === 200) joinSuccess.add(1);
    else if (res.status === 400) joinRejected.add(1);
    else if (res.status === 403) joinWrongPassword.add(1);
    else if (res.status >= 500) joinServerError.add(1);
    else joinOther.add(1);

    if (wrongPassword) {
        check(res, { '틀린 비밀번호는 403': (r) => r.status === 403 });
    } else {
        check(res, { '참여 응답은 200(성공) 또는 400(정원 초과)': (r) => r.status === 200 || r.status === 400 });
    }
}

// 측정: 참여와 상관없는 회원이 0.2초마다 내 정보 조회
export function bystander(data) {
    const res = http.get(`${BASE_URL}/api/members/me`, {
        headers: { Authorization: `Bearer ${data.bystanderToken}` },
        tags: { name: 'me' },
    });
    check(res, { '내 정보 조회는 200': (r) => r.status === 200 });
    sleep(0.2);
}

// 측정 후 확인: 실제 멤버 수가 기대값과 같은지
export function teardown(data) {
    if (MODE === 'idle') return;

    const res = http.get(`${BASE_URL}/api/groups/${data.roomId}/members`, {
        headers: { Authorization: `Bearer ${data.ownerToken}` },
        tags: { name: 'teardown' },
    });
    const memberCount = res.json().length;
    const correctUsers = USERS - WRONG_USERS;
    const expected = MODE === 'rejoin' ? USERS + 1 : MODE === 'same' ? 2 : Math.min(LIMIT, correctUsers + 1);
    console.log(`끝난 뒤 멤버 수: ${memberCount} (기대 ${expected}, 정원 ${LIMIT}, 방장 포함)`);

    check(memberCount, {
        '끝난 뒤 멤버 수 = 기대값 (정원 초과 없음)': (n) => n === expected,
    });
}
