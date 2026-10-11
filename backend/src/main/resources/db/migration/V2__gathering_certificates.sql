-- V1 is immutable because it has already run on deployed databases.
ALTER TABLE daily_races RENAME TO daily_gatherings;
ALTER TABLE schedule_eligibility RENAME COLUMN race_day_id TO gathering_day_id;
ALTER TABLE proofs RENAME COLUMN race_day_id TO gathering_day_id;
ALTER INDEX proof_race RENAME TO proof_gathering;
UPDATE settings SET setting_key='gathering_mutex' WHERE setting_key='race_mutex';
-- Preserve archived snapshots byte-for-byte. Only editable configuration is renamed.
UPDATE settings SET setting_value=REPLACE(REPLACE(REPLACE(setting_value,
 'RACE','GATHERING'),'Race','Gathering'),'race','gathering') WHERE setting_key='config';
-- Old pending uploads were never accepted; do not retroactively award participation.
UPDATE proofs SET status='REJECTED' WHERE status='PENDING';
ALTER TABLE proofs DROP COLUMN proof_url;
ALTER TABLE proofs DROP COLUMN object_key;
ALTER TABLE proofs DROP COLUMN content_type;
ALTER TABLE proofs DROP COLUMN reviewed_by;
UPDATE settings SET setting_value=REPLACE(REPLACE(setting_value,
 'reviewNote','certificateNote'),
 'Review proofs before the gathering day closes at 00:00 KST.',
 'Certificates are accepted automatically after a format check; images are not stored.')
 WHERE setting_key='config';
