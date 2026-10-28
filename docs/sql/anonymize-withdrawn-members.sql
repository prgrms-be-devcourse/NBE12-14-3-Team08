-- 기존 탈퇴 회원 익명화 (환경마다 한 번만 실행)
--
-- 회원 탈퇴 정책을 "행은 남기고 계정 정보를 익명화"로 바꾸기 전에 탈퇴한 회원(status = 'WITHDRAWN')은
-- 아이디·비밀번호·소셜 식별자가 그대로 남아 있어 같은 아이디나 구글 계정으로 다시 가입할 수 없다.
-- 이 스크립트로 새 정책과 같은 값으로 맞춘다. 닉네임과 방·인증·벌칙 기록은 그대로 둔다.
--
-- 새 정책과 같은 값
--   username    : 'withdrawn_' + 회원 id   (아이디 중복을 피하기 위해 회원마다 다르게)
--   password    : '!withdrawn'             (BCrypt 해시 형식이 아니라 어떤 비밀번호와도 일치하지 않음)
--   provider    : 'LOCAL', provider_id: NULL (같은 구글 계정으로 새로 가입할 수 있음)
--   refresh_token: NULL
--
-- 실행 순서 (psql 기준)
--   1) 아래 두 조회를 먼저 실행한다.
--      -- 대상 확인
--      select id, username, nickname, provider from member where status = 'WITHDRAWN' order by id;
--      -- 충돌 확인: 탈퇴하지 않은 회원이 이미 'withdrawn_' 아이디를 쓰고 있으면 유니크 제약에 걸리므로 결과가 0건이어야 한다.
--      select id, username from member where username like 'withdrawn\_%' and status <> 'WITHDRAWN';
--   2) begin; 으로 트랜잭션을 열고 아래 update를 실행한 뒤, 건수를 확인하고 commit; 한다. (틀렸다면 rollback;)
--
-- 여러 번 실행해도 결과가 같다. (이미 같은 값으로 바뀐 행은 값이 그대로다)

update member
set username = 'withdrawn_' || id,
    password = '!withdrawn',
    provider = 'LOCAL',
    provider_id = null,
    refresh_token = null
where status = 'WITHDRAWN';
