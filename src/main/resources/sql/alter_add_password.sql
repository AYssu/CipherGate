-- 为 users 表添加密码字段，支持本地账号密码登录
-- 注册仅通过 GitHub OAuth，密码登录仅作为已注册用户的备选登录方式

ALTER TABLE users
    ADD COLUMN password VARCHAR(128) NULL COMMENT '密码(BCrypt加密)，本地账号登录使用' AFTER access_token;
