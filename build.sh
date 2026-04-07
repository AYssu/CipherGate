#!/bin/bash

set -e

echo "======================================"
echo "CipherGate 项目构建脚本"
echo "======================================"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查必要的工具
check_requirements() {
    echo -e "${YELLOW}检查构建环境...${NC}"
    
    if ! command -v docker &> /dev/null; then
        echo -e "${RED}错误: Docker 未安装${NC}"
        exit 1
    fi
    
    if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
        echo -e "${RED}错误: Docker Compose 未安装${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}✓ 环境检查通过${NC}"
}

# 构建后端
build_backend() {
    echo -e "${YELLOW}开始构建后端...${NC}"
    
    # 使用 Gradle 构建 JAR
    if [ -f "./gradlew" ]; then
        chmod +x ./gradlew
        ./gradlew clean bootJar --no-daemon
    else
        gradle clean bootJar --no-daemon
    fi
    
    # 检查 JAR 文件是否生成
    if [ ! -f build/libs/*.jar ]; then
        echo -e "${RED}错误: JAR 文件构建失败${NC}"
        exit 1
    fi
    
    # 构建 Docker 镜像
    docker build -f Dockerfile.prod -t ciphergate-backend:latest .
    
    echo -e "${GREEN}✓ 后端构建完成${NC}"
}

# 构建前端
build_frontend() {
    echo -e "${YELLOW}开始构建前端...${NC}"
    
    cd frontend
    
    # 安装依赖并构建
    if [ ! -d "node_modules" ]; then
        npm ci
    fi
    npm run build
    
    # 检查构建产物
    if [ ! -d "dist" ]; then
        echo -e "${RED}错误: 前端构建失败${NC}"
        exit 1
    fi
    
    # 构建 Docker 镜像
    docker build -f Dockerfile.prod -t ciphergate-frontend:latest .
    
    cd ..
    
    echo -e "${GREEN}✓ 前端构建完成${NC}"
}

# 主函数
main() {
    check_requirements
    
    echo ""
    echo "======================================"
    echo "开始构建项目"
    echo "======================================"
    echo ""
    
    # 构建后端
    build_backend
    echo ""
    
    # 构建前端
    build_frontend
    echo ""
    
    echo "======================================"
    echo -e "${GREEN}构建完成！${NC}"
    echo "======================================"
    echo ""
    echo "下一步操作："
    echo "1. 本地开发: cp .env.example .env 并修改配置"
    echo "2. 生产部署: 使用 .env.prod（已包含在部署包中）"
    echo "3. 启动服务: docker-compose -f docker-compose.prod.yaml up -d"
    echo ""
    echo "查看日志: docker-compose -f docker-compose.prod.yaml logs -f"
    echo "停止服务: docker-compose -f docker-compose.prod.yaml down"
    echo ""
}

main "$@"
