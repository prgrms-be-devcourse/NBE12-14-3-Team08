'use client';

import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { useMember } from '../context/MemberContext';
import { consumeSocialState, isSocialLoginProvider, socialRedirectUri } from '../lib/socialAuth';

// 소셜 공급자가 로그인을 마치고 돌려보내는 화면(/auth/callback/{provider}).
// state가 로그인을 시작할 때와 같은지 확인한 뒤 인가 코드를 서버로 보내 로그인을 마무리한다.
export const SocialCallbackPage: React.FC<{ provider: string }> = ({ provider }) => {
  const params = useSearchParams();
  const { socialLogin } = useMember();
  // 인가 코드는 한 번만 쓸 수 있다. 개발 모드의 effect 이중 실행에서도 서버 호출은 한 번만 하게 막는다.
  const startedRef = useRef(false);
  const [failure, setFailure] = useState('');

  const code = params.get('code');
  const state = params.get('state');
  const providerError = params.get('error');

  // 화면에 그리는 도중에 알 수 있는 오류(취소, 잘못된 주소)는 상태로 옮기지 않고 바로 보여 준다.
  let immediateError = '';
  if (!isSocialLoginProvider(provider)) immediateError = '지원하지 않는 로그인 방식입니다.';
  else if (providerError) immediateError = '로그인이 취소되었습니다. 다시 시도해주세요.';
  else if (!code || !state) immediateError = '로그인 정보가 올바르지 않습니다. 다시 시도해주세요.';

  useEffect(() => {
    if (immediateError || startedRef.current || !code || !isSocialLoginProvider(provider)) return;
    startedRef.current = true;

    void (async () => {
      try {
        const redirectTo = consumeSocialState(provider, state);
        if (!redirectTo) {
          throw new Error('로그인 요청이 만료되었거나 올바르지 않습니다. 다시 시도해주세요.');
        }
        await socialLogin(provider, code, socialRedirectUri(provider), redirectTo);
      } catch (caught) {
        setFailure(caught instanceof Error ? caught.message : '로그인에 실패했습니다.');
      }
    })();
  }, [immediateError, code, state, provider, socialLogin]);

  const message = immediateError || failure;

  return (
    <div className="min-h-[85vh] flex items-center justify-center px-4 py-12">
      <div className="w-full max-w-md rounded-3xl border border-slate-100 bg-white p-8 text-center shadow-xl">
        {message ? (
          <>
            <p role="alert" className="text-sm font-semibold text-rose-600">{message}</p>
            <Link
              href="/login"
              className="mt-6 inline-block rounded-xl bg-forest-500 px-6 py-2.5 text-sm font-bold text-lime-400 hover:bg-forest-600"
            >
              로그인으로 돌아가기
            </Link>
          </>
        ) : (
          <p className="text-sm font-semibold text-slate-500">구글 계정으로 로그인하는 중입니다...</p>
        )}
      </div>
    </div>
  );
};
