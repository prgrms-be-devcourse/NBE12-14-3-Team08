'use client';

import { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { RoomPasswordModal } from '../components/RoomPasswordModal';
import { useMember } from '../context/MemberContext';
import { groupApi, type GroupDetailResponse } from '../lib/groupApi';

export function InvitePage() {
  const { invitecode } = useParams<{ invitecode: string }>();
  const router = useRouter();
  const { currentUser, authReady } = useMember();
  const [showPasswordModal, setShowPasswordModal] = useState(false);
  const [preview, setPreview] = useState<GroupDetailResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!authReady || !invitecode) return;

    if (!currentUser) {
      const invitePath = `/invite/${encodeURIComponent(invitecode)}`;
      router.replace(`/login?redirect=${encodeURIComponent(invitePath)}`);
      return;
    }

    let cancelled = false;

    groupApi.getGroupByInviteCode(invitecode)
      .then((data) => {
        if (!cancelled) {setPreview(data);
          if (data.isJoined) {
            router.push('/main');
          }
        }
      })
      .catch((caught) => {
        if (!cancelled) {
          setError(caught instanceof Error ? caught.message : '초대받은 방 정보를 불러오지 못했습니다.');
        }
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [invitecode, authReady, currentUser, router]);

  useEffect(() => {
    if (preview && preview.isJoined) {
      router.replace(`/group/${preview.id}`);
    }
  }, [preview, router]);

  const handleJoinClick = () => {
    if (!authReady || !preview || preview.status === 'FINISH') return;
    if (!currentUser) {
      const invitePath = `/invite/${encodeURIComponent(invitecode)}`;
      router.push(`/login?redirect=${encodeURIComponent(invitePath)}`);
      return;
    }
    setShowPasswordModal(true);
  };

  if (!authReady) {
    return (
        <main className="flex min-h-[85vh] items-center justify-center">
          <div className="text-sm font-semibold text-slate-400">사용자 인증 확인 중...</div>
        </main>
    );
  }

  return (
    <main className="flex min-h-[85vh] items-center justify-center px-4 py-10">
      <section className="relative w-full max-w-md overflow-hidden rounded-3xl border border-slate-100 bg-white p-8 text-center shadow-xl">
        <div aria-hidden="true" className="pointer-events-none absolute -right-12 -top-12 h-32 w-32 rounded-full bg-forest-100 opacity-60 blur-2xl" />
        <div aria-hidden="true" className="pointer-events-none absolute -bottom-12 -left-12 h-32 w-32 rounded-full bg-lime-200 opacity-60 blur-2xl" />

        <button type="button" onClick={() => router.push('/main')} aria-label="방 목록으로 돌아가기"
          className="absolute left-6 top-6 text-xl text-slate-400 transition-colors hover:text-slate-600">
          ←
        </button>

        <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-3xl bg-forest-100 text-3xl text-forest-600 shadow-sm">💌</div>
        <p className="mb-1 text-xs font-bold uppercase tracking-wider text-forest-600">방에 초대받으셨어요!</p>

        {isLoading ? (
          <div className="py-12 text-sm font-semibold text-slate-400">
            초대받은 방 정보를 불러오는 중...
          </div>
        ) : error || !preview ? (
          <div className="py-10">
            <p role="alert" className="text-sm font-bold text-rose-600">
              {error || '유효하지 않은 초대 코드입니다.'}
            </p>
            <button
              type="button"
              onClick={() => router.push('/main')}
              className="mt-5 rounded-xl bg-slate-900 px-5 py-2.5 text-xs font-bold text-white"
            >
              방 목록으로 돌아가기
            </button>
          </div>
        ) : (
          <>
            <h1 className="mb-2 text-2xl font-black tracking-tight text-slate-900">{preview.title}</h1>
            <p className="mb-6 text-xs leading-relaxed text-slate-500">
              {preview.description || '함께 목표를 달성해보세요!'}
            </p>

            <div className="mb-6 space-y-2.5 rounded-2xl border border-slate-100 bg-slate-50 p-4 text-left">
              <InfoRow
                icon="👥"
                label="참여 인원"
                value={`${preview.currentMemberCount} / ${preview.memberLimit}명`}
              />
              <InfoRow
                icon="📅"
                label="기간"
                value={preview.deadline ? `${preview.startDate} ~ ${preview.deadline}` : `${preview.startDate}부터`}
              />
              <InfoRow icon="☕" label="실패 시 벌칙" value={preview.penalty || '벌칙 없음'} emphasis />
            </div>

            <button
              type="button"
              onClick={handleJoinClick}
              disabled={!authReady || !invitecode || preview.status === 'FINISH'}
              className="w-full cursor-pointer rounded-2xl bg-forest-500 py-3.5 text-sm font-extrabold text-lime-400 shadow-lg shadow-forest-500/25 transition-all hover:bg-forest-600 disabled:cursor-not-allowed disabled:bg-slate-300 disabled:shadow-none"
            >
              {preview.status === 'FINISH'
                ? '종료된 방입니다'
                : authReady
                  ? '이 방 참여하기'
                  : '로그인 상태 확인 중...'}
            </button>

            {preview.status === 'ACTIVE' && (
              <p className="mt-3 text-[11px] text-slate-400">🔒 참여를 위해 비밀번호 입력이 필요합니다.</p>
            )}

            <RoomPasswordModal
              isOpen={showPasswordModal}
              onClose={() => setShowPasswordModal(false)}
              roomTitle={preview.title}
              roomDescription={preview.description || '함께 목표를 달성해보세요!'}
              onSubmit={async (password) => {
                await groupApi.join(invitecode, password);
                router.push(`/group/${preview.id}`);
              }}
            />
          </>
        )}
      </section>
    </main>
  );
}

function InfoRow({ icon, label, value, emphasis = false }: { icon: string; label: string; value: string; emphasis?: boolean }) {
  return (
    <div className="flex items-center justify-between gap-3 text-xs">
      <span className="flex items-center gap-1.5 font-medium text-slate-400"><span aria-hidden="true">{icon}</span>{label}</span>
      <span className={`text-right font-bold ${emphasis ? 'text-rose-600' : 'text-slate-800'}`}>{value}</span>
    </div>
  );
}
