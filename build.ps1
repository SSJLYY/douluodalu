$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
Set-Location E:\android\testGame
$result = & .\gradlew.bat assembleDebug --no-daemon 2>&1 | Out-String
$result | Out-File -FilePath "E:\android\testGame\compile.log" -Encoding UTF8
Write-Host "BUILD COMPLETE"
