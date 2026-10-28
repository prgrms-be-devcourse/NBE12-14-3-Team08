export function ProfileAvatar({ nickname }: { nickname: string }) {
  const initial = nickname.trim().charAt(0).toUpperCase() || '?';

  return (
    <div
      role="img"
      aria-label={`${nickname} 프로필`}
      className="flex h-16 w-16 shrink-0 items-center justify-center rounded-full bg-lime-400 text-2xl font-black text-forest-900 shadow-sm ring-4 ring-lime-400/25"
    >
      {initial}
    </div>
  );
}
