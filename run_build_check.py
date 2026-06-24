import subprocess, os, sys

os.chdir(r"E:\android\testGame")

# Run gradle build and capture output
result = subprocess.run(
    [r"E:\android\testGame\gradlew.bat", "assembleDebug"],
    capture_output=True, text=True, encoding="utf-8", errors="replace",
    timeout=600
)

# Save full output
with open(r"E:\android\testGame\build_output.txt", "w", encoding="utf-8") as f:
    f.write("=== STDOUT ===\n")
    f.write(result.stdout)
    f.write("\n=== STDERR ===\n")
    f.write(result.stderr)

# Print only error lines
print("=" * 60)
print("COMPILATION ERRORS:")
print("=" * 60)
for line in (result.stdout + "\n" + result.stderr).split("\n"):
    if "error:" in line.lower() and ": error:" in line:
        print(line)

print(f"\nExit code: {result.returncode}")
print(f"Full output saved to build_output.txt ({len(result.stdout + result.stderr)} bytes)")
