# 数据库初始化与升级

CipherGate 使用 Spring Boot 集成的 Flyway 管理数据库版本，不需要额外安装 Flyway 命令行。

## 执行规则

- 空数据库：启动时执行 `src/main/resources/db/migration/V1__initial_schema.sql`，建立表、索引和初始菜单/权限。
- 已接入的数据库：检查 `flyway_schema_history`，按版本只执行未应用的迁移。
- 普通重启：校验迁移历史，不重放建表或菜单、权限脚本。
- 迁移失败或已执行脚本校验不一致：阻止后端启动，日志报告失败版本和原因。

原有脚本保存在 `docs/database/legacy/`，仅作历史追溯和旧库差异核对，不会打入 JAR 或自动执行。
原来的两个补字段/索引初始化器和 Spring Session 自动建表也已统一到 V1。
业务默认配置保留“缺失才补”的独立初始化器，不承担数据库结构升级。

## 首次安装

配置空数据库后，按原流程启动即可，无需导入 SQL，也无需设置旧库接入开关。
Flyway 使用应用的 MySQL 账号，需要建表、建索引以及后续迁移涉及的 DDL 权限。

## 旧数据库首次接入

只针对尚无 `flyway_schema_history` 的既有 CipherGate 数据库执行一次。

1. 停止旧版后端，保留数据库和数据卷。备份完整数据库，并确认备份可以恢复。
2. 建议先用备份副本试运行新版本，核对用户、应用、凭证、菜单及授权。
3. 在部署目录 `.env` 添加 `CIPHERGATE_ADOPT_LEGACY_DATABASE=true`。
4. 启动新版后端：

   ```bash
   docker compose -f docker-compose.server.yml up -d backend
   docker compose -f docker-compose.server.yml logs --tail=200 backend
   ```

5. 检查通过后会登记版本 `1`、类型 `BASELINE`，跳过首次安装脚本，并执行未来新增的迁移。
6. 验证功能正常后，删除该环境变量或改为 `false`，重新创建后端容器。

Compose 文件已透传该开关。源码直接运行时使用：

```bash
CIPHERGATE_ADOPT_LEGACY_DATABASE=true ./gradlew bootRun
```

开关不会跳过结构核验，也不会重新写入旧库的菜单、角色授权、用户、凭证或初始化状态。
默认不开启旧库接入；误连到已有数据库时会停止并给出提示。
即使忘记关闭开关，已有迁移历史的数据库也不会再次建立基线。

### 结构不符合时

核验以冻结的 `src/main/resources/db/baseline-v1.tsv` 为接入契约，检查 V1 所需的字段类型、可空性、
索引唯一性和列顺序，以及外键引用和规则；允许保留额外字段和索引。
MySQL 整数显示宽度差异会被规范化。其他差异采用保守策略，例如更长的 VARCHAR 也需人工核对。
核验还要求存在有效的 `SYSTEM_INITIALIZED` 标记。

仅有一个已核验的历史索引例外：`plugin_module.uk_plugin_version` 可以是唯一索引
`(plugin_id, plugin_version, deleted, deleted_at)`，而 V1 定义为
`(plugin_id, plugin_version, deleted)`。接入时保留原索引和全部业务行，不修改 V1 或冻结契约。
这个例外必须同时匹配表名、索引名、唯一性、完整列清单及顺序；不接受其他索引的追加列或类似前缀。
由于 `deleted_at` 可空，这两种唯一性约束并不等价：历史变体允许保留更多删除记录，
但数据库本身不能阻止 `deleted_at=NULL` 的重复未删除版本。核验发现同一 `plugin_id/plugin_version`
存在多个 `deleted=0` 行时会拒绝接入，不会自动删行、合并或改索引。
当前插件服务按插件标识与版本查找并更新已有行，删除时写入删除时间；接入保留这一既有行为，
不新增并发上传唯一性保证。如需统一约束，应另行评估并新增正式迁移，不能通过修改 V1 处理。

检查失败会列出具体表、字段或索引，不会建立版本记录，也不会自动修改业务表。
先在备份副本中对比 V1 和归档历史脚本，确认差异来自漏升级还是有意定制，再安排修复。
不要直接执行整份 V1、批量执行所有归档 SQL、删除业务表或修改契约来消除错误。

这项检查不是数据审计：不验证业务行内容、默认值、字符集、触发器、存储过程或自定义逻辑的兼容性。
正式接入前仍需核对关键业务功能。不要对未经核验的库手工插入历史记录或设置自动 baseline。

## 后续开发规范

只有数据库结构或必要基础数据发生变化时才新增迁移。普通代码更新无需编写 SQL。

```text
src/main/resources/db/migration/
  V1__initial_schema.sql
  V2__add_application_feature.sql
  V3__add_user_lookup_index.sql
```

编号递增，版本与名称之间是两个下划线。脚本自动发现，不再修改 Java 文件中的列表。
已经应用到任何共享环境的迁移保持不变，用新版本修正，避免校验和不一致。
不使用 repeatable migration 重复更新用户可编辑的菜单、配置或授权。

每次变更至少验证：空库从头安装、上一版本数据升级、升级后再次启动、关键数据保留。
大表索引应评估影响，必要时显式使用 `ALGORITHM=INPLACE LOCK=NONE` 并安排维护窗口；
不再在每次启动时静默尝试补索引，也不在在线 DDL 失败后自动回退为锁表操作。

## 失败与回退

MySQL DDL 可能隐式提交；Flyway 失败不代表已执行的建表、加列自动回滚。
停止继续发布，检查失败版本、历史表和实际结构，优先在副本复现并修复。
不要自动调用 `repair`，也不要只删除失败记录重试；先处理已经执行的部分并确认可安全重试。
数据库 `clean` 默认禁用。

仅建立 V1 基线不会改变旧库业务数据。若尚未执行后续结构变更，保留历史表并换回旧版应用即可；
但旧版仍会按旧方式重放 SQL，应限制为应急回退。已应用后续迁移时，须核对旧版兼容性，
必要时恢复经过验证的备份，不能只换 JAR 并假设结构已回退。

## 验证命令

迁移测试需要专用的临时 MySQL 数据库，**会清空该测试库内的表**。
测试代码只允许数据库名为 `ciphergate_migration_test` 的 URL。

先按根目录 `CONTRIBUTING.md` 配置四组隔离测试库的 `CIPHERGATE_*_JDBC_URL/PASSWORD`，再运行：

```bash
./gradlew integrationTest --tests '*DatabaseMigrationMysqlTest' --no-daemon
```

`test` 和 `unitTest` 不包含真实数据库测试；`integrationTest` 缺少必需环境变量会直接失败，
不能把跳过视为迁移验证成功。迁移测试不需要 Redis；运行全部集成测试还需配置专用 Redis。
