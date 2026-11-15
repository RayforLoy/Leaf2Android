"""Check signed 0.3.1 -> 0.3.2 update and release UI on the owned API 36 AVD only.

Resets only com.leaf2android on Leaf2Android_API36. No physical camera or phone.
Requires both signed APKs in dist/. Historical migration evidence is not replaced.
"""
from pathlib import Path
import hashlib
import json
import runpy
import time

ROOT = Path(__file__).resolve().parents[1]
ui = runpy.run_path(str(ROOT / "tools/capture-emulator-ui.py"))
adb, find, tap, capture = (ui[name] for name in ("adb", "find", "tap", "capture"))
settings = runpy.run_path(str(ROOT / "tools/check-cache-upgrade-ui.py"))
scroll_to, launch_settings, cache, assert_cache = (settings[name] for name in ("scroll_to", "launch_settings", "cache", "assert_cache"))
PACKAGE = "com.leaf2android"
OLD = ROOT / "dist/Leaf2Android-0.3.1.apk"
NEW = ROOT / "dist/Leaf2Android-0.3.2.apk"


def top():
    for _ in range(5):
        adb("shell", "input", "swipe", "540", "700", "540", "2050", "250")


def selected(tag):
    node = scroll_to(tag=tag)
    assert node.get("checked") == "true" or node.get("selected") == "true", node.attrib


def disabled_dialog_button(text):
    # Dialog windows do not inherit testTagsAsResourceId from the activity root.
    root = ui["tree"]()
    parents = {child: parent for parent in root.iter() for child in parent}
    node = next(n for n in root.iter("node") if n.get("text") == text)
    while node is not None:
        if node.get("enabled") == "false":
            return True
        node = parents.get(node)
    return False


def main():
    assert adb("shell", "getprop", "ro.kernel.qemu") == "1"
    assert adb("emu", "avd", "name").splitlines()[0] == "Leaf2Android_API36"
    assert OLD.is_file() and NEW.is_file()
    assert hashlib.sha256(OLD.read_bytes()).hexdigest() == "5b89cb996306a2d69dbb0465c3042a051d2d29437704b5d872e95ad4126b29f7"
    adb("shell", "wm", "size", "1080x2520")
    adb("shell", "wm", "density", "420")
    if "package:" + PACKAGE in adb("shell", "pm", "list", "packages", PACKAGE).splitlines():
        adb("uninstall", PACKAGE)
    adb("install", str(OLD))
    launch_settings()
    tap(tag="style-pda")
    cache(64)
    scroll_to(tag="shooting-compatibility-DP67")
    tap(tag="shooting-compatibility-DP67")
    time.sleep(.7)
    adb("shell", "am", "force-stop", PACKAGE)
    adb("install", "-r", str(NEW))
    launch_settings()
    assert_cache(64)
    selected("shooting-compatibility-DP67")
    top()
    selected("style-pda")
    checks = {"signed_in_place_update": True, "selected_cache_64_preserved": True,
              "dp67_compatibility_preserved": True, "pda_style_preserved": True}
    # Fresh-install behavior is checked separately, only in this disposable emulator.
    adb("shell", "pm", "clear", PACKAGE)
    adb("logcat", "-c")
    launch_settings()
    assert_cache(1024)
    checks["fresh_default_cache_1024"] = True
    top()
    tap(tag="language-zh")
    tap(tag="page-SHOOT")
    tap(tag="open-demo")
    ui["wait_text"]("离线演示")
    assert find(tag="photo-open-actions") is not None
    assert find(tag="photo-open-image-tools").get("enabled") == "false"
    capture("v032-release-modern-zh.png")
    tap(tag="photo-open-actions")
    assert disabled_dialog_button("从后背删除")
    adb("shell", "input", "keyevent", "4")
    checks["offline_demo_cannot_write_back"] = True
    tap(tag="page-SETTINGS"); tap(tag="style-pda"); tap(tag="page-SHOOT")
    capture("v032-release-pda-zh.png")
    tap(tag="page-SETTINGS"); tap(tag="language-en"); tap(tag="style-modern"); tap(tag="page-SHOOT")
    adb("shell", "wm", "size", "2520x1080")
    ui["wait_text"]("Session photos")
    capture("v032-release-landscape-en.png")
    checks["signed_minified_demo_styles_languages_and_landscape"] = True
    adb("shell", "wm", "size", "1080x2520")
    assert "FATAL EXCEPTION" not in adb("logcat", "-d", "-s", "AndroidRuntime:E", "*:S")
    checks["no_fatal_runtime_exception"] = True
    report = {"date": "2026-11-15", "avd": "Leaf2Android_API36", "api": 36,
        "physical_devices_used": False, "app_data_reset_only_on_owned_avd": True,
        "old_version": "0.3.1", "new_version": "0.3.2",
        "old_apk_sha256": hashlib.sha256(OLD.read_bytes()).hexdigest(),
        "new_apk_sha256": hashlib.sha256(NEW.read_bytes()).hexdigest(), "checks": checks,
        "screenshots": ["docs/screenshots/" + name for name in
            ["v032-release-modern-zh.png", "v032-release-pda-zh.png", "v032-release-landscape-en.png"]]}
    (ROOT / "research/reports/release-ui-verification.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(checks), flush=True)


if __name__ == "__main__":
    main()
