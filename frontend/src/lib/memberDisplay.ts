import type { MemberProvider } from './memberApi';

const PROVIDER_LABEL: Record<MemberProvider, string> = {
  LOCAL: '',
  GOOGLE: 'Google 계정',
};

export function isSocialProvider(provider: MemberProvider | undefined): boolean {
  return provider !== undefined && provider !== 'LOCAL';
}

// 닉네임 아래에 보여 주는 보조 표기. 아이디/비밀번호 회원은 "@아이디",
// 소셜 회원은 자동으로 만들어진 아이디 대신 가입 방식을 보여 준다.
export function memberHandle(provider: MemberProvider | undefined, username: string): string {
  if (provider && isSocialProvider(provider)) return PROVIDER_LABEL[provider];
  return `@${username}`;
}
