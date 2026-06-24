$ErrorActionPreference = "Stop"
Set-Location E:\android\testGame
$output = ""
$output += "=== GIT DIFF STAT ===`n"
$output += & git diff --stat 2>&1 | Out-String
$output += "`n=== GIT STATUS ===`n"  
$output += & git status -s 2>&1 | Out-String
$output += "`n=== GRADLE BUILD ===`n"
$output += & .\gradlew.bat assembleDebug --no-daemon 2>&1 | Out-String
$output | Out-File -FilePath "E:\android\testGame\build_result.txt" -Encoding UTF8
Write-Host "Done. Results saved to build_result.txt"
