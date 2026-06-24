import subprocess, os, sys

log_path = r"E:\android\testGame\build_result.txt"
work_dir = r"E:\android\testGame"
gradlew = os.path.join(work_dir, "gradlew.bat")

def log(msg):
    with open(log_path, "a", encoding="utf-8") as f:
        f.write(msg + "\n")

log("=== Build Start ===")

# Create keystore if needed
if not os.path.exists(os.path.join(work_dir, "release.keystore")):
    log("Creating release keystore...")
    # Use Android Studio bundled keytool as fallback
    keytool_paths = [
        os.path.join(os.environ.get("JAVA_HOME", ""), "bin", "keytool.exe"),
        r"C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe",
        "keytool"
    ]
    kt = None
    for p in keytool_paths:
        if p and (os.path.exists(p) or p == "keytool"):
            kt = p
            break
    if kt:
        r = subprocess.run([kt, "-genkey", "-v", "-keystore", os.path.join(work_dir, "release.keystore"),
           "-alias", "release", "-keyalg", "RSA", "-keysize", "2048",
           "-validity", "10000", "-storepass", "android", "-keypass", "android",
           "-dname", "CN=Test, OU=Test, O=Test, L=City, S=State, C=CN"],
           capture_output=True, text=True)
        log(f"keytool exit: {r.returncode}")
    else:
        log("keytool not found, skipping keystore creation")

# Run gradle build
log("Running gradle assembleDebug...")
proc = subprocess.run([gradlew, "assembleDebug", "--no-daemon"], cwd=work_dir,
                     capture_output=True, text=True, timeout=600)

log(f"Exit code: {proc.returncode}")
log(f"STDOUT last 200 lines:\n" + "\n".join(proc.stdout.splitlines()[-200:]))
if proc.stderr:
    log(f"STDERR last 100 lines:\n" + "\n".join(proc.stderr.splitlines()[-100:]))

# Search for APK
log("\n=== Searching for APK ===")
found = False
for root, dirs, files in os.walk(os.path.join(work_dir, "app", "build")):
    for f in files:
        if f.endswith(".apk"):
            fp = os.path.join(root, f)
            sz = os.path.getsize(fp)
            log(f"APK: {fp} ({sz/1024:.1f} KB)")
            found = True
            log(f"\n✅ APK Path: {fp}")

if not found:
    log("No APK found in app/build")

log("=== Build Complete ===")
