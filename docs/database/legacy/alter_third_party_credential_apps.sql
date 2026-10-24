-- 三方充值凭证支持多个应用。旧凭证没有关联记录时仍使用原 app_id；不重置 Key/Secret。
CREATE TABLE IF NOT EXISTS third_party_credential_app (
    credential_id BIGINT NOT NULL COMMENT '三方充值凭证ID',
    app_id BIGINT NOT NULL COMMENT '授权应用ID',
    PRIMARY KEY (credential_id, app_id),
    INDEX idx_tp_credential_app_lookup (app_id, credential_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='三方充值凭证授权应用';
