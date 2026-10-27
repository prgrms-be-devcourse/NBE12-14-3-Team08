'use client';

import { createContext, useContext, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  clearAuthSession,
  getAuthSession,
  memberApi,
  type MemberProvider as LoginProvider,
  MemberResponse,
  saveAuthSession,
} from '../lib/memberApi';

interface MemberUser {
  id: string;
  name: string;
  username: string;
  provider: LoginProvider;
}

interface MemberContextValue {
  currentUser: MemberUser | null;
  authReady: boolean;
  login: (username: string, password: string, redirectTo?: string) => Promise<void>;
  socialLogin: (provider: string, code: string, redirectUri: string, redirectTo?: string) => Promise<void>;
  signup: (nickname: string, username: string, password: string) => Promise<void>;
  updateProfile: (nickname: string, currentPassword?: string, newPassword?: string) => Promise<void>;
  logout: () => void;
  deleteAccount: () => Promise<void>;
}

const MemberContext = createContext<MemberContextValue | null>(null);

function toUser(member: MemberResponse): MemberUser {
  return {
    id: String(member.memberId),
    name: member.nickname,
    username: member.username,
    provider: member.provider ?? 'LOCAL',
  };
}

export function MemberProvider({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const [currentUser, setCurrentUser] = useState<MemberUser | null>(null);
  const [authReady, setAuthReady] = useState(false);

  useEffect(() => {
    if (!getAuthSession()) {
      setAuthReady(true);
      return;
    }
    memberApi.getMe()
      .then((member) => setCurrentUser(toUser(member)))
      .catch(() => clearAuthSession())
      .finally(() => setAuthReady(true));
  }, []);

  // 세션을 저장하고 내 정보를 불러온 뒤 이동한다. 내 정보를 못 불러오면 세션을 지운다.
  const completeLogin = async (session: Awaited<ReturnType<typeof memberApi.login>>, redirectTo: string) => {
    saveAuthSession(session);
    try {
      setCurrentUser(toUser(await memberApi.getMe()));
      const safeRedirect = redirectTo.startsWith('/') && !redirectTo.startsWith('//')
        ? redirectTo
        : '/main';
      router.push(safeRedirect);
    } catch (error) {
      clearAuthSession();
      throw error;
    }
  };

  const login = async (username: string, password: string, redirectTo = '/main') => {
    await completeLogin(await memberApi.login(username, password), redirectTo);
  };

  const socialLogin = async (provider: string, code: string, redirectUri: string, redirectTo = '/main') => {
    await completeLogin(await memberApi.socialLogin(provider, code, redirectUri), redirectTo);
  };

  const signup = async (nickname: string, username: string, password: string) => {
    await memberApi.signup(nickname, username, password);
    router.push('/login');
  };

  const updateProfile = async (nickname: string, currentPassword?: string, newPassword?: string) => {
    setCurrentUser(toUser(await memberApi.updateMe(nickname, currentPassword, newPassword)));
  };

  const logout = () => {
    clearAuthSession();

    if (typeof window !== 'undefined') {
      localStorage.removeItem('accessToken');

      document.cookie = "JSESSIONID=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;";
    }

    setCurrentUser(null);
    router.push('/login');
  };

  const deleteAccount = async () => {
    await memberApi.deleteMe();
    clearAuthSession();
    setCurrentUser(null);
    router.push('/login');
  };

  return (
    <MemberContext.Provider value={{ currentUser, authReady, login, socialLogin, signup, updateProfile, logout, deleteAccount }}>
      {children}
    </MemberContext.Provider>
  );
}

export function useMember() {
  const context = useContext(MemberContext);
  if (!context) throw new Error('MemberProvider가 필요합니다.');
  return context;
}
