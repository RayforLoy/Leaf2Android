"""Capture the project's disposable API 36 emulator; resets only this test app's data there."""
from pathlib import Path
import argparse
import os
import re
import subprocess
import time
import xml.etree.ElementTree as E

ROOT = Path(__file__).resolve().parents[1]
ADB = Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk/platform-tools/adb.exe"
SERIAL = "emulator-5554"
OUT = ROOT / "docs/screenshots"


def adb(*args: str) -> str:
    result = subprocess.run([str(ADB), "-s", SERIAL, *args], check=True, capture_output=True, timeout=40)
    return result.stdout.decode("utf-8", "replace").strip()


def tree():
    adb("shell", "uiautomator", "dump", "/sdcard/leaf-ui.xml")
    return E.fromstring(adb("exec-out", "cat", "/sdcard/leaf-ui.xml"))


def find(text: str | None = None, description: str | None = None, tag: str | None = None):
    for node in tree().iter("node"):
        resource = node.get("resource-id", "")
        if tag is not None and (resource == tag or resource.endswith("/" + tag)):
            return node
        if (text is not None and node.get("text") == text) or (description is not None and node.get("content-desc") == description):
            return node
    return None


def wait_text(text: str):
    for _ in range(8):
        if find(text=text) is not None:
            return
        time.sleep(.25)
    raise RuntimeError(f"UI text missing: {text}")


def tap(text: str | None = None, description: str | None = None, tag: str | None = None):
    node = find(text, description, tag)
    if node is None:
        raise RuntimeError(f"UI node missing: {text or description or tag}")
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds", "")))
    adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))


def capture(name: str):
    OUT.mkdir(parents=True, exist_ok=True)
    tree()  # Wait for semantics/layout before sampling the compositor and system-bar transition.
    time.sleep(.7)
    with (OUT / name).open("wb") as image:
        subprocess.run([str(ADB), "-s", SERIAL, "exec-out", "screencap", "-p"], stdout=image, check=True, timeout=20)
    (OUT / name.replace(".png", ".xml")).write_text(E.tostring(tree(), encoding="unicode"), encoding="utf-8")
    print(name, flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, default=ROOT / "app/build/outputs/apk/debug/app-debug.apk")
    args = parser.parse_args()
    assert adb("shell", "getprop", "ro.kernel.qemu") == "1", "Only a disposable emulator is allowed"
    assert adb("emu", "avd", "name").splitlines()[0] == "Leaf2Android_API36", "Unexpected AVD"
    # Only this disposable AVD is reset, allowing debug -> independently signed release transitions.
    if "package:com.leaf2android" in adb("shell", "pm", "list", "packages", "com.leaf2android").splitlines():
        adb("uninstall", "com.leaf2android")
    adb("install", "-r", str(args.apk))
    adb("shell", "wm", "size", "1080x2520")
    adb("shell", "wm", "density", "420")
    adb("shell", "pm", "clear", "com.leaf2android")
    adb("shell", "am", "start", "-W", "-n", "com.leaf2android/.MainActivity")
    wait_text("Waiting for the first photo")
    capture("modern-empty.png")
    tap(tag="open-demo")
    wait_text("OFFLINE DEMO")
    tap(description="JPEG histogram")
    capture("modern-narrow.png")
    tap(tag="page-SETTINGS"); tap(tag="language-zh"); tap(tag="page-SHOOT")
    wait_text("离线演示")
    capture("modern-narrow-zh.png")
    tap(tag="page-SETTINGS"); tap(tag="style-pda"); tap(tag="page-SHOOT")
    capture("pda-narrow-zh.png")
    tap(tag="page-SETTINGS"); tap(tag="language-en"); tap(tag="page-SHOOT")
    capture("pda-narrow.png")
    adb("shell", "wm", "size", "2208x1840")
    wait_text("Session photos")
    capture("pda-wide.png")
    tap(tag="page-SETTINGS"); tap(tag="style-modern"); tap(tag="page-SHOOT")
    wait_text("Session photos")
    capture("modern-wide.png")
    tap(tag="page-SETTINGS"); tap(tag="language-zh"); tap(tag="page-SHOOT")
    wait_text("会话照片")
    capture("modern-wide-zh.png")
    tap(tag="page-SETTINGS"); tap(tag="language-en"); tap(tag="page-SHOOT")
    adb("shell", "wm", "size", "2520x1080")
    wait_text("Session photos")
    capture("modern-landscape.png")
    adb("shell", "wm", "size", "1080x2520")
    tap(tag="page-BACK"); tap(tag="open-laboratory")
    wait_text("Laboratory")
    capture("laboratory-empty-en.png")
    tap(description="Finish and return")
    tap(tag="page-SETTINGS"); tap(tag="language-zh"); tap(tag="page-BACK"); tap(tag="open-laboratory")
    wait_text("实验室")
    capture("laboratory-empty-zh.png")
    tap(description="结束并返回")
    tap(tag="page-SETTINGS"); tap(tag="language-system")
    print("Emulator captures complete; physical folding and One UI were not tested.", flush=True)


if __name__ == "__main__":
    main()
