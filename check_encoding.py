import os
import chardet

src_dir = r"E:\android\testGame\app\src\main\java\com\example\garygame"
lines = []
for root, dirs, files in os.walk(src_dir):
    for f in sorted(files):
        if f.endswith(".kt"):
            path = os.path.join(root, f)
            with open(path, "rb") as fh:
                raw = fh.read(5000)
            detected = chardet.detect(raw)
            encoding = detected.get("encoding", "unknown")
            confidence = detected.get("confidence", 0)
            try:
                raw.decode("utf-8")
                utf8_ok = True
            except:
                utf8_ok = False
            rel_path = os.path.relpath(path, src_dir)
            marker = "WARN" if (not utf8_ok) or (encoding and encoding.lower() not in ("utf-8", "ascii") and confidence > 0.5) else "OK"
            lines.append(f"{marker} {rel_path:45s} detected={encoding:10s} conf={confidence:.0%} utf8_valid={utf8_ok}")
with open(r"E:\android\testGame\encoding_report.txt", "w", encoding="utf-8") as out:
    out.write("\n".join(lines))
print("Report written to encoding_report.txt")
