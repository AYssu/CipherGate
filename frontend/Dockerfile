# 构建阶段 - 仅用于本地构建，生产环境不使用
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# 生产运行阶段 - 使用 Nginx 服务编译后的静态文件
FROM nginx:alpine
WORKDIR /usr/share/nginx/html

# 删除默认的 nginx 静态文件
RUN rm -rf ./*

# 复制编译好的前端文件
COPY --from=builder /app/dist .

# 复制 nginx 配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:80/health || exit 1

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
