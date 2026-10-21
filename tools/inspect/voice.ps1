# Op-V0: read-only investigation of how the projector turns its display off/on, plus volume and launch targets
# (docs/plans/next-features.md, 6.4).
#   .\voice.ps1             static part only (reads, pulls candidate APKs to <InspectRoot>\apks)
#   .\voice.ps1 -Observed   adds the observed part. NEEDS SHIBI'S OK: a person uses the remote's power menu and
#                           picks "Display off", which blanks the picture. This script sends no key and changes
#                           no setting; it only runs reads between the steps the person does.
param(
    [string]$Run,
    [string]$OutRoot,
    [switch]$Observed
)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\common.ps1"
$o = Initialize-Inspect -Topic 'voice' -Run $Run -OutRoot $OutRoot
$ui = 'com.xgimi.uiconfiguration'

# static, read-only
Invoke-Adb -Name '01-uiconfiguration-package' -AdbArgs @('shell', 'dumpsys', 'package', $ui) | Out-Null
Invoke-Adb -Name '02-powerlistener-service' -AdbArgs @('shell', 'dumpsys', 'activity', 'services', "$ui/.PowerListenerService") | Out-Null
Invoke-Adb -Name '03-getprop' -Filter 'xgimi|standby|gmpf|power|panel|backlight' -AdbArgs @('shell', 'getprop') | Out-Null
Invoke-Adb -Name '04-standby-stage' -AdbArgs @('shell', 'getprop', 'vendor.xgimi.standby.stage') | Out-Null
Invoke-Adb -Name '05-services' -Filter 'gmpf|xgimi|power|panel' -AdbArgs @('shell', 'service', 'list') | Out-Null
Invoke-Adb -Name '06-dumpsys-power' -AdbArgs @('shell', 'dumpsys', 'power') | Out-Null
Invoke-Adb -Name '07-dumpsys-display' -AdbArgs @('shell', 'dumpsys', 'display') | Out-Null
Invoke-Adb -Name '08-package-strings' -Filter 'standby|displayoff|display_off|screenoff|screen_off|panel_off|backlight|Gmpf' -Context 2, 2 -AdbArgs @('shell', 'dumpsys', 'package') | Out-Null
Invoke-Adb -Name '09-xgimi-packages' -Filter 'xgimi' -AdbArgs @('shell', 'pm', 'list', 'packages', '-f') | Out-Null

# pull (read-only) the candidate APKs for analysis on the PC
$apkDir = Join-Path $script:InspectRoot 'apks'
New-Item -ItemType Directory -Force $apkDir | Out-Null
foreach ($p in 'com.xgimi.uiconfiguration', 'com.xgimi.xgimiservice', 'com.xgimi.misckey', 'com.xgimi.windowsystem') {
    $path = ((Invoke-Adb -AdbArgs @('shell', 'pm', 'path', $p) | Select-Object -First 1) -replace '^package:', '').Trim()
    if ($path) { Invoke-Adb -AdbArgs @('pull', $path, (Join-Path $apkDir "$p.apk")) | Out-Null }
    else { Write-Warning "pm path returned nothing for $p" }
}

# volume, read-only
Invoke-Adb -Name '10-media-session-help' -AdbArgs @('shell', 'cmd', 'media_session', 'help') | Out-Null
Invoke-Adb -Name '11-volume-get' -AdbArgs @('shell', 'cmd', 'media_session', 'volume', '--stream', '3', '--get') | Out-Null
Invoke-Adb -Name '12-audio-stream-music' -Filter 'STREAM_MUSIC' -Context 0, 8 -AdbArgs @('shell', 'dumpsys', 'audio') | Out-Null
Invoke-Adb -Name '13-soundermode-package' -AdbArgs @('shell', 'dumpsys', 'package', 'com.xgimi.soundermodeservice') | Out-Null

