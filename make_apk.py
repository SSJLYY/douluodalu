import subprocess, os

work_dir = r"E:\android\testGame"
gradlew = os.path.join(work_dir, "gradlew.bat")

print("[1/3] Building APK...")
r = subprocess.run([gradlew, "assembleDebug", "--no-daemon"], cwd=work_dir, capture_output=True, text=True)

# Save full log
with open(os.path.join(work_dir, "gradle_build_log.txt"), "w", encoding="utf-8") as f:
    f.write(r.stdout + "\n\nSTDERR:\n" + r.stderr)

print(f"[2/3] Exit code: {r.returncode}")

# Check result
if r.returncode == 0:
    found = False
    for root, dirs, files in os.walk(os.path.join(work_dir, "app", "build")):
        for f in files:
            if f.endswith(".apk"):
                fp = os.path.join(root, f)
                size_kb = os.path.getsize(fp)/1024
                print(f"[3/3] ✅ APK: {f} ({size_kb:.1f} KB)")
                found = True
    if not found:
        print("[3/3] ⚠️ BUILD SUCCESSFUL but no APK found. Checking reason...")
        # Print last 50 lines
        lines = r.stdout.splitlines()
        for line in lines[-30:]:
            print(line)
else:
    print(f"[3/3] ❌ BUILD FAILED")
    lines = r.stdout.splitlines()
    for line in lines[-30:]:
        print(line)
