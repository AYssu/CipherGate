-- Legacy recharge grants remain unchanged; new integrations require explicit administrator authorization.
ALTER TABLE third_party_credential
    ADD COLUMN allow_trial TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN allow_query TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE third_party_recharge_log
    ADD COLUMN operation VARCHAR(32) NOT NULL DEFAULT 'RECHARGE';
