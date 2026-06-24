import subprocess, os, sys, time

work_dir = r"E:\android\testGame"
gradlew = os.path.join(work_dir, "gradlew.bat")
log_file = os.path.join(work_dir, "build_final_log.txt")

with open(log_file, "w", encoding="utf-8") as f:
    f.write("=== Build Start ===\n")

# Launch in a new independent window using Windows start command
# This bypasses the broken PowerShell terminal
start_cmd = f'start "APK-Build" /MIN cmd /c "cd /d {work_dir} && {gradlew} assembleDebug --no-daemon > {work_dir}\\build_final_log.txt 2>&1 && echo BUILD_SUCCESS >> {work_dir}\\build_final_log.txt || echo BUILD_FAILED >> {work_dir}\\build_final_log.txt"'

# Use os.system which creates a new cmd.exe process
os.system(start_cmd)

with open(log_file, "a", encoding="utf-8") as f:
    f.write("Build launched in new window, PID? check build_final_log.txt after completion\n")
    f.write(f"Command: {start_cmd}\n")
