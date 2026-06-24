import subprocess, os, time

apk_dir = r'E:\android\testGame\app\build\outputs\apk\debug'
log_file = r'E:\android\testGame\build_log.txt'

# 清理旧日志
if os.path.exists(log_file):
    os.remove(log_file)

# 运行 gradle build
r = subprocess.run(
    [r'E:\android\testGame\gradlew.bat', 'assembleDebug', '--no-daemon'],
    cwd=r'E:\android\testGame',
    capture_output=True, text=True
)

# 写日志
with open(log_file, 'w', encoding='utf-8') as f:
    f.write('=== STDOUT ===\n')
    f.write(r.stdout[-5000:])
    f.write('\n\n=== STDERR ===\n')
    if r.stderr:
        f.write(r.stderr[-5000:])
    f.write(f'\n\n=== RC: {r.returncode} ===\n')

print(f'RC={r.returncode}')

# 检查 APK
if os.path.exists(apk_dir):
    apks = [f for f in os.listdir(apk_dir) if f.endswith('.apk')]
    if apks:
        for apk in apks:
            size = os.path.getsize(os.path.join(apk_dir, apk)) / 1024
            print(f'APK_OK: {apk} ({size:.1f} KB)')
    else:
        print('APK_NOT_FOUND')
else:
    print('APK_DIR_NOT_EXISTS')
