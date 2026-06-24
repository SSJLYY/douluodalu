cd E:\android\testGame
.\gradlew assembleDebug --no-daemon 2>&1 | Out-File -FilePath E:\android\testGame\build_log.txt -Encoding UTF8
Write-Output "BUILD_DONE" | Out-File -FilePath E:\android\testGame\build_done.txt -Encoding UTF8
