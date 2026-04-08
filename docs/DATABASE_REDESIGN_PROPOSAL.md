# CipherGate 数据库重构方案

## 一、EasyVerify 现有模块分析

### 1.1 核心表结构

#### 原有表关系
```
easy_user (管理员用户)
    ↓
easy_project (应用/项目)
    ↓
easy_card (卡密)

open_user (终端用户)
    ↓
open_user_project (用户-应用关联)
```

### 1.2 字段分析

#### easy_project (应用表) - 27个字段
**核心字段（必须保留）：**
- project_id, project_user, project_name, project_create_time
- project_key (API密钥), project_aes (AES密钥)
- project_message (描述), project_notice (公告)
- project_status (状态), deleted (软删除)

**加密相关（需要但可优化）：**
- project_encryption (加密方式: 1=base64, 2=rsa)
- project_rsa_private, project_rsa_public
- project_base64 (自定义base64编码表)

**业务模式（需要但可优化）：**
- project_model (1=收费, 2=免费, 3=加时)
- free_status (是否免费)
- web_unbind (网页解绑开关)
- return_update, return_verify (返回配置)
- time_enable (函数碰撞解密)

**流量控制（可选，看需求）：**
- traffic_max, traffic_use (流量统计)

**问题分析：**
1. ❌ project_model 和 free_status 功能重叠
2. ❌ 加密配置分散，应该统一管理
3. ❌ 缺少应用分类、标签等扩展字段
4. ❌ 缺少版本管理字段

---

#### easy_card (卡密表) - 25个字段
**核心字段（必须保留）：**
- cid, pid, uid, card_key
- card_type (卡密类型: 1=天卡, 2=周卡, 3=月卡, 4=半年卡, 5=年卡, 6=永久卡, 7=自定义, 8=季卡)
- card_time (倍率)
- create_time, first_bind_time, last_use_time, end_time
- state (状态), deleted

**设备绑定（必须保留）：**
- bind_imei (设备标识), bind_ip (IP地址)
- imei_check (是否开启设备码验证: 1=开启, 2=关闭)
- ip_check (是否开启IP验证: 1=开启, 2=关闭)
- need_imei (是否需要设备码)
- unbind_number, limit_unbind_number (解绑次数控制)

**使用限制（必须保留）：**
- use_number, limit_use_number (使用次数)
- limit_use_time_begin, limit_use_time_end (时间段限制 - DATETIME 类型)

**扩展字段（必须保留）：**
- introduction (备注)
- core_date (核心标记数据)

**问题分析：**
1. ✅ 字段设计较合理，基本都有用
2. ⚠️ card_type 用数字表示，建议改为枚举或独立配置表
3. ⚠️ limit_use_time_begin/end 是 DATETIME，应该改为 TIME 类型（只需要时间段）
4. ❌ 缺少批次管理（批量生成的卡密应该有批次号）
5. ❌ 缺少来源追踪（从哪个渠道生成的）


---

#### open_user (终端用户表) - 10个字段
**核心字段：**
- open_user_id, open_email, open_password
- user_name (用户名), autograph (个性签名)
- open_create_time, deleted

**VIP相关（可优化）：**
- open_vip (VIP等级: 1=普通用户)
- open_svip_time (体验时长到期时间)
- open_last_solo_time (每日体验时间记录)

**问题分析：**
1. ❌ 字段命名不统一（open_ 前缀混乱）
2. ❌ VIP 逻辑应该独立到会员表或移到绑定表
3. ❌ 缺少手机号、头像、昵称等基础字段
4. ❌ 缺少最后登录时间、登录次数等统计
5. ✅ user_name 字段已存在（之前遗漏了）

---

#### open_user_project (用户-应用关联表) - 10个字段
**完整字段列表：**
- open_user_project_id (主键)
- open_user_id, project_id (外键)
- last_time (最后登录时间)
- key_time (到期时间)
- allow_unlink (允许解绑: 1=允许, 0=不允许)
- use_num (使用次数)
- band (是否封禁: 0=正常, 1=封禁)
- trial (是否已试用: 0=未试用, 1=已试用)
- trial_end_time (试用结束时间)

**问题分析：**
1. ✅ 关联表设计合理
2. ❌ 缺少绑定设备信息（应该记录用户在哪个设备上使用）
3. ❌ 缺少首次绑定时间
4. ❌ 缺少用户备注字段
5. ❌ 缺少绑定类型（卡密绑定、试用绑定、VIP绑定）

---

## 二、CipherGate 重构方案

### 2.1 设计原则
1. **统一命名规范**：去除混乱的前缀，使用清晰的表名
2. **职责分离**：将复杂功能拆分到独立表
3. **扩展性优先**：预留扩展字段和配置表
4. **审计完整**：所有关键操作都要有日志
5. **兼容 CipherGate 现有架构**：与现有的 User、Role、Permission 体系集成


### 2.2 新表结构设计

