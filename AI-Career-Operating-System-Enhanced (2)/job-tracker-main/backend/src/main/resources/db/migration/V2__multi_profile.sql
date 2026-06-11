-- ============================================================
-- V2 — Multi-profile support
--
-- Each person creates their own profile; jobs, applications and
-- sync_records are scoped to a profile via profile_id.
--
-- Job IDs are namespaced from "source#extId" → "profileId#source#extId"
-- so the same external listing can be scored separately per person.
-- ============================================================

-- user_profile: add name field and convert singleton id to auto-increment
ALTER TABLE user_profile
    MODIFY COLUMN id INT NOT NULL AUTO_INCREMENT,
    ADD COLUMN name VARCHAR(255) NOT NULL DEFAULT 'Me' AFTER id;

-- Preserve the legacy default profile (id=1)
INSERT IGNORE INTO user_profile (id, name) VALUES (1, 'Me');

-- jobs: add profile_id and migrate existing IDs to "1#source#extId" format
ALTER TABLE jobs
    ADD COLUMN profile_id INT NOT NULL DEFAULT 1 AFTER id,
    ADD INDEX idx_jobs_profile_id (profile_id);

UPDATE jobs
    SET id = CONCAT('1#', id)
    WHERE id REGEXP '^(greenhouse|lever|ashby|remotive)#';

-- applications: add profile_id and fix job_id references to match new format
ALTER TABLE applications
    ADD COLUMN profile_id INT NOT NULL DEFAULT 1 AFTER id,
    ADD INDEX idx_applications_profile_id (profile_id);

UPDATE applications
    SET job_id = CONCAT('1#', job_id)
    WHERE job_id REGEXP '^(greenhouse|lever|ashby|remotive)#';

-- sync_records: add profile_id
ALTER TABLE sync_records
    ADD COLUMN profile_id INT NOT NULL DEFAULT 1 AFTER id,
    ADD INDEX idx_sync_records_profile_id (profile_id);
