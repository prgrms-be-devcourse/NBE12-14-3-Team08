'use client';

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useMember } from '../context/MemberContext';
import { DeleteAccountModal } from '../components/DeleteAccountModal';
import { Eyebrow, HeroCard, btnForest, cardClass, inputClass } from '../components/DesignKit';
import { MemberNavigation } from '../components/MemberNavigation';
import { ProfileAvatar } from '../components/ProfileAvatar';
import { ProfileUpdateModal } from '../components/ProfileUpdateModal';
import { isSocialProvider, memberHandle } from '../lib/memberDisplay';

export const MyPage: React.FC = () => {
  const { currentUser, authReady, updateProfile, deleteAccount } = useMember();
  const router = useRouter();
  const [nameEdit, setNameEdit] = useState<{ userId: string; value: string } | null>(null);
  const name = nameEdit && nameEdit.userId === currentUser?.id ? nameEdit.value : currentUser?.name || '';
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [newPasswordConfirm, setNewPasswordConfirm] = useState('');
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; message: string } | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  // 소셜 로그인 회원은 아이디와 비밀번호가 없어 그 입력칸을 숨긴다.
  const isSocial = isSocialProvider(currentUser?.provider);


  if (!authReady) {
    return <div className="px-4 py-20 text-center text-sm text-slate-500">내 정보를 불러오는 중...</div>;
  }

  if (!currentUser) {
    return (
      <div className="px-4 py-20 text-center">
        <p className="mb-4 text-slate-500">로그인이 필요한 서비스입니다.</p>
        <button
          type="button"
          onClick={() => router.push('/login')}
          className="rounded-xl bg-forest-500 px-6 py-2.5 text-sm font-bold text-lime-400 hover:bg-forest-600"
        >
          로그인하러 가기
        </button>
      </div>
    );
  }

  const handleSave = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const trimmedName = name.trim();
    if (!trimmedName) {
      setFeedback({ type: 'error', message: '닉네임을 입력해주세요.' });
      return;
    }

    const isChangingPassword = Boolean(currentPassword || newPassword || newPasswordConfirm);
    if (isChangingPassword && !currentPassword) {
      setFeedback({ type: 'error', message: '기존 비밀번호를 입력해주세요.' });
      return;
    }
    if (isChangingPassword && !newPassword) {
      setFeedback({ type: 'error', message: '새 비밀번호를 입력해주세요.' });
      return;
    }
    if (isChangingPassword && newPassword !== newPasswordConfirm) {
      setFeedback({ type: 'error', message: '새 비밀번호가 서로 일치하지 않습니다.' });
      return;
    }

    setIsSaving(true);
    try {
      await updateProfile(
        trimmedName,
        isChangingPassword ? currentPassword : undefined,
        isChangingPassword ? newPassword : undefined,
      );
      setCurrentPassword('');
      setNewPassword('');
      setNewPasswordConfirm('');
      setNameEdit(null);
      setFeedback({ type: 'success', message: '변경사항이 성공적으로 저장되었습니다.' });
    } catch (caught) {
      setFeedback({
        type: 'error',
        message: caught instanceof Error ? caught.message : '프로필을 저장하지 못했습니다.',
      });
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <main className="mx-auto max-w-5xl px-4 py-8 sm:px-6 sm:py-10">
      <HeroCard>
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <Eyebrow>MY PAGE</Eyebrow>
            <h1 className="mt-2 text-[26px] font-extrabold tracking-tight sm:text-3xl">마이페이지</h1>
          </div>
          <MemberNavigation embedded />
        </div>

        <div className="mt-6 flex items-center gap-4">
          <ProfileAvatar nickname={currentUser.name} />
          <div>
            <p className="text-lg font-extrabold">{currentUser.name}</p>
            <p className="mt-0.5 text-sm text-white/55">{memberHandle(currentUser.provider, currentUser.username)}</p>
          </div>
        </div>
      </HeroCard>

      <section className={`mt-6 ${cardClass}`}>
        <h2 className="text-base font-extrabold text-slate-900">회원 정보</h2>
        <form onSubmit={handleSave} className="mt-5">
          <div className="max-w-md space-y-5">
            {isSocial ? (
              <p className="rounded-xl bg-slate-50 px-4 py-3 text-xs leading-5 text-slate-500">
                {memberHandle(currentUser.provider, currentUser.username)}으로 로그인한 회원입니다. 아이디와 비밀번호는 따로 없어요.
              </p>
            ) : (
            <div>
              <label htmlFor="mypage-username" className="mb-1.5 block text-sm font-bold text-slate-700">아이디</label>
              <input
                id="mypage-username"
                type="text"
                readOnly
                value={currentUser.username}
                className="w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-500"
              />
              <p className="mt-1.5 text-xs text-slate-400">아이디는 변경할 수 없습니다.</p>
            </div>
            )}
            <div>
              <label htmlFor="mypage-name" className="mb-1.5 block text-sm font-bold text-slate-700">닉네임</label>
              <input
                id="mypage-name"
                type="text"
                required
                value={name}
                onChange={(event) => {
                  setNameEdit({ userId: currentUser.id, value: event.target.value });
                }}
                className={inputClass}
              />
            </div>
            {!isSocial && (
              <>
            <div>
              <label htmlFor="mypage-current-password" className="mb-1.5 block text-sm font-bold text-slate-700">기존 비밀번호</label>
              <input
                id="mypage-current-password"
                type="password"
                autoComplete="current-password"
                value={currentPassword}
                onChange={(event) => setCurrentPassword(event.target.value)}
                placeholder="비밀번호를 변경할 때만 입력하세요"
                className={inputClass}
              />
            </div>
            <div>
              <label htmlFor="mypage-new-password" className="mb-1.5 block text-sm font-bold text-slate-700">새 비밀번호</label>
              <input
                id="mypage-new-password"
                type="password"
                autoComplete="new-password"
                value={newPassword}
                onChange={(event) => setNewPassword(event.target.value)}
                placeholder="변경할 새 비밀번호를 입력하세요"
                className={inputClass}
              />
            </div>
            <div>
              <label htmlFor="mypage-new-password-confirm" className="mb-1.5 block text-sm font-bold text-slate-700">새 비밀번호 확인</label>
              <input
                id="mypage-new-password-confirm"
                type="password"
                autoComplete="new-password"
                value={newPasswordConfirm}
                onChange={(event) => setNewPasswordConfirm(event.target.value)}
                placeholder="새 비밀번호를 한 번 더 입력하세요"
                className={inputClass}
              />
            </div>
              </>
            )}
          </div>

          <div className="mt-10 flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-6">
            <button
              type="button"
              onClick={() => setShowDeleteModal(true)}
              className="rounded-2xl bg-rose-50 px-5 py-3 text-sm font-bold text-rose-600 transition-colors hover:bg-rose-100 cursor-pointer"
            >
              회원 탈퇴
            </button>
            <button
              type="submit"
              disabled={isSaving}
              className={`${btnForest} px-6 disabled:cursor-wait`}
            >
              {isSaving ? '저장 중...' : '변경사항 저장'}
            </button>
          </div>
        </form>
      </section>

      <DeleteAccountModal
        isOpen={showDeleteModal}
        onClose={() => setShowDeleteModal(false)}
        onConfirm={deleteAccount}
      />
      <ProfileUpdateModal
        isOpen={feedback !== null}
        type={feedback?.type ?? 'success'}
        message={feedback?.message ?? ''}
        onClose={() => setFeedback(null)}
      />
    </main>
  );
};
