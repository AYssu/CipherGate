@echo off
setlocal enabledelayedexpansion

echo ======================================
echo CipherGate 一键打包启动脚本 (Windows)
echo ======================================
echo.

:: 检查 Docker
where docker >nul 2>nul
if %errorlevel% neq 0 (
    echo [错误] Docker 未安装或未添加到 PATH
    exit /b 1
)

:: 检查 Docker Compose
docker compose version >nul 2>nul
if %errorlevel% neq 0 (
    docker-compose --version >nul 2>nul
    if %errorlevel% neq 0 (
        echo [错误] Docker Compose 未安装
        exit /b 1
    )
)

echo [√] 环境检查通过
echo.

:: 检查 .env
if not exist ".env" (
    if exist ".env.example" (
        copy ".env.example" ".env" >nul
        echo [提示] 未检测到 .env，已由 .env.example 自动生成
    ) else (
        echo [错误] 缺少 .env 和 .env.example
        exit /b 1
    )
)
echo.

echo ======================================
echo 开始构建并启动 Docker Compose...
echo ======================================
echo.

docker compose up -d --build
if %errorlevel% neq 0 (
    echo [错误] Docker Compose 构建/启动失败
    exit /b 1
)

echo [√] 构建并启动完成
echo.
echo 访问地址：
echo - 前端: http://localhost:5173
echo - 后端: http://localhost:8080
echo.
echo 常用命令：
echo - 查看日志: docker compose logs -f
echo - 停止服务: docker compose down
echo - 仅重建镜像: docker compose build --no-cache
echo.

endlocal
