CREATE TABLE settings (setting_key VARCHAR(80) PRIMARY KEY, setting_value TEXT NOT NULL);
-- A lock, not demo data: serializes race mutations across backend instances.
INSERT INTO settings(setting_key,setting_value) VALUES ('race_mutex','1');
CREATE TABLE browser_sessions (token_hash VARCHAR(64) PRIMARY KEY, expires_at BIGINT NOT NULL);
CREATE TABLE quiz_results (
 id VARCHAR(36) PRIMARY KEY, user_id VARCHAR(64) NOT NULL, country VARCHAR(2) NOT NULL,
 clan VARCHAR(16) NOT NULL, payload TEXT NOT NULL, version INTEGER NOT NULL,
 created_at BIGINT NOT NULL, retake INTEGER NOT NULL
);
CREATE TABLE participants (
 id VARCHAR(36) PRIMARY KEY, user_id VARCHAR(64) NOT NULL UNIQUE, created_at BIGINT NOT NULL,
 country VARCHAR(2) NOT NULL, region VARCHAR(80) NOT NULL, timezone VARCHAR(100) NOT NULL,
 clan VARCHAR(16) NOT NULL CHECK (clan IN ('cullen','volturi','denali','egyptian')),
 quiz_result_id VARCHAR(36) NOT NULL REFERENCES quiz_results(id),
 quiz_completed BOOLEAN NOT NULL DEFAULT TRUE, clan_confirmed BOOLEAN NOT NULL DEFAULT TRUE,
 clan_confirmed_at BIGINT NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX participant_cutoff ON participants(created_at,clan);
CREATE TABLE daily_races (
 id VARCHAR(10) PRIMARY KEY, start_at BIGINT NOT NULL, end_at BIGINT NOT NULL,
 config TEXT NOT NULL, snapshot TEXT, archived_at BIGINT
);
CREATE TABLE schedule_eligibility (
 id VARCHAR(80) PRIMARY KEY, race_day_id VARCHAR(10) NOT NULL REFERENCES daily_races(id),
 schedule_id INTEGER NOT NULL, clan VARCHAR(16) NOT NULL, eligible INTEGER NOT NULL,
 UNIQUE(race_day_id,schedule_id,clan)
);
CREATE TABLE proofs (
 id VARCHAR(36) PRIMARY KEY, race_day_id VARCHAR(10) NOT NULL REFERENCES daily_races(id),
 schedule_id INTEGER NOT NULL, participant_id VARCHAR(36) NOT NULL REFERENCES participants(id),
 clan VARCHAR(16) NOT NULL, country VARCHAR(2) NOT NULL, region VARCHAR(80) NOT NULL,
 submitted_at BIGINT NOT NULL, proof_url TEXT, object_key VARCHAR(100), content_type VARCHAR(40),
 hash VARCHAR(64) NOT NULL, verified_hash VARCHAR(64) UNIQUE,
 status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING','VERIFIED','REJECTED','DUPLICATE')),
 verified_at BIGINT, reviewed_by VARCHAR(100),
 UNIQUE(race_day_id,schedule_id,participant_id)
);
CREATE INDEX proof_race ON proofs(race_day_id,schedule_id,status);
CREATE INDEX proof_hash ON proofs(hash);
CREATE TABLE admin_audit (
 id VARCHAR(36) PRIMARY KEY, actor VARCHAR(100) NOT NULL, action VARCHAR(40) NOT NULL,
 created_at BIGINT NOT NULL, detail TEXT NOT NULL
);
