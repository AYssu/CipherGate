# CipherGate Docker 部署指南

## 快速开始

### 前置要求

- Docker 20.10+
- Docker Compose 2.0+
- 至少 2GB 可用内存

### 一键部署（推荐）

```bash
# 1. 构建项目
./build.sh          # Linux/Mac
build.bat           # Windows

# 2. 部署服务
./deploy.sh         # Linux/Mac
```

Windows 用户手动部署：
```cmd
# 1. 构建
build.bat

# 2. 配置环境变量
copy .env.example .env

# 3. 启动服务
docker-compose -f docker-compose.prod.yaml up -d
```

### 访问应用

- 前端: http://localhost:80
- 后端 API: http://localhost:80/api
- MySQL: localhost:13306

## 详细说明

### 项目结构

```
.
├── Dockerfile                      # 后端 Dockerfile
├── frontend/
│   ├── Dockerfile                  # 前端 Dockerfile
│   └── nginx.conf                  # Nginx 配置
├── docker-compose.prod.yaml        # 生产环境编排
├── .env.example                    # 环境变量模板
├── build.sh / build.bat            # 构建脚本
└── deploy.sh                       # 部署脚本
```

### 构建流程

1. **后端构建**
   - 使用 Gradle 编译 Spring Boot 项目
   - 生成 JAR 文件到 `build/libs/`
   - 打包到 Docker 镜像 `ciphergate-backend:latest`

2. **前端构建**
   - 使用 npm 构建 React 项目
   - 生成静态文件到 `frontend/dist/`
   - 打包到 Nginx Docker 镜像 `ciphergate-frontend:latest`

### 环境变量配置

复制 `.env.example` 到 `.env` 并根据需要修改：

```bash
# MySQL 配置
MYSQL_ROOT_PASSWORD=your_root_password
MYSQL_DATABASE=ciphergate
MYSQL_USER=your_user
MYSQL_PASSWORD=your_password
MYSQL_PORT=13306

# Spring Boot 配置
SPRING_PROFILES_ACTIVE=prod
JAVA_OPTS=-Xms512m -Xmx1g

# 前端端口
FRONTEND_PORT=80
```

### 常用命令

```bash
# 查看服务状态
docker-compose -f docker-compose.prod.yaml ps

# 查看日志
docker-compose -f docker-compose.prod.yaml logs -f

# 查看特定服务日志
docker-compose -f docker-compose.prod.yaml logs -f backend
docker-compose -f docker-compose.prod.yaml logs -f frontend

# 重启服务
docker-compose -f docker-compose.prod.yaml restart

# 停止服务
docker-compose -f docker-compose.prod.yaml down

# 停止并删除数据卷
docker-compose -f docker-compose.prod.yaml down -v

# 进入容器
docker exec -it ciphergate-backend sh
docker exec -it ciphergate-mysql bash
```

### 数据持久化

MySQL 数据存储在 Docker volume `mysql-data` 中，即使删除容器数据也不会丢失。

如需完全清理：
```bash
docker-compose -f docker-compose.prod.yaml down -v
```

### 健康检查

所有服务都配置了健康检查：

- **MySQL**: 每 10 秒检查一次
- **Backend**: 每 30 秒检查一次（通过 `/actuator/health`）
- **Frontend**: 每 30 秒检查一次（通过 `/health`）

查看健康状态：
```bash
docker-compose -f docker-compose.prod.yaml ps
```

### 生产环境优化

1. **安全性**
   - 修改默认密码
   - 使用 HTTPS（配置 SSL 证书）
   - 限制数据库端口访问

2. **性能优化**
   - 调整 `JAVA_OPTS` 内存配置
   - 配置 MySQL 参数
   - 启用 Nginx 缓存

3. **监控**
   - 使用 Spring Boot Actuator 监控后端
   - 配置日志收集
   - 设置告警

### 故障排查

#### 服务无法启动

```bash
# 查看详细日志
docker-compose -f docker-compose.prod.yaml logs

# 检查端口占用
netstat -tulpn | grep :80
netstat -tulpn | grep :13306
```

#### 数据库连接失败

1. 检查 MySQL 是否健康：
   ```bash
   docker-compose -f docker-compose.prod.yaml ps mysql
   ```

2. 检查网络连接：
   ```bash
   docker exec -it ciphergate-backend ping mysql
   ```

3. 验证数据库凭据是否正确

#### 前端无法访问后端

1. 检查 Nginx 配置
2. 验证后端服务是否运行
3. 检查网络配置

### 更新部署

```bash
# 1. 重新构建镜像
./build.sh

# 2. 重启服务
docker-compose -f docker-compose.prod.yaml up -d --force-recreate
```

### 备份与恢复

#### 备份数据库

```bash
docker exec ciphergate-mysql mysqldump -u root -p ciphergate > backup.sql
```

#### 恢复数据库

```bash
docker exec -i ciphergate-mysql mysql -u root -p ciphergate < backup.sql
```

## 高级配置

### 使用外部 MySQL

修改 `docker-compose.prod.yaml`，移除 MySQL 服务，并更新后端环境变量：

```yaml
backend:
  environment:
    SPRING_DATASOURCE_URL: jdbc:mysql://your-mysql-host:3306/ciphergate
    SPRING_DATASOURCE_USERNAME: your_user
    SPRING_DATASOURCE_PASSWORD: your_password
```

### 配置 HTTPS

1. 准备 SSL 证书
2. 修改 `frontend/nginx.conf` 添加 SSL 配置
3. 更新 `docker-compose.prod.yaml` 暴露 443 端口

### 集群部署

使用 Docker Swarm 或 Kubernetes 进行多节点部署。

## 技术栈

- **前端**: React + Vite + Ant Design + Nginx
- **后端**: Spring Boot + MyBatis Plus
- **数据库**: MySQL 8.0
- **容器**: Docker + Docker Compose

## 支持

如有问题，请查看日志或提交 Issue。
