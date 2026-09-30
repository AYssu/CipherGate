# 变量管理

应用变量用于向客户端下发配置。变量按应用隔离，可设置类型、默认值、校验规则、标签、版本和安全等级。

## 变量类型

- `STRING`：字符串。
- `NUMBER`：数值。
- `BOOLEAN`：布尔值。
- `JSON`：JSON 对象。
- `ARRAY`：数组。

只有启用且未删除的变量会通过卡密登录、变量查询或 WebSocket 心跳下发。

## 安全等级

WebSocket 变量包会按等级分桶：

| 等级 | 值 | 客户端建议 |
| --- | --- | --- |
| STANDARD | 0 | 可在普通客户端环境使用 |
| SENSITIVE | 1 | 限制明文暴露和日志记录 |
| CRITICAL | 2 | 仅在 TEE/安全区域解析和使用 |

新建变量默认按 `CRITICAL` 处理；显式设为 `STANDARD` 才进入标准桶。

## 配置能力

- 单个创建、修改、删除和批量删除。
- 复制变量并修改名称。
- 校验变量值。
- 查看创建、修改、删除历史及变更原因。
- 按应用导入或导出变量配置。

## 下发方式

- 卡密登录和换绑响应返回 `variables`。
- `/api/v1/app/variables` 可直接查询已启用变量。
- WebSocket `HEARTBEAT` 按 `STANDARD`、`SENSITIVE`、`CRITICAL` 分桶下发。

接口和加密细节见 [API 对接](/developer/api-integration)。
