ALTER TABLE inventory
ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ;

UPDATE inventory
SET created_at = NOW()
WHERE created_at IS NULL;