// 부하 테스트용 액세스 토큰 생성. 서버와 같은 비밀키(기본값)로 HS256 서명한다.
// 회원마다 로그인(BCrypt)을 거치면 준비 시간이 길어지므로, 시드된 회원 id로 바로 토큰을 만든다.
import crypto from 'k6/crypto';
import encoding from 'k6/encoding';

const SECRET = __ENV.JWT_SECRET || 'local-development-jwt-secret-key-1234567890';
const cache = {};

function b64url(text) {
  return encoding.b64encode(text, 'rawurl');
}

export function tokenFor(memberId) {
  if (cache[memberId]) {
    return cache[memberId];
  }
  const now = Math.floor(Date.now() / 1000);
  const header = b64url(JSON.stringify({ alg: 'HS256' }));
  const payload = b64url(JSON.stringify({ sub: String(memberId), tokenType: 'access', iat: now, exp: now + 3600 }));
  const signature = crypto.hmac('sha256', SECRET, `${header}.${payload}`, 'base64rawurl');
  cache[memberId] = `${header}.${payload}.${signature}`;
  return cache[memberId];
}

export function authHeaders(memberId) {
  return { Authorization: `Bearer ${tokenFor(memberId)}`, 'Content-Type': 'application/json' };
}
