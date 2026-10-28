// components/MemberHabitHeatmap.tsx
'use client';

import React, { useEffect, useState } from 'react';
import { apiRequest } from '@/lib/memberApi';

interface HabitVerifyResponse {
    id: number;
    verifyDate: any;
    status: 'PENDING' | 'APPROVED' | 'REJECTED';
}

interface MemberHabitHeatmapProps {
    habitId: number;
    startDate?: string;
    deadline?: string;
}

function parseLocalDate(dateInput: any): Date | null {
    if (!dateInput) return null;

    if (Array.isArray(dateInput)) {
        return new Date(dateInput[0], dateInput[1] - 1, dateInput[2]);
    }

    if (typeof dateInput === 'string') {
        const clean = dateInput.replace(/[.\s/]+/g, '-').slice(0, 10);
        const parts = clean.split('-');
        if (parts.length === 3) {
            const y = parseInt(parts[0], 10);
            const m = parseInt(parts[1], 10) - 1;
            const d = parseInt(parts[2], 10);
            return new Date(y, m, d);
        }
    }

    const d = new Date(dateInput);
    return isNaN(d.getTime()) ? null : d;
}

function formatYMD(d: Date): string {
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

export const MemberHabitHeatmap: React.FC<MemberHabitHeatmapProps> = ({
                                                                          habitId,
                                                                          startDate,
                                                                          deadline,
                                                                      }) => {
    const [verifications, setVerifications] = useState<HabitVerifyResponse[]>([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        let isMounted = true;

        async function loadVerifications() {
            try {
                // 백엔드 호출: GET /api/habits/{habitId}/verifications
                const data = await apiRequest<HabitVerifyResponse[]>(
                    `/habits/${habitId}/verifications`,
                    {},
                    true
                );

                console.log(`[Habit ${habitId}] 백엔드 응답 원본:`, data);
                if (isMounted) setVerifications(data || []);
            } catch (err) {
                console.error(`[Habit ${habitId}] 인증 내역 조회 실패:`, err);
            } finally {
                if (isMounted) setLoading(false);
            }
        }

        loadVerifications();
        return () => {
            isMounted = false;
        };
    }, [habitId]);

    if (loading) {
        return (
            <div className="mt-3 pt-2 text-[11px] text-slate-400 flex items-center gap-1.5">
                <span className="w-1.5 h-1.5 rounded-full bg-slate-300 animate-pulse" />
                <span>실천 잔디 불러오는 중...</span>
            </div>
        );
    }

    let parsedEnd = parseLocalDate(deadline);
    if (!parsedEnd) {
        parsedEnd = new Date(); // deadline이 없으면 오늘 기준
    }

    let parsedStart = parseLocalDate(startDate);
    if (!parsedStart || parsedStart > parsedEnd) {
        // startDate가 없거나 마감일보다 늦으면, 마감일 기준 13일 전(2주 기간)으로 설정
        parsedStart = new Date(parsedEnd.getTime() - 13 * 24 * 60 * 60 * 1000);
    }

    const approvedDates = new Set<string>();

    verifications.forEach((v) => {
        if (v.status === 'APPROVED') {
            const parsed = parseLocalDate(v.verifyDate);
            if (parsed) {
                approvedDates.add(formatYMD(parsed));
            }
        }
    });

    console.log(`[Habit ${habitId}] 승인된 날짜 Set:`, Array.from(approvedDates));

    const tiles: { dateStr: string; isApproved: boolean }[] = [];
    const cur = new Date(parsedStart.getFullYear(), parsedStart.getMonth(), parsedStart.getDate());
    const finalEnd = new Date(parsedEnd.getFullYear(), parsedEnd.getMonth(), parsedEnd.getDate());

    while (cur <= finalEnd) {
        const dateStr = formatYMD(cur);
        tiles.push({
            dateStr,
            isApproved: approvedDates.has(dateStr),
        });
        cur.setDate(cur.getDate() + 1);
    }

    const successCount = tiles.filter((t) => t.isApproved).length;
    const totalDays = tiles.length;
    const rate = totalDays > 0 ? Math.round((successCount / totalDays) * 100) : 0;

    return (
        <div
            className="mt-3 pt-3 border-t border-slate-100/90 w-full"
            onClick={(e) => e.stopPropagation()}
        >
            <div className="flex items-center justify-between text-[11px] mb-2 text-slate-500">
                <span className="font-bold text-slate-700">챌린지 실천 잔디</span>
                <span className="tabular-nums font-semibold">
                    <strong className="text-forest-700 font-extrabold">{successCount}</strong> / {totalDays}일 완주 ({rate}%)
                </span>
            </div>

            <div className="flex flex-wrap gap-1.5 items-center">
                {tiles.map((tile) => (
                    <div
                        key={tile.dateStr}
                        title={`${tile.dateStr}: ${tile.isApproved ? '실천 성공 (방장 승인)' : '미인증'}`}
                        className={`w-3.5 h-3.5 sm:w-4 sm:h-4 rounded-xs transition-transform hover:scale-125 cursor-pointer ${
                            tile.isApproved
                                ? 'bg-forest-600 shadow-2xs'
                                : 'bg-slate-100 border border-slate-200/80'
                        }`}
                    />
                ))}
            </div>
        </div>
    );
};