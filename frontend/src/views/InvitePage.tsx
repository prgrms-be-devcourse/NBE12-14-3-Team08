'use client';

import { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { Eyebrow, HeroCard, btnLime } from '../components/DesignKit';
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
      <HeroCard className="w-full max-w-md text-center">

        <button type="button" onClick={() => router.push('/main')} aria-label="방 목록으로 돌아가기"
          className="absolute left-0 top-0 text-xl text-white/50 transition-colors hover:text-white cursor-pointer">
          ←
        </button>

        <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-3xl bg-lime-400/15 text-3xl">💌</div>
        <Eyebrow>방에 초대받으셨어요!</Eyebrow>

        {isLoading ? (
          <div className="py-12 text-sm font-semibold text-white/60">
            초대받은 방 정보를 불러오는 중...
          </div>
        ) : error || !preview ? (
          <div className="py-10">
            <p role="alert" className="text-sm font-bold text-rose-300">
              {error || '유효하지 않은 초대 코드입니다.'}
            </p>
            <button
              type="button"
              onClick={() => router.push('/main')}
              className={`${btnLime} mt-5`}
            >
              방 목록으로 돌아가기
            </button>
          </div>
        ) : (
          <>
            <h1 className="mb-2 mt-2 text-2xl font-extrabold tracking-tight">{preview.title}</h1>
            <p className="mb-6 text-xs leading-relaxed text-white/60">
              {preview.description || '함께 목표를 달성해보세요!'}
            </p>

            <div className="mb-6 space-y-2.5 rounded-[18px] bg-white/10 p-4 text-left">
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
              <InfoRow icon="🚨" label="실패 시 벌칙" value={preview.penalty || '벌칙 없음'} emphasis />
            </div>

            <button
              type="button"
              onClick={handleJoinClick}
              disabled={!authReady || !invitecode || preview.status === 'FINISH'}
              className={`${btnLime} w-full py-3.5 disabled:cursor-not-allowed disabled:bg-white/20 disabled:text-white/50 disabled:shadow-none`}
            >
              {preview.status === 'FINISH'
                ? '종료된 방입니다'
                : authReady
                  ? '이 방 참여하기'
                  : '로그인 상태 확인 중...'}
            </button>

            {preview.status === 'ACTIVE' && (
              <p className="mt-3 text-[11px] text-white/45">🔒 참여를 위해 비밀번호 입력이 필요합니다.</p>
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
      </HeroCard>
    </main>
  );
}

function InfoRow({ icon, label, value, emphasis = false }: { icon: string; label: string; value: string; emphasis?: boolean }) {
  return (
    <div className="flex items-center justify-between gap-3 text-xs">
      <span className="flex items-center gap-1.5 font-medium text-white/55"><span aria-hidden="true">{icon}</span>{label}</span>
      <span className={`text-right font-bold ${emphasis ? 'text-rose-300' : 'text-white'}`}>{value}</span>
    </div>
  );
}
