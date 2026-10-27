'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { Target } from 'lucide-react';
import { useMember } from '../context/MemberContext';
import { memberHandle } from '../lib/memberDisplay';

export default function Header() {
    const router = useRouter();
    const { currentUser, authReady, logout } = useMember();

    const handleLogout = () => {
        logout();
    };

    if (!authReady || !currentUser) {
        return null;
    }

    const initial = currentUser?.name?.charAt(0) || '';

    return (
        <header className="bg-white border-b border-slate-100 sticky top-0 z-50">
            <div className="max-w-5xl mx-auto px-4 sm:px-6 w-full h-16 flex items-center justify-between">
                {/* 좌측 영역: 로고 및 타이틀 (클릭 시 /main 이동) */}
                <Link href="/main" className="flex items-center gap-3 group transition-transform active:scale-[0.99]">
                    <div className="w-10 h-10 rounded-full bg-emerald-500 flex items-center justify-center text-white shadow-sm shrink-0">
                        <Target className="w-5 h-5" strokeWidth={2.5} />
                    </div>
                    <div className="flex flex-col">
                        <div className="flex items-center gap-1.5">
              <span className="text-lg font-extrabold text-slate-900 leading-tight tracking-tight">
                내기? 내기!
              </span>
                            <span className="bg-emerald-100 text-emerald-700 text-[10px] font-bold px-1.5 py-0.5 rounded-full leading-none">
                습관 챌린지
              </span>
                        </div>
                        <span className="text-[11px] text-slate-400 font-medium leading-tight">
              실천하면 습관! 실패하면 내기 한판!
            </span>
                    </div>
                </Link>

                {/* 우측 영역: 유저 메뉴 (로그아웃 & 프로필 마이페이지 링크) */}
                <div className="flex items-center">
                    {/* 로그아웃 버튼 */}
                    <button
                        type="button"
                        onClick={handleLogout}
                        className="text-slate-500 hover:text-slate-900 text-sm font-bold mr-4 cursor-pointer transition-colors"
                    >
                        로그아웃
                    </button>

                    {/* 유저 프로필 영역 */}
                    <Link
                        href="/mypage"
                        className="flex items-center gap-2.5 p-1 rounded-lg hover:bg-slate-50 transition-colors"
                    >
                        {/* 프로필 아바타: 닉네임 첫 글자 표시 */}
                        <div className="bg-emerald-500 text-white rounded-full w-8 h-8 flex items-center justify-center text-xs font-black shrink-0 shadow-xs select-none">
                            {initial}
                        </div>

                        {/* 프로필 텍스트: 닉네임 및 @username */}
                        <div className="flex flex-col text-left">
              <span className="text-sm font-bold text-slate-800 leading-tight">
                {currentUser?.name}
              </span>
                            <span className="text-xs text-slate-400 font-normal leading-tight">
                {memberHandle(currentUser?.provider, currentUser?.username ?? '')}
              </span>
                        </div>
                    </Link>
                </div>
            </div>
        </header>
    );
}