@echo off
setlocal enabledelayedexpansion

echo ======================================
echo CipherGate 部署包打包脚本 (Windows)
echo ======================================
echo.

set VERSION=%1
if "%VERSION%"=="" set VERSION=latest
set PACKAGE_NAME=ciphergate-deploy-%VERSION%

echo [!] 开始打包部署文件...
echo.

:: 创建打包目录
if exist "%PACKAGE_NAME%" rmdir /s /q "%PACKAGE_NAME%"
mkdir "%PACKAGE_NAME%"

:: 导出 Docker 镜像
echo [!] 导出 Docker 镜像...
docker save ciphergate-backend:latest -o %PACKAGE_NAME%\ciphergate-backend.tar
if %errorlevel% neq 0 (
    echo [错误] 后端镜像导出失败
    exit /b 1
)

docker save ciphergate-frontend:latest -o %PACKAGE_NAME%\ciphergate-frontend.tar
if %errorlevel% neq 0 (
    echo [错误] 前端镜像导出失败
    exit /b 1
)
echo [√] 镜像导出完成
echo.

:: 复制部署文件
echo [!] 复制部署文件...
copy docker-compose.prod.yaml %PACKAGE_NAME%\ >nul
copy .env.prod %PACKAGE_NAME%\.env >nul
copy README.Docker.md %PACKAGE_NAME%\README.md >nul

:: 创建部署脚本
(
echo @echo off
echo echo ======================================
echo echo CipherGate 部署脚本
echo echo ======================================
echo echo.
echo.
echo echo [!] 加载 Docker 镜像...
echo docker load -i ciphergate-backend.tar
echo docker load -i ciphergate-frontend.tar
echo echo [√] 镜像加载完成
echo echo.
echo.
echo echo [!] 启动服务...
echo docker-compose -f docker-compose.prod.yaml up -d
echo echo [√] 服务启动完成
echo echo.
echo.
echo echo ======================================
echo echo 部署完成！
echo echo ======================================
echo echo.
echo echo 访问地址: http://localhost:1123
echo echo 后端 API: http://localhost:7754
echo echo.
echo echo 查看日志: docker-compose -f docker-compose.prod.yaml logs -f
echo echo 停止服务: docker-compose -f docker-compose.prod.yaml down
echo echo.
) > %PACKAGE_NAME%\deploy.bat

echo [√] 部署文件复制完成
echo.

:: 压缩打包（需要 tar 命令，Windows 10+ 自带）
echo [!] 压缩打包...
tar -czf %PACKAGE_NAME%.tar.gz %PACKAGE_NAME%
if %errorlevel% neq 0 (
    echo [警告] tar 压缩失败，尝试使用 zip...
    powershell Compress-Archive -Path %PACKAGE_NAME% -DestinationPath %PACKAGE_NAME%.zip -Force
    if %errorlevel% neq 0 (
        echo [错误] 压缩失败
        exit /b 1
    )
    echo [√] 打包完成: %PACKAGE_NAME%.zip
) else (
    echo [√] 打包完成: %PACKAGE_NAME%.tar.gz
)
echo.

:: 显示结果
echo ======================================
echo 打包完成！
echo ======================================
echo.
echo 包含文件：
echo   - ciphergate-backend.tar (后端镜像)
echo   - ciphergate-frontend.tar (前端镜像)
echo   - docker-compose.prod.yaml (编排文件)
echo   - .env (生产环境配置)
echo   - deploy.bat (一键部署脚本)
echo   - README.md (使用说明)
echo.
echo 客户使用方法：
echo   1. 解压部署包
echo   2. 进入目录: cd %PACKAGE_NAME%
echo   3. 修改配置: notepad .env (可选)
echo   4. 部署: deploy.bat
echo.

:: 询问是否清理
set /p CLEANUP="是否删除临时目录 %PACKAGE_NAME%? (y/n): "
if /i "%CLEANUP%"=="y" (
    rmdir /s /q "%PACKAGE_NAME%"
    echo [√] 临时目录已清理
)

endlocal
