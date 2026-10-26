-- 주간 판정 부하 테스트용 시드. 부하 테스트 전용 DB(naegi_load)를 비우고 다시 채우므로 판정을 한 번 돌린 뒤 초기 상태로 되돌리는 용도로도 쓴다.
-- 사용: seed.sh 가 `psql -v habits=<습관 수>` 로 실행한다. (습관 수는 10의 배수)
--
-- 만드는 데이터 (습관 10개 = 그룹 1개, 회원 10명이 습관을 하나씩)
--   - 회원 id = 그룹 멤버 id = 습관 id (1..habits) 로 같은 순서라 k6가 id만으로 토큰과 요청 대상을 정할 수 있다.
--   - 그룹의 70%는 14일 전에 생성(오늘이 마감 요일이라 검토 유예가 남음), 30%는 17일 전에 생성(마감 요일이 3일 전이라 유예가 지남)
--   - 그룹 안 10명의 인증 상태: 0~5번째는 승인 3건(통과), 6~8번째는 승인 1건(부족으로 실패), 9번째는 대기 3건
--       유예가 남은 그룹의 대기 건은 판정 보류, 유예가 지난 그룹의 대기 건은 자동 승인되어 통과
\set ON_ERROR_STOP on

create extension if not exists pgcrypto;

truncate penalty_verify, habit_verify, habit, group_member, groups, member restart identity cascade;

-- 비밀번호는 한 번만 해시해서 모두에게 쓴다. (모든 시드 회원의 비밀번호: password1234)
create temp table seed_pw as select crypt('password1234', gen_salt('bf', 10)) as hash;

insert into member (create_date, modify_date, nickname, password, status, username)
select now(), now(), 'lt-nick-' || i, (select hash from seed_pw), 'ACTIVE', 'lt-' || i
from generate_series(1, :habits) as i
order by i;

-- 그룹 g: 7/10은 14일 전, 3/10은 17일 전 생성
insert into groups (create_date, modify_date, deadline, description, invite_code, member_limit, password, penalty, status, title)
select
    date_trunc('day', now()) - (case when g % 10 < 7 then 14 else 17 end) * interval '1 day' + interval '9 hours',
    now(), current_date + 30, '부하 테스트 그룹', 'LT' || lpad(g::text, 6, '0'), 10,
    (select hash from seed_pw), '커피 사기', 'ACTIVE', 'lt-group-' || g
from generate_series(1, :habits / 10) as g
order by g;

-- 회원 i는 그룹 (i-1)/10+1 의 (i-1)%10 번째 멤버. 첫 번째가 방장.
insert into group_member (create_date, modify_date, role, status, group_id, member_id)
select g.create_date + interval '1 day', now(),
       case when (i - 1) % 10 = 0 then 'OWNER' else 'MEMBER' end, 'ACTIVE', g.id, i
from generate_series(1, :habits) as i
join groups g on g.id = (i - 1) / 10 + 1
order by i;

insert into habit (create_date, modify_date, days, description, status, title, group_member_id)
select gm.create_date, now(), 3, '부하 테스트 습관', 'ACTIVE', 'lt-habit-' || gm.id, gm.id
from group_member gm
order by gm.id;

-- 인증: 판정 구간(마감 기준 직전 7일) 안의 날짜 1~3일 전에 올린 것으로 만든다.
-- 마감 기준일: 14일 전 그룹은 오늘 0시, 17일 전 그룹은 3일 전 0시
insert into habit_verify (create_date, modify_date, description, image_url, status, verify_date, habit_id)
select
    base.deadline - k * interval '1 day' + interval '10 hours', now(), '오늘도 완료', 'https://example.com/lt.png',
    case when pos = 9 then 'PENDING' else 'APPROVED' end,
    (base.deadline - k * interval '1 day')::date,
    h.id
from habit h
join group_member gm on gm.id = h.group_member_id
join groups g on g.id = gm.group_id
cross join lateral (select (h.id - 1) % 10 as pos,
                           date_trunc('day', now()) - (case when g.id % 10 < 7 then 0 else 3 end) * interval '1 day' as deadline) base
cross join generate_series(1, 3) as k
where (pos <= 5) or (pos between 6 and 8 and k = 1) or (pos = 9);

analyze;

select 'members' as what, count(*) from member
union all select 'groups', count(*) from groups
union all select 'habits', count(*) from habit
union all select 'verifies', count(*) from habit_verify
union all select 'pending verifies', count(*) from habit_verify where status = 'PENDING';
