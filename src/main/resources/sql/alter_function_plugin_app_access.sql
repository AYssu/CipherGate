-- 函数插件必须显式授权给应用后，应用才能通过 WS 调用其函数。
CREATE TABLE IF NOT EXISTS function_plugin_app_access (
    plugin_id VARCHAR(100) NOT NULL COMMENT '函数插件标识',
    app_id BIGINT NOT NULL COMMENT '被授权应用ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (plugin_id, app_id),
    INDEX idx_func_access_app (app_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='函数插件应用访问授权';
