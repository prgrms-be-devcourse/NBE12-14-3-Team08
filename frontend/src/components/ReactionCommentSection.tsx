'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { Heart, MessageCircle, Pencil, Send, Trash2 } from 'lucide-react';
import {
  COMMENT_MAX_LENGTH,
  engagementApi,
  type EngagementKind,
  type ReactionState,
  type VerifyComment,
} from '@/lib/engagementApi';

export interface EngagementSnapshot {
  reactionCount: number;
  commentCount: number;
  reacted: boolean;
}

interface ReactionCommentSectionProps {
  kind: EngagementKind;
  targetId: number;
  // 내 인증에는 하트를 누를 수 없다. 개수만 보여 준다.
  isOwnVerify: boolean;
  // 쓰기 가능 여부: 진행 중인 방이어야 한다. 종료된 방은 읽기만 된다.
  writable: boolean;
  // 하트·댓글 개수가 바뀔 때 알린다. (방 메인 갤러리 호버의 숫자를 맞추는 데 쓴다)
  onChange?: (snapshot: EngagementSnapshot) => void;
}

const errorText = (error: unknown, fallback: string) =>
  error instanceof Error && error.message ? error.message : fallback;

// "2026-10-27T18:06:46" → "10월 27일 18:06"
function formatCommentTime(value: string): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(value);
  if (!match) return '';
  return `${Number(match[2])}월 ${Number(match[3])}일 ${match[4]}:${match[5]}`;
}

const actionButtonClass =
  'flex h-7 w-7 items-center justify-center rounded-lg text-slate-400 transition hover:bg-slate-100 hover:text-slate-700 disabled:cursor-default disabled:opacity-40 cursor-pointer';

// 인증 상세 패널 아래에 붙는 하트(반응)와 댓글 영역. 습관 인증과 벌칙 인증이 함께 쓴다.
// 다른 인증으로 바뀌면 key로 새로 만들어 처음부터 다시 읽는다.
export function ReactionCommentSection(props: ReactionCommentSectionProps) {
  return <ReactionCommentBody key={`${props.kind}-${props.targetId}`} {...props} />;
}

