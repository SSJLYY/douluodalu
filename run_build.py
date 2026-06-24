import subprocess, os, sys

os.chdir(r'E:\android\testGame')
log_path = r'E:\android\testGame\build_log.txt'
done_path = r'E:\android\testGame\build_done.txt'

try:
    result = subprocess.run(
        ['.\\gradlew', 'assembleDebug', '--no-daemon'],
        capture_output=True, text=True, timeout=600
    )
    with open(log_path, 'w', encoding='utf-8') as f:
        f.write('STDOUT:\n' + result.stdout + '\n\nSTDERR:\n' + result.stderr)
    with open(done_path, 'w') as f:
        f.write('BUILD_DONE')
    print('BUILD COMPLETE')
except subprocess.TimeoutExpired:
    with open(log_path, 'w', encoding='utf-8') as f:
        f.write('TIMEOUT')
    print('TIMEOUT')
except Exception as e:
    with open(log_path, 'w', encoding='utf-8') as f:
        f.write(f'ERROR: {e}')
    print(f'ERROR: {e}')
