// 방 참여 스트레스 테스트: 몇 명부터, 어떻게 깨지는지, 부하가 지나가면 회복하는지 본다.
// (정해진 인원의 응답 시간을 재는 몰림 테스트는 k6/join-concurrency.js)
//
// 회원은 k6/prepare-members.js로 미리 만들어 둔 토큰 파일을 쓴다. 한 벌을 모든 가상 사용자가 같이 읽는다.
//
// 실행 예
//   계단:      k6 run -e MODE=step -e USERS=3000 -e TOKENS=k6/results/stress/tokens.json k6/join-stress.js
//   도착 속도: k6 run -e MODE=arrival -e RATES=50,100,150,200 -e STAGE_SECONDS=10 -e TOKENS=... k6/join-stress.js
//   여러 방:   k6 run -e MODE=step -e USERS=1000 -e ROOMS=100 -e TOKENS=... k6/join-stress.js  (방 100개에 10명씩)
// 환경 변수
//   MODE           step    : USERS명이 같은 방에 한꺼번에 참여 (BATCH명씩 묶어 가상 사용자 하나가 동시에 보냄)
//                  arrival : 초당 RATES명씩 계속 도착. 단계마다 STAGE_SECONDS초. 따라가지 못하면 dropped_iterations가 생김
//   USERS          step에서 동시에 참여하는 회원 수
//   BATCH          step에서 가상 사용자 하나가 동시에 보내는 참여 요청 수 (기본 20). 가상 사용자 수를 줄여 k6 메모리를 아낀다
//   RATES, STAGE_SECONDS, MAX_VUS   arrival 설정 (기본 50,100,150,200 / 10초 / 2000)
//   LIMIT          방 정원, 방장 포함 (기본 5)
//   TOKENS         토큰 파일 경로 (필수). 상대 경로는 이 스크립트(k6/) 기준이라 절대 경로가 편하다
//   OFFSET         토큰 파일에서 몇 번째 회원부터 쓸지 (기본 0)
//   ROOMS          참여 요청을 나눠 받을 방 수 (기본 1). 요청 i는 방 i % ROOMS로 간다
//                  2 이상이면 방장은 토큰 파일 맨 뒤의 회원들이 맡는다 (참여하는 회원과 겹치지 않게)
//   BYSTANDER_SECONDS  참여와 상관없는 회원 1명이 0.2초마다 내 정보 조회를 하는 시간. 0이면 끔 (기본 0)
//                      회복을 보려면 참여가 끝난 뒤까지 넉넉히 잡고 --out csv로 시간별 응답을 남긴다
import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import exec from 'k6/execution';
import { SharedArray } from 'k6/data';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const MODE = __ENV.MODE || 'step';
const USERS = parseInt(__ENV.USERS || '100', 10);
const BATCH = parseInt(__ENV.BATCH || '20', 10);
const RATES = (__ENV.RATES || '50,100,150,200').split(',').map((r) => parseInt(r, 10));
const STAGE_SECONDS = parseInt(__ENV.STAGE_SECONDS || '10', 10);
const MAX_VUS = parseInt(__ENV.MAX_VUS || '2000', 10);
const LIMIT = parseInt(__ENV.LIMIT || '5', 10);
const OFFSET = parseInt(__ENV.OFFSET || '0', 10);
const ROOMS = parseInt(__ENV.ROOMS || '1', 10);
const BYSTANDER_SECONDS = parseInt(__ENV.BYSTANDER_SECONDS || '0', 10);
const ROOM_PASSWORD = 'room1234';
const MEMBER_PASSWORD = 'loadtest1234';
const JSON_HEADERS = { 'Content-Type': 'application/json' };

if (!__ENV.TOKENS) throw new Error('TOKENS(토큰 파일 경로)가 필요해요. k6/prepare-members.js로 먼저 만드세요.');
const TOKENS = new SharedArray('tokens', () => JSON.parse(open(__ENV.TOKENS)));

