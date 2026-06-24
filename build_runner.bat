@echo off
cd /d E:\android\testGame
call .\gradlew.bat assembleDebug --no-daemon > build_output.txt 2>&1
echo BUILD_DONE >> build_output.txt
