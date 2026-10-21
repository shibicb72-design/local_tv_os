# Op-P0: read-only codec, audio and display investigation for Player (docs/plans/next-features.md, section 8).
param(
    [string]$Run,
    [string]$OutRoot
)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\common.ps1"
$o = Initialize-Inspect -Topic 'media' -Run $Run -OutRoot $OutRoot

# media_codecs*.xml: list each etc folder, then cat every codec/profile file found
$n = 1
foreach ($dir in '/vendor/etc', '/odm/etc', '/system/etc') {
    $tag = $dir.Trim('/').Replace('/', '-')
    $files = Invoke-Adb -Name ('01-ls-{0}' -f $tag) -AdbArgs @('shell', 'ls', $dir)
    foreach ($f in $files) {
        $f = $f.Trim()
        if ($f -match '^media_(codecs|profiles).*\.xml$|^audio_policy.*\.(xml|conf)$') {
            Invoke-Adb -Name ('02-{0:00}-{1}-{2}' -f $n, $tag, $f) -AdbArgs @('shell', 'cat', "$dir/$f") | Out-Null
            $n++
        }
    }
}
if ($n -eq 1) { Write-Warning 'No media_codecs*.xml found in /vendor/etc, /odm/etc or /system/etc' }

Invoke-Adb -Name '03-media-extractor' -AdbArgs @('shell', 'dumpsys', 'media.extractor') | Out-Null
Invoke-Adb -Name '04-audio' -Filter 'surround|encoded|passthrough|STREAM_MUSIC' -AdbArgs @('shell', 'dumpsys', 'audio') | Out-Null
Invoke-Adb -Name '05-encoded-surround' -AdbArgs @('shell', 'settings', 'get', 'global', 'encoded_surround_output') | Out-Null
Invoke-Adb -Name '06-display' -Filter 'mSupportedModes|HdrCapabilities|mActiveModeId' -AdbArgs @('shell', 'dumpsys', 'display') | Out-Null
Invoke-Adb -Name '07-view-mkv' -AdbArgs @('shell', 'cmd', 'package', 'query-activities', '--brief', '-a', 'android.intent.action.VIEW', '-t', 'video/x-matroska') | Out-Null
Write-Host "Done. Raw files: $o"
