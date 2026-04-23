## 本次更新说明

{{CHANGELOG}}

## 构建产物

- `deploy-bundle.zip`：全量部署包（首次部署使用）
- `app.jar`：仅后端更新包
- `frontend-dist.zip`：仅前端更新包

## 使用说明

### 首次部署（全量）

1. 下载并解压 `deploy-bundle.zip`
2. 在解压目录执行：`docker compose -f docker-compose.server.yml up -d`

### 仅更新前端

1. 下载 `frontend-dist.zip`
2. 覆盖服务器 `frontend/dist` 目录
3. 执行：`docker compose -f docker-compose.server.yml up -d frontend`

### 仅更新后端

1. 下载 `app.jar`
2. 覆盖服务器 `app/app.jar`
3. 执行：`docker compose -f docker-compose.server.yml up -d backend`

## 更新策略

- 首次部署：下载全量包 `deploy-bundle.zip`
- 日常更新：按需下载前端或后端对应产物，无需每次下载全量包
