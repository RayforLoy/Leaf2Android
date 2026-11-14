"""Publish the explicitly requested release using existing Git credentials; never print or store tokens."""
from __future__ import annotations

import argparse
import hashlib
import json
import mimetypes
import os
import posixpath
from pathlib import Path
import re
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request


def credentials() -> str:
    token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    if token:
        return token
    result = subprocess.run(["git", "credential", "fill"], input="protocol=https\nhost=github.com\n\n",
        text=True, capture_output=True, env={**os.environ, "GIT_TERMINAL_PROMPT": "0", "GCM_INTERACTIVE": "Never"}, timeout=30)
    fields = dict(line.split("=", 1) for line in result.stdout.splitlines() if "=" in line)
    if result.returncode or not fields.get("password"):
        raise RuntimeError("No GitHub credential is available. Sign in to GitHub for Git, or set GH_TOKEN.")
    return fields["password"]


class Github:
    def __init__(self, repo: str):
        if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repo):
            raise ValueError("Repository must be owner/name")
        self.base = f"https://api.github.com/repos/{repo}"
        self.token = credentials()

    def call(self, url: str, method: str = "GET", data=None, content_type: str = "application/json"):
        parsed = urllib.parse.urlsplit(url)
        if parsed.scheme != "https" or parsed.hostname not in {"api.github.com", "uploads.github.com"}:
            raise ValueError("Authenticated requests are restricted to GitHub API/upload hosts")
        if isinstance(data, dict):
            data = json.dumps(data, ensure_ascii=False).encode("utf-8")
        request = urllib.request.Request(url, data=data, method=method, headers={
            "Authorization": "Bearer " + self.token, "Accept": "application/vnd.github+json",
            "Content-Type": content_type, "User-Agent": "Leaf2Android-release", "X-GitHub-Api-Version": "2022-11-28",
        })
        for attempt in range(4):
            try:
                with urllib.request.urlopen(request, timeout=180) as response:
                    body = response.read()
                    return json.loads(body) if body else None
            except urllib.error.HTTPError as error:
                if method != "GET" or error.code not in {429, 500, 502, 503, 504} or attempt == 3:
                    raise
            except (urllib.error.URLError, TimeoutError, ConnectionError):
                if method != "GET" or attempt == 3:
                    raise
            # Mutations are never replayed blindly; rerunning main reconciles the draft/assets first.
            time.sleep(0.5 * 2 ** attempt)


def render_notes(path: Path, repository: str, tag: str) -> str:
    """Release pages do not resolve links relative to docs/releases; pin local links to the tag."""
    root = Path(__file__).resolve().parents[1]
    folder = path.resolve().parent.relative_to(root).as_posix()
    def link(match):
        target = match.group(2).strip("<>")
        parsed = urllib.parse.urlsplit(target)
        if parsed.scheme or target.startswith("#"):
            return match.group(0)
        relative = posixpath.normpath(posixpath.join(folder, urllib.parse.unquote(parsed.path)))
        if relative == ".." or relative.startswith("../") or not (root / relative).exists():
            raise ValueError("Invalid local release-note link: " + target)
        route = "tree" if (root / relative).is_dir() else "blob"
        url = f"https://github.com/{repository}/{route}/{urllib.parse.quote(tag, safe='')}/{urllib.parse.quote(relative, safe='/')}"
        if parsed.fragment:
            url += "#" + parsed.fragment
        return match.group(1) + "(" + url + ")"
    return re.sub(r"(\[[^\]]+\])\((<[^>]+>|[^)\s]+)\)", link, path.read_text(encoding="utf-8"))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", default="RayforLoy/Leaf2Android")
    parser.add_argument("--tag", default="v0.3.1")
    parser.add_argument("--commit", required=True)
    parser.add_argument("--notes", type=Path, required=True)
    parser.add_argument("--asset", type=Path, action="append", required=True)
    parser.add_argument("--output", type=Path, default=Path("dist/github-release.json"))
    args = parser.parse_args()
    if not re.fullmatch(r"[0-9a-f]{40}", args.commit):
        raise ValueError("--commit must identify a concrete 40-character Git commit")
    assets = args.asset
    if len({p.name for p in assets}) != len(assets) or any(not p.is_file() for p in assets):
        raise ValueError("Assets must be existing files with distinct filenames")
    github = Github(args.repo)
    repo = github.call(github.base)
    if not repo.get("permissions", {}).get("push"):
        raise RuntimeError("The authenticated account cannot push this repository")
    try:
        release = github.call(github.base + "/releases/tags/" + urllib.parse.quote(args.tag, safe=""))
    except urllib.error.HTTPError as e:
        if e.code != 404:
            raise
        drafts = [item for item in github.call(github.base + "/releases?per_page=100") if item["tag_name"] == args.tag]
        if drafts:
            if len(drafts) != 1:
                raise RuntimeError("Multiple releases use this tag; none was modified")
            release = drafts[0]
        else:
            release = github.call(github.base + "/releases", "POST", {
                "tag_name": args.tag, "target_commitish": args.commit,
                "name": f"Leaf2Android {args.tag} — Valeo DP-67 compatibility / Valeo DP-67 兼容",
                "body": render_notes(args.notes, args.repo, args.tag) + f"\n\nSource commit: `{args.commit}`\n",
                "draft": True, "prerelease": True,
            })
    if not release["draft"]:
        raise RuntimeError("This tag already has a published release; it was not modified")
    if release["target_commitish"] != args.commit:
        raise RuntimeError("The draft targets a different commit; it was not modified")
    existing = {asset["name"]: asset for asset in release.get("assets", [])}
    uploaded = []
    for path in assets:
        digest = "sha256:" + hashlib.sha256(path.read_bytes()).hexdigest()
        if path.name in existing:
            asset = existing[path.name]
            if asset.get("digest") != digest:
                raise RuntimeError(f"Draft asset already exists with different content: {path.name}")
        else:
            url = release["upload_url"].split("{", 1)[0] + "?" + urllib.parse.urlencode({"name": path.name})
            asset = github.call(url, "POST", path.read_bytes(), mimetypes.guess_type(path.name)[0] or "application/octet-stream")
        if asset.get("digest") is not None and asset["digest"] != digest:
            raise RuntimeError(f"GitHub asset checksum mismatch: {path.name}")
        if asset["size"] != path.stat().st_size or asset["state"] != "uploaded":
            raise RuntimeError(f"Incomplete GitHub asset: {path.name}")
        uploaded.append({"name": path.name, "bytes": asset["size"], "sha256": digest.removeprefix("sha256:"), "url": asset["browser_download_url"]})
        print(f"Uploaded and checked: {path.name}", flush=True)
    release = github.call(github.base + "/releases/" + str(release["id"]), "PATCH", {"draft": False, "prerelease": True})
    # GitHub changes draft asset URLs from an untagged path to the published tag.
    published_assets = {asset["name"]: asset for asset in release["assets"]}
    for asset in uploaded:
        asset["url"] = published_assets[asset["name"]]["browser_download_url"]
    report = {"repository": args.repo, "tag": args.tag, "commit": args.commit, "release_url": release["html_url"], "prerelease": True, "assets": uploaded}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("Published " + release["html_url"], flush=True)


if __name__ == "__main__":
    main()