#### 1. application (应用表)
```sql
CREATE TABLE `application` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '应用ID',
  `owner_id` BIGINT NOT NULL COMMENT '所属用户ID (关联 user 表)',
  `app_name` VARCHAR(100) NOT NULL COMMENT '应用名称',
  `app_key` VARCHAR(64) NOT NULL COMMENT 'API密钥',
  `app_secret` VARCHAR(128) NOT NULL COMMENT 'API密钥(加密存储)',
  
  -- 基础信息
  `description` VARCHAR(500) COMMENT '应用描述',
  `notice` TEXT COMMENT '应用公告',
  `category` VARCHAR(50) COMMENT '应用分类',
  `tags` VARCHAR(255) COMMENT '标签(逗号分隔)',
  `icon_url` VARCHAR(255) COMMENT '应用图标',
  
  -- 业务模式
  `business_model` TINYINT NOT NULL DEFAULT 1 COMMENT '业务模式: 1=付费, 2=免费, 3=试用+付费',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1=正常, 2=维护, 3=停用',
  
  -- 加密配置 (JSON 存储，灵活扩展)
  `encryption_config` JSON COMMENT '加密配置: {rsaPublic, rsaPrivate, customParams}',
  `encryption_plugin` VARCHAR(100) DEFAULT 'rsa-default' COMMENT '加密插件标识',
  
  -- 功能开关
  `features` JSON COMMENT '功能开关: {webUnbind, returnUpdate, returnVerify, timeCollision}',
  
  -- 流量统计
  `traffic_limit` BIGINT COMMENT '流量限制(字节)',
  `traffic_used` BIGINT DEFAULT 0 COMMENT '已使用流量',
  
  -- 版本管理
  `current_version` VARCHAR(20) COMMENT '当前版本号',
  `min_version` VARCHAR(20) COMMENT '最低支持版本',
  
  -- 审计字段
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` DATETIME COMMENT '软删除时间',
  
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_key` (`app_key`),
  KEY `idx_owner` (`owner_id`),
  KEY `idx_status` (`status`),
  KEY `idx_deleted` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用表';
```

**优化点：**
- ✅ 统一命名，去除 project 前缀
- ✅ 使用 JSON 存储加密配置参数，便于不同插件的自定义配置
- ✅ encryption_plugin 字段标识使用的加密插件（支持插件化扩展）
- ✅ 增加分类、标签、图标等现代化字段
- ✅ 版本管理字段独立出来
- ✅ 使用 BIGINT 作为主键，支持更大规模
- ✅ 去除 project_encryption、project_base64、project_aes 等固定加密方式字段


---

#### 2. license_key (卡密表)
```sql
CREATE TABLE `license_key` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '卡密ID',
  `app_id` BIGINT NOT NULL COMMENT '所属应用ID',
  `owner_id` BIGINT NOT NULL COMMENT '创建者ID',
  `key_code` VARCHAR(128) NOT NULL COMMENT '卡密码',
  
  -- 卡密类型
  `key_type` VARCHAR(20) NOT NULL COMMENT '卡密类型: DAY,WEEK,MONTH,QUARTER,HALF_YEAR,YEAR,PERMANENT,CUSTOM',
  `duration_value` INT COMMENT '时长数值(配合 key_type 使用)',
  `duration_unit` VARCHAR(10) COMMENT '时长单位: HOUR,DAY,MONTH',
  
  -- 批次管理
  `batch_id` BIGINT COMMENT '批次ID',
  `source` VARCHAR(50) COMMENT '来源: MANUAL,BATCH,API,IMPORT',
  
  -- 绑定信息
  `bind_device_id` VARCHAR(255) COMMENT '绑定设备标识',
  `bind_ip` VARCHAR(50) COMMENT '绑定IP',
  `bind_user_id` BIGINT COMMENT '绑定的终端用户ID',
  
  -- 时间管理
  `first_used_at` DATETIME COMMENT '首次使用时间',
  `last_used_at` DATETIME COMMENT '最后使用时间',
  `expires_at` DATETIME COMMENT '到期时间',
  
  -- 使用限制
  `use_count` INT DEFAULT 0 COMMENT '使用次数',
  `use_limit` INT DEFAULT 0 COMMENT '使用次数限制(0=不限)',
  `unbind_count` INT DEFAULT 0 COMMENT '解绑次数',
  `unbind_limit` INT DEFAULT 0 COMMENT '解绑次数限制(0=不限)',
  
  -- 时间段限制
  `use_time_start` TIME COMMENT '可使用时间段-开始',
  `use_time_end` TIME COMMENT '可使用时间段-结束',
  
  -- 验证开关
  `device_check_enabled` BOOLEAN DEFAULT TRUE COMMENT '是否验证设备',
  `ip_check_enabled` BOOLEAN DEFAULT FALSE COMMENT '是否验证IP',
  
  -- 扩展字段
  `remark` VARCHAR(500) COMMENT '备注',
  `core_data` TEXT COMMENT '核心标记数据',
  `metadata` JSON COMMENT '扩展元数据',
  
  -- 状态
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1=正常, 2=已禁用, 3=已过期, 4=已用完',
  
  -- 审计字段
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` DATETIME COMMENT '软删除时间',
  
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_key_code` (`key_code`),
  KEY `idx_app` (`app_id`),
  KEY `idx_owner` (`owner_id`),
  KEY `idx_batch` (`batch_id`),
  KEY `idx_bind_user` (`bind_user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_expires` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='卡密表';
```

**优化点：**
- ✅ 重命名为 license_key，更专业
- ✅ key_type 使用字符串枚举，更清晰
- ✅ 增加批次管理和来源追踪
- ✅ 时间段限制使用 TIME 类型
- ✅ 使用 JSON 存储扩展元数据
- ✅ 状态字段更细化


---

