-- Keep legacy Skill columns during the compatibility window while adding one typed review identity.
ALTER TABLE review_task
    ADD COLUMN subject_type VARCHAR(32),
    ADD COLUMN subject_id BIGINT,
    ADD COLUMN subject_version_id BIGINT,
    ADD COLUMN subject_version VARCHAR(64);

UPDATE review_task
SET subject_type = 'SKILL_VERSION',
    subject_id = skill_id,
    subject_version_id = skill_version_id,
    subject_version = skill_version;

ALTER TABLE review_task
    ALTER COLUMN subject_type SET NOT NULL,
    ALTER COLUMN subject_id SET NOT NULL,
    ALTER COLUMN subject_version SET NOT NULL,
    ALTER COLUMN skill_id DROP NOT NULL,
    ALTER COLUMN skill_version DROP NOT NULL;

CREATE INDEX idx_review_task_subject_attempts
    ON review_task(subject_type, subject_id, subject_version, submitted_at DESC, id DESC);

CREATE UNIQUE INDEX idx_review_task_suite_version_pending
    ON review_task(subject_type, subject_version_id)
    WHERE subject_type = 'SUITE_VERSION' AND status = 'PENDING';
