# Windows version of backup.sh: save the TV's package lists, properties, settings and current Home
# to backup\<date>\ on this computer. Read-only on the TV.
#
# Usage: tools\backup.ps1 [-Serial 10.0.0.242:5555]
param(
    [string]$Serial = $(if ($env:TV_SERIAL) { $env:TV_SERIAL } else { '10.0.0.242:5555' })
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$adbCmd = Get-Command adb -ErrorAction SilentlyContinue
if ($adbCmd) { $adb = $adbCmd.Source } else { $adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe' }
if (-not (Test-Path $adb)) { throw 'adb not found: add platform-tools to PATH' }

$out = Join-Path $repo ('backup\' + (Get-Date -Format 'yyyy-MM-dd'))
New-Item -ItemType Directory -Force $out | Out-Null
if ($Serial -match ':') { & $adb connect $Serial | Out-Null }

function Save([string]$File, [string[]]$ShellArgs) {
    $lines = & $adb -s $Serial shell @ShellArgs
    if ($LASTEXITCODE -ne 0) { throw "adb failed for: $ShellArgs" }
    [System.IO.File]::WriteAllLines((Join-Path $out $File), [string[]]@($lines), (New-Object System.Text.UTF8Encoding $false))
    Write-Host "saved $out\$File"
}
Save 'packages.txt' @('pm', 'list', 'packages')
Save 'packages-disabled.txt' @('pm', 'list', 'packages', '-d')
Save 'getprop.txt' @('getprop')
Save 'settings-global.txt' @('settings', 'list', 'global')
Save 'settings-secure.txt' @('settings', 'list', 'secure')
Save 'settings-system.txt' @('settings', 'list', 'system')
Save 'home-resolve.txt' @('cmd', 'package', 'resolve-activity', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME')
Write-Host "backup written to $out"
