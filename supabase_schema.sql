-- ============================================================
-- 专注时刻 Supabase 数据库结构 v1.1
-- 使用方法：
--   1. 打开 https://supabase.com -> 你的项目
--   2. 左侧 SQL Editor -> New query
--   3. 粘贴本文件全部内容 -> Run
--   4. Authentication -> Providers -> Email：
--      关闭「Confirm email」（否则手机号注册无法直接登录）
-- ============================================================

-- ---------- 用户资料（昵称 / 头像 / 手机号） ----------
create table if not exists profiles (
    id uuid primary key,
    nickname text not null default '专注者',
    avatar text,                -- 头像 base64
    phone text
);

-- ---------- 日程 ----------
create table if not exists schedules (
    id text primary key,
    user_id uuid,
    title text not null default '',
    category text not null default 'OTHER',
    date text not null default '',
    start_time text,
    planned_minutes int,
    mode text not null default 'COUNTDOWN',
    repeat_rule text not null default 'ONCE',
    repeat_days text,
    archived boolean not null default false,
    updated_at bigint not null default 0,
    deleted boolean not null default false
);

-- ---------- 专注记录 ----------
create table if not exists focus_sessions (
    id text primary key,
    user_id uuid,
    schedule_id text,
    title text not null default '',
    category text not null default 'OTHER',
    started_at bigint not null default 0,
    ended_at bigint not null default 0,
    planned_minutes int not null default 0,
    actual_seconds int not null default 0,
    mode text not null default 'COUNTDOWN',
    status text not null default 'COMPLETED',
    updated_at bigint not null default 0,
    deleted boolean not null default false,
    todo_item_id text,
    source text
);

-- ---------- 待办 ----------
create table if not exists todo_items (
    id text primary key,
    user_id uuid,
    name text not null default '',
    type text not null default 'NORMAL',      -- NORMAL / GOAL / HABIT
    timing text not null default 'COUNTDOWN', -- COUNTDOWN / COUNTUP / NONE
    planned_minutes int,
    target_minutes int,
    note text,
    hide_next_day boolean not null default false,
    rest_minutes int not null default 5,
    set_id text,
    order_idx int not null default 0,
    last_done_date text,
    archived boolean not null default false,
    updated_at bigint not null default 0,
    deleted boolean not null default false
);

-- ---------- 待办集 ----------
create table if not exists todo_sets (
    id text primary key,
    user_id uuid,
    name text not null default '',
    auto_continue boolean not null default true,
    long_rest_minutes int not null default 15,
    updated_at bigint not null default 0,
    deleted boolean not null default false
);

-- ---------- 自定义锁机时段 ----------
create table if not exists lock_periods (
    id text primary key,
    user_id uuid,
    start_hhmm text not null default '',
    end_hhmm text not null default '',
    repeat_rule text not null default 'DAILY',
    repeat_days text,
    anchor_date text not null default '',
    enabled boolean not null default true,
    updated_at bigint not null default 0,
    deleted boolean not null default false
);

-- ============================================================
-- RLS（行级安全）：用户只能访问自己的数据
-- ============================================================
alter table profiles      enable row level security;
alter table schedules     enable row level security;
alter table focus_sessions enable row level security;
alter table todo_items    enable row level security;
alter table todo_sets     enable row level security;
alter table lock_periods  enable row level security;

-- profiles
drop policy if exists "profiles_own" on profiles;
create policy "profiles_own" on profiles
    for all to authenticated
    using (auth.uid() = id)
    with check (auth.uid() = id);

-- 数据表
drop policy if exists "schedules_own" on schedules;
create policy "schedules_own" on schedules
    for all to authenticated
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

drop policy if exists "focus_sessions_own" on focus_sessions;
create policy "focus_sessions_own" on focus_sessions
    for all to authenticated
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

drop policy if exists "todo_items_own" on todo_items;
create policy "todo_items_own" on todo_items
    for all to authenticated
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

drop policy if exists "todo_sets_own" on todo_sets;
create policy "todo_sets_own" on todo_sets
    for all to authenticated
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

drop policy if exists "lock_periods_own" on lock_periods;
create policy "lock_periods_own" on lock_periods
    for all to authenticated
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

-- 索引（提升同步查询速度）
create index if not exists idx_schedules_user on schedules(user_id);
create index if not exists idx_sessions_user on focus_sessions(user_id);
create index if not exists idx_todo_items_user on todo_items(user_id);
create index if not exists idx_todo_sets_user on todo_sets(user_id);
create index if not exists idx_lock_periods_user on lock_periods(user_id);
