CREATE TABLE IF NOT EXISTS post_series (
    id BIGSERIAL PRIMARY KEY,
    author_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    slug VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_post_series_author_slug UNIQUE (author_id, slug)
);

ALTER TABLE posts
    ADD COLUMN IF NOT EXISTS series_id BIGINT REFERENCES post_series(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS series_order INTEGER;

ALTER TABLE post_autosave_snapshots
    ADD COLUMN IF NOT EXISTS series_title VARCHAR(255),
    ADD COLUMN IF NOT EXISTS series_order INTEGER;

CREATE INDEX IF NOT EXISTS idx_posts_series_id ON posts(series_id);
CREATE INDEX IF NOT EXISTS idx_posts_series_id_order ON posts(series_id, series_order);
