-- V3: Add dismissed flag to jobs
-- Allows users to mark a job as "won't apply", removing it from the active queue.
ALTER TABLE jobs
    ADD COLUMN dismissed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD INDEX idx_jobs_dismissed (dismissed);
