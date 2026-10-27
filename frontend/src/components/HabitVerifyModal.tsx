'use client';

import { useEffect, useState } from 'react';
import type { ChangeEvent, FormEvent } from 'react';
import { habitApi, uploadFileToSignedUrl } from '../lib/habitApi';
import { readImageSize, type ImageSize } from '../lib/imageSize';

interface HabitVerifyModalProps {
  isOpen: boolean;
  onClose: () => void;
  habitId: number;
  onSubmit: (data: {
    description: string;
    imageUrl: string;
    imageWidth?: number;
    imageHeight?: number;
  }) => Promise<void>;
}

const inputClassName =
  'w-full rounded-xl border border-slate-200 px-4 py-2.5 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-emerald-500';

export function HabitVerifyModal({
  isOpen,
  onClose,
  habitId,
  onSubmit,
}: HabitVerifyModalProps) {
  const [description, setDescription] = useState('');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState('');
  const [imageUrl, setImageUrl] = useState('');
  const [imageSize, setImageSize] = useState<ImageSize | null>(null);

  const [isUploading, setIsUploading] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const [error, setError] = useState('');

  // 모달을 새로 열었을 때 입력값 초기화
  useEffect(() => {
    if (!isOpen) return;

    setDescription('');
    setSelectedFile(null);
    setPreviewUrl('');
    setImageUrl('');
    setImageSize(null);
    setError('');
    setIsUploading(false);
    setIsSubmitting(false);
  }, [isOpen]);

  // ESC로 모달 닫기
  useEffect(() => {
    if (!isOpen) return;

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !isSubmitting && !isUploading) {
        onClose();
      }
    };

    document.addEventListener('keydown', handleKeyDown);

    return () => {
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [isOpen, isSubmitting, isUploading, onClose]);

  // 사진 미리보기 URL 정리
  useEffect(() => {
    return () => {
      if (previewUrl) {
        URL.revokeObjectURL(previewUrl);
      }
    };
  }, [previewUrl]);

  if (!isOpen) return null;

  const today = new Date().toISOString().slice(0, 10);

  // 사진 선택
  const handleFileChange = async (
    event: ChangeEvent<HTMLInputElement>,
  ) => {
    const file = event.target.files?.[0];

    if (!file) return;

    const allowedTypes = [
      'image/jpeg',
      'image/png',
      'image/webp',
    ];

    if (!allowedTypes.includes(file.type)) {
      setError('JPG, PNG, WEBP 이미지만 업로드할 수 있습니다.');
      event.target.value = '';
      return;
    }

    setError('');
    setImageUrl('');
    setImageSize(null);

    if (previewUrl) {
      URL.revokeObjectURL(previewUrl);
    }

    // 선택한 사진 미리보기
    const newPreviewUrl = URL.createObjectURL(file);

    setSelectedFile(file);
    setPreviewUrl(newPreviewUrl);

    try {
      setIsUploading(true);

      // 갤러리가 사진 비율대로 자리를 잡도록 사진 크기를 읽어 둔다 (읽지 못해도 업로드는 진행)
      const size = await readImageSize(file);

      // 1. 백엔드에 업로드 URL 요청
      const uploadResponse = await habitApi.getHabitUploadUrl(habitId, file.name);

      // 2. 백엔드가 발급한 URL로 사진 업로드
      await uploadFileToSignedUrl(uploadResponse.uploadUrl, file);

      // 3. DB에 저장할 publicUrl 저장
      setImageUrl(uploadResponse.publicUrl);
      setImageSize(size);
    } catch (caught) {
      setImageUrl('');
      setImageSize(null);

      setError(
        caught instanceof Error
          ? caught.message
          : '이미지 업로드에 실패했습니다.',
      );
    } finally {
      setIsUploading(false);
    }
  };

  // 인증 등록
  const handleSubmit = async (
    event: FormEvent<HTMLFormElement>,
  ) => {
    event.preventDefault();

    setError('');

    if (!selectedFile) {
      setError('인증 사진을 선택해주세요.');
      return;
    }

    if (!imageUrl) {
      setError('이미지 업로드가 완료될 때까지 기다려주세요.');
      return;
    }

    if (!description.trim()) {
      setError('인증 내용을 입력해주세요.');
      return;
    }

    setIsSubmitting(true);

    try {
      await onSubmit({
        description: description.trim(),
        imageUrl,
        ...imageSize,
      });

      onClose();
    } catch (caught) {
      setError(
        caught instanceof Error
          ? caught.message
          : '습관 인증 등록에 실패했습니다.',
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-slate-900/55 p-4 backdrop-blur-sm"
      onMouseDown={(event) => {
        if (
          event.target === event.currentTarget &&
          !isSubmitting &&
          !isUploading
        ) {
          onClose();
        }
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="habit-verify-title"
        className="my-6 w-full max-w-lg rounded-3xl border border-slate-100 bg-white p-6 shadow-2xl sm:p-8"
      >
        <div className="flex items-center justify-between border-b border-slate-100 pb-4">
          <h2
            id="habit-verify-title"
            className="text-xl font-extrabold text-slate-900"
          >
            오늘 인증하기
          </h2>

          <button
            type="button"
            onClick={onClose}
            disabled={isSubmitting || isUploading}
            aria-label="인증 모달 닫기"
            className="rounded-full p-1 text-2xl leading-none text-slate-400 hover:bg-slate-100"
          >
            ×
          </button>
        </div>

        <form
          onSubmit={handleSubmit}
          className="mt-5 space-y-4"
        >
          <div>
            <label className="mb-1.5 block text-sm font-bold text-slate-700">
              인증 사진
            </label>

            <label className="block cursor-pointer">
              <div className="flex h-52 items-center justify-center overflow-hidden rounded-xl border border-slate-200 bg-slate-50 transition hover:border-emerald-300 hover:bg-emerald-50">
                {previewUrl ? (
                  <img
                    src={previewUrl}
                    alt="선택한 인증 사진"
                    className="h-full w-full object-cover"
                  />
                ) : (
                  <span className="text-sm text-slate-400">
                    인증 사진을 선택해주세요.
                  </span>
                )}
              </div>

              <input
                type="file"
                accept="image/jpeg,image/png,image/webp"
                onChange={handleFileChange}
                className="hidden"
                disabled={isUploading || isSubmitting}
              />
            </label>

            {selectedFile && (
              <p className="mt-2 truncate text-xs text-slate-500">
                {isUploading
                  ? '이미지를 업로드하는 중입니다...'
                  : `${selectedFile.name}`}
              </p>
            )}
          </div>

          <div>
            <label
              htmlFor="habit-verify-date"
              className="mb-1.5 block text-sm font-bold text-slate-700"
            >
              인증 날짜
            </label>

            <input
              id="habit-verify-date"
              type="date"
              value={today}
              readOnly
              className={`${inputClassName} bg-slate-50 text-slate-500`}
            />

            <p className="mt-1 text-xs text-slate-400">
              인증 날짜는 오늘 날짜로 자동 처리됩니다.
            </p>
          </div>

          <div>
            <label
              htmlFor="habit-verify-description"
              className="mb-1.5 block text-sm font-bold text-slate-700"
            >
              인증 내용
            </label>

            <textarea
              id="habit-verify-description"
              value={description}
              onChange={(event) =>
                setDescription(event.target.value)
              }
              rows={4}
              placeholder="오늘 습관을 어떻게 실천했는지 적어주세요."
              className={`${inputClassName} resize-none`}
            />
          </div>

          <div>
            <label
              htmlFor="habit-verify-image-url"
              className="mb-1.5 block text-sm font-bold text-slate-700"
            >
              이미지 업로드
            </label>

            <input
              id="habit-verify-image-url"
              type="text"
              value={imageUrl}
              readOnly
              placeholder="사진을 선택하면 URL이 자동으로 표시됩니다."
              className={`${inputClassName} bg-slate-50 text-slate-500`}
            />
          </div>

          {error && (
            <p
              role="alert"
              className="text-sm font-semibold text-rose-600"
            >
              {error}
            </p>
          )}

          {/* 등록 버튼 */}
          <button
            type="submit"
            disabled={
              isSubmitting ||
              isUploading ||
              !imageUrl
            }
            className="w-full rounded-xl bg-emerald-500 px-6 py-2.5 text-sm font-bold text-white shadow-md shadow-emerald-500/20 hover:bg-emerald-600 disabled:cursor-not-allowed disabled:opacity-60"
          >
            {isUploading
              ? '이미지 업로드 중...'
              : isSubmitting
                ? '등록하는 중...'
                : '인증 등록'}
          </button>
        </form>
      </div>
    </div>
  );
}