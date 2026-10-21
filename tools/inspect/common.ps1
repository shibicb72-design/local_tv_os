# Shared helpers for tools/inspect/*.ps1 (dot-source this file).
#
# Every device command in the inspection scripts goes through Invoke-Adb, which
#   1. REFUSES (throws, before adb runs) anything matching $script:AdbDenyPattern,
#   2. logs "yyyy-MM-dd HH:mm:ss adb -s <device> <args>" to the adb log,
#   3. writes the output to <run>\<topic>\NN-<name>.txt.
# The scripts only read. Anything that writes to the projector needs Shibi's OK and
# is done by hand by the operator, not by these scripts.
#
# Overrides (environment variables): XGIMI_ADB, XGIMI_DEVICE, XGIMI_ADB_LOG,
# XGIMI_INSPECT_ROOT. XGIMI_DRYRUN=1 checks the guard and logs but does not call adb.

Set-StrictMode -Version 2

$script:AdbExe = if ($env:XGIMI_ADB) { $env:XGIMI_ADB } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe' }
$script:Device = if ($env:XGIMI_DEVICE) { $env:XGIMI_DEVICE } else { '10.0.0.242:5555' }
$script:AdbLog = if ($env:XGIMI_ADB_LOG) { $env:XGIMI_ADB_LOG } else { 'C:\Users\shibi\cc_xgimi\adb_log.txt' }
$script:InspectRoot = if ($env:XGIMI_INSPECT_ROOT) { $env:XGIMI_INSPECT_ROOT } else { 'C:\Users\shibi\cc_xgimi\inspect' }
$script:TopicDir = $null

# One line, single quotes: tests/test_inspect_scripts.py reads this exact line.
$script:AdbDenyPattern = '\b(install|uninstall)\b|\bsettings\s+(put|delete)\b|\bsetprop\b|\bappops\s+(set|reset)\b|\bpm\s+(disable|disable-user|enable|clear|grant|revoke|hide|unhide|suspend|unsuspend|set-\S+)\b|\bam\s+(start|broadcast|startservice|start-service|force-stop|kill|kill-all|instrument)\b|\binput\s|\bsvc\s|\breboot\b|\bcmd\s.*--set\b|\brm\s|\bmv\s|\bcp\s|\bdd\s|\blogcat\b.*\s-(c|G)\b|\bsm\s+(mount|unmount|format|partition|forget|benchmark|idle-maint|fstrim|set-\S+)\b|\bwm\s|\bcontent\s+(insert|update|delete)\b'

function Assert-ReadOnly {
    param([Parameter(Mandatory)][string]$Command)
    if ($Command -match $script:AdbDenyPattern) {
        throw "Refused (not read-only): adb $Command"
    }
}

# Creates <InspectRoot>\<run>\<topic> and remembers it. -Run lets several scripts share one run folder.
function Initialize-Inspect {
    param([Parameter(Mandatory)][string]$Topic, [string]$Run, [string]$OutRoot)
    if (-not $Run) { $Run = Get-Date -Format 'yyyyMMdd-HHmm' }
    $root = if ($OutRoot) { $OutRoot } else { $script:InspectRoot }
    $script:TopicDir = Join-Path (Join-Path $root $Run) $Topic
    New-Item -ItemType Directory -Force $script:TopicDir | Out-Null
    New-Item -ItemType Directory -Force (Split-Path -Parent $script:AdbLog) | Out-Null
    Write-Host "Output: $($script:TopicDir)"
    return $script:TopicDir
}

# Runs `adb -s <device> <AdbArgs>`. Returns the output lines.
# -Name   file name (without .txt) inside the topic folder, e.g. '01-package'
# -Filter regex applied on this PC (Select-String), with -Context before,after lines
# -Append add to the file instead of replacing it
function Invoke-Adb {
    param(
        [Parameter(Mandatory)][string[]]$AdbArgs,
        [string]$Name,
        [string]$Filter,
        [int[]]$Context,
        [switch]$Append
    )
    $cmd = $AdbArgs -join ' '
    Assert-ReadOnly $cmd
    $line = '{0} adb -s {1} {2}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $script:Device, $cmd
    [IO.File]::AppendAllLines($script:AdbLog, [string[]]@($line))
    if ($env:XGIMI_DRYRUN) {
        Write-Host "[dry-run] $line"
        return @()
    }
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $out = @(& $script:AdbExe -s $script:Device @AdbArgs 2>&1 | ForEach-Object { "$_" })
    } finally {
        $ErrorActionPreference = $prev
    }
    if ($Name) {
        $text = $out
        if ($Filter) {
            $found = if ($Context) {
                $after = if ($Context.Count -gt 1) { $Context[1] } else { $Context[0] }
                $out | Select-String -Pattern $Filter -Context $Context[0], $after
            } else {
                $out | Select-String -Pattern $Filter
            }
            $text = @($found | ForEach-Object { $_.ToString() })
        }
        Write-InspectFile -Name $Name -Lines $text -Append:$Append
    }
    return $out
}

# Writes lines to <topic>\<Name>.txt (no device command involved).
function Write-InspectFile {
    param([Parameter(Mandatory)][string]$Name, [string[]]$Lines = @(), [switch]$Append)
    $path = Join-Path $script:TopicDir "$Name.txt"
    if ($Append) { [IO.File]::AppendAllLines($path, [string[]]@($Lines)) }
    else { [IO.File]::WriteAllLines($path, [string[]]@($Lines)) }
}

function Get-InspectPath {
    param([Parameter(Mandatory)][string]$FileName)
    return (Join-Path $script:TopicDir $FileName)
}
