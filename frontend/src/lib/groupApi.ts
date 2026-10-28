import { apiRequest } from './memberApi';

export type GroupStatus = 'ACTIVE' | 'FINISH';
export type GroupMemberRole = 'OWNER' | 'MEMBER';

// 서버 Group.isFinished()와 같은 기준: 상태가 FINISH이거나, 오늘이 마감일보다 뒤일 때(마감일 당일은 아직 진행 중).
// 반응·댓글처럼 서버가 방 종료를 검사하는 기능에서 입력창을 보일지 정할 때 쓴다.
export function isGroupFinished(group: { status: GroupStatus; deadline?: string | null }): boolean {
  if (group.status === 'FINISH') return true;
  if (!group.deadline) return false;

  const now = new Date();
  const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
  return today > group.deadline.slice(0, 10);
}

export interface GroupSummary {
  id: number;
  title: string;
  description: string;
  memberLimit: number;
  currentMemberCount: number;
  status: GroupStatus;
  role: GroupMemberRole;
}

export interface CreateGroupRequest {
  title: string;
  description: string;
  deadline: string;
  penalty: string;
  password: string;
  memberLimit: number;
}

export interface UpdateGroupRequest {
  title?: string;
  description?: string;
  deadline?: string;
  penalty?: string;
  password?: string;
  memberLimit: number;
}

interface GroupDetail {
  id: number;
}

export interface JoinGroupResponse {
  groupId: number;
}

export interface GroupDetailResponse {
  id: number;
  title: string;
  description: string | null;
  startDate: string;
  deadline: string | null;
  penalty: string | null;
  inviteCode: string;
  memberLimit: number;
  currentMemberCount: number;
  createDate: string;
  inviteLink: string;
  status: GroupStatus;
  isJoined: boolean;
}

export const groupApi = {
  getMyGroups: (status: GroupStatus) =>
    apiRequest<GroupSummary[]>(`/groups?status=${status}`, {}, true),
  create: (request: CreateGroupRequest) =>
    apiRequest<GroupDetail>('/groups', {
      method: 'POST',
      body: JSON.stringify(request),
    }, true),
  getById: (groupId: number | string) =>
    apiRequest<GroupDetailResponse>(`/groups/${groupId}`, {}, true),
  getGroupByInviteCode: (inviteCode: string) =>
    apiRequest<GroupDetailResponse>(`/groups/invite/${inviteCode}`, {}, true),
  join: (inviteCode: string, password: string) =>
    apiRequest<JoinGroupResponse>(`/groups/join/${encodeURIComponent(inviteCode)}`, {
      method: 'POST',
      body: JSON.stringify({ password }),
    }, true),
  update: (groupId: number, request: UpdateGroupRequest) =>
    apiRequest<void>(`/groups/${groupId}`, {
      method: 'PUT',
      body: JSON.stringify(request),
    }, true),
};
