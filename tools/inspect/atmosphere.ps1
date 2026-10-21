# Op-A: read-only inspection of com.xgimi.atmosphere ("XGIMI Wall"). Plan: docs/plans/next-features.md 7.2.
# Leave the projector idle on Home (no USB or HDMI activity). Takes about 70 minutes.
#   .\atmosphere.ps1                       full run: 13 samples, 5 minutes apart
#   .\atmosphere.ps1 -Samples 2 -IntervalSeconds 10     quick check of the script
#   .\atmosphere.ps1 -Active               optional: 5 minutes while Shibi has XGIMI Wall on screen (needs OK)
param(
    [string]$Run,
    [string]$OutRoot,
    [int]$Samples = 13,
    [int]$IntervalSeconds = 300,
    [switch]$Active
)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\common.ps1"
$pkg = 'com.xgimi.atmosphere'
$o = Initialize-Inspect -Topic 'atmosphere' -Run $Run -OutRoot $OutRoot

# identity, manifest, size
Invoke-Adb -Name '01-package' -AdbArgs @('shell', 'dumpsys', 'package', $pkg) | Out-Null
$uidLines = Invoke-Adb -Name '02-path-uid' -AdbArgs @('shell', 'pm', 'list', 'packages', '-f', '-U', $pkg)
$uid = $null
$m = [regex]::Match(($uidLines -join "`n"), 'uid:(\d+)')
if ($m.Success) { $uid = $m.Groups[1].Value } else { Write-Warning 'uid not found; network samples are skipped' }
$pathLines = Invoke-Adb -AdbArgs @('shell', 'pm', 'path', $pkg)
$apk = (($pathLines | Select-Object -First 1) -replace '^package:', '').Trim()
if ($apk) {
    Invoke-Adb -Name '03-apk-size' -AdbArgs @('shell', 'ls', '-l', $apk) | Out-Null
    Invoke-Adb -AdbArgs @('pull', $apk, (Get-InspectPath 'atmosphere.apk')) | Out-Null
} else {
    Write-Warning 'pm path returned nothing: package missing? APK size and pull skipped'
}