# launch targets resolve (read-only)
Invoke-Adb -Name '14-resolve-youtube' -AdbArgs @('shell', 'cmd', 'package', 'resolve-activity', '--brief', '-n', 'com.google.android.youtube.tv/com.google.android.apps.youtube.tv.activity.ShellActivity') | Out-Null
Invoke-Adb -Name '15-resolve-prime' -AdbArgs @('shell', 'cmd', 'package', 'resolve-activity', '--brief', '-n', 'com.amazon.amazonvideo.livingroom/com.amazon.ignition.IgnitionActivity') | Out-Null
Invoke-Adb -Name '16-resolve-hdmi' -AdbArgs @('shell', 'cmd', 'package', 'resolve-activity', '--brief', '-a', 'com.xgimi.action.hdmiPlayer', '-t', 'vnd.android.cursor.item/channel') | Out-Null
Invoke-Adb -Name '17-resolve-home' -AdbArgs @('shell', 'cmd', 'package', 'resolve-activity', '--brief', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME') | Out-Null

Write-Host ''
Write-Host 'On the PC (not a device command): aapt2 dump xmltree --file AndroidManifest.xml <apk> for each pulled APK in'
Write-Host "  $apkDir"
Write-Host 'and jadx (or apktool d) to search for: Display off, standby, Gmpf, PowerListener, setBacklight, panel,'
Write-Host 'vendor.xgimi.standby.stage. Put the decompiled text under the inspect folder so it can be read.'

function Read-Power {
    param([string]$Suffix)
    Invoke-Adb -Name "30-standby-stage$Suffix" -AdbArgs @('shell', 'getprop', 'vendor.xgimi.standby.stage') | Out-Null
    Invoke-Adb -Name "31-power$Suffix" -Filter 'mWakefulness|Display Power|mScreenOn|state=' -AdbArgs @('shell', 'dumpsys', 'power') | Out-Null
    Invoke-Adb -Name "32-display$Suffix" -Filter 'mState|mScreenState|Display Power' -AdbArgs @('shell', 'dumpsys', 'display') | Out-Null
    Invoke-Adb -Name "33-broadcasts$Suffix" -Filter 'xgimi|standby|SCREEN' -Context 0, 3 -AdbArgs @('shell', 'dumpsys', 'activity', 'broadcasts', 'history') | Out-Null
    Invoke-Adb -Name "34-alive$Suffix" -AdbArgs @('shell', 'echo', 'alive') | Out-Null
}

# observed part: a person does the steps, this script only reads
if ($Observed) {
    Write-Host ''
    Write-Host 'OBSERVED PART. Shibi must have OK-ed it: the picture will go dark, as from the power menu.'
    Write-Host 'The script presses nothing and changes nothing; the person does steps 1 to 3 with the remote.'
    $ok = Read-Host 'Type YES to continue'
    if ($ok -cne 'YES') { Write-Host 'Observed part skipped.'; return }

    Write-Host '1. Press Power on the remote. The power menu (Power off / Restart / Display off) appears.'
    Read-Host 'Press Enter when the menu is showing' | Out-Null
    Invoke-Adb -Name '20-top-activity' -Filter 'ACTIVITY|mResumed' -AdbArgs @('shell', 'dumpsys', 'activity', 'top') | Out-Null
    Invoke-Adb -Name '21-window-focus' -Filter 'mCurrentFocus|mFocusedApp' -AdbArgs @('shell', 'dumpsys', 'window') | Out-Null
    Invoke-Adb -Name '22-menu-tree' -AdbArgs @('exec-out', 'uiautomator', 'dump', '/dev/tty') | Out-Null

    Write-Host '2. Pick "Display off" with the remote and wait 10 seconds.'
    Read-Host 'Press Enter after the 10 seconds' | Out-Null
    Read-Power -Suffix '-displayoff'

    Write-Host '3. Wake it the normal way (the key XGIMI documents: a person presses OK or Power on the remote).'
    Read-Host 'Press Enter when the picture is back' | Out-Null
    Read-Power -Suffix '-afterwake'

    # the system log is dumped, not streamed and not cleared
    Invoke-Adb -Name '40-logcat-displayoff' -AdbArgs @('logcat', '-d', '-v', 'threadtime', '-b', 'main,system,events') | Out-Null
    Invoke-Adb -Name '41-logcat-displayoff-filtered' -Filter 'xgimi|standby|Gmpf|PowerManager|DisplayPower' -AdbArgs @('logcat', '-d', '-v', 'threadtime', '-b', 'main,system,events') | Out-Null
}
Write-Host "Done. Raw files: $o"
