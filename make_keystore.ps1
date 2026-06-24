$env:JAVA_HOME = [System.Environment]::GetEnvironmentVariable("JAVA_HOME","Machine")
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = [System.Environment]::GetEnvironmentVariable("JAVA_HOME","User") }
$keytool = Join-Path $env:JAVA_HOME "bin\keytool.exe"
& $keytool -genkey -v -keystore release.keystore -alias release -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android -dname "CN=Test, OU=Test, O=Test, L=City, S=State, C=CN"