const joinSuccess = new Counter('join_success_200');
const joinRejected = new Counter('join_rejected_400');
const joinServerError = new Counter('join_server_error_5xx');
// 응답을 못 받음: k6 요청 시간 제한(60초) 초과, 연결 거절 등 (상태 코드 0)
const joinNoResponse = new Counter('join_no_response');
const joinOther = new Counter('join_other_status');

const scenarios = {};
const joinRequests = MODE === 'step' ? USERS : RATES.reduce((sum, r) => sum + r * STAGE_SECONDS, 0);
if (MODE === 'step') {
    // 모두 동시에 출발. 가상 사용자 하나가 BATCH명분 요청을 한꺼번에 보낸다
    scenarios.join = {
        executor: 'per-vu-iterations', vus: Math.ceil(USERS / BATCH), iterations: 1, maxDuration: '10m', exec: 'joinBatch',
    };
} else if (MODE === 'arrival') {
    // 단계마다 1초 만에 목표 속도로 올리고 STAGE_SECONDS초 유지
    const stages = RATES.flatMap((r) => [{ target: r, duration: '1s' }, { target: r, duration: `${STAGE_SECONDS - 1}s` }]);
    scenarios.join = {
        executor: 'ramping-arrival-rate', startRate: 0, timeUnit: '1s', stages,
        preAllocatedVUs: Math.min(MAX_VUS, 200), maxVUs: MAX_VUS, exec: 'joinOne',
    };
} else {
    throw new Error(`알 수 없는 MODE: ${MODE}`);
}
const ownerTokens = ROOMS > 1 ? ROOMS : 0;
if (OFFSET + joinRequests + ownerTokens > TOKENS.length) {
    throw new Error(`토큰이 모자라요: 필요 ${OFFSET + joinRequests + ownerTokens}, 파일 ${TOKENS.length}`);
}
if (BYSTANDER_SECONDS > 0) {
    scenarios.bystander = { executor: 'constant-vus', vus: 1, duration: `${BYSTANDER_SECONDS}s`, exec: 'bystander' };
}

const thresholds = { 'http_req_duration{name:join}': ['max>=0'], join_server_error_5xx: ['count>=0'] };
if (BYSTANDER_SECONDS > 0) thresholds['http_req_duration{name:me}'] = ['max>=0'];

export const options = {
    setupTimeout: '5m',
    batch: BATCH,
    batchPerHost: BATCH,
    scenarios,
    summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
    thresholds,
};

function post(path, body, token, name) {
    const headers = token ? { ...JSON_HEADERS, Authorization: `Bearer ${token}` } : JSON_HEADERS;
    return http.post(`${BASE_URL}${path}`, JSON.stringify(body), { headers, tags: { name } });
}

// 방장들의 방을 BATCH개씩 동시에 만든다. 방장은 토큰 파일 맨 뒤의 회원
function createRooms(runId) {
    const rooms = [];
    for (let start = 0; start < ROOMS; start += BATCH) {
        const requests = [];
        for (let r = start; r < Math.min(start + BATCH, ROOMS); r++) {
            requests.push({
                method: 'POST',
                url: `${BASE_URL}/api/groups`,
                body: JSON.stringify({ title: `스트레스 ${runId}-${r}`, penalty: '커피 쏘기', password: ROOM_PASSWORD, memberLimit: LIMIT }),
                params: { headers: { ...JSON_HEADERS, Authorization: `Bearer ${TOKENS[TOKENS.length - 1 - r]}` }, tags: { name: 'setup' } },
            });
        }
        http.batch(requests).forEach((res) => {
            if (res.status !== 201) fail(`방 만들기 실패: ${res.status} ${res.body}`);
            rooms.push({ roomId: res.json('id'), inviteCode: res.json('inviteCode'), ownerToken: TOKENS[TOKENS.length - 1 - rooms.length] });
        });
    }
    return rooms;
}

