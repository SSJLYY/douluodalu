@echo off
chcp 65001 >nul 2>&1
cd /d E:\android\testGame
echo ========================================
echo  ★ 斗罗大陆·放置传说 - APK 打包工具
echo ========================================
echo.
echo [1/2] 正在编译构建，请耐心等待...
echo.
call .\gradlew.bat assembleDebug --no-daemon
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [✖] 构建失败，请检查上面的错误信息！
    pause
    exit /b 1
)
echo.
echo [2/2] 构建成功！查找APK文件...
echo.
set "APK_DIR=app\build\outputs\apk\debug"
if exist "%APK_DIR%" (
    echo APK文件列表：
    dir "%APK_DIR%\*.apk"
    echo.
    echo APK位置：%CD%\%APK_DIR%\
) else (
    echo [⚠] APK目录不存在
)
echo.
echo 按任意键退出...
pause >nul
