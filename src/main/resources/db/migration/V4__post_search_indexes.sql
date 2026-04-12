CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_posts_published_title_trgm
    ON posts USING gin (lower(title) gin_trgm_ops)
    WHERE status = 'PUBLISHED';

CREATE INDEX IF NOT EXISTS idx_posts_published_excerpt_trgm
    ON posts USING gin (lower(coalesce(excerpt, '')) gin_trgm_ops)
    WHERE status = 'PUBLISHED';

CREATE INDEX IF NOT EXISTS idx_posts_published_markdown_content_trgm
    ON posts USING gin (lower(markdown_content) gin_trgm_ops)
    WHERE status = 'PUBLISHED';
