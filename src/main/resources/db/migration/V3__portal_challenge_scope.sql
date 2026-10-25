-- Bound email-change challenges include the user and application identifiers.
-- Widening columns preserves existing rows and remains readable by older application versions.
ALTER TABLE portal_verify_code MODIFY purpose VARCHAR(80) NOT NULL COMMENT 'RECOVERY or EMAIL_CHANGE:userId:appId';
ALTER TABLE portal_verify_code MODIFY email VARCHAR(254) NOT NULL;
