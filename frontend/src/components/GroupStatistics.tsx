'use client';

import React from 'react';
import {
    BarChart,
    Bar,
    XAxis,
    YAxis,
    Tooltip,
    CartesianGrid,
    ResponsiveContainer,
} from 'recharts';

interface RankingItem {
    rank: number;
    memberId: number;
    nickname: string | null;
    count: number;
}

interface GroupRankings {
    habitVerifyTop3: RankingItem[];
    penaltyVerifyTop3: RankingItem[];
    habitSuccessTop3: RankingItem[];
}

interface GroupStatisticsProps {
    rankings: GroupRankings;
}

interface ChartItem {
    memberId: number;
    nickname: string;
    count: number;
}

export function GroupStatistics({
                                    rankings,
                                }: {
    rankings?: GroupRankings | null;
}) {
    const successTop3 = rankings?.habitSuccessTop3 ?? [];
    const verifyTop3 = rankings?.habitVerifyTop3 ?? [];
    const penaltyTop3 = rankings?.penaltyVerifyTop3 ?? [];

    const renderChart = (data: ChartItem[], barColor: string) => (
        <div className="h-60 w-full mt-4">
            <ResponsiveContainer width="100%" height="100%">
                <BarChart
                    data={data}
                    margin={{ top: 10, right: 10, left: -25, bottom: 0 }}
                >
                    <CartesianGrid
                        strokeDasharray="3 3"
                        stroke="#f1f5f9"
                    />
                    <XAxis
                        dataKey="nickname"
                        tick={{ fontSize: 12, fill: '#64748b' }}
                    />
                    <YAxis
                        allowDecimals={false}
                        tick={{ fontSize: 12, fill: '#64748b' }}
                    />
                    <Tooltip />
                    <Bar
                        dataKey="count"
                        fill={barColor}
                        radius={[6, 6, 0, 0]}
                        maxBarSize={50}
                    />
                </BarChart>
            </ResponsiveContainer>
        </div>
    );

    return (
        <section className="mt-8 space-y-6">
            <div className="border-b border-slate-100 pb-3">
                <h2 className="text-lg font-bold text-slate-900">
                    챌린지 종료 최종 통계{' '}
                    <span className="text-xs font-normal text-slate-400">
                        (TOP 3)
                    </span>
                </h2>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                <div className="rounded-2xl border border-slate-100 bg-slate-50/50 p-4 shadow-sm">
                    <h3 className="text-sm font-bold text-slate-700">
                        습관 성공 횟수
                    </h3>
                    {successTop3.length > 0 ? (
                        renderChart(successTop3.map((item) => ({
                            memberId: item.memberId,
                            nickname: item.nickname ?? '알 수 없음',
                            count: item.count,
                        })), '#10b981')
                    ) : (
                        <p className="text-xs text-slate-400 mt-8 text-center">
                            기록이 없습니다.
                        </p>
                    )}
                </div>

                <div className="rounded-2xl border border-slate-100 bg-slate-50/50 p-4 shadow-sm">
                    <h3 className="text-sm font-bold text-slate-700">
                        습관 인증 횟수
                    </h3>
                    {verifyTop3.length > 0 ? (
                        renderChart(verifyTop3.map((item) => ({
                            memberId: item.memberId,
                            nickname: item.nickname ?? '알 수 없음',
                            count: item.count,
                        })), '#3b82f6')
                    ) : (
                        <p className="text-xs text-slate-400 mt-8 text-center">
                            기록이 없습니다.
                        </p>
                    )}
                </div>

                <div className="rounded-2xl border border-slate-100 bg-slate-50/50 p-4 shadow-sm">
                    <h3 className="text-sm font-bold text-slate-700">
                        벌칙 이행 횟수
                    </h3>
                    {penaltyTop3.length > 0 ? (
                        renderChart(penaltyTop3.map((item) => ({
                            memberId: item.memberId,
                            nickname: item.nickname ?? '알 수 없음',
                            count: item.count,
                        })), '#ef4444')
                    ) : (
                        <p className="text-xs text-slate-400 mt-8 text-center">
                            기록이 없습니다.
                        </p>
                    )}
                </div>
            </div>
        </section>
    );
}