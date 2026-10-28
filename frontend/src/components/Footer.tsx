'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';

export function Footer() {
    const pathname = usePathname();
    const currentYear = new Date().getFullYear();

    // 결산은 스크롤 없이 한 화면에 담는 전체 화면 페이지라 푸터를 두지 않는다
    if (/^\/group\/[^/]+\/settlement\/?$/.test(pathname)) return null;

    return (
        <footer className="w-full border-t border-slate-100 bg-white py-8 text-slate-500">
            <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
                <div className="flex flex-col items-center justify-between gap-6 border-b border-slate-100 pb-6 sm:flex-row">
                    <div className="flex items-center gap-2">
            <span className="text-lg font-black tracking-tight text-slate-900">
              내기? 내기!
            </span>
                    </div>

                    <nav className="flex flex-wrap justify-center gap-x-6 gap-y-2 text-sm font-semibold">
                        <Link href="/" className="hover:text-forest-600 transition-colors text-slate-600">
                            홈
                        </Link>
                        <Link href="/docs/terms-of-service" className="hover:text-forest-600 transition-colors text-slate-600">
                            이용약관
                        </Link>
                        <Link href="/docs/privacy-policy" className="hover:text-forest-600 transition-colors font-bold text-slate-900">
                            개인정보처리방침
                        </Link>
                        <a
                            href="https://github.com/prgrms-be-devcourse/NBE12-14-3-Team08"
                            target="_blank"
                            rel="noopener noreferrer"
                            className="hover:text-forest-600 transition-colors text-slate-600"
                        >
                            GitHub
                        </a>
                    </nav>
                </div>

                <div className="flex flex-col items-center justify-between gap-4 pt-6 text-xs sm:flex-row text-slate-400">
                    <p>
                        Contact: <a href="mailto:team-naegi@gmail.com" className="hover:text-slate-600 underline">team-naegi@gmail.com</a>
                    </p>
                    <p className="font-medium">
                        &copy; {currentYear} 내기? 내기!. All rights reserved.
                    </p>
                </div>
            </div>
        </footer>
    );
}
