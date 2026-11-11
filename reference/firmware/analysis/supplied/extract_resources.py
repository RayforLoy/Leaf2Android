"""Extract LeafCE PE resources verbatim, preserving resource RVAs and hashes."""

from collections import Counter
from io import BytesIO
from pathlib import Path
import hashlib
import json

import pefile
from PIL import Image

from analyze_firmware import dump_json


def main():
    root = Path(__file__).resolve().parent
    source = root / "ce_reconstructed/fs/Windows/LeafCE.exe"
    pe = pefile.PE(str(source))
    manifest = []
    extensions = {"2": ".dib", "JPEG": ".jpg", "WAVE": ".wav"}
    for type_entry in pe.DIRECTORY_ENTRY_RESOURCE.entries:
        kind = str(type_entry.name) if type_entry.name else str(type_entry.id)
        for name_entry in type_entry.directory.entries:
            name = str(name_entry.name) if name_entry.name else str(name_entry.id)
            for lang_entry in name_entry.directory.entries:
                info = lang_entry.data.struct
                data = pe.get_data(info.OffsetToData, info.Size)
                filename = f"{name}_lang{lang_entry.id}{extensions.get(kind, '.bin')}"
                output = root / "resources" / kind / filename
                output.parent.mkdir(parents=True, exist_ok=True)
                output.write_bytes(data)
                row = {"type": kind, "name": name, "lang": lang_entry.id,
                       "path": output.relative_to(root).as_posix(), "rva": info.OffsetToData,
                       "reconstructed_pe_file_offset": pe.get_offset_from_rva(info.OffsetToData),
                       "size": len(data), "sha256": hashlib.sha256(data).hexdigest()}
                if kind in ("2", "JPEG"):
                    try:
                        image = Image.open(BytesIO(data))
                        image.load()
                        row["image"] = {"format": image.format, "width": image.width,
                                        "height": image.height, "mode": image.mode}
                    except Exception as error:
                        row["image_decode_error"] = str(error)
                manifest.append(row)
    dump_json(root / "resources_manifest.json", manifest)
    print("Resource counts:", dict(Counter(row["type"] for row in manifest)))
    print("Image decode failures:", sum("image_decode_error" in row for row in manifest))
    print("JPEG resources:", [row for row in manifest if row["type"] == "JPEG"])
    pe.close()


if __name__ == "__main__":
    main()