// 방장과 방, 그리고 내 정보 조회를 할 회원 1명을 새로 만든다 (참여할 회원은 토큰 파일)
export function setup() {
    const runId = Date.now();
    const login = (username) => {
        post('/api/members', { nickname: username, username, password: MEMBER_PASSWORD }, null, 'setup');
        const res = post('/api/auth/login', { username, password: MEMBER_PASSWORD }, null, 'setup');
        if (res.status !== 200) fail(`로그인 실패 ${username}: ${res.status} ${res.body}`);
        return res.json('accessToken');
    };
    let rooms;
    if (ROOMS > 1) {
        rooms = createRooms(runId);
    } else {
        const ownerToken = login(`ss-${runId}-owner`);
        const roomRes = post(
            '/api/groups',
            { title: `스트레스 ${runId}`, penalty: '커피 쏘기', password: ROOM_PASSWORD, memberLimit: LIMIT },
            ownerToken,
            'setup',
        );
        if (roomRes.status !== 201) fail(`방 만들기 실패: ${roomRes.status} ${roomRes.body}`);
        rooms = [{ roomId: roomRes.json('id'), inviteCode: roomRes.json('inviteCode'), ownerToken }];
    }
    const bystanderToken = BYSTANDER_SECONDS > 0 ? login(`ss-${runId}-bystander`) : null;
    console.log(`준비 완료: 모드 ${MODE}, 참여 요청 ${joinRequests}건, 방 ${ROOMS}개, 정원 ${LIMIT}`);
    return { rooms, bystanderToken };
}

function record(res) {
    if (res.status === 200) joinSuccess.add(1);
    else if (res.status === 400) joinRejected.add(1);
    else if (res.status >= 500) joinServerError.add(1);
    else if (res.status === 0) joinNoResponse.add(1);
    else joinOther.add(1);
}

function joinRequest(data, index) {
    return {
        method: 'POST',
        url: `${BASE_URL}/api/groups/join/${data.rooms[index % ROOMS].inviteCode}`,
        body: JSON.stringify({ password: ROOM_PASSWORD }),
        params: { headers: { ...JSON_HEADERS, Authorization: `Bearer ${TOKENS[OFFSET + index]}` }, tags: { name: 'join' } },
    };
}

// step: 가상 사용자 하나가 자기 몫(BATCH명)의 참여 요청을 동시에 보낸다
export function joinBatch(data) {
    const start = exec.scenario.iterationInTest * BATCH;
    const end = Math.min(start + BATCH, USERS);
    const requests = [];
    for (let i = start; i < end; i++) requests.push(joinRequest(data, i));
    http.batch(requests).forEach(record);
}

// arrival: 도착한 회원 한 명이 참여 요청 1번
export function joinOne(data) {
    const req = joinRequest(data, exec.scenario.iterationInTest);
    record(http.post(req.url, req.body, req.params));
}

export function bystander(data) {
    const res = http.get(`${BASE_URL}/api/members/me`, {
        headers: { Authorization: `Bearer ${data.bystanderToken}` },
        tags: { name: 'me' },
    });
    check(res, { '내 정보 조회는 200': (r) => r.status === 200 });
    sleep(0.2);
}

export function teardown(data) {
    const counts = [];
    for (let start = 0; start < data.rooms.length; start += BATCH) {
        const requests = data.rooms.slice(start, start + BATCH).map((room) => ({
            method: 'GET',
            url: `${BASE_URL}/api/groups/${room.roomId}/members`,
            params: { headers: { Authorization: `Bearer ${room.ownerToken}` }, tags: { name: 'teardown' } },
        }));
        http.batch(requests).forEach((res) => counts.push(res.status === 200 ? res.json().length : -1));
    }
    const total = counts.reduce((sum, n) => sum + n, 0);
    console.log(`끝난 뒤 멤버 수: 방 ${counts.length}개 합계 ${total}, 가장 많은 방 ${Math.max(...counts)} (정원 ${LIMIT}, 방장 포함)`);
    counts.forEach((n) => check(n, { '정원 초과 없음': (c) => c >= 1 && c <= LIMIT }));
}
