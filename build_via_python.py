import subprocess, os, sys, time

work_dir = r"E:\android\testGame"
result_file = r"E:\android\testGame\build_final_result.txt"
gradlew = os.path.join(work_dir, "gradlew.bat")

with open(result_file, "w", encoding="utf-8") as f:
    f.write("Starting build...\n")
    
    # Run gradle without capturing output to avoid pipe deadlock
    f.write(f"Running: {gradlew} assembleDebug --no-daemon\n")
    f.flush()
    
    with open(result_file + ".stdout", "w", encoding="utf-8") as fout:
        with open(result_file + ".stderr", "w", encoding="utf-8") as ferr:
            proc = subprocess.Popen(
                [gradlew, "assembleDebug", "--no-daemon"],
                cwd=work_dir,
                stdout=fout,
                stderr=ferr
            )
            proc.wait(timeout=600)
    
    f.write(f"Exit code: {proc.returncode}\n")
    
    # Search for APK
    f.write("\nSearching for APK files...\n")
    found = False
    for root, dirs, files in os.walk(os.path.join(work_dir, "app", "build")):
        for fn in files:
            if fn.endswith(".apk"):
                fp = os.path.join(root, fn)
                sz = os.path.getsize(fp)
                f.write(f"✅ FOUND: {fp} ({sz/1024:.1f} KB)\n")
                found = True
    
    if not found:
        f.write("❌ No APK found\n")
    
    f.write("\nDone.\n")

print("Build complete. Check build_final_result.txt")
