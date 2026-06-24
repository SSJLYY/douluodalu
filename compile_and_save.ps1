cd E:\android\testGame
$output = .\gradlew.bat assembleDebug 2>&1 | Out-String
$output | Out-File -FilePath compile_result.txt -Encoding UTF8 -Force
