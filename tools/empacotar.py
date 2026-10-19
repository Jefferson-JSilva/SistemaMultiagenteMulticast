"""Create a source + docs + evidence ZIP, excluding build and temporary files."""
from pathlib import Path
import hashlib
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "output/sistema-cardio-multicast.zip"
INCLUDE = ["src", "test", "config", "docs", "tools", "evidencias", "output/pdf",
           "README.md", ".gitignore", "build.ps1", "run.ps1", "test.ps1"]
files = []
for name in INCLUDE:
    path = ROOT / name
    if path.is_dir():
        files.extend(p for p in path.rglob("*") if p.is_file() and "__pycache__" not in p.parts)
    else:
        files.append(path)
OUTPUT.parent.mkdir(parents=True, exist_ok=True)
manifest = []
with zipfile.ZipFile(OUTPUT, "w", zipfile.ZIP_DEFLATED) as archive:
    for path in sorted(files):
        relative = path.relative_to(ROOT).as_posix()
        data = path.read_bytes()
        manifest.append(hashlib.sha256(data).hexdigest() + "  " + relative)
        archive.writestr("sistema-cardio-multicast/" + relative, data)
    archive.writestr("sistema-cardio-multicast/MANIFEST.sha256", "\n".join(manifest) + "\n")
with zipfile.ZipFile(OUTPUT) as archive:
    assert archive.testzip() is None, "ZIP validation failed"
print(f"{OUTPUT}\n{len(files)} arquivos e manifesto SHA-256; ZIP verificado.")
