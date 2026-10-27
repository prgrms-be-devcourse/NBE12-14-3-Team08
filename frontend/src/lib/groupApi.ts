import { apiRequest } from './memberApi';

export type GroupStatus = 'ACTIVE' | 'FINISH';
export type GroupMemberRole = 'OWNER' | 'MEMBER';

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
