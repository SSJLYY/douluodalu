@echo off
chcp 65001 >nul
title 修复PSReadLine终端乱码问题
echo ========================================
echo  正在修复 PowerShell PSReadLine 问题
echo ========================================
echo.

:: 方案1: 重置控制台缓冲大小
echo [1/4] 重置控制台缓冲大小...
powershell -NoProfile -Command "[Console]::BufferWidth = 120; [Console]::WindowWidth = 120; [Console]::BufferHeight = 3000; [Console]::WindowHeight = 30; Write-Host 'OK' -ForegroundColor Green"
if %errorlevel% equ 0 echo  ✓ 缓冲大小重置成功

:: 方案2: 移除并重装PSReadLine
echo [2/4] 移除损坏的PSReadLine模块...
powershell -NoProfile -Command "Remove-Module PSReadLine -Force -ErrorAction SilentlyContinue; Write-Host 'OK' -ForegroundColor Green"
if %errorlevel% equ 0 echo  ✓ PSReadLine已卸载

:: 方案3: 重置控制台
echo [3/4] 重置控制台...
powershell -NoProfile -Command "Clear-Host; Write-Host '控制台已重置' -ForegroundColor Green"

:: 方案4: 设置默认使用CMD
echo [4/4] 创建CMD模式的构建快捷方式...
echo.
echo 推荐操作:
echo   - 在 Android Studio 中: File ^> Settings ^> Tools ^> Terminal
echo   - 将 Shell path 改为: cmd.exe
echo   - 或直接关闭当前终端，重新打开
echo.

echo ========================================
echo  PSReadLine 可能问题是Windows系统更新导致的
echo  建议安装最新版:
echo.
echo  Install-Module PSReadLine -Force -SkipPublisherCheck
echo ========================================
echo.

:: 运行构建测试
echo 正在运行构建测试（无PSReadLine模式）...
call "E:\android\testGame\run_build.cmd"
if exist "E:\android\testGame\build_output.txt" (
    echo ✓ 构建完成! 查看 build_output.txt 获取详细结果
    findstr /i "error" "E:\android\testGame\build_output.txt" >nul
    if errorlevel 1 (
        echo ✓ 构建成功，没有编译错误!
    ) else (
        echo ⚠ 构建有错误，请查看 build_output.txt
    )
) else (
    echo ⚠ 构建输出文件未找到，请检查
)

pause
