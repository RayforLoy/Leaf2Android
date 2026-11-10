"""Package reproducible sources and the requested manufacturer reference materials; exclude caches and keys."""
from pathlib import Path
import hashlib
import zipfile

ROOT = Path(__file__).resolve().parents[1]
VERSION = "0.2.0"
OUTPUT = ROOT / f"dist/Leaf2Android-{VERSION}-source.zip"
ROOT_FILES = ["README.md", "README.en.md", "NOTICE.md", ".gitignore", ".gitattributes", "settings.gradle.kts", "build.gradle.kts", "gradle.properties",
              "gradlew", "gradlew.bat", "requirements-research.txt"]
FOLDERS = ["app", "protocol", "gradle", "docs", "tools", "tests", "leafresearch", "reference", "research/reports", "research/fixtures", ".github"]
SKIP = {"build", "__pycache__", ".gradle", ".idea", ".kotlin"}
PRIVATE_NAMES = {"local.properties", "signing.properties", "release-signing.properties", "release-signing.json"}


def archive(destination: Path, paths: list[Path], prefix: str) -> None:
    with zipfile.ZipFile(destination, "w", compression=zipfile.ZIP_DEFLATED) as output:
        for path in sorted(set(paths)):
            if path.is_file():
                if path.name in PRIVATE_NAMES:
                    continue
                assert path.suffix.lower() not in {".jks", ".keystore", ".p12", ".pfx"}
                name = prefix + path.relative_to(ROOT).as_posix()
                if path == ROOT / "gradlew":
                    info = zipfile.ZipInfo.from_file(path, name)
                    info.create_system = 3
                    info.external_attr = 0o100755 << 16
                    output.writestr(info, path.read_bytes(), compress_type=zipfile.ZIP_DEFLATED)
                else:
                    output.write(path, name)
    digest = hashlib.sha256(destination.read_bytes()).hexdigest()
    destination.with_suffix(destination.suffix + ".sha256").write_text(f"{digest}  {destination.name}\n", encoding="ascii")
    print(destination, destination.stat().st_size, digest)


def main() -> None:
    paths = [ROOT / name for name in ROOT_FILES]
    for folder in FOLDERS:
        paths += [p for p in (ROOT / folder).rglob("*") if p.is_file() and not SKIP.intersection(p.relative_to(ROOT).parts)]
    OUTPUT.parent.mkdir(exist_ok=True)
    archive(OUTPUT, paths, "Leaf2Android/")
    references = [p for p in (ROOT / "reference").rglob("*") if p.is_file() and not SKIP.intersection(p.relative_to(ROOT).parts)]
    archive(ROOT / f"dist/Leaf2Android-{VERSION}-reference.zip", references, "Leaf2Android/")
    distributables = [ROOT / f"dist/Leaf2Android-{VERSION}.apk", OUTPUT, ROOT / f"dist/Leaf2Android-{VERSION}-reference.zip"]
    if all(p.is_file() for p in distributables):
        (ROOT / "dist/SHA256SUMS.txt").write_text("".join(f"{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n" for p in distributables), encoding="ascii")


if __name__ == "__main__":
    main()
