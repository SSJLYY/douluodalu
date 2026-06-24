import subprocess, os

work_dir = r"E:\android\testGame"
keystore = os.path.join(work_dir, "release.keystore")

# Step 1: Create keystore if not exists
if not os.path.exists(keystore):
    keytool = r"C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"
    if not os.path.exists(keytool):
        # try find JAVA_HOME
        jh = os.environ.get("JAVA_HOME", "")
        keytool = os.path.join(jh, "bin", "keytool.exe") if jh else keytool
        if not os.path.exists(keytool):
            keytool = r"C:\Program Files\Eclipse Adoptium\jdk-17.0.12.7-hotspot\bin\keytool.exe"
        if not os.path.exists(keytool):
            keytool = "keytool"  # fallback to PATH
    cmd = [keytool, "-genkey", "-v", "-keystore", keystore,
           "-alias", "release", "-keyalg", "RSA", "-keysize", "2048",
           "-validity", "10000", "-storepass", "android", "-keypass", "android",
           "-dname", "CN=Test, OU=Test, O=Test, L=City, S=State, C=CN"]
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode == 0:
        print("Keystore created successfully")
    else:
        print(f"Keystore creation failed: {result.stderr}")
        exit(1)
else:
    print("Keystore already exists")

# Step 2: Build debug APK
print("\nBuilding debug APK...")
gradlew = os.path.join(work_dir, "gradlew.bat")
result = subprocess.run([gradlew, "assembleDebug", "--no-daemon"], cwd=work_dir, capture_output=True, text=True)
print(result.stdout[-3000:] if len(result.stdout) > 3000 else result.stdout)
if result.stderr:
    print(f"STDERR: {result.stderr[-2000:]}")
if result.returncode != 0:
    print(f"\n❌ Build failed with exit code {result.returncode}")
    exit(1)

# Step 3: Check output APK
apk_dir = os.path.join(work_dir, "app", "build", "outputs", "apk", "debug")
if os.path.exists(apk_dir):
    apks = [f for f in os.listdir(apk_dir) if f.endswith(".apk")]
    if apks:
        print(f"\n✅ APK generated successfully:")
        for apk in apks:
            apk_path = os.path.join(apk_dir, apk)
            size_kb = os.path.getsize(apk_path) / 1024
            print(f"   📦 {apk} ({size_kb:.1f} KB)")
    else:
        print(f"\n⚠️ No APK found in {apk_dir}")
else:
    print(f"\n⚠️ APK directory not found: {apk_dir}")
