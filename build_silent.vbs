CreateObject("WScript.Shell").Run "cmd /c cd /d E:\android\testGame && .\gradlew.bat assembleDebug --no-daemon > E:\android\testGame\build_out.txt 2>&1", 0, True
