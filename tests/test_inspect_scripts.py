"""Check that the device inspection scripts in tools/inspect only read.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
INSPECT = ROOT / "tools" / "inspect"
SCRIPTS = ("atmosphere.ps1", "voice.ps1", "storage.ps1", "media.ps1")

LOG_PATH = r"C:\Users\shibi\cc_xgimi\adb_log.txt"
INSPECT_ROOT = r"C:\Users\shibi\cc_xgimi\inspect"

READ_ONLY = [
    "shell dumpsys package com.xgimi.atmosphere",
    "shell pm list packages -f -U com.xgimi.atmosphere",
    "shell pm path com.xgimi.atmosphere",
    "pull /system/app/x.apk C:\\tmp\\x.apk",
    "shell cmd package query-activities --brief -a android.intent.action.MAIN",
    "shell settings get secure screensaver_enabled",
    "shell cmd media_session volume --stream 3 --get",
    "shell sm list-volumes all",
    "shell appops get com.xgimi.filemanager",
    "shell cat /proc/123/stat",
    "shell ps -A -o PID,PPID,USER,RSS,VSZ,TIME,S,NAME",
    "shell getprop vendor.xgimi.standby.stage",
    "exec-out uiautomator dump /dev/tty",
    "logcat -d -v threadtime -b main,system,events",
    "shell echo alive",
]

REFUSED = [
    "install app.apk",
    "shell pm install x.apk",
    "uninstall com.x",
    "shell settings put global x 1",
    "shell setprop a b",
    "shell appops set com.x MANAGE_EXTERNAL_STORAGE allow",
    "shell pm disable-user --user 0 com.x",
    "shell pm clear com.x",
    "shell pm grant com.x android.permission.X",
    "shell am start -n com.x/.A",
    "shell am force-stop com.x",
    "shell am broadcast -a X",
    "shell input keyevent 26",
    "shell svc power stayon true",
    "reboot",
    "shell cmd media_session volume --stream 3 --set 5",
    "shell rm /sdcard/x",
    "shell mv /sdcard/a /sdcard/b",
    "shell cp /sdcard/a /sdcard/b",
    "shell dd if=/dev/zero of=/sdcard/x",
    "logcat -c",
    "shell sm format public:8,1",
]


def deny_pattern():
    text = (INSPECT / "common.ps1").read_text(encoding="utf-8")
    m = re.search(r"^\$script:AdbDenyPattern = '(.*)'\s*$", text, re.M)
    assert m, "common.ps1 has no single-line $script:AdbDenyPattern"
    return re.compile(m.group(1), re.I)


def adb_commands(script):
    """Yield (line number, reconstructed command) for every Invoke-Adb call."""
    lines = (INSPECT / script).read_text(encoding="utf-8").splitlines()
    for number, line in enumerate(lines, 1):
        code = line.split(" #")[0]
        if "Invoke-Adb" not in code or code.lstrip().startswith(("function", "#")):
            continue
        m = re.search(r"-AdbArgs\s+@\((.*)\)", code)
        yield number, code, (m.group(1) if m else None)


class InspectScriptsTest(unittest.TestCase):
    def test_guard_accepts_reads(self):
        pattern = deny_pattern()
        for cmd in READ_ONLY:
            with self.subTest(cmd=cmd):
                self.assertIsNone(pattern.search(cmd))

    def test_guard_refuses_writes(self):
        pattern = deny_pattern()
        for cmd in REFUSED:
            with self.subTest(cmd=cmd):
                self.assertIsNotNone(pattern.search(cmd))

    def test_every_invoke_adb_line_passes_the_denylist(self):
        pattern = deny_pattern()
        for script in SCRIPTS:
            calls = list(adb_commands(script))
            self.assertTrue(calls, script + " makes no Invoke-Adb calls")
            for number, code, args in calls:
                with self.subTest(script=script, line=number):
                    self.assertFalse(code.rstrip().endswith("`"), "keep Invoke-Adb on one line")
                    self.assertIsNotNone(args, "Invoke-Adb needs -AdbArgs @(...) on the same line")
                    tokens = re.findall(r"'([^']*)'|\"([^\"]*)\"|(\$\w+)", args)
                    command = " ".join(a or b or c for a, b, c in tokens)
                    self.assertIsNone(pattern.search(command), command)

    def test_adb_is_only_called_through_invoke_adb(self):
        for script in SCRIPTS:
            text = (INSPECT / script).read_text(encoding="utf-8")
            with self.subTest(script=script):
                self.assertNotRegex(text, r"&\s*\$(script:)?adb\b|\badb\.exe\b|\bAdbExe\b")
                self.assertIn('. "$PSScriptRoot\\common.ps1"', text)

    def test_logger_paths(self):
        text = (INSPECT / "common.ps1").read_text(encoding="utf-8")
        self.assertIn("'" + LOG_PATH + "'", text)
        self.assertIn("'" + INSPECT_ROOT + "'", text)
        self.assertIn("Assert-ReadOnly $cmd", text)

    def test_guard_runs_before_the_log_and_adb(self):
        text = (INSPECT / "common.ps1").read_text(encoding="utf-8")
        body = text[text.index("function Invoke-Adb"):]
        self.assertLess(body.index("Assert-ReadOnly"), body.index("AppendAllLines"))
        self.assertLess(body.index("Assert-ReadOnly"), body.index("& $script:AdbExe"))

    def test_paths_are_made_absolute(self):
        text = (INSPECT / "common.ps1").read_text(encoding="utf-8")
        self.assertIn("(New-Item -ItemType Directory -Force $dir).FullName", text)
        self.assertIn("GetUnresolvedProviderPathFromPSPath($script:AdbLog)", text)

    def test_net_filter_is_uid_exact_and_flags_shared_uid(self):
        text = (INSPECT / "atmosphere.ps1").read_text(encoding="utf-8")
        self.assertIn(r'-Filter "uid=$uid\b"', text)
        self.assertIn("[int]$uid -lt 10000", text)

    def test_build_dirs_are_ignored_and_untracked(self):
        ignore = (ROOT / ".gitignore").read_text(encoding="utf-8").splitlines()
        self.assertIn("launcher/*/build/", ignore)


if __name__ == "__main__":
    unittest.main()
