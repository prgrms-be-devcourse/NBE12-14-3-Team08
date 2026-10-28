import type { MemberProvider } from './memberApi';

// 탈퇴한 회원의 아이디는 서버가 "withdrawn_" + 회원 id로 바꿔 둔다. (Member.WITHDRAWN_USERNAME_PREFIX)
const WITHDRAWN_USERNAME_PREFIX = 'withdrawn_';

const PROVIDER_LABEL: Record<MemberProvider, string> = {
  LOCAL: '',
  GOOGLE: 'Google 계정',
};

export function isSocialProvider(provider: MemberProvider | undefined): boolean {
  return provider !== undefined && provider !== 'LOCAL';
}

export function isWithdrawnUsername(username: string): boolean {
  return username.startsWith(WITHDRAWN_USERNAME_PREFIX);
}

// 닉네임 아래에 보여 주는 보조 표기. 아이디/비밀번호 회원은 "@아이디",
// 소셜 회원은 자동으로 만들어진 아이디 대신 가입 방식을 보여 준다.
// 탈퇴한 회원은 닉네임이 그대로 남아 있어 구분할 수 있도록 "탈퇴한 회원"이라고 보여 준다.
export function memberHandle(provider: MemberProvider | undefined, username: string): string {
  if (isWithdrawnUsername(username)) return '탈퇴한 회원';
  if (provider && isSocialProvider(provider)) return PROVIDER_LABEL[provider];
  return `@${username}`;
}
