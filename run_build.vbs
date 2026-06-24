Set WshShell = CreateObject("WScript.Shell")
WshShell.Run "cmd.exe /c cd /d E:\android\testGame && gradlew.bat assembleDebug > build_output.txt 2>&1", 0, True
