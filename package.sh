#!/bin/bash

set -e

echo "======================================"
echo "CipherGate 部署包打包脚本"
echo "======================================"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

VERSION=${1:-latest}
PACKAGE_NAME="ciphergate-deploy-${VERSION}"

echo -e "${YELLOW}开始打包部署文件...${NC}"
echo ""

# 创建打包目录
rm -rf ${PACKAGE_NAME}
mkdir -p ${PACKAGE_NAME}

# 导出 Docker 镜像
echo -e "${YELLOW}导出 Docker 镜像...${NC}"
docker save ciphergate-backend:latest -o ${PACKAGE_NAME}/ciphergate-backend.tar
docker save ciphergate-frontend:latest -o ${PACKAGE_NAME}/ciphergate-frontend.tar
echo -e "${GREEN}✓ 镜像导出完成${NC}"
echo ""

# 复制部署文件
echo -e "${YELLOW}复制部署文件...${NC}"
cp docker-compose.prod.yaml ${PACKAGE_NAME}/
cp .env.prod ${PACKAGE_NAME}/.env
cp README.Docker.md ${PACKAGE_NAME}/README.md

# 创建部署脚本
cat > ${PACKAGE_NAME}/deploy.sh << 'EOF'
#!/bin/bash
set -e

echo "======================================"
echo "CipherGate 部署脚本"
echo "======================================"

# 加载镜像
echo "加载 Docker 镜像..."
docker load -i ciphergate-backend.tar
docker load -i ciphergate-frontend.tar
echo "✓ 镜像加载完成"
echo ""

# 启动服务
echo "启动服务..."
docker-compose -f docker-compose.prod.yaml up -d
echo "✓ 服务启动完成"
echo ""

# 读取端口
FRONTEND_PORT=$(grep FRONTEND_PORT .env 2>/dev/null | cut -d '=' -f2 || echo "1123")
BACKEND_PORT=$(grep BACKEND_PORT .env 2>/dev/null | cut -d '=' -f2 || echo "7754")

echo "======================================"
echo "部署完成！"
echo "======================================"
echo ""
echo "访问地址: http://localhost:${FRONTEND_PORT}"
echo "后端 API: http://localhost:${BACKEND_PORT}"
echo ""
echo "查看日志: docker-compose -f docker-compose.prod.yaml logs -f"
echo "停止服务: docker-compose -f docker-compose.prod.yaml down"
echo ""
EOF

chmod +x ${PACKAGE_NAME}/deploy.sh

echo -e "${GREEN}✓ 部署文件复制完成${NC}"
echo ""

# 打包成压缩文件
echo -e "${YELLOW}压缩打包...${NC}"
tar -czf ${PACKAGE_NAME}.tar.gz ${PACKAGE_NAME}
echo -e "${GREEN}✓ 打包完成: ${PACKAGE_NAME}.tar.gz${NC}"
echo ""

# 显示文件大小
SIZE=$(du -h ${PACKAGE_NAME}.tar.gz | cut -f1)
echo "======================================"
echo -e "${GREEN}打包完成！${NC}"
echo "======================================"
echo ""
echo "部署包: ${PACKAGE_NAME}.tar.gz (${SIZE})"
echo ""
echo "包含文件："
echo "  - ciphergate-backend.tar (后端镜像)"
echo "  - ciphergate-frontend.tar (前端镜像)"
echo "  - docker-compose.prod.yaml (编排文件)"
echo "  - .env (生产环境配置)"
echo "  - deploy.sh (一键部署脚本)"
echo "  - README.md (使用说明)"
echo ""
echo "客户使用方法："
echo "  1. 解压: tar -xzf ${PACKAGE_NAME}.tar.gz"
echo "  2. 进入目录: cd ${PACKAGE_NAME}"
echo "  3. 修改配置: vi .env (可选)"
echo "  4. 部署: ./deploy.sh"
echo ""

# 清理临时目录（可选）
read -p "是否删除临时目录 ${PACKAGE_NAME}? (y/n) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    rm -rf ${PACKAGE_NAME}
    echo -e "${GREEN}✓ 临时目录已清理${NC}"
fi
