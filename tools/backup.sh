#!/usr/bin/env bash
# Save the TV's package lists, properties, settings and current Home to backup/<date>/ on this
# computer. Read-only on the TV: only `pm list`, `getprop`, `settings list` and `cmd package resolve-activity`.
#
# Usage: tools/backup.sh [serial]        (default 10.0.0.242:5555, or $TV_SERIAL)
set -euo pipefail
SERIAL="${1:-${TV_SERIAL:-10.0.0.242:5555}}"
cd "$(dirname "$0")/.."
ADB="$(command -v adb || true)"
if [ -z "$ADB" ]; then
  for d in "${LOCALAPPDATA:-}/Android/Sdk/platform-tools" "$HOME/Library/Android/sdk/platform-tools" "$HOME/Android/Sdk/platform-tools"; do
    for n in adb adb.exe; do [ -x "$d/$n" ] && ADB="$d/$n" && break 2; done
  done
fi
[ -n "$ADB" ] || { echo "adb not found: add platform-tools to PATH" >&2; exit 1; }
export MSYS_NO_PATHCONV=1

OUT="backup/$(date +%Y-%m-%d)"
mkdir -p "$OUT"
case "$SERIAL" in *:*) "$ADB" connect "$SERIAL" >/dev/null ;; esac

save() { # save <file> <shell command...>
  local f="$1"; shift
  "$ADB" -s "$SERIAL" shell "$@" | tr -d '\r' > "$OUT/$f"
  echo "saved $OUT/$f"
}
save packages.txt pm list packages
save packages-disabled.txt pm list packages -d
save getprop.txt getprop
save settings-global.txt settings list global
save settings-secure.txt settings list secure
save settings-system.txt settings list system
save home-resolve.txt cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME
echo "backup written to $OUT"
