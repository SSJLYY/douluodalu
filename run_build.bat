@echo off
cd /d E:\android\testGame
call gradlew.bat assembleDebug > build_output.txt 2>&1
echo EXIT CODE = %ERRORLEVEL% >> build_output.txt
echo Build complete. Exit code: %ERRORLEVEL%
