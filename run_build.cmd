@echo off
cd /d E:\android\testGame
echo Building...
call gradlew.bat assembleDebug > E:\android\testGame\build_output.txt 2>&1
echo DONE >> E:\android\testGame\build_output.txt
exit
