cd E:\android\testGame
& .\gradlew.bat assembleDebug --no-daemon 2>&1 | Out-File -FilePath E:\android\testGame\build_output.txt -Encoding UTF8
"BUILD_FINISHED_$(Get-Date -Format 'HH:mm:ss')" | Out-File -FilePath E:\android\testGame\build_done.txt
