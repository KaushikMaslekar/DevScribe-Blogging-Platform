-- Normalize user roles for writer/editor/admin workflow
UPDATE users
SET role = 'WRITER'
WHERE role = 'USER';

ALTER TABLE users
    ALTER COLUMN role SET DEFAULT 'WRITER';

-- Post soft delete support
ALTER TABLE posts
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS deleted_by BIGINT;

ALTER TABLE posts
    DROP CONSTRAINT IF EXISTS fk_posts_deleted_by;

ALTER TABLE posts
    ADD CONSTRAINT fk_posts_deleted_by
        FOREIGN KEY (deleted_by)
        REFERENCES users(id)
        ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_posts_deleted_at
    ON posts (deleted_at DESC)
    WHERE deleted_at IS NOT NULL;

-- Audit log for critical actions
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGSERIAL PRIMARY KEY,
    actor_user_id BIGINT,
    action VARCHAR(80) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id VARCHAR(120),
    details TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_audit_logs_actor FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON audit_logs (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_actor_user_id ON audit_logs (actor_user_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_entity ON audit_logs (entity_type, entity_id, created_at DESC);

-- Feature flags with rollout cohorts
CREATE TABLE IF NOT EXISTS feature_flags (
    flag_key VARCHAR(120) PRIMARY KEY,
    description TEXT,
    enabled BOOLEAN NOT NULL DEFAULT false,
    rollout_percentage INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_feature_flags_rollout CHECK (rollout_percentage >= 0 AND rollout_percentage <= 100)
);

INSERT INTO feature_flags (flag_key, description, enabled, rollout_percentage)
VALUES
    ('editor.inline-comments', 'Inline comments in editor', false, 0),
    ('editor.suggest-mode', 'Track changes suggest mode', false, 0),
    ('posts.co-author', 'Co-author support for posts', false, 0)
ON CONFLICT (flag_key) DO NOTHING;
