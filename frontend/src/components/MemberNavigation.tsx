'use client';

import { usePathname, useRouter } from 'next/navigation';
import { useMember } from '../context/MemberContext';

export function MemberNavigation({ embedded = false }: { embedded?: boolean }) {
  const pathname = usePathname();
  const router = useRouter();
  const { currentUser, authReady } = useMember();

  if (!authReady || !currentUser || pathname === '/login' || pathname === '/signup') {
    return null;
  }

  const isMainPage = pathname === '/main';
  const isMyPage = pathname === '/mypage';

  return (
    <nav className={embedded ? 'flex justify-end' : 'flex justify-end px-4 pt-4'} aria-label="회원 페이지 이동">
      <div className="inline-flex overflow-hidden rounded-lg border border-forest-300 bg-white">
        <button
          type="button"
          onClick={() => router.push('/main')}
          aria-current={isMainPage ? 'page' : undefined}
          className={`min-w-24 px-4 py-2 text-sm font-semibold ${
            isMainPage ? 'bg-forest-500 text-lime-400' : 'bg-white text-slate-600 hover:bg-forest-50 hover:text-forest-700'
          }`}
        >
          방 목록
        </button>
        <button
          type="button"
          onClick={() => router.push('/mypage')}
          aria-current={isMyPage ? 'page' : undefined}
          className={`min-w-24 border-l border-forest-300 px-4 py-2 text-sm font-semibold ${
            isMyPage ? 'bg-forest-500 text-lime-400' : 'bg-white text-slate-600 hover:bg-forest-50 hover:text-forest-700'
          }`}
        >
          마이페이지
        </button>
      </div>
    </nav>
  );
}
