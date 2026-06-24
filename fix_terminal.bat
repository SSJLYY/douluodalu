@echo off
chcp 65001 >nul
echo ========================================
echo  fix_terminal.bat - 修复终端乱码问题
echo ========================================

:: 1. 在CMD中直接运行构建（绕开PSReadLine）
echo.
echo 运行项目构建测试...
cd /d "E:\android\testGame"
call gradlew.bat assembleDebug > build_test_result.txt 2>&1

:: 2. 检查结果
if %errorlevel% equ 0 (
    echo [PASS] 构建成功!
) else (
    echo [FAIL] 构建有错误
    findstr /i "error" build_test_result.txt
)

echo.
echo ========================================
echo  终端乱码解决方案:
echo.
echo  方案A: 在 Android Studio 中切换默认终端
echo    Settings ^> Tools ^> Terminal
echo    Shell path 改为: cmd.exe
echo.
echo  方案B: 打开新的 PowerShell 窗口执行:
echo    Install-Module PSReadLine -Force
echo.
echo  构建日志已保存到 build_test_result.txt
echo ========================================
pause
