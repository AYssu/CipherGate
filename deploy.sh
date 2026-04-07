#!/bin/bash

set -e

echo "======================================"
echo "CipherGate 一键部署脚本"
echo "======================================"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# 检查并使用生产环境配置
check_env() {
    if [ -f ".env.prod" ]; then
        echo -e "${GREEN}使用生产环境配置 .env.prod${NC}"
        cp .env.prod .env
    elif [ ! -f ".env" ]; then
        echo -e "${YELLOW}未找到配置文件，从模板创建...${NC}"
        cp .env.example .env
        echo -e "${GREEN}✓ .env 文件已创建，请根据需要修改配置${NC}"
    else
        echo -e "${GREEN}使用现有 .env 配置${NC}"
    fi
}

# 停止旧服务
stop_old_services() {
    echo -e "${YELLOW}停止旧服务...${NC}"
    docker-compose -f docker-compose.prod.yaml down 2>/dev/null || true
    echo -e "${GREEN}✓ 旧服务已停止${NC}"
}

# 启动服务
start_services() {
    echo -e "${YELLOW}启动服务...${NC}"
    docker-compose -f docker-compose.prod.yaml up -d
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ 服务启动成功${NC}"
    else
        echo -e "${RED}错误: 服务启动失败${NC}"
        exit 1
    fi
}

# 等待服务健康
wait_for_health() {
    echo -e "${YELLOW}等待服务启动...${NC}"
    
    max_attempts=30
    attempt=0
    
    while [ $attempt -lt $max_attempts ]; do
        if docker-compose -f docker-compose.prod.yaml ps | grep -q "healthy"; then
            echo -e "${GREEN}✓ 服务健康检查通过${NC}"
            return 0
        fi
        
        attempt=$((attempt + 1))
        echo -n "."
        sleep 2
    done
    
    echo ""
    echo -e "${YELLOW}警告: 健康检查超时，请手动检查服务状态${NC}"
}

# 显示服务状态
show_status() {
    echo ""
    echo "======================================"
    echo "服务状态"
    echo "======================================"
    docker-compose -f docker-compose.prod.yaml ps
    echo ""
}

# 显示访问信息
show_info() {
    FRONTEND_PORT=$(grep FRONTEND_PORT .env 2>/dev/null | cut -d '=' -f2 || echo "1123")
    BACKEND_PORT=$(grep BACKEND_PORT .env 2>/dev/null | cut -d '=' -f2 || echo "7754")
    
    echo "======================================"
    echo -e "${GREEN}部署完成！${NC}"
    echo "======================================"
    echo ""
    echo "访问地址: http://localhost:${FRONTEND_PORT}"
    echo "后端 API: http://localhost:${BACKEND_PORT}"
    echo ""
    echo "常用命令："
    echo "  查看日志: docker-compose -f docker-compose.prod.yaml logs -f"
    echo "  查看状态: docker-compose -f docker-compose.prod.yaml ps"
    echo "  停止服务: docker-compose -f docker-compose.prod.yaml down"
    echo "  重启服务: docker-compose -f docker-compose.prod.yaml restart"
    echo ""
}

# 主函数
main() {
    check_env
    echo ""
    
    stop_old_services
    echo ""
    
    start_services
    echo ""
    
    wait_for_health
    
    show_status
    show_info
}

main "$@"
