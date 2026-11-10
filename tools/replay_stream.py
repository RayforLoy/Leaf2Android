"""Replay a local binary/hex stream through the offline WiView framer."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from leafresearch.framing import ProtocolError, StreamFramer


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("--hex", action="store_true", help="Input is whitespace-separated hex, not raw bytes")
    parser.add_argument("--chunk-size", type=int, default=4096)
    args = parser.parse_args()
    if not 1 <= args.chunk_size <= 1024 * 1024:
        parser.error("--chunk-size must be between 1 and 1048576")
    try:
        framer = StreamFramer()
        with args.input.open("rb") as stream:
            if args.hex:
                # Hex mode is for small, inspectable fixtures; raw captures stream from disk.
                text = stream.read(8 * 1024 * 1024 + 1)
                if len(text) > 8 * 1024 * 1024:
                    raise ValueError("Hex fixture exceeds 8 MiB; use a raw binary capture")
                from io import BytesIO
                stream = BytesIO(bytes.fromhex(text.decode("ascii")))
            while chunk := stream.read(args.chunk_size):
                for frame in framer.feed(chunk):
                    print(json.dumps({
                        "tag": frame.tag,
                        "attributes": {key: ("[redacted]" if "pin" in key.lower() else value.decode("ascii", "backslashreplace"))
                                       for key, value in frame.attributes},
                        "attachment_bytes": len(frame.attachment),
                        "attachment_sha256": hashlib.sha256(frame.attachment).hexdigest() if frame.attachment else None,
                    }, ensure_ascii=True))
        framer.finish()
        print(json.dumps({"discarded_bytes": framer.discarded_bytes, "resync_count": framer.resync_count}))
        return 0
    except (OSError, ValueError, ProtocolError) as error:
        print(str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())

