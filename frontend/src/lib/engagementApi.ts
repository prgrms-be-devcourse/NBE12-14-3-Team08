import { apiRequest } from './memberApi';

// 습관 인증과 벌칙 인증이 같은 모양의 반응·댓글 API를 쓴다. (docs/adr/0002-verify-reaction-comment-tables.md)
export type EngagementKind = 'habit' | 'penalty';

export interface ReactionState {
  reacted: boolean;
  reactionCount: number;
}

export interface VerifyComment {
  id: number;
  // 방을 나간 멤버의 댓글이면 null이고, 닉네임은 "탈퇴한 멤버"로 내려온다.
  authorMemberId: number | null;
  authorNickname: string;
  content: string;
  createDate: string;
  edited: boolean;
  mine: boolean;
  canEdit: boolean;
  canDelete: boolean;
}

export interface VerifyCommentPage {
  items: VerifyComment[];
  nextCursor: number | null;
  totalCount: number;
}

export const COMMENT_MAX_LENGTH = 500;

const basePath = (kind: EngagementKind, id: number | string) =>
  kind === 'habit' ? `/habits/verifications/${id}` : `/penalties/${id}`;

export const engagementApi = {
  getReaction: (kind: EngagementKind, id: number | string) =>
    apiRequest<ReactionState>(`${basePath(kind, id)}/reactions`, {}, true),

  react: (kind: EngagementKind, id: number | string) =>
    apiRequest<ReactionState>(`${basePath(kind, id)}/reactions`, { method: 'PUT' }, true),

  cancelReaction: (kind: EngagementKind, id: number | string) =>
    apiRequest<ReactionState>(`${basePath(kind, id)}/reactions`, { method: 'DELETE' }, true),

  getComments: (kind: EngagementKind, id: number | string, cursor?: number | null, size = 30) => {
    const query = new URLSearchParams({ size: String(size) });
    if (cursor != null) query.set('cursor', String(cursor));
    return apiRequest<VerifyCommentPage>(`${basePath(kind, id)}/comments?${query}`, {}, true);
  },

  createComment: (kind: EngagementKind, id: number | string, content: string) =>
    apiRequest<VerifyComment>(`${basePath(kind, id)}/comments`, {
      method: 'POST',
      body: JSON.stringify({ content }),
    }, true),

  editComment: (kind: EngagementKind, id: number | string, commentId: number, content: string) =>
    apiRequest<VerifyComment>(`${basePath(kind, id)}/comments/${commentId}`, {
      method: 'PATCH',
      body: JSON.stringify({ content }),
    }, true),

  deleteComment: (kind: EngagementKind, id: number | string, commentId: number) =>
    apiRequest<void>(`${basePath(kind, id)}/comments/${commentId}`, { method: 'DELETE' }, true),
};
