// 소셜 로그인(인가 코드 방식)의 프론트 쪽 절차.
// 1) 로그인 화면으로 보내기 전에 state를 만들어 sessionStorage에 저장하고
// 2) 공급자가 /auth/callback/{provider}로 돌려보내면 state가 같은지 확인한 뒤 코드를 서버로 넘긴다.
// 코드와 토큰 교환은 서버가 하므로 클라이언트 시크릿은 프론트에 두지 않는다.

export type SocialProvider = 'google';

const STATE_KEY = 'naegi-social-state';
const GOOGLE_AUTH_URL = 'https://accounts.google.com/o/oauth2/v2/auth';

interface PendingLogin {
  state: string;
  provider: SocialProvider;
  redirectTo: string;
}

export function isSocialLoginProvider(value: string): value is SocialProvider {
  return value === 'google';
}

// 공급자가 로그인을 마친 뒤 돌려보낼 주소. 공급자 콘솔에 등록한 리다이렉트 URI와 정확히 같아야 한다.
export function socialRedirectUri(provider: SocialProvider): string {
  return `${window.location.origin}/auth/callback/${provider}`;
}

// 클라이언트 ID가 없으면(로컬에서 설정하지 않은 경우) 소셜 로그인 버튼을 보여 주지 않는다.
export function isSocialLoginConfigured(provider: SocialProvider): boolean {
  switch (provider) {
    case 'google':
      return Boolean(process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID);
  }
}

function safeRedirect(path: string): string {
  return path.startsWith('/') && !path.startsWith('//') ? path : '/main';
}

export function startSocialLogin(provider: SocialProvider, redirectTo = '/main') {
  const clientId = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID;
  if (!clientId) throw new Error('구글 로그인이 설정되지 않았습니다.');

  const state = crypto.randomUUID();
  const pending: PendingLogin = { state, provider, redirectTo: safeRedirect(redirectTo) };
  window.sessionStorage.setItem(STATE_KEY, JSON.stringify(pending));

  const params = new URLSearchParams({
    client_id: clientId,
    redirect_uri: socialRedirectUri(provider),
    response_type: 'code',
    // 이메일은 쓰지 않으므로 요청하지 않는다.
    scope: 'openid profile',
    state,
    prompt: 'select_account',
  });
  // 구글 로그인 화면(외부 주소)으로 나가는 이동이라 Next 라우터를 쓸 수 없다.
  // eslint-disable-next-line @next/next/no-location-assign-relative-destination
  window.location.assign(`${GOOGLE_AUTH_URL}?${params.toString()}`);
}

// 돌아온 state가 로그인을 시작할 때 저장한 값과 같으면 로그인 후 이동할 주소를 돌려준다.
// 한 번 확인하면 지워서 같은 state를 다시 쓸 수 없다. 다르거나 없으면 null이다.
export function consumeSocialState(provider: SocialProvider, receivedState: string | null): string | null {
  const stored = window.sessionStorage.getItem(STATE_KEY);
  window.sessionStorage.removeItem(STATE_KEY);
  if (!stored || !receivedState) return null;

  try {
    const pending = JSON.parse(stored) as Partial<PendingLogin>;
    if (pending.provider !== provider || pending.state !== receivedState) return null;
    return safeRedirect(pending.redirectTo ?? '/main');
  } catch {
    return null;
  }
}
