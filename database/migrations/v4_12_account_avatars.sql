-- Persist an uploaded avatar URL against the authenticated account.
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(500) NULL;