// 하트는 누르는 즉시 화면에 반영하고(낙관적 반영) 실패하면 되돌린다.
function ReactionCommentBody({ kind, targetId, isOwnVerify, writable, onChange }: ReactionCommentSectionProps) {
  const [reaction, setReaction] = useState<ReactionState | null>(null);
  const [comments, setComments] = useState<VerifyComment[]>([]);
  const [nextCursor, setNextCursor] = useState<number | null>(null);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [draft, setDraft] = useState('');
  const [posting, setPosting] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editDraft, setEditDraft] = useState('');
  const [busyId, setBusyId] = useState<number | null>(null);
  const [togglingHeart, setTogglingHeart] = useState(false);

  const onChangeRef = useRef(onChange);
  useEffect(() => {
    onChangeRef.current = onChange;
  }, [onChange]);

  const report = useCallback((nextReaction: ReactionState, commentCount: number) => {
    onChangeRef.current?.({
      reactionCount: nextReaction.reactionCount,
      commentCount,
      reacted: nextReaction.reacted,
    });
  }, []);

  // 다른 인증으로 바뀌면 처음부터 다시 읽는다.
  useEffect(() => {
    let cancelled = false;

    Promise.all([engagementApi.getReaction(kind, targetId), engagementApi.getComments(kind, targetId)])
      .then(([reactionResult, page]) => {
        if (cancelled) return;
        setReaction(reactionResult);
        setComments(page.items);
        setNextCursor(page.nextCursor);
        setTotal(page.totalCount);
        report(reactionResult, page.totalCount);
      })
      .catch((error) => {
        if (!cancelled) setLoadError(errorText(error, '반응과 댓글을 불러오지 못했습니다.'));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [kind, targetId, report]);

  const toggleHeart = async () => {
    if (!reaction || togglingHeart || isOwnVerify || !writable) return;
    const previous = reaction;
    const optimistic: ReactionState = {
      reacted: !previous.reacted,
      reactionCount: Math.max(0, previous.reactionCount + (previous.reacted ? -1 : 1)),
    };
    setReaction(optimistic);
    setTogglingHeart(true);
    setNotice(null);

    try {
      const result = previous.reacted
        ? await engagementApi.cancelReaction(kind, targetId)
        : await engagementApi.react(kind, targetId);
      setReaction(result);
      report(result, total);
    } catch (error) {
      setReaction(previous);
      setNotice(errorText(error, '하트를 처리하지 못했습니다.'));
    } finally {
      setTogglingHeart(false);
    }
  };

  const loadMore = async () => {
    if (nextCursor == null || loadingMore) return;
    setLoadingMore(true);
    try {
      const page = await engagementApi.getComments(kind, targetId, nextCursor);
      setComments((current) => [...current, ...page.items.filter((item) => !current.some((c) => c.id === item.id))]);
      setNextCursor(page.nextCursor);
      setTotal(page.totalCount);
    } catch (error) {
      setNotice(errorText(error, '댓글을 더 불러오지 못했습니다.'));
    } finally {
      setLoadingMore(false);
    }
  };

  const submitComment = async () => {
    const content = draft.trim();
    if (!content || posting || !writable) return;
    setPosting(true);
    setNotice(null);
    try {
      const created = await engagementApi.createComment(kind, targetId, content);
      setComments((current) => [...current, created]);
      const nextTotal = total + 1;
      setTotal(nextTotal);
      if (reaction) report(reaction, nextTotal);
      setDraft('');
    } catch (error) {
      setNotice(errorText(error, '댓글을 남기지 못했습니다.'));
    } finally {
      setPosting(false);
    }
  };

  const saveEdit = async (comment: VerifyComment) => {
    const content = editDraft.trim();
    if (!content || busyId != null) return;
    if (content === comment.content) {
      setEditingId(null);
      return;
    }
    setBusyId(comment.id);
    setNotice(null);
    try {
      const updated = await engagementApi.editComment(kind, targetId, comment.id, content);
      setComments((current) => current.map((item) => (item.id === updated.id ? updated : item)));
      setEditingId(null);
    } catch (error) {
      setNotice(errorText(error, '댓글을 고치지 못했습니다.'));
    } finally {
      setBusyId(null);
    }
  };

  const removeComment = async (comment: VerifyComment) => {
    if (busyId != null || !window.confirm('이 댓글을 삭제할까요?')) return;
    setBusyId(comment.id);
    setNotice(null);
    try {
      await engagementApi.deleteComment(kind, targetId, comment.id);
      setComments((current) => current.filter((item) => item.id !== comment.id));
      const nextTotal = Math.max(0, total - 1);
      setTotal(nextTotal);
      if (reaction) report(reaction, nextTotal);
    } catch (error) {
      setNotice(errorText(error, '댓글을 삭제하지 못했습니다.'));
    } finally {
      setBusyId(null);
    }
  };

  if (loading) {
    return <div className="border-t border-slate-100 px-6 py-5 text-xs font-semibold text-slate-400">불러오는 중...</div>;
  }

  if (loadError || !reaction) {
    return (
      <div className="border-t border-slate-100 px-6 py-5 text-xs font-semibold text-rose-500">
        {loadError ?? '반응과 댓글을 불러오지 못했습니다.'}
      </div>
    );
  }

  const heartDisabled = isOwnVerify || !writable || togglingHeart;
  const heartTitle = isOwnVerify
    ? '내 인증에는 하트를 누를 수 없어요'
    : !writable
      ? '종료된 방에서는 하트를 누를 수 없어요'
      : reaction.reacted
        ? '하트 취소'
        : '하트 누르기';

  return (
    <section aria-label="반응과 댓글" className="border-t border-slate-100">
      <div className="flex items-center gap-4 px-6 pt-4">
        <button
          type="button"
          onClick={toggleHeart}
          disabled={heartDisabled}
          aria-pressed={reaction.reacted}
          aria-label={heartTitle}
          title={heartTitle}
          className={`flex items-center gap-1.5 rounded-xl py-1 pr-2 text-sm font-extrabold transition disabled:cursor-default ${
            reaction.reacted ? 'text-rose-500' : 'text-slate-500'
          } ${heartDisabled ? '' : 'cursor-pointer hover:text-rose-500'}`}
        >
          <Heart className="h-5 w-5" strokeWidth={2.2} fill={reaction.reacted ? 'currentColor' : 'none'} />
          <span>{reaction.reactionCount}</span>
        </button>
        <span className="flex items-center gap-1.5 text-sm font-extrabold text-slate-500">
          <MessageCircle className="h-5 w-5" strokeWidth={2.2} />
          <span>{total}</span>
        </span>
      </div>

      {notice && (
        <p role="alert" className="px-6 pt-3 text-xs font-semibold text-rose-500">
          {notice}
        </p>
      )}

      <ul className="mt-3 flex flex-col gap-4 px-6 pb-4">
        {comments.length === 0 && (
          <li className="py-2 text-xs font-semibold text-slate-400">
            {writable ? '첫 댓글을 남겨 보세요.' : '댓글이 없어요.'}
          </li>
        )}
        {comments.map((comment) => (
          <li key={comment.id} className="flex gap-2.5">
            <span className="flex h-7 w-7 flex-none items-center justify-center rounded-lg border border-emerald-100 bg-emerald-50 text-xs font-black text-emerald-700">
              {comment.authorNickname.slice(0, 1)}
            </span>
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2">
                <span className="truncate text-xs font-extrabold text-slate-900">{comment.authorNickname}</span>
                <span className="flex-none text-[11px] font-semibold text-slate-400">
                  {formatCommentTime(comment.createDate)}
                  {comment.edited && ' · 수정됨'}
                </span>
                <span className="flex-1" />
                {writable && comment.canEdit && editingId !== comment.id && (
                  <button
                    type="button"
                    aria-label="댓글 수정"
                    className={actionButtonClass}
                    disabled={busyId != null}
                    onClick={() => {
                      setEditingId(comment.id);
                      setEditDraft(comment.content);
                    }}
                  >
                    <Pencil className="h-3.5 w-3.5" />
                  </button>
                )}
                {writable && comment.canDelete && editingId !== comment.id && (
                  <button
                    type="button"
                    aria-label="댓글 삭제"
                    className={actionButtonClass}
                    disabled={busyId != null}
                    onClick={() => removeComment(comment)}
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                  </button>
                )}
              </div>

              {editingId === comment.id ? (
                <div className="mt-1.5">
                  <textarea
                    value={editDraft}
                    onChange={(event) => setEditDraft(event.target.value)}
                    maxLength={COMMENT_MAX_LENGTH}
                    rows={2}
                    aria-label="댓글 수정 내용"
                    className="w-full resize-none rounded-xl border border-slate-200 px-3 py-2 text-sm leading-5 text-slate-700 outline-none focus:border-emerald-400"
                  />
                  <div className="mt-1.5 flex justify-end gap-1.5">
                    <button
                      type="button"
                      onClick={() => setEditingId(null)}
                      className="h-8 cursor-pointer rounded-lg px-3 text-xs font-bold text-slate-500 hover:bg-slate-100"
                    >
                      취소
                    </button>
                    <button
                      type="button"
                      onClick={() => saveEdit(comment)}
                      disabled={!editDraft.trim() || busyId != null}
                      className="h-8 cursor-pointer rounded-lg bg-[#0f3d2e] px-3 text-xs font-extrabold text-[#c6f24a] disabled:cursor-default disabled:opacity-50"
                    >
                      저장
                    </button>
                  </div>
                </div>
              ) : (
                <p className="mt-0.5 whitespace-pre-wrap break-words text-sm leading-5 text-slate-700">{comment.content}</p>
              )}
            </div>
          </li>
        ))}
        {nextCursor != null && (
          <li>
            <button
              type="button"
              onClick={loadMore}
              disabled={loadingMore}
              className="cursor-pointer text-xs font-bold text-emerald-700 hover:underline disabled:opacity-50"
            >
              {loadingMore ? '불러오는 중...' : '댓글 더 보기'}
            </button>
          </li>
        )}
      </ul>

      {writable ? (
        <form
          className="sticky bottom-0 flex items-end gap-2 border-t border-slate-100 bg-white px-6 py-3"
          onSubmit={(event) => {
            event.preventDefault();
            void submitComment();
          }}
        >
          <textarea
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
                event.preventDefault();
                void submitComment();
              }
            }}
            maxLength={COMMENT_MAX_LENGTH}
            rows={1}
            placeholder="댓글을 남겨 보세요"
            aria-label="댓글 입력"
            className="max-h-28 min-h-10 flex-1 resize-none rounded-xl border border-slate-200 px-3 py-2 text-sm leading-5 text-slate-700 outline-none focus:border-emerald-400"
          />
          <button
            type="submit"
            disabled={!draft.trim() || posting}
            aria-label="댓글 등록"
            className="flex h-10 w-10 flex-none cursor-pointer items-center justify-center rounded-xl bg-[#0f3d2e] text-[#c6f24a] transition hover:bg-[#0a2a20] disabled:cursor-default disabled:opacity-40"
          >
            <Send className="h-4 w-4" />
          </button>
        </form>
      ) : (
        <p className="border-t border-slate-100 px-6 py-3 text-xs font-semibold text-slate-400">
          종료된 방에서는 읽기만 할 수 있어요.
        </p>
      )}
    </section>
  );
}
