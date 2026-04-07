@echo off
setlocal enabledelayedexpansion

echo ======================================
echo CipherGate 一键部署脚本 (Windows)
echo ======================================
echo.

:: 检查并使用生产环境配置
if exist ".env.prod" (
    echo [√] 使用生产环境配置 .env.prod
    copy /Y .env.prod .env >nul
) else if not exist ".env" (
    echo [!] 未找到配置文件，从模板创建...
    copy .env.example .env >nul
    echo [√] .env 文件已创建，请根据需要修改配置
) else (
    echo [√] 使用现有 .env 配置
)
echo.

:: 停止旧服务
echo [!] 停止旧服务...
docker-compose -f docker-compose.prod.yaml down 2>nul
echo [√] 旧服务已停止
echo.

:: 启动服务
echo [!] 启动服务...
docker-compose -f docker-compose.prod.yaml up -d
if %errorlevel% neq 0 (
    echo [错误] 服务启动失败
    exit /b 1
)
echo [√] 服务启动成功
echo.

:: 等待服务启动
echo [!] 等待服务启动...
timeout /t 10 /nobreak >nul
echo.

:: 显示服务状态
echo ======================================
echo 服务状态
echo ======================================
docker-compose -f docker-compose.prod.yaml ps
echo.

:: 读取端口配置
set FRONTEND_PORT=1123
set BACKEND_PORT=7754
if exist ".env" (
    for /f "tokens=1,2 delims==" %%a in ('findstr "FRONTEND_PORT" .env') do set FRONTEND_PORT=%%b
    for /f "tokens=1,2 delims==" %%a in ('findstr "BACKEND_PORT" .env') do set BACKEND_PORT=%%b
)

:: 显示访问信息
echo ======================================
echo 部署完成！
echo ======================================
echo.
echo 访问地址: http://localhost:%FRONTEND_PORT%
echo 后端 API: http://localhost:%BACKEND_PORT%
echo.
echo 常用命令：
echo   查看日志: docker-compose -f docker-compose.prod.yaml logs -f
echo   查看状态: docker-compose -f docker-compose.prod.yaml ps
echo   停止服务: docker-compose -f docker-compose.prod.yaml down
echo   重启服务: docker-compose -f docker-compose.prod.yaml restart
echo.

endlocal
