import { Suspense } from 'react';
import { SocialCallbackPage } from '../../../../views/SocialCallbackPage';

// 소셜 공급자(구글 등)가 로그인 후 돌려보내는 주소: /auth/callback/google?code=...&state=...
export default async function Page({ params }: { params: Promise<{ provider: string }> }) {
  const { provider } = await params;

  return (
    <Suspense fallback={null}>
      <SocialCallbackPage provider={provider} />
    </Suspense>
  );
}
