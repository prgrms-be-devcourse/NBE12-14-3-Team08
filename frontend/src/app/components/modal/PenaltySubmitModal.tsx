'use client';

import React, { useState } from 'react';
import { habitApi, uploadFileToSignedUrl } from '@/lib/habitApi';

interface PenaltySubmitModalProps {
  isOpen: boolean;
  onClose: () => void;
  penaltyId: number;
  habitId: number;
  penaltyText: string;
  onSuccess?: () => void;
}

export default function PenaltySubmitModal({
                                             isOpen,
                                             onClose,
                                             penaltyId,
                                             habitId,
                                             penaltyText,
                                             onSuccess,
                                           }: PenaltySubmitModalProps) {
  const [description, setDescription] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  if (!isOpen) return null;

  const todayStr = new Date().toISOString().slice(0, 10).replace(/-/g, '.');

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      const selected = e.target.files[0];
      setFile(selected);
      setPreviewUrl(URL.createObjectURL(selected));
    }  };
  const handleSubmit = async () => {
    if (!file) {
      alert('인증 사진을 첨부해주세요.');
      return;
    }    if (!description.trim()) {
      alert('어떻게 벌칙을 수행했는지 적어주세요.');
      return;
    }
    const token = localStorage.getItem('accessToken') || '';
    const authHeader = token.startsWith('Bearer ') ? token : `Bearer ${token}`;

    setIsLoading(true);
    try {
      // 1. Presigned Upload URL 발급 요청
      const { uploadUrl, publicUrl } = await habitApi.getPenaltyUploadUrl(penaltyId, file.name);

      // 2. Supabase Storage 직접 PUT 업로드
      await uploadFileToSignedUrl(uploadUrl, file);

      // 3. 최종 벌칙 제출
      const submitRes = await fetch(`http://localhost:8080/api/habits/${habitId}/penalties`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: authHeader,
        },        body: JSON.stringify({
          imageUrl: publicUrl,
          description: description,
        }),      });      if (!submitRes.ok) {
        const err = await submitRes.json();
        throw new Error(err.message || '벌칙 제출에 실패했습니다.');
      }
      alert('벌칙 인증이 정상적으로 제출되었습니다!');
      if (onSuccess) onSuccess();
      onClose();
    } catch (err: any) {
      alert(`오류: ${err.message}`);
    } finally {
      setIsLoading(false);
    }  };
  return (
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
        <div className="w-full max-w-sm rounded-3xl border-2 border-black bg-white p-6 shadow-xl">
          <h2 className="text-xl font-extrabold text-gray-900">벌칙 인증하기</h2>
          <p className="text-xs text-gray-500 mt-1">벌칙 내용을 남겨보세요.</p>

          <div className="mt-4 text-sm font-bold text-gray-800">
            벌칙 내용 : <span className="font-medium text-gray-600">{penaltyText}</span>
          </div>

          <div className="mt-5 space-y-4">
            <div>
              <label className="block text-xs font-bold text-gray-700 mb-1">인증 날짜</label>
              <input
                  type="text"
                  readOnly
                  value={todayStr}
                  className="w-full rounded-2xl border border-gray-400 bg-gray-50 px-4 py-2.5 text-sm text-gray-700 outline-none"
              />
            </div>

            <div>
              <label className="block text-xs font-bold text-gray-700 mb-1">인증 내용</label>
              <textarea
                  rows={3}
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="어떻게 벌칙을 수행했는지 적어주세요."
                  className="w-full resize-none rounded-2xl border border-gray-400 p-3 text-sm text-gray-800 outline-none placeholder:text-gray-400 focus:border-black"
              />
            </div>

            <div>
              <label className="block text-xs font-bold text-gray-700 mb-1">이미지 업로드</label>
              <input
                  type="file"
                  accept="image/*"
                  onChange={handleFileChange}
                  className="w-full text-xs text-gray-500 file:mr-3 file:rounded-xl file:border file:border-gray-300 file:bg-gray-100 file:px-3 file:py-1.5 file:text-xs file:font-semibold hover:file:bg-gray-200 cursor-pointer"
              />
              {previewUrl && (
                  <img
                      src={previewUrl}
                      alt="미리보기"
                      className="mt-2 h-28 w-full rounded-2xl border object-cover"
                  />
              )}            </div>
          </div>

          <div className="mt-6 flex justify-end gap-2">
            <button
                onClick={onClose}
                disabled={isLoading}
                className="rounded-xl border border-gray-300 px-4 py-2 text-xs font-bold text-gray-600 hover:bg-gray-100"
            >
              취소
            </button>
            <button
                onClick={handleSubmit}
                disabled={isLoading}
                className="rounded-xl border border-black bg-forest-100 px-5 py-2 text-xs font-extrabold text-gray-900 transition-colors hover:bg-forest-200 disabled:bg-gray-200"
            >
              {isLoading ? '등록 중...' : '인증 등록'}
            </button>
          </div>
        </div>
      </div>
  );}
