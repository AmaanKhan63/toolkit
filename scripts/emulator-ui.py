"""Small adb UI helper, restricted to this project's isolated ToolkitTest AVD."""
import argparse
import pathlib
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
SERIAL = "emulator-5556"

def adb(*args, binary=False):
    result = subprocess.run(["adb", "-s", SERIAL, *args], capture_output=True, timeout=45, check=True)
    return result.stdout if binary else result.stdout.decode("utf-8", errors="replace").strip()

def tree():
    adb("shell", "uiautomator", "dump", "/sdcard/toolkit-qa-ui.xml")
    return ET.fromstring(adb("shell", "cat", "/sdcard/toolkit-qa-ui.xml"))

def tap(node):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.attrib["bounds"]))
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))

def main():
    sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=["tree", "tap", "screen", "fixtures"])
    parser.add_argument("value", nargs="?")
    args = parser.parse_args()
    avd = adb("shell", "getprop", "ro.boot.qemu.avd_name")
    if avd != "ToolkitTest":
        raise SystemExit("Refusing to operate on an AVD other than ToolkitTest.")
    if args.action == "tree":
        for node in tree().iter("node"):
            if node.get("text") or node.get("content-desc"):
                print(node.get("text"), "|", node.get("content-desc"), "|", node.get("bounds"), "|", node.get("class"))
    elif args.action == "tap":
        for attempt in range(5):
            for node in tree().iter("node"):
                if args.value in (node.get("text"), node.get("content-desc")) or node.get("content-desc", "").startswith(args.value + ","):
                    tap(node)
                    print("Tapped:", args.value)
                    return
            time.sleep(0.5)
        raise SystemExit("Control not found: " + str(args.value))
    elif args.action == "screen":
        name = args.value or "screen"
        if not re.fullmatch(r"[a-z0-9-]+", name):
            raise SystemExit("Use a simple screenshot name.")
        output = ROOT / ".local" / (name + ".png")
        output.parent.mkdir(exist_ok=True)
        output.write_bytes(adb("exec-out", "screencap", "-p", binary=True))
        print(output)
    elif args.action == "fixtures":
        from PIL import Image, ImageDraw
        fixtures = ROOT / ".local" / "qa-fixtures"
        fixtures.mkdir(parents=True, exist_ok=True)
        for index, color in enumerate(["#245BDD", "#217047"], 1):
            image = Image.new("RGB", (600, 800), color)
            draw = ImageDraw.Draw(image)
            draw.rectangle((40, 40, 560, 760), outline="white", width=4)
            draw.text((80, 100), f"TOOLKIT TEST PAGE {index}", fill="white")
            file = fixtures / f"toolkit-test-{index}.png"
            image.save(file)
            adb("push", str(file), f"/sdcard/Download/{file.name}")
            adb("shell", "am", "broadcast", "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE", "-d", f"file:///sdcard/Download/{file.name}")
        print("Two synthetic images copied to ToolkitTest Downloads.")

if __name__ == "__main__":
    main()
