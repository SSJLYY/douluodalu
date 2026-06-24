@echo off
chcp 65001 >nul
echo ========================================
echo   斗罗大陆·放置传说 - 完整构建部署脚本
echo ========================================
echo.

echo [1/5] 构建Web前端 (生产模式)...
call gradlew :web:jsBrowserDistribution
if %errorlevel% neq 0 (
    echo 前端构建失败！
    pause
    exit /b 1
)
echo 前端构建成功！

echo.
echo [2/5] 清理旧的前端资源...
if exist "backend\src\main\resources\web" rmdir /S /Q "backend\src\main\resources\web"
mkdir "backend\src\main\resources\web"

echo.
echo [3/5] 复制前端文件到后端资源目录...
xcopy /E /Y /Q "web\build\dist\js\productionExecutable\*" "backend\src\main\resources\web\"
if %errorlevel% neq 0 (
    echo 复制失败！请检查 web\build\dist\js\productionExecutable\ 是否存在
    pause
    exit /b 1
)
echo 前端文件复制完成！

echo.
echo [4/5] 构建后端 Fat JAR...
call gradlew :backend:shadowJar
if %errorlevel% neq 0 (
    echo 后端构建失败！
    pause
    exit /b 1
)
echo 后端构建成功！

echo.
echo [5/5] 完成！
echo.
echo ========================================
echo   构建产物: backend\build\libs\backend-all.jar
echo ========================================
echo.
echo 部署步骤:
echo   1. 确保服务器已安装 JDK 17+ 和 MySQL 8.0+
echo   2. 创建数据库: mysql -u root -p ^< backend\init_database.sql
echo   3. 上传 backend\build\libs\backend-all.jar 到服务器
echo   4. 启动: java -jar backend-all.jar
echo   5. 访问: http://你的IP:8080
echo.
echo 环境变量 (可选):
echo   DB_URL=jdbc:mysql://your-server:3306/douluo_game
echo   DB_USER=root
echo   DB_PASSWORD=your_password
echo   JWT_SECRET=your_secret_key
echo   PORT=8080
echo   ENV=production
echo.
pause
