# 依赖安全门禁

后端使用 Spring Boot BOM 管理依赖族。安全补丁临时覆盖必须同时更新整个兼容版本族、严格 SHA256 校验清单，并检查 `runtimeClasspath` 和实际发布 JAR；声明版本不等于最终解析版本。

## 本地检查

```bash
./gradlew runtimeDependencyInventory --no-daemon
python3 .github/scripts/audit_java_dependencies.py build/reports/security/runtime-dependencies.json
```

清单包含实际解析的 Maven 运行时依赖。OSV 按准确的生态、包名和版本查询，任何已知告警都会阻断；接口不可用、空依赖图或不完整响应也会失败，不以“扫描成功”代替“没有执行扫描”。报告位于 `build/reports/security/`。该查询会向公开漏洞服务发送依赖坐标，不发送源码或配置。

PR、主分支及发布必需检查调用同一门禁，每周一另外复扫主分支，以发现代码未变化时新增的安全通告。扫描失败应检查报告、官方通告和实际调用条件；本项目当前没有永久忽略或默认豁免。若确需例外，应另行评审包含责任人、证据、到期日和回归测试的明确实现，不能通过忽略返回码绕过。

## 本轮修复范围

2026-10-25 根据已解析版本告警和 Maven Central 发布元数据，将 Tomcat 更新为 11.0.27、Jackson 3 保持 3.1 系列更新到 3.1.8、Jackson 2 保持 2.21 系列更新到 2.21.8，RabbitMQ Java Client 更新为 5.37.0。部分告警写出的 RabbitMQ 5.36.1 没有发布到 Maven Central，因此使用已经发布的后续兼容 5.x 版本。完整基线扫描另发现 Bouncy Castle 1.84 的告警，更新到 1.86。

依赖元数据来源：Maven Central 对应组件的 `maven-metadata.xml`；漏洞匹配来源：OSV API。版本命中并不表示已证明当前配置可以利用；扫描通过也不能证明未知漏洞不存在。补丁仍需完整单元、集成、协议、插件及发布安装恢复检查。
