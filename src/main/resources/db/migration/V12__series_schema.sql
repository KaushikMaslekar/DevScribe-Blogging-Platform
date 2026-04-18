create table if not exists series (
    id bigserial primary key,
    author_id bigint not null references users(id) on delete cascade,
    slug varchar(255) not null unique,
    title varchar(255) not null,
    description text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_series_author_updated_at on series(author_id, updated_at desc);

create table if not exists series_posts (
    id bigserial primary key,
    series_id bigint not null references series(id) on delete cascade,
    post_id bigint not null references posts(id) on delete cascade,
    sort_order integer not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_series_posts_series_post unique (series_id, post_id),
    constraint uq_series_posts_series_sort_order unique (series_id, sort_order)
);

create index if not exists idx_series_posts_post_id on series_posts(post_id);

