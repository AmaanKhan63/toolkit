"""Cross-check synthetic OfficeWorkflowTest artifacts on the isolated ToolkitTest AVD.

Run the instrumentation suite first. Requires msoffcrypto-tool and openpyxl in
the host Python environment. Neither is a dependency of the Android app.
"""
import hashlib
import io
import json
import os
from pathlib import Path
import shutil
import subprocess

import msoffcrypto
import openpyxl


ROOT = Path(__file__).resolve().parents[1]
ADB = shutil.which("adb") or str(Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk/platform-tools/adb.exe")
SERIAL = "emulator-5556"


def adb(*args):
    return subprocess.run([ADB, "-s", SERIAL, *args], capture_output=True, timeout=45, check=True).stdout


def main():
    if adb("shell", "getprop", "ro.boot.qemu.avd_name").strip() != b"ToolkitTest":
        raise SystemExit("This check only operates on the isolated ToolkitTest AVD.")
    expected = (ROOT / "app/src/androidTest/assets/office-interop/original.xlsx").read_bytes()
    actual = adb("exec-out", "run-as", "com.amaankhan.toolkit", "cat", "cache/office-interop-test/original.xlsx")
    if expected != actual:
        raise SystemExit("The device artifact is not the known synthetic fixture.")
    encrypted = adb("exec-out", "run-as", "com.amaankhan.toolkit", "cat", "cache/office-interop-test/encrypted.xlsx")
    office = msoffcrypto.OfficeFile(io.BytesIO(encrypted))
    office.load_key(password="toolkit-interop-test", verify_password=True)
    decoded = io.BytesIO()
    office.decrypt(decoded, verify_integrity=True)
    assert decoded.getvalue() == expected, "Native encryption changed the original package bytes"
    workbook = openpyxl.load_workbook(io.BytesIO(decoded.getvalue()))
    assert workbook.active["A2"].value == "Independent fixture"
    assert workbook.active["B2"].value == 42.5
    result = {
        "nativeEncryptionReadBy": "msoffcrypto",
        "passwordVerified": True,
        "integrityVerified": True,
        "exactOriginalBytes": True,
        "cellValuesVerifiedWith": "openpyxl",
        "plaintextSha256": hashlib.sha256(expected).hexdigest(),
        "encryptedSha256": hashlib.sha256(encrypted).hexdigest(),
    }
    output = ROOT / ".local/office-interop"
    output.mkdir(parents=True, exist_ok=True)
    (output / "verification.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result))


if __name__ == "__main__":
    main()
