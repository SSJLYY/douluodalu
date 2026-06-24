@echo off
echo ========================================
echo   斗罗大陆·放置传说 - 服务器启动脚本
echo ========================================
echo.

REM 检查MySQL是否运行
echo [1/3] 检查MySQL连接...
mysql -u root -e "SELECT 1" >nul 2>&1
if %errorlevel% neq 0 (
    echo 错误: MySQL未运行或连接失败
    echo 请确保MySQL已启动，并创建数据库:
    echo   mysql -u root -p ^< backend\init_database.sql
    pause
    exit /b 1
)
echo MySQL连接成功！

REM 初始化数据库（如果需要）
echo [2/3] 初始化数据库...
mysql -u root -p < backend\init_database.sql 2>nul

REM 启动后端服务器
echo [3/3] 启动Ktor服务器...
echo.
call gradlew :backend:run

pause
