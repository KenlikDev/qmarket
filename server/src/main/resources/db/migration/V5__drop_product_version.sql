-- Drop orphan version column (never used with @Version; stock uses atomic UPDATE)
ALTER TABLE products DROP COLUMN IF EXISTS version;
