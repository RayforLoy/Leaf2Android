"""Download or verify pinned research inputs. Never execute downloaded files."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import tempfile
import urllib.request


ROOT = Path(__file__).resolve().parents[1]
LOCK = ROOT / "reference" / "sources.lock.json"


def local_path(relative: str) -> Path:
    path = (ROOT / relative).resolve()
    if not path.is_relative_to(ROOT) or Path(relative).is_absolute():
        raise ValueError(f"Source path must stay inside the project: {relative}")
    return path


def verify(path: Path, source: dict) -> None:
    digest = hashlib.sha256()
    size = 0
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            size += len(chunk)
            digest.update(chunk)
    if size != source["size"] or digest.hexdigest() != source["sha256"]:
        raise ValueError(f"Integrity check failed: {path.name}")


def acquire(source: dict, offline: bool) -> None:
    path = local_path(source["path"])
    if path.exists():
        verify(path, source)
        return
    if offline:
        raise FileNotFoundError(f"Missing input: {source['path']}")
    if not source.get("url"):
        raise FileNotFoundError(f"User-supplied input must be provided locally: {source['path']}")
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        request = urllib.request.Request(
            source["url"], headers={"User-Agent": "Leaf2Android research/1.0"}
        )
        with urllib.request.urlopen(request, timeout=60) as response:
            with tempfile.NamedTemporaryFile(
                dir=path.parent, prefix=path.name + ".", suffix=".part", delete=False
            ) as output:
                temporary = Path(output.name)
                total = 0
                while chunk := response.read(1024 * 1024):
                    total += len(chunk)
                    if total > source["size"]:
                        raise ValueError(f"Response exceeds pinned size: {source['id']}")
                    output.write(chunk)
        verify(temporary, source)
        os.replace(temporary, path)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("fetch", "verify"))
    parser.add_argument("--source", action="append", help="Source ID; repeat to select several")
    args = parser.parse_args()
    sources = json.loads(LOCK.read_text(encoding="utf-8"))["sources"]
    requested = set(args.source or ())
    unknown = requested - {source["id"] for source in sources}
    if unknown:
        parser.error(f"Unknown source IDs: {', '.join(sorted(unknown))}")
    failed = False
    for source in sources:
        if requested and source["id"] not in requested:
            continue
        try:
            acquire(source, offline=args.action == "verify")
            print(f"OK {source['id']} {source['sha256']}")
        except (OSError, ValueError) as error:
            failed = True
            print(f"FAIL {source['id']}: {error}")
    return int(failed)


if __name__ == "__main__":
    raise SystemExit(main())

