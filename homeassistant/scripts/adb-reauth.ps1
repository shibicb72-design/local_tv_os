# Re-authorises the adb key on the projector after a factory reset, "Revoke USB debugging authorisations",
# or a new PC key. It only runs "adb connect"; the prompt is accepted by a person on the projector.
# Usage: powershell -ExecutionPolicy Bypass -File homeassistant\scripts\adb-reauth.ps1 [-AdbPath <path to adb.exe>]
param(
    [string]$AdbPath = 'adb',
    [string]$Device = '10.0.0.242:5555'
)
$ErrorActionPreference = 'Stop'
$ha = Split-Path -Parent $PSScriptRoot

Write-Host @"
Re-authorisation steps
1. Make sure the key in homeassistant\secrets is the one this PC uses (setup.ps1 copies it from %USERPROFILE%\.android).
2. This script now runs 'adb connect $Device'. The projector shows "Allow USB debugging?".
3. Someone with the remote accepts it and ticks "Always allow". (The operator needs OK before this step.)
4. Then: docker compose restart homeassistant   (in the homeassistant folder)
5. If the projector allows only one adb connection at a time, run 'adb disconnect $Device' after each manual session.
"@

$answer = Read-Host 'Run adb connect now? (y/N)'
if ($answer -ne 'y') { Write-Host 'Stopped; nothing was run.'; exit 0 }

& $AdbPath connect $Device
if ($LASTEXITCODE -ne 0) { throw 'adb connect failed. Check the projector is on and on the LAN.' }
Write-Host 'Accept the prompt on the projector with the remote, then restart Home Assistant (step 4 above).'
