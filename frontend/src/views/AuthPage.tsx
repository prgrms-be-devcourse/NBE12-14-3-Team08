'use client';

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useMember } from '../context/MemberContext';
import { SocialLoginButtons } from '../components/SocialLoginButtons';
import { btnForest, inputClass } from '../components/DesignKit';

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
      <div className="w-full max-w-md overflow-hidden rounded-[32px] bg-white shadow-[0_18px_40px_rgba(11,20,17,0.18)]">
        {/* Brand header */}
        <div className="relative overflow-hidden bg-gradient-to-br from-forest-500 to-forest-800 px-6 pb-8 pt-9 text-center text-white sm:px-8">
          <div className="relative">
            <div className="mx-auto mb-3 flex h-14 w-14 items-center justify-center rounded-2xl bg-lime-400 text-2xl shadow-lg shadow-lime-400/25">
              🎯
            </div>
            <h1 className="text-2xl font-extrabold tracking-tight">내기? 내기!</h1>
            <p className="mt-1.5 text-[13px] text-white/60">실천하면 습관! 실패하면 내기 한판!</p>
          </div>
        </div>

        <div className="p-6 sm:p-8">
        {error && (
          <div className="mb-4 rounded-2xl border border-rose-200 bg-rose-50 p-3 text-center text-xs font-semibold text-rose-600">
            {error}
          </div>
        )}

        {isLogin ? (
          /* Login Form */
          <form onSubmit={handleLoginSubmit} className="space-y-4">
            <h2 className="text-left text-xl font-extrabold tracking-tight text-slate-900">로그인</h2>
            <div>
              <label className="block text-xs font-bold text-slate-700 mb-1">아이디</label>
              <input
                type="text"
                required
                value={loginUsername}
                onChange={(e) => setLoginUsername(e.target.value)}
                placeholder="아이디를 입력하세요"
                className={inputClass}
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
                className={inputClass}
              />
            </div>

            <button
              type="submit"
              disabled={isSubmitting}
              className={`${btnForest} mt-4 w-full disabled:cursor-wait`}
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
          <form onSubmit={handleSignupSubmit} className="space-y-3.5">
            <h2 className="text-left text-xl font-extrabold tracking-tight text-slate-900">회원가입</h2>
            <div>
              <label className="block text-xs font-bold text-slate-700 mb-1">닉네임</label>
              <input
                type="text"
                required
                value={signupName}
                onChange={(e) => setSignupName(e.target.value)}
                placeholder="예: 정우, 멋진도전자"
                className={inputClass}
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
                className={inputClass}
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
                className={inputClass}
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
                className={inputClass}
              />
            </div>

            <button
              type="submit"
              disabled={isSubmitting}
              className={`${btnForest} mt-4 w-full disabled:cursor-wait`}
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
    </div>
  );
};