#### 3. license_batch (卡密批次表)
```sql
CREATE TABLE `license_batch` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '批次ID',
  `app_id` BIGINT NOT NULL COMMENT '所属应用ID',
  `creator_id` BIGINT NOT NULL COMMENT '创建者ID',
  `batch_name` VARCHAR(100) NOT NULL COMMENT '批次名称',
  `batch_code` VARCHAR(50) NOT NULL COMMENT '批次编号',
  
  -- 批次配置
  `key_type` VARCHAR(20) NOT NULL COMMENT '卡密类型',
  `duration_value` INT COMMENT '时长数值',
  `total_count` INT NOT NULL COMMENT '生成总数',
  `used_count` INT DEFAULT 0 COMMENT '已使用数量',
  
  -- 审计字段
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `remark` VARCHAR(500) COMMENT '备注',
  
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_code` (`batch_code`),
  KEY `idx_app` (`app_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='卡密批次表';
```

---

#### 4. app_user (应用终端用户表)
```sql
CREATE TABLE `app_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` VARCHAR(50) COMMENT '用户名',
  `email` VARCHAR(100) COMMENT '邮箱',
  `phone` VARCHAR(20) COMMENT '手机号',
  `password` VARCHAR(128) COMMENT '密码(加密)',
  
  -- 基础信息
  `nickname` VARCHAR(50) COMMENT '昵称',
  `avatar_url` VARCHAR(255) COMMENT '头像',
  `signature` VARCHAR(200) COMMENT '个性签名',
  
  -- 统计信息
  `login_count` INT DEFAULT 0 COMMENT '登录次数',
  `last_login_at` DATETIME COMMENT '最后登录时间',
  `last_login_ip` VARCHAR(50) COMMENT '最后登录IP',
  
  -- 审计字段
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` DATETIME COMMENT '软删除时间',
  
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_email` (`email`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_deleted` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用终端用户表';
```

**优化点：**
- ✅ 统一命名，去除 open_ 前缀
- ✅ 增加手机号、昵称等基础字段
- ✅ 增加登录统计字段
- ✅ VIP 逻辑移到关联表


---

#### 5. app_user_binding (应用-用户绑定表)
```sql
CREATE TABLE `app_user_binding` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '绑定ID',
  `app_id` BIGINT NOT NULL COMMENT '应用ID',
  `user_id` BIGINT NOT NULL COMMENT '终端用户ID',
  
  -- 绑定信息
  `bind_type` VARCHAR(20) NOT NULL COMMENT '绑定类型: LICENSE,TRIAL,VIP',
  `license_key_id` BIGINT COMMENT '关联的卡密ID',
  
  -- 设备信息
  `device_id` VARCHAR(255) COMMENT '设备标识',
  `device_name` VARCHAR(100) COMMENT '设备名称',
  `device_os` VARCHAR(50) COMMENT '设备系统',
  
  -- 时间管理
  `expires_at` DATETIME COMMENT '到期时间',
  `first_bind_at` DATETIME COMMENT '首次绑定时间',
  `last_active_at` DATETIME COMMENT '最后活跃时间',
  
  -- 使用统计
  `use_count` INT DEFAULT 0 COMMENT '使用次数',
  `unbind_count` INT DEFAULT 0 COMMENT '解绑次数',
  
  -- 试用相关
  `is_trial` BOOLEAN DEFAULT FALSE COMMENT '是否试用',
  `trial_expires_at` DATETIME COMMENT '试用到期时间',
  
  -- 权限控制
  `allow_unbind` BOOLEAN DEFAULT TRUE COMMENT '允许解绑',
  `is_banned` BOOLEAN DEFAULT FALSE COMMENT '是否封禁',
  `ban_reason` VARCHAR(255) COMMENT '封禁原因',
  
  -- 扩展字段
  `remark` VARCHAR(500) COMMENT '备注',
  `metadata` JSON COMMENT '扩展元数据',
  
  -- 状态
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1=正常, 2=已过期, 3=已封禁',
  
  -- 审计字段
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` DATETIME COMMENT '软删除时间',
  
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_user_device` (`app_id`, `user_id`, `device_id`),
  KEY `idx_app` (`app_id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_license` (`license_key_id`),
  KEY `idx_expires` (`expires_at`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用用户绑定表';
```

**优化点：**
- ✅ 增加设备详细信息
- ✅ 增加绑定类型（卡密、试用、VIP）
- ✅ 增加首次绑定时间
- ✅ 封禁原因字段
- ✅ 唯一索引防止重复绑定


---

#### 6. app_version (应用版本表)
```sql
CREATE TABLE `app_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '版本ID',
  `app_id` BIGINT NOT NULL COMMENT '应用ID',
  `version_code` VARCHAR(20) NOT NULL COMMENT '版本号',
  `version_name` VARCHAR(50) COMMENT '版本名称',
  
  -- 更新信息
  `update_log` TEXT COMMENT '更新日志',
  `download_url` VARCHAR(500) COMMENT '下载地址',
  `file_size` BIGINT COMMENT '文件大小(字节)',
  `file_hash` VARCHAR(64) COMMENT '文件哈希(SHA256)',
  
  -- 更新策略
  `is_force_update` BOOLEAN DEFAULT FALSE COMMENT '是否强制更新',
  `min_support_version` VARCHAR(20) COMMENT '最低支持版本',
  
  -- 发布状态
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1=草稿, 2=已发布, 3=已下架',
  `published_at` DATETIME COMMENT '发布时间',
  
  -- 审计字段
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_version` (`app_id`, `version_code`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用版本表';
```

---

#### 7. app_variable (应用变量表)
```sql
CREATE TABLE `app_variable` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '变量ID',
  `app_id` BIGINT NOT NULL COMMENT '应用ID',
  `var_key` VARCHAR(100) NOT NULL COMMENT '变量键',
  `var_value` TEXT COMMENT '变量值',
  `var_type` VARCHAR(20) COMMENT '变量类型: STRING,NUMBER,BOOLEAN,JSON',
  `description` VARCHAR(255) COMMENT '变量描述',
  
  -- 审计字段
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_var` (`app_id`, `var_key`),
  KEY `idx_app` (`app_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用变量表';
```

---

#### 8. app_login_log (应用登录日志表)
```sql
CREATE TABLE `app_login_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `app_id` BIGINT NOT NULL COMMENT '应用ID',
  `user_id` BIGINT COMMENT '用户ID',
  `license_key_id` BIGINT COMMENT '卡密ID',
  `binding_id` BIGINT COMMENT '绑定ID',
  
  -- 登录信息
  `login_type` VARCHAR(20) NOT NULL COMMENT '登录类型: LICENSE,USER,TRIAL',
  `device_id` VARCHAR(255) COMMENT '设备标识',
  `ip_address` VARCHAR(50) COMMENT 'IP地址',
  `user_agent` VARCHAR(500) COMMENT 'User Agent',
  
  -- 结果
  `is_success` BOOLEAN NOT NULL COMMENT '是否成功',
  `error_code` VARCHAR(50) COMMENT '错误码',
  `error_message` VARCHAR(255) COMMENT '错误信息',
  
  -- 审计字段
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  
  PRIMARY KEY (`id`),
  KEY `idx_app` (`app_id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_license` (`license_key_id`),
  KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用登录日志表';
```


---

## 三、字段对比与迁移映射

### 3.1 应用表字段映射

| EasyVerify (easy_project) | CipherGate (application) | 说明 |
|---------------------------|--------------------------|------|
| project_id | id | 改用 BIGINT |
| project_user | owner_id | 统一命名 |
| project_name | app_name | 统一命名 |
| project_create_time | created_at | 统一命名 |
| project_key | app_key | 统一命名 |
| project_aes | encryption_config.aesKey | 移到 JSON |
| project_message | description | 统一命名 |
| project_notice | notice | 保留 |
| project_model | business_model | 统一命名 |
| project_encryption | ❌ 删除 | 改用插件化 |
| project_rsa_private | encryption_config.rsaPrivate | 移到 JSON（可选） |
| project_rsa_public | encryption_config.rsaPublic | 移到 JSON（可选） |
| project_base64 | ❌ 删除 | 插件自定义 |
| project_aes | ❌ 删除 | 插件自定义 |
| ❌ 无 | encryption_plugin | 新增（插件标识） |
| web_unbind | features.webUnbind | 移到 JSON |
| return_update | features.returnUpdate | 移到 JSON |
| return_verify | features.returnVerify | 移到 JSON |
| time_enable | features.timeCollision | 移到 JSON |
| project_status | status | 统一命名 |
| free_status | ❌ 删除 | 合并到 business_model |
| traffic_max | traffic_limit | 统一命名 |
| traffic_use | traffic_used | 统一命名 |
| deleted | deleted_at | 改用时间戳 |
| ❌ 无 | category | 新增 |
| ❌ 无 | tags | 新增 |
| ❌ 无 | icon_url | 新增 |
| ❌ 无 | current_version | 新增 |
| ❌ 无 | min_version | 新增 |

### 3.2 卡密表字段映射

| EasyVerify (easy_card) | CipherGate (license_key) | 说明 |
|------------------------|--------------------------|------|
| cid | id | 改用 BIGINT |
| pid | app_id | 统一命名 |
| uid | owner_id | 统一命名 |
| card_key | key_code | 统一命名 |
| card_type | key_type | 改用字符串枚举 |
| card_time | duration_value | 统一命名 |
| ❌ 无 | duration_unit | 新增 |
| ❌ 无 | batch_id | 新增 |
| ❌ 无 | source | 新增 |
| bind_imei | bind_device_id | 统一命名 |
| bind_ip | bind_ip | 保留 |
| ❌ 无 | bind_user_id | 新增 |
| first_bind_time | first_used_at | 统一命名 |
| create_time | created_at | 统一命名 |
| last_use_time | last_used_at | 统一命名 |
| end_time | expires_at | 统一命名 |
| use_number | use_count | 统一命名 |
| limit_use_number | use_limit | 统一命名 |
| unbind_number | unbind_count | 统一命名 |
| limit_unbind_number | unbind_limit | 统一命名 |
| limit_use_time_begin | use_time_start | 改用 TIME 类型 |
| limit_use_time_end | use_time_end | 改用 TIME 类型 |
| imei_check | device_check_enabled | 统一命名 |
| ip_check | ip_check_enabled | 统一命名 |
| introduction | remark | 统一命名 |
| core_date | core_data | 统一命名 |
| ❌ 无 | metadata | 新增 JSON |
| state | status | 统一命名，状态更细化 |
| deleted | deleted_at | 改用时间戳 |


### 3.3 用户表字段映射

| EasyVerify (open_user) | CipherGate (app_user) | 说明 |
|------------------------|----------------------|------|
| open_user_id | id | 统一命名 |
| open_email | email | 去除前缀 |
| open_password | password | 去除前缀 |
| user_name | username | 统一命名 |
| autograph | signature | 统一命名 |
| open_create_time | created_at | 统一命名 |
| ❌ 无 | nickname | 新增 |
| ❌ 无 | phone | 新增 |
| ❌ 无 | avatar_url | 新增 |
| ❌ 无 | login_count | 新增 |
| ❌ 无 | last_login_at | 新增 |
| ❌ 无 | last_login_ip | 新增 |
| open_vip | ❌ 移到绑定表 | VIP 逻辑重构 |
| open_svip_time | ❌ 移到绑定表 | VIP 逻辑重构 |
| open_last_solo_time | ❌ 移到绑定表 | 试用逻辑重构 |
| deleted | deleted_at | 改用时间戳 |

### 3.4 绑定表字段映射

| EasyVerify (open_user_project) | CipherGate (app_user_binding) | 说明 |
|--------------------------------|-------------------------------|------|
| open_user_project_id | id | 统一命名 |
| open_user_id | user_id | 统一命名 |
| project_id | app_id | 统一命名 |
| key_time | expires_at | 统一命名 |
| use_num | use_count | 统一命名 |
| last_time | last_active_at | 统一命名 |
| allow_unlink | allow_unbind | 统一命名 |
| band | is_banned | 统一命名 |
| trial | is_trial | 统一命名 |
| trial_end_time | trial_expires_at | 统一命名 |
| ❌ 无 | bind_type | 新增 |
| ❌ 无 | license_key_id | 新增 |
| ❌ 无 | device_id | 新增 |
| ❌ 无 | device_name | 新增 |
| ❌ 无 | device_os | 新增 |
| ❌ 无 | first_bind_at | 新增 |
| ❌ 无 | unbind_count | 新增 |
| ❌ 无 | ban_reason | 新增 |
| ❌ 无 | remark | 新增 |
| ❌ 无 | metadata | 新增 JSON |
| ❌ 无 | status | 新增 |

---

## 四、核心优化总结

### 4.1 删除的字段
1. **easy_project.free_status** - 合并到 business_model
2. **easy_project.project_encryption** - 改用插件化架构
3. **easy_project.project_base64** - 插件自定义参数
4. **easy_project.project_aes** - 插件自定义参数
5. **open_user.open_vip/open_svip_time** - 移到绑定表，支持多应用不同 VIP

### 4.2 合并的字段
1. **加密配置** - 简化为 encryption_plugin + encryption_config JSON
2. **功能开关** - 合并到 features JSON
3. **卡密类型** - 从数字改为字符串枚举

### 4.3 新增的字段
1. **应用表**：category, tags, icon_url, current_version, min_version
2. **卡密表**：batch_id, source, bind_user_id, metadata
3. **用户表**：nickname, phone, avatar_url, login_count, last_login_at
4. **绑定表**：bind_type, device_name, device_os, first_bind_at, ban_reason

### 4.4 新增的表
1. **license_batch** - 卡密批次管理
2. **app_version** - 应用版本管理
3. **app_variable** - 应用变量（原来是 JSON 存储）
4. **app_login_log** - 登录日志（原来是 easy_login_log）


---

## 五、数据迁移策略

### 5.1 迁移步骤

```sql
-- 1. 迁移应用数据
INSERT INTO application (
  id, owner_id, app_name, app_key, description, notice,
  business_model, status, encryption_config, features,
  traffic_limit, traffic_used, created_at, encryption_plugin
)
SELECT 
  project_id,
  project_user,
  project_name,
  project_key,
  project_message,
  project_notice,
  project_model,
  CASE WHEN project_status = 0 THEN 1 ELSE 3 END,
  JSON_OBJECT(
    'rsaPrivate', project_rsa_private,
    'rsaPublic', project_rsa_public
  ),
  JSON_OBJECT(
    'webUnbind', web_unbind,
    'returnUpdate', return_update,
    'returnVerify', return_verify,
    'timeCollision', time_enable
  ),
  traffic_max,
  traffic_use,
  project_create_time,
  CASE 
    WHEN project_encryption = 2 THEN 'rsa-default'
    ELSE 'rsa-default'  -- 统一使用默认 RSA 插件
  END as encryption_plugin
FROM easy_project
WHERE deleted = 0;

-- 2. 迁移卡密数据
INSERT INTO license_key (
  id, app_id, owner_id, key_code, key_type, duration_value,
  bind_device_id, bind_ip, first_used_at, last_used_at, expires_at,
  use_count, use_limit, unbind_count, unbind_limit,
  device_check_enabled, ip_check_enabled, remark, core_data,
  status, created_at
)
SELECT 
  cid, pid, uid, card_key,
  CASE card_type
    WHEN 1 THEN 'DAY'
    WHEN 2 THEN 'WEEK'
    WHEN 3 THEN 'MONTH'
    WHEN 4 THEN 'HALF_YEAR'
    WHEN 5 THEN 'YEAR'
    WHEN 6 THEN 'PERMANENT'
    WHEN 7 THEN 'CUSTOM'
    WHEN 8 THEN 'QUARTER'
  END,
  card_time,
  bind_imei, bind_ip, first_bind_time, last_use_time, end_time,
  use_number, limit_use_number, unbind_number, limit_unbind_number,
  imei_check = 1, ip_check = 1, introduction, core_date,
  state, create_time
FROM easy_card
WHERE deleted = 0;

-- 3. 迁移终端用户数据
INSERT INTO app_user (
  id, username, email, password, signature, created_at
)
SELECT 
  open_user_id, user_name, open_email, open_password,
  autograph, open_create_time
FROM open_user
WHERE deleted = 0;

-- 4. 迁移绑定关系
INSERT INTO app_user_binding (
  id, app_id, user_id, bind_type, expires_at, last_active_at,
  use_count, allow_unbind, is_banned, is_trial, trial_expires_at
)
SELECT 
  open_user_project_id, project_id, open_user_id,
  CASE WHEN trial = 1 THEN 'TRIAL' ELSE 'LICENSE' END,
  key_time, last_time, use_num, allow_unlink = 1, band = 1,
  trial = 1, trial_end_time
FROM open_user_project;
```

### 5.2 数据验证

```sql
-- 验证数据迁移完整性
SELECT 
  '应用' as table_name,
  (SELECT COUNT(*) FROM easy_project WHERE deleted = 0) as old_count,
  (SELECT COUNT(*) FROM application WHERE deleted_at IS NULL) as new_count
UNION ALL
SELECT 
  '卡密',
  (SELECT COUNT(*) FROM easy_card WHERE deleted = 0),
  (SELECT COUNT(*) FROM license_key WHERE deleted_at IS NULL)
UNION ALL
SELECT 
  '终端用户',
  (SELECT COUNT(*) FROM open_user WHERE deleted = 0),
  (SELECT COUNT(*) FROM app_user WHERE deleted_at IS NULL)
UNION ALL
SELECT 
  '绑定关系',
  (SELECT COUNT(*) FROM open_user_project),
  (SELECT COUNT(*) FROM app_user_binding WHERE deleted_at IS NULL);
```

---

## 六、实施建议

### 6.1 分阶段实施

**阶段一：核心表创建（1-2天）**
- 创建 application, license_key, app_user, app_user_binding 四张核心表
- 编写对应的 Entity、Mapper、Service

**阶段二：数据迁移（1天）**
- 编写迁移脚本
- 在测试环境验证
- 数据完整性检查

**阶段三：业务逻辑重构（3-5天）**
- 重写应用管理接口
- 重写卡密管理接口
- 重写用户绑定接口

**阶段四：辅助表创建（1-2天）**
- 创建 license_batch, app_version, app_variable, app_login_log
- 完善相关功能

### 6.2 兼容性考虑

1. **保留旧表**：迁移完成后保留 EasyVerify 旧表一段时间，便于回滚
2. **双写策略**：过渡期可以考虑双写新旧表
3. **API 版本**：提供 v1 (旧) 和 v2 (新) 两套 API

### 6.3 性能优化

1. **索引优化**：所有外键、状态字段、时间字段都建立索引
2. **分区表**：日志表可以按月分区
3. **缓存策略**：应用配置、卡密信息使用 Redis 缓存

---

## 八、加密插件架构设计

### 8.1 设计理念

CipherGate 采用插件化的加密架构，系统只提供接口，具体加密逻辑通过加载 JAR 包实现。

**优势：**
1. ✅ 灵活扩展 - 用户可以自定义加密算法
2. ✅ 安全隔离 - 加密逻辑与业务逻辑分离
3. ✅ 热插拔 - 无需重启系统即可更换加密插件
4. ✅ 多租户 - 不同应用可以使用不同的加密插件

### 8.2 插件接口定义

```java
package com.ayssu.ciphergate.plugin;

/**
 * 加密插件接口
 */
public interface EncryptionPlugin {
    
    /**
     * 获取插件标识
     */
    String getPluginId();
    
    /**
     * 获取插件名称
     */
    String getPluginName();
    
    /**
     * 获取插件版本
     */
    String getVersion();
    
    /**
     * 初始化插件
     * @param config 加密配置（从 encryption_config JSON 读取）
     */
    void initialize(Map<String, Object> config);
    
    /**
     * 加密数据
     * @param plaintext 明文
     * @return 密文
     */
    String encrypt(String plaintext);
    
    /**
     * 解密数据
     * @param ciphertext 密文
     * @return 明文
     */
    String decrypt(String ciphertext);
    
    /**
     * 生成密钥对（如果需要）
     * @return 密钥对配置
     */
    Map<String, String> generateKeyPair();
    
    /**
     * 验证配置是否有效
     * @param config 配置
     * @return 是否有效
     */
    boolean validateConfig(Map<String, Object> config);
}
```

### 8.3 默认 RSA 插件

系统默认提供 RSA 加密插件：

```java
package com.ayssu.ciphergate.plugin.impl;

@Component
public class RsaDefaultPlugin implements EncryptionPlugin {
    
    private RSAPublicKey publicKey;
    private RSAPrivateKey privateKey;
    
    @Override
    public String getPluginId() {
        return "rsa-default";
    }
    
    @Override
    public String getPluginName() {
        return "RSA 默认加密";
    }
    
    @Override
    public String getVersion() {
        return "1.0.0";
    }
    
    @Override
    public void initialize(Map<String, Object> config) {
        String publicKeyStr = (String) config.get("rsaPublic");
        String privateKeyStr = (String) config.get("rsaPrivate");
        
        // 加载密钥
        this.publicKey = loadPublicKey(publicKeyStr);
        this.privateKey = loadPrivateKey(privateKeyStr);
    }
    
    @Override
    public String encrypt(String plaintext) {
        // RSA 加密实现
        // ...
    }
    
    @Override
    public String decrypt(String ciphertext) {
        // RSA 解密实现
        // ...
    }
    
    @Override
    public Map<String, String> generateKeyPair() {
        // 生成 RSA 密钥对
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        
        Map<String, String> result = new HashMap<>();
        result.put("rsaPublic", Base64.encode(keyPair.getPublic().getEncoded()));
        result.put("rsaPrivate", Base64.encode(keyPair.getPrivate().getEncoded()));
        return result;
    }
    
    @Override
    public boolean validateConfig(Map<String, Object> config) {
        return config.containsKey("rsaPublic") && config.containsKey("rsaPrivate");
    }
}
```

### 8.4 插件管理器

```java
package com.ayssu.ciphergate.service;

@Service
public class EncryptionPluginManager {
    
    private final Map<String, EncryptionPlugin> plugins = new ConcurrentHashMap<>();
    
    /**
     * 注册插件
     */
    public void registerPlugin(EncryptionPlugin plugin) {
        plugins.put(plugin.getPluginId(), plugin);
    }
    
    /**
     * 获取插件
     */
    public EncryptionPlugin getPlugin(String pluginId) {
        return plugins.get(pluginId);
    }
    
    /**
     * 从 JAR 加载插件
     */
    public void loadPluginFromJar(String jarPath) throws Exception {
        URLClassLoader classLoader = new URLClassLoader(
            new URL[]{new File(jarPath).toURI().toURL()},
            this.getClass().getClassLoader()
        );
        
        // 使用 ServiceLoader 加载插件
        ServiceLoader<EncryptionPlugin> loader = 
            ServiceLoader.load(EncryptionPlugin.class, classLoader);
        
        for (EncryptionPlugin plugin : loader) {
            registerPlugin(plugin);
            log.info("加载加密插件: {} ({})", plugin.getPluginName(), plugin.getPluginId());
        }
    }
    
    /**
     * 列出所有插件
     */
    public List<PluginInfo> listPlugins() {
        return plugins.values().stream()
            .map(p -> new PluginInfo(p.getPluginId(), p.getPluginName(), p.getVersion()))
            .collect(Collectors.toList());
    }
}
```

### 8.5 应用加密服务

```java
package com.ayssu.ciphergate.service;

@Service
public class ApplicationEncryptionService {
    
    @Autowired
    private EncryptionPluginManager pluginManager;
    
    @Autowired
    private ApplicationMapper applicationMapper;
    
    /**
     * 获取应用的加密插件实例
     */
    public EncryptionPlugin getApplicationPlugin(Long appId) {
        Application app = applicationMapper.selectById(appId);
        if (app == null) {
            throw new BusinessException("应用不存在");
        }
        
        // 获取插件
        EncryptionPlugin plugin = pluginManager.getPlugin(app.getEncryptionPlugin());
        if (plugin == null) {
            throw new BusinessException("加密插件不存在: " + app.getEncryptionPlugin());
        }
        
        // 初始化插件配置
        Map<String, Object> config = parseEncryptionConfig(app.getEncryptionConfig());
        plugin.initialize(config);
        
        return plugin;
    }
    
    /**
     * 加密数据
     */
    public String encrypt(Long appId, String plaintext) {
        EncryptionPlugin plugin = getApplicationPlugin(appId);
        return plugin.encrypt(plaintext);
    }
    
    /**
     * 解密数据
     */
    public String decrypt(Long appId, String ciphertext) {
        EncryptionPlugin plugin = getApplicationPlugin(appId);
        return plugin.decrypt(ciphertext);
    }
    
    /**
     * 为应用生成密钥
     */
    public Map<String, String> generateKeys(Long appId, String pluginId) {
        EncryptionPlugin plugin = pluginManager.getPlugin(pluginId);
        if (plugin == null) {
            throw new BusinessException("插件不存在");
        }
        
        return plugin.generateKeyPair();
    }
}
```

### 8.6 插件配置示例

#### 默认 RSA 插件配置
```json
{
  "rsaPublic": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...",
  "rsaPrivate": "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC..."
}
```

#### 自定义 AES 插件配置（示例）
```json
{
  "aesKey": "6d554f4f3846726b396d353168376473795432794a673d3d",
  "mode": "CBC",
  "padding": "PKCS5Padding",
  "iv": "1234567890123456"
}
```

#### 自定义 Base64 插件配置（示例）
```json
{
  "customTable": "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/",
  "padding": "="
}
```

### 8.7 插件开发指南

用户可以按照以下步骤开发自定义加密插件：

1. **创建 Maven 项目**
```xml
<dependency>
    <groupId>com.ayssu</groupId>
    <artifactId>ciphergate-plugin-api</artifactId>
    <version>1.0.0</version>
</dependency>
```

2. **实现 EncryptionPlugin 接口**
```java
public class MyCustomPlugin implements EncryptionPlugin {
    // 实现接口方法
}
```

3. **创建 SPI 配置文件**
```
META-INF/services/com.ayssu.ciphergate.plugin.EncryptionPlugin
```
文件内容：
```
com.example.MyCustomPlugin
```

4. **打包并部署**
```bash
mvn clean package
# 将 JAR 放到 CipherGate 的 plugins 目录
cp target/my-plugin.jar /path/to/ciphergate/plugins/
```

5. **重启或热加载**
```bash
# 调用管理接口热加载插件
POST /api/admin/plugins/load
{
  "jarPath": "/path/to/plugins/my-plugin.jar"
}
```

### 8.8 数据库字段说明

```sql
-- application 表
`encryption_plugin` VARCHAR(100) DEFAULT 'rsa-default' COMMENT '加密插件标识'
`encryption_config` JSON COMMENT '加密配置参数（插件自定义）'
```

**示例数据：**
```sql
INSERT INTO application (
  app_name, encryption_plugin, encryption_config
) VALUES (
  '我的应用',
  'rsa-default',
  '{"rsaPublic": "...", "rsaPrivate": "..."}'
);
```

### 8.9 迁移策略

从 EasyVerify 迁移时：
```sql
-- 所有应用统一使用 rsa-default 插件
UPDATE application 
SET encryption_plugin = 'rsa-default',
    encryption_config = JSON_OBJECT(
      'rsaPublic', project_rsa_public,
      'rsaPrivate', project_rsa_private
    )
WHERE project_encryption = 2;  -- 原来使用 RSA 的

-- 原来使用 Base64 的，也迁移到 RSA（或开发 Base64 插件）
UPDATE application 
SET encryption_plugin = 'rsa-default',
    encryption_config = JSON_OBJECT(
      'rsaPublic', NULL,
      'rsaPrivate', NULL
    )
WHERE project_encryption = 1;  -- 需要重新生成密钥
```

---

## 九、下一步行动

1. ✅ **审核方案** - 确认表结构设计是否满足需求
2. ⏳ **创建 Entity 类** - 根据表结构创建 Java 实体类
3. ⏳ **创建 Mapper 接口** - 编写 MyBatis-Plus Mapper
4. ⏳ **编写迁移脚本** - 完善数据迁移 SQL
5. ⏳ **API 设计** - 设计 RESTful API 接口
6. ⏳ **前端适配** - 修改前端调用新接口

---

**方案制定时间：** 2026-04-07  
**制定人：** Kiro AI Assistant  
**版本：** v1.1 (基于最新生产环境 SQL 更新)

---

## 附录：新发现的表和功能

### A.1 其他相关表（可选移植）

从最新 SQL 中发现以下表，可根据需求决定是否移植到 CipherGate：

#### 1. easy_ad (广告表)
```sql
CREATE TABLE `easy_ad` (
  `aid` int NOT NULL AUTO_INCREMENT COMMENT '广告ID',
  `uid` int NULL DEFAULT NULL COMMENT '创建者ID',
  `message` varchar(1024) COMMENT '广告内容',
  PRIMARY KEY (`aid`)
);
```
**建议：** 如果需要广告功能，可以移植并改名为 `advertisement`

#### 2. easy_link (API链接配置表)
```sql
CREATE TABLE `easy_link` (
  `aid` int NOT NULL AUTO_INCREMENT COMMENT 'API ID',
  `link` varchar(255) NOT NULL COMMENT '链接',
  `project_id` int NOT NULL COMMENT '程序ID',
  `type` int NOT NULL COMMENT '链接格式',
  `code` int NOT NULL DEFAULT 200 COMMENT '返回校验码',
  `code_type` int NOT NULL DEFAULT 1 COMMENT 'code返回格式',
  `safe_type` int NOT NULL DEFAULT 1 COMMENT '安全传输',
  `return_time` int NOT NULL DEFAULT 1 COMMENT '返回时间戳',
  PRIMARY KEY (`aid`, `link`)
);
```
**建议：** 这是 API 端点配置，可以考虑用更现代的方式实现（如配置文件或环境变量）

#### 3. easy_messages (消息表)
```sql
CREATE TABLE `easy_messages` (
  `mid` int NOT NULL AUTO_INCREMENT,
  `mtype` int NOT NULL COMMENT '消息类型',
  `title` varchar(255) COMMENT '标题',
  `content` text NOT NULL COMMENT '内容',
  `sender` int NOT NULL COMMENT '发送者',
  `receiver_uid` int COMMENT '接收者',
  `read_status` int DEFAULT 0 COMMENT '阅读状态',
  `timestamp` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`mid`)
);
```
**建议：** CipherGate 已有 `system_message` 和 `user_message`，可以整合

#### 4. easy_notice (公告表)
```sql
CREATE TABLE `easy_notice` (
  `nid` int NOT NULL AUTO_INCREMENT COMMENT '公告ID',
  `uid` int COMMENT '发布者ID',
  `notice_title` varchar(255) COMMENT '发布标题',
  `notice_message` varchar(2048) COMMENT '发布内容',
  `deleted` int DEFAULT 0,
  PRIMARY KEY (`nid`)
);
```
**建议：** 可以移植为 `announcement` 表

#### 5. project_file (项目文件表)
```sql
CREATE TABLE `project_file` (
  `file_id` varchar(255) NOT NULL COMMENT '文件ID',
  `uid` int COMMENT '用户ID',
  `pid` int COMMENT '项目ID',
  `title` varchar(255) COMMENT '标题',
  `description` varchar(255) COMMENT '描述',
  `download_num` int DEFAULT 0 COMMENT '下载次数',
  `file_name` varchar(255) COMMENT '文件名称',
  `md5` varchar(255) COMMENT '文件md5',
  `url` varchar(255) COMMENT '保存的文件地址',
  `file_size` bigint COMMENT '文件大小',
  `create_time` datetime,
  `update_time` datetime,
  `deleted` int DEFAULT 0,
  PRIMARY KEY (`file_id`)
);
```
**建议：** 如果需要应用文件管理功能（如客户端下载），可以移植为 `app_file`

#### 6. easy_encrypt (加密文件表)
```sql
CREATE TABLE `easy_encrypt` (
  `fid` int NOT NULL AUTO_INCREMENT COMMENT '文件ID',
  `uid` int COMMENT '用户ID',
  `file_name` varchar(255) COMMENT '文件名',
  `file_size` int COMMENT '文件大小',
  `file_type` varchar(30) COMMENT '文件类型',
  `md5` varchar(255) COMMENT '文件md5',
  `error_message` varchar(2048) COMMENT '错误信息',
  `create_time` datetime,
  `status` int DEFAULT 1 COMMENT '处理状态',
  `deleted` int DEFAULT 0,
  PRIMARY KEY (`fid`)
);
```
**建议：** 如果有文件加密需求，可以考虑移植

### A.2 重要发现和修正

#### 1. easy_user 表补充字段
从最新 SQL 发现 `easy_user` 表有 `limit_card` 字段：
```sql
`limit_card` int NOT NULL DEFAULT 2000 COMMENT '卡密额度'
```
**建议：** 在 CipherGate 的 `user` 表中增加配额管理字段

#### 2. 数据规模参考
从 AUTO_INCREMENT 值可以看出生产环境数据规模：
- easy_card: 97,614 条（接近 10 万）
- easy_login_log: 4,167,567 条（超过 400 万）
- open_user: 1,493 条
- open_user_project: 610 条
- easy_project: 10,210 条（超过 1 万）

**性能优化建议：**
- 日志表必须分区（按月或按季度）
- 卡密表需要优化索引（card_key, app_id, status 组合索引）
- 考虑使用 BIGINT 作为主键（当前 INT 最大 21 亿）

#### 3. 第三方 API 功能
发现了完整的第三方 API 凭证和充值日志表，这是一个重要的商业功能：
- `third_party_api_credential` - API 凭证管理
- `third_party_recharge_log` - 充值记录审计

**建议：** 在 CipherGate 中保留并增强这个功能，支持：
- API 密钥管理
- IP 白名单
- 调用频率限制
- 完整的审计日志

---

## 附录 B：字段值说明

### B.1 卡密类型 (card_type)
```
1 = 天卡
2 = 周卡
3 = 月卡
4 = 半年卡
5 = 年卡
6 = 永久卡
7 = 自定义时间卡
8 = 季卡
```

### B.2 设备验证 (imei_check)
```
1 = 开启设备码验证
2 = 关闭设备码验证
```

### B.3 IP验证 (ip_check)
```
1 = 开启IP验证
2 = 关闭IP验证
```

### B.4 应用模式 (project_model)
```
1 = 收费模式
2 = 免费模式
3 = 加时模式
```

### B.5 加密方式 (project_encryption)
```
1 = base64自定义编码
2 = rsa非对称加密
```

---

**最后更新时间：** 2026-04-07 16:30  
**数据来源：** EasyVerify 生产环境数据库导出
