-- Hibernate creates or updates the table first; SQL initialization then installs this index.
-- Existing active duplicates intentionally fail startup so data can be resolved explicitly.
ALTER TABLE friendship ADD COLUMN IF NOT EXISTS requested_at timestamp;
ALTER TABLE friendship ALTER COLUMN requester_id SET NOT NULL;
ALTER TABLE friendship ALTER COLUMN receiver_id SET NOT NULL;
ALTER TABLE friendship ALTER COLUMN status SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_friendship_active_pair
    ON friendship (LEAST(requester_id, receiver_id), GREATEST(requester_id, receiver_id))
    WHERE status IN ('REQUESTED', 'ACCEPTED');
