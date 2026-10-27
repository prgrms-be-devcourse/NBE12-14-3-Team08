const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

// 가입 방식. LOCAL은 아이디/비밀번호, 그 외는 소셜 로그인이다.
export type MemberProvider = 'LOCAL' | 'GOOGLE';

export interface MemberResponse {
  memberId: number;
  nickname: string;
  username: string;
  provider: MemberProvider;
}

interface AuthSession {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
}

const SESSION_KEY = 'naegi-auth-session';

export const getAuthSession = (): AuthSession | null => {
  if (typeof window === 'undefined') return null;
  try {
    const value = window.sessionStorage.getItem(SESSION_KEY);
    if (!value) return null;
    const session = JSON.parse(value) as Partial<AuthSession>;
    return session.accessToken && session.refreshToken
      ? { accessToken: session.accessToken, refreshToken: session.refreshToken, tokenType: session.tokenType || 'Bearer' }
      : null;
  } catch {
    return null;
  }
};

export const saveAuthSession = (session: AuthSession) => {
  window.sessionStorage.setItem(SESSION_KEY, JSON.stringify(session));
};

export const clearAuthSession = () => {
  if (typeof window !== 'undefined') window.sessionStorage.removeItem(SESSION_KEY);
};

async function errorMessage(response: Response): Promise<string> {
  try {
    const body = await response.json() as { message?: string; errors?: string[] };
    return body.errors?.[0] || body.message || `요청에 실패했습니다. (${response.status})`;
  } catch {
    return `요청에 실패했습니다. (${response.status})`;
  }
}

export async function apiRequest<T>(path: string, options: RequestInit = {}, authenticated = false): Promise<T> {
  const send = (accessToken?: string) => fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      ...options.headers,
    },
  });

  const session = authenticated ? getAuthSession() : null;
  if (authenticated && !session) throw new Error('로그인이 필요합니다.');

  let response: Response;
  try {
    response = await send(session?.accessToken);
    if (authenticated && response.status === 401 && session?.refreshToken) {
      const refreshed = await fetch(`${API_BASE_URL}/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: session.refreshToken }),
      });
      if (!refreshed.ok) {
        clearAuthSession();
        throw new Error('로그인이 만료되었습니다. 다시 로그인해주세요.');
      }
      const nextSession = await refreshed.json() as AuthSession;
      saveAuthSession(nextSession);
      response = await send(nextSession.accessToken);
    }
  } catch (error) {
    if (error instanceof Error && error.message === '로그인이 만료되었습니다. 다시 로그인해주세요.') throw error;
    throw new Error('서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.');
  }

  if (authenticated && response.status === 401) {
    clearAuthSession();
    throw new Error('로그인이 만료되었습니다. 다시 로그인해주세요.');
  }
  if (!response.ok) throw new Error(await errorMessage(response));
  const responseBody = await response.text();
  if (!responseBody) return undefined as T;
  return JSON.parse(responseBody) as T;
}

export const memberApi = {
  signup: (nickname: string, username: string, password: string) =>
    apiRequest<MemberResponse>('/members', {
      method: 'POST',
      body: JSON.stringify({ nickname, username, password }),
    }),
  login: (username: string, password: string) =>
    apiRequest<AuthSession>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    }),
  // 소셜 공급자 로그인 화면에서 받은 인가 코드로 로그인한다. redirectUri는 로그인을 시작할 때 쓴 주소와 같아야 한다.
  socialLogin: (provider: string, code: string, redirectUri: string) =>
    apiRequest<AuthSession>(`/auth/social/${provider}`, {
      method: 'POST',
      body: JSON.stringify({ code, redirectUri }),
    }),
  getMe: () => apiRequest<MemberResponse>('/members/me', {}, true),
  updateMe: (nickname: string, currentPassword?: string, newPassword?: string) =>
    apiRequest<MemberResponse>('/members/me', {
      method: 'PATCH',
      body: JSON.stringify({
        nickname,
        ...(newPassword ? { currentPassword, newPassword } : {}),
      }),
    }, true),
  deleteMe: () => apiRequest<void>('/members/me', { method: 'DELETE' }, true),
};
