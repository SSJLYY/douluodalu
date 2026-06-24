import subprocess, os, sys, time

# 构建APK
log_file = r'E:\android\testGame\build_log.txt'
apk_dir = r'E:\android\testGame\app\build\outputs\apk\debug'

# 运行gradlew
proc = subprocess.run(
    [r'E:\android\testGame\gradlew.bat', 'assembleDebug', '--no-daemon'],
    cwd=r'E:\android\testGame',
    capture_output=True, text=True, timeout=600
)

with open(log_file, 'w', encoding='utf-8') as f:
    f.write(proc.stdout[-5000:])
    if proc.stderr:
        f.write('\n=== STDERR ===\n')
        f.write(proc.stderr[-2000:])
    f.write(f'\n=== RC: {proc.returncode} ===\n')

# 检查APK输出
if os.path.exists(apk_dir):
    apks = [f for f in os.listdir(apk_dir) if f.endswith('.apk')]
    with open(log_file, 'a', encoding='utf-8') as f:
        f.write(f'\n=== APK files: {apks} ===\n')
        for apk in apks:
            size = os.path.getsize(os.path.join(apk_dir, apk))
            f.write(f'{apk}: {size/1024:.1f} KB\n')

print(f'Build RC: {proc.returncode}')
print(f'APKs: {[f for f in os.listdir(apk_dir) if f.endswith(".apk")] if os.path.exists(apk_dir) else "NODIR"}')
