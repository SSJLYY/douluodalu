import subprocess, os, sys, time

work_dir = r"E:\android\testGame"
gradlew = os.path.join(work_dir, "gradlew.bat")
log_file = os.path.join(work_dir, "build_new_log.txt")

def log(msg):
    with open(log_file, "a", encoding="utf-8") as f:
        f.write(str(msg) + "\n")

log("=== Build Started ===")
sys.stdout.flush()

# Use CREATE_NEW_CONSOLE to spawn an independent window
CREATE_NEW_CONSOLE = 0x00000010
NORMAL_PRIORITY = 0x00000020

proc = subprocess.Popen(
    [gradlew, "assembleDebug", "--no-daemon"],
    cwd=work_dir,
    stdout=subprocess.PIPE,
    stderr=subprocess.PIPE,
    text=True,
    creationflags=CREATE_NEW_CONSOLE | NORMAL_PRIORITY,
    shell=True
)

log(f"PID: {proc.pid}")
sys.stdout.flush()

# Wait for completion with polling
try:
    stdout, stderr = proc.communicate(timeout=600)
    log(f"Exit code: {proc.returncode}")
    log(f"STDOUT:\n{stdout[-5000:]}")
    if stderr:
        log(f"STDERR:\n{stderr[-5000:]}")
except subprocess.TimeoutExpired:
    proc.kill()
    log("TIMEOUT - process killed")
    sys.exit(1)

# Check APK
apk_dir = os.path.join(work_dir, "app", "build", "outputs", "apk", "debug")
if os.path.exists(apk_dir):
    apks = [f for f in os.listdir(apk_dir) if f.endswith(".apk")]
    if apks:
        log(f"✅ APKs found:")
        for apk in apks:
            size = os.path.getsize(os.path.join(apk_dir, apk)) / 1024
            log(f"   {apk} ({size:.1f} KB)")
    else:
        log("⚠️ APK directory is empty")
else:
    log(f"⚠️ APK dir not found: {apk_dir}")

log("=== Build Complete ===")
