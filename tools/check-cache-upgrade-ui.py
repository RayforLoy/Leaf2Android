"""Verify cache migration and compatibility settings on the owned disposable AVD only.

Installs/clears only com.leaf2android on Leaf2Android_API36. No physical device
or camera is used. Requires the signed 0.3.0 and 0.3.1 APKs in dist/.
"""
from pathlib import Path
import hashlib
import json
import runpy
import time

ROOT = Path(__file__).resolve().parents[1]
ui = runpy.run_path(str(ROOT / "tools/capture-emulator-ui.py"))
adb, find, tap, capture = (ui[name] for name in ("adb", "find", "tap", "capture"))
PACKAGE = "com.leaf2android"
OLD = ROOT / "dist/Leaf2Android-0.3.0.apk"
NEW = ROOT / "dist/Leaf2Android-0.3.1.apk"


def scroll_to(**selector):
    for _ in range(16):
        node = find(**selector)
        if node is not None:
            return node
        # Short, slow swipes avoid flinging past a whole lazy-list item between snapshots.
        adb("shell", "input", "swipe", "540", "1720", "540", "1120", "600")
    raise RuntimeError(f"Setting not found: {selector}")


def launch_settings():
    adb("shell", "am", "start", "-W", "-n", PACKAGE + "/.MainActivity")
    tap(tag="page-SETTINGS")


def cache(amount):
    scroll_to(text=str(amount))
    tap(text=str(amount))
    time.sleep(.7)  # Allow SharedPreferences.apply() to persist before stopping the test process.
    assert find(text=f"Local preview cache · {amount} MiB") is not None


def assert_cache(amount):
    scroll_to(text=str(amount))
    assert find(text=f"Local preview cache · {amount} MiB") is not None


def install_old():
    if "package:" + PACKAGE in adb("shell", "pm", "list", "packages", PACKAGE).splitlines():
        adb("uninstall", PACKAGE)
    adb("install", str(OLD))
    launch_settings()


def upgrade():
    adb("shell", "am", "force-stop", PACKAGE)
    adb("install", "-r", str(NEW))
    launch_settings()


def main():
    assert adb("shell", "getprop", "ro.kernel.qemu") == "1"
    assert adb("emu", "avd", "name").splitlines()[0] == "Leaf2Android_API36"
    assert OLD.is_file() and NEW.is_file()
    assert hashlib.sha256(OLD.read_bytes()).hexdigest() == "3089764328f8664f887314ee143a43c82fc79a1a43367fc7c54e87580ac36fbd"
    adb("shell", "wm", "size", "1080x2520")
    adb("shell", "wm", "density", "420")
    checks = {}
    install_old(); cache(256); upgrade(); assert_cache(1024)
    checks["legacy_256_upgrades_to_1024"] = True
    print("Old 256 MiB migrated to 1024 MiB", flush=True)
    cache(256); adb("shell", "am", "force-stop", PACKAGE); launch_settings(); assert_cache(256)
    checks["later_256_choice_survives_restart"] = True
    print("Later 256 MiB choice retained", flush=True)
    install_old(); cache(64); upgrade(); assert_cache(64)
    checks["existing_64_choice_retained"] = True
    print("Existing 64 MiB choice retained", flush=True)
    adb("shell", "pm", "clear", PACKAGE); launch_settings(); assert_cache(1024)
    checks["fresh_default_1024"] = True
    capture("settings-cache-en.png")
    scroll_to(tag="shooting-compatibility-DP67"); tap(tag="shooting-compatibility-DP67")
    time.sleep(.7)
    adb("shell", "am", "force-stop", PACKAGE); launch_settings()
    node = scroll_to(tag="shooting-compatibility-DP67")
    assert node.get("checked") == "true" or node.get("selected") == "true", node.attrib
    checks["explicit_dp67_persists_across_restart"] = True
    capture("settings-compatibility-en.png")
    # Return to the top to change language, then inspect the same persistent radio choice.
    for _ in range(4): adb("shell", "input", "swipe", "540", "700", "540", "2050", "300")
    tap(tag="language-zh")
    node = scroll_to(tag="shooting-compatibility-DP67")
    assert node.get("checked") == "true" or node.get("selected") == "true", node.attrib
    capture("settings-compatibility-zh.png")
    checks["explicit_dp67_survives_language_change"] = True
    report = {"date": "2026-11-14", "avd": "Leaf2Android_API36", "api": 36,
              "physical_devices_used": False, "app_data_reset_only_on_owned_avd": True,
              "old_apk_sha256": hashlib.sha256(OLD.read_bytes()).hexdigest(),
              "new_apk_sha256": hashlib.sha256(NEW.read_bytes()).hexdigest(), "checks": checks}
    target = ROOT / "research/reports/cache-upgrade-verification.json"
    target.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(checks), flush=True)


if __name__ == "__main__":
    main()
