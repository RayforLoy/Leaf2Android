"""Compare CAB components, PE sections and protocol string anchors."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def protocol_strings(directory: Path) -> dict[str, dict]:
    rows = read_json(directory / "00LeafCE.001.strings.json")
    return {row["text"]: row for row in rows if row["text"].startswith("^<")}


def compare(before: Path, after: Path) -> dict:
    old = read_json(before / "binary-analysis.json")
    new = read_json(after / "binary-analysis.json")
    old_binaries = {binary["cab_name"]: binary for binary in old["binaries"]}
    components = []
    for binary in new["binaries"]:
        previous = old_binaries.get(binary["cab_name"])
        if previous is None:
            components.append({"cab_name": binary["cab_name"], "status": "added"})
            continue
        sections = {s["name"]: s for s in previous["sections"]}
        components.append({
            "cab_name": binary["cab_name"], "module_name": binary["module_name"],
            "before_size": previous["size"], "after_size": binary["size"],
            "identical_file": previous["sha256"] == binary["sha256"],
            "sections": [{"name": s["name"], "identical_bytes": s["sha256"] == sections.get(s["name"], {}).get("sha256"),
                          "identical_without_export_timestamp": s["sha256_without_export_timestamp"] == sections.get(s["name"], {}).get("sha256_without_export_timestamp")}
                         for s in binary["sections"]],
        })
    a, b = protocol_strings(before), protocol_strings(after)
    # These are string anchors, not a claim that a full command or wire format is known.
    return {
        "before_cab_sha256": old["cab_sha256"], "after_cab_sha256": new["cab_sha256"],
        "components": components,
        "command_anchors": [{"text": text, "before": a.get(text), "after": b.get(text)}
                            for text in sorted(a.keys() | b.keys())],
        "added_command_anchors": sorted(b.keys() - a.keys()),
        "removed_command_anchors": sorted(a.keys() - b.keys()),
        "interpretation": "File and section equality is byte equality only. Strings are static evidence, not hardware validation.",
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--before", type=Path, default=ROOT / "reference/analysis/wiview-1.0.2")
    parser.add_argument("--after", type=Path, default=ROOT / "reference/analysis/wiview-1.0.3")
    parser.add_argument("--output", type=Path, default=ROOT / "reference/analysis/version-comparison.json")
    args = parser.parse_args()
    report = compare(args.before, args.after)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Compared {len(report['components'])} components; added command anchors: {report['added_command_anchors']}")


if __name__ == "__main__":
    main()
