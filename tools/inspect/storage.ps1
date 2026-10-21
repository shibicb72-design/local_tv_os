# Op-F0: read-only storage investigation for Files (docs/plans/next-features.md, section 8).
# Insert a USB stick first (FAT32; exFAT and NTFS too if you have them).
param(
    [string]$Run,
    [string]$OutRoot
)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\common.ps1"
$o = Initialize-Inspect -Topic 'storage' -Run $Run -OutRoot $OutRoot
$fm = 'com.xgimi.filemanager'

$vols = Invoke-Adb -Name '01-volumes' -AdbArgs @('shell', 'sm', 'list-volumes', 'all')
Invoke-Adb -Name '02-disks' -AdbArgs @('shell', 'sm', 'list-disks') | Out-Null
Invoke-Adb -Name '03-dumpsys-mount' -AdbArgs @('shell', 'dumpsys', 'mount') | Out-Null
Invoke-Adb -Name '04-df' -AdbArgs @('shell', 'df', '-h') | Out-Null
Invoke-Adb -Name '05-mount' -Filter 'storage' -AdbArgs @('shell', 'mount') | Out-Null
Invoke-Adb -Name '06-ls-storage' -AdbArgs @('shell', 'ls', '-la', '/storage') | Out-Null
# one listing per volume uuid found in `sm list-volumes all` (XXXX-XXXX or a longer uuid)
$uuids = @()
foreach ($l in $vols) {
    if ($l -match '\s([0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}|[0-9A-Fa-f]{8}(-[0-9A-Fa-f]{4}){3}-[0-9A-Fa-f]{12})\s*$') {
        $uuids += $Matches[1]
    }
}
foreach ($u in ($uuids | Select-Object -Unique)) {
    Invoke-Adb -Name "07-ls-$u" -AdbArgs @('shell', 'ls', '-la', "/storage/$u") | Out-Null
}
if (-not $uuids) { Write-Warning 'No removable volume uuid found in 01-volumes.txt: is a USB stick inserted?' }
Invoke-Adb -Name '08-fuse' -AdbArgs @('shell', 'getprop', 'persist.sys.fuse') | Out-Null
Invoke-Adb -Name '09-documentsui' -Filter 'documentsui' -AdbArgs @('shell', 'pm', 'list', 'packages') | Out-Null

# do the system screens Files might open resolve?
Invoke-Adb -Name '10-open-document-tree' -AdbArgs @('shell', 'cmd', 'package', 'resolve-activity', '--brief', '-a', 'android.intent.action.OPEN_DOCUMENT_TREE') | Out-Null
Invoke-Adb -Name '11-all-files-access' -AdbArgs @('shell', 'cmd', 'package', 'resolve-activity', '--brief', '-a', 'android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION') | Out-Null
Invoke-Adb -Name '12-app-all-files-access' -AdbArgs @('shell', 'cmd', 'package', 'resolve-activity', '--brief', '-a', 'android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION', '-d', "package:$fm") | Out-Null

# the XGIMI file manager as a reference
Invoke-Adb -Name '13-filemanager-package' -AdbArgs @('shell', 'dumpsys', 'package', $fm) | Out-Null
Invoke-Adb -Name '14-filemanager-appops' -AdbArgs @('shell', 'appops', 'get', $fm) | Out-Null

# who opens media
foreach ($t in 'video/*', 'audio/*', 'image/*') {
    $n = $t.Replace('/*', '')
    Invoke-Adb -Name "15-view-$n" -AdbArgs @('shell', 'cmd', 'package', 'query-activities', '--brief', '-a', 'android.intent.action.VIEW', '-t', $t) | Out-Null
}
Write-Host "Done. Raw files: $o"
