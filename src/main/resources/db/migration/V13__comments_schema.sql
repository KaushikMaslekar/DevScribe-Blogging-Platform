-- Comments table with threaded support
create table if not exists comments (
    id bigserial primary key,
    post_id bigint not null references posts(id) on delete cascade,
    author_id bigint not null references users(id) on delete cascade,
    parent_comment_id bigint references comments(id) on delete cascade,
    content text not null,
    status varchar(32) not null default 'ACTIVE', -- ACTIVE, FLAGGED, DELETED
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);

-- Indexes for efficient querying
create index if not exists idx_comments_post_id on comments(post_id);
create index if not exists idx_comments_author_id on comments(author_id);
create index if not exists idx_comments_parent_id on comments(parent_comment_id);
create index if not exists idx_comments_post_created on comments(post_id, created_at desc);
create index if not exists idx_comments_active on comments(status) where status = 'ACTIVE';

-- Constraint: ensure no cycles in threaded structure
-- (cycle prevention is handled at application level)

