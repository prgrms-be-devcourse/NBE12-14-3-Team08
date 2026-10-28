'use client';

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useMember } from '../context/MemberContext';
import { SocialLoginButtons } from '../components/SocialLoginButtons';

export const AuthPage: React.FC<{
  initialMode?: 'login' | 'signup';
  redirectTo?: string;
}> = ({ initialMode = 'login', redirectTo = '/main' }) => {
  const { login, signup } = useMember();
  const router = useRouter();
  const [isLogin, setIsLogin] = useState(initialMode === 'login');
  
  // Login fields
  const [loginUsername, setLoginUsername] = useState('');
  const [loginPassword, setLoginPassword] = useState('');

  // Signup fields
  const [signupName, setSignupName] = useState('');
  const [signupUsername, setSignupUsername] = useState('');
  const [signupPassword, setSignupPassword] = useState('');
  const [signupPasswordConfirm, setSignupPasswordConfirm] = useState('');
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleLoginSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!loginUsername.trim() || !loginPassword.trim()) {
      setError('아이디와 비밀번호를 입력해주세요.');
      return;
    }
    setError('');
    setIsSubmitting(true);
    try {
      await login(loginUsername.trim(), loginPassword, redirectTo);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : '로그인에 실패했습니다.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleSignupSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!signupName.trim() || !signupUsername.trim() || !signupPassword.trim()) {
      setError('모든 필수 항목을 입력해주세요.');
      return;
    }
    if (signupPassword !== signupPasswordConfirm) {
      setError('비밀번호가 일치하지 않습니다.');
      return;
    }
    setError('');
    setIsSubmitting(true);
    try {
      await signup(signupName.trim(), signupUsername.trim(), signupPassword);
      setIsLogin(true);
      setSignupPassword('');
      setSignupPasswordConfirm('');
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : '회원가입에 실패했습니다.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="min-h-[85vh] flex items-center justify-center px-4 py-12">
      <div className="w-full max-w-md bg-white rounded-3xl shadow-xl border border-slate-100 p-6 sm:p-8">
        {/* Brand header */}
        <div className="text-center mb-6">
          <div className="inline-flex items-center justify-center w-14 h-14 rounded-2xl bg-forest-500 text-lime-400 font-extrabold text-2xl shadow-lg shadow-forest-500/20 mb-3">
            🎯
          </div>
          <h1 className="text-2xl font-black text-slate-900 tracking-tight">내기? 내기!</h1>
        </div>

        {error && (
          <div className="mb-4 p-3 rounded-xl bg-rose-50 border border-rose-200 text-xs text-rose-600 font-semibold text-center">
            {error}
          </div>
        )}

        {isLogin ? (
          /* Login Form */
          <form onSubmit={handleLoginSubmit} className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5 sm:p-6 shadow-sm">
            <h2 className="text-left text-xl font-bold text-slate-900">로그인</h2>
            <div>
              <label className="block text-xs font-bold text-slate-700 mb-1">아이디</label>
              <input
                type="text"
                required
                value={loginUsername}
                onChange={(e) => setLoginUsername(e.target.value)}
                placeholder="아이디를 입력하세요"
                className="w-full px-4 py-2.5 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-forest-500"
              />
            </div>

            <div>
              <label className="block text-xs font-bold text-slate-700 mb-1">비밀번호</label>
              <input
                type="password"
                required
                value={loginPassword}
                onChange={(e) => setLoginPassword(e.target.value)}
                placeholder="비밀번호를 입력하세요"
                className="w-full px-4 py-2.5 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-forest-500"
              />
            </div>

            <button
              type="submit"
              disabled={isSubmitting}
              className="mx-auto block w-full max-w-48 py-3 rounded-xl font-bold text-lime-400 bg-forest-500 hover:bg-forest-600 shadow-md shadow-forest-500/20 transition-all hover:scale-101 text-sm mt-4 cursor-pointer disabled:cursor-wait disabled:opacity-60"
            >
              {isSubmitting ? '로그인 중...' : '로그인'}
            </button>

            <div className="text-center pt-2">
              <button
                type="button"
                onClick={() => {
                  setIsLogin(false);
                  setError('');
                  router.push('/signup');
                }}
                className="text-xs text-slate-500 hover:text-forest-600 font-semibold"
              >
                아직 계정이 없으신가요? <span className="underline text-forest-600 font-bold">회원가입</span>
              </button>
            </div>

          </form>
        ) : (
          /* Signup Form */
          <form onSubmit={handleSignupSubmit} className="space-y-3.5 rounded-2xl border border-slate-200 bg-white p-5 sm:p-6 shadow-sm">
            <h2 className="text-left text-xl font-bold text-slate-900">회원가입</h2>
            <div>
              <label className="block text-xs font-bold text-slate-700 mb-1">닉네임</label>
              <input
                type="text"
                required
                value={signupName}
                onChange={(e) => setSignupName(e.target.value)}
                placeholder="예: 정우, 멋진도전자"
                className="w-full px-4 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-forest-500"
              />
            </div>

            <div>
              <label className="block text-xs font-bold text-slate-700 mb-1">아이디</label>
              <input
                type="text"
                required
                value={signupUsername}
                onChange={(e) => setSignupUsername(e.target.value)}
                placeholder="영문/숫자 아이디"
                className="w-full px-4 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-forest-500"
              />
            </div>

            <div>
              <label className="block text-xs font-bold text-slate-700 mb-1">비밀번호</label>
              <input
                type="password"
                required
                value={signupPassword}
                onChange={(e) => setSignupPassword(e.target.value)}
                placeholder="비밀번호"
                className="w-full px-4 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-forest-500"
              />
            </div>

            <div>
              <label className="block text-xs font-bold text-slate-700 mb-1">비밀번호 확인</label>
              <input
                type="password"
                required
                value={signupPasswordConfirm}
                onChange={(e) => setSignupPasswordConfirm(e.target.value)}
                placeholder="비밀번호 재입력"
                className="w-full px-4 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-forest-500"
              />
            </div>

            <button
              type="submit"
              disabled={isSubmitting}
              className="mx-auto block w-full max-w-48 py-3 rounded-xl font-bold text-lime-400 bg-forest-500 hover:bg-forest-600 shadow-md shadow-forest-500/20 transition-all hover:scale-101 text-sm mt-4 cursor-pointer disabled:cursor-wait disabled:opacity-60"
            >
              {isSubmitting ? '가입 중...' : '회원가입'}
            </button>

            <div className="text-center pt-2">
              <button
                type="button"
                onClick={() => {
                  setIsLogin(true);
                  setError('');
                  router.push('/login');
                }}
                className="text-xs text-slate-500 hover:text-forest-600 font-semibold"
              >
                이미 계정이 있으신가요? <span className="underline text-forest-600 font-bold">로그인</span>
              </button>
            </div>
          </form>
        )}

        <SocialLoginButtons redirectTo={redirectTo} />
      </div>
    </div>
  );
};