# triggers: launcher entry, dreams, receivers, alarms, jobs
Invoke-Adb -Name '04-launchers' -AdbArgs @('shell', 'cmd', 'package', 'query-activities', '--brief', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.LEANBACK_LAUNCHER') | Out-Null
Invoke-Adb -Name '05-dreams' -AdbArgs @('shell', 'cmd', 'package', 'query-services', '--brief', '-a', 'android.service.dreams.DreamService') | Out-Null
Invoke-Adb -Name '06-screensaver' -AdbArgs @('shell', 'settings', 'get', 'secure', 'screensaver_components') | Out-Null
Invoke-Adb -Name '06-screensaver' -Append -AdbArgs @('shell', 'settings', 'get', 'secure', 'screensaver_enabled') | Out-Null
Invoke-Adb -Name '06-screensaver' -Append -AdbArgs @('shell', 'settings', 'get', 'secure', 'screensaver_activate_on_sleep') | Out-Null
Invoke-Adb -Name '07-dreams-state' -AdbArgs @('shell', 'dumpsys', 'dreams') | Out-Null
Invoke-Adb -Name '08-receivers' -Filter 'atmosphere' -Context 3, 6 -AdbArgs @('shell', 'dumpsys', 'activity', 'broadcasts') | Out-Null
Invoke-Adb -Name '09-alarms' -Filter 'atmosphere' -Context 1, 4 -AdbArgs @('shell', 'dumpsys', 'alarm') | Out-Null
Invoke-Adb -Name '10-jobs' -Filter 'atmosphere' -Context 1, 6 -AdbArgs @('shell', 'dumpsys', 'jobscheduler') | Out-Null
Invoke-Adb -Name '11-services' -AdbArgs @('shell', 'dumpsys', 'activity', 'services', $pkg) | Out-Null
Invoke-Adb -Name '12-procstate' -Filter 'atmosphere' -Context 2, 12 -AdbArgs @('shell', 'dumpsys', 'activity', 'processes') | Out-Null
Invoke-Adb -Name '13-usage' -Filter 'atmosphere' -Context 0, 2 -AdbArgs @('shell', 'dumpsys', 'usagestats') | Out-Null
Invoke-Adb -Name '14-cpu-since-boot' -AdbArgs @('shell', 'dumpsys', 'batterystats', '--charged', $pkg) | Out-Null
Invoke-Adb -Name '15-logcat' -Filter 'atmosphere|XgimiWall|Wall' -AdbArgs @('logcat', '-d', '-v', 'threadtime') | Out-Null
Invoke-Adb -Name '16-clk-tck' -AdbArgs @('shell', 'getconf', 'CLK_TCK') | Out-Null

# samples: Samples times, IntervalSeconds apart (60 minutes by default)
function Get-Sample {
    param([string]$Suffix, [bool]$WithGfx)
    $t = Get-Date -Format 'HH:mm:ss'
    $pidText = ((Invoke-Adb -AdbArgs @('shell', 'pidof', $pkg)) -join ' ').Trim()
    $procId = ($pidText -split '\s+')[0]
    Write-InspectFile -Name "20-samples$Suffix" -Append -Lines @("== $t pid=$procId")
    Invoke-Adb -Name "20-samples$Suffix" -Append -Filter 'atmosphere' -AdbArgs @('shell', 'ps', '-A', '-o', 'PID,PPID,USER,RSS,VSZ,TIME,S,NAME') | Out-Null
    Invoke-Adb -Name "21-top$Suffix" -Append -AdbArgs @('shell', 'top', '-b', '-n', '1', '-m', '15', '-o', 'PID,USER,%CPU,RES,NAME') | Out-Null
    if ($procId -match '^\d+$') {
        Invoke-Adb -Name "22-stat$Suffix" -Append -AdbArgs @('shell', 'cat', "/proc/$procId/stat") | Out-Null
        Invoke-Adb -Name "23-meminfo$Suffix" -Append -AdbArgs @('shell', 'dumpsys', 'meminfo', $pkg) | Out-Null
        if ($WithGfx) {
            Invoke-Adb -Name "25-gfxinfo$Suffix" -Append -AdbArgs @('shell', 'dumpsys', 'gfxinfo', $pkg) | Out-Null
        }
    }
    if ($uid) {
        if ([int]$uid -lt 10000) {
            Write-InspectFile -Name "24-net$Suffix" -Append -Lines @("uid $uid is a shared system uid: network use cannot be pinned on this app")
        }
        Invoke-Adb -Name "24-net$Suffix" -Append -Filter "uid=$uid\b" -Context 0, 6 -AdbArgs @('shell', 'dumpsys', 'netstats', 'detail') | Out-Null
    }
}

for ($i = 0; $i -lt $Samples; $i++) {
    Write-Host "Sample $($i + 1) of $Samples"
    Get-Sample -Suffix '' -WithGfx $false
    if ($i -lt ($Samples - 1)) { Start-Sleep -Seconds $IntervalSeconds }
}

# Optional active cost. Needs Shibi's OK: he opens "XGIMI Wall" with the remote. This script never launches it.
if ($Active) {
    $answer = Read-Host 'Is XGIMI Wall on screen now (opened by Shibi with the remote)? Type YES to sample for 5 minutes'
    if ($answer -ceq 'YES') {
        for ($i = 0; $i -lt 5; $i++) {
            Write-Host "Active sample $($i + 1) of 5"
            Get-Sample -Suffix '-active' -WithGfx $true
            if ($i -lt 4) { Start-Sleep -Seconds 60 }
        }
    } else {
        Write-Host 'Active run skipped.'
    }
}

# On the PC only (no device): aapt2 and tar read the pulled APK.
$apkFile = Get-InspectPath 'atmosphere.apk'
if (Test-Path $apkFile) {
    $aapt2 = Get-Command aapt2 -ErrorAction SilentlyContinue
    if (-not $aapt2) {
        $aapt2 = Get-ChildItem (Join-Path $env:LOCALAPPDATA 'Android\Sdk\build-tools') -Recurse -Filter 'aapt2.exe' -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending | Select-Object -First 1
    }
    if ($aapt2) {
        $aaptPath = if ($aapt2.PSObject.Properties['Source']) { $aapt2.Source } else { $aapt2.FullName }
        & $aaptPath dump badging $apkFile | Set-Content (Get-InspectPath '30-badging.txt') -Encoding UTF8
        & $aaptPath dump xmltree --file AndroidManifest.xml $apkFile | Set-Content (Get-InspectPath '31-manifest.txt') -Encoding UTF8
    } else {
        Write-Warning "aapt2 not found. Run by hand: aapt2 dump badging '$apkFile'"
    }
    & tar -tvf $apkFile | Set-Content (Get-InspectPath '32-apk-files.txt') -Encoding UTF8
}
Write-Host "Done. Raw files: $o"
