# Checks from the Alienware what the internet can reach on the public URL. Read-only HTTP requests; nothing changes.
# Usage: powershell -ExecutionPolicy Bypass -File homeassistant\scripts\check-exposure.ps1 -PublicHost <host>.<tailnet>.ts.net
param(
    [Parameter(Mandatory = $true)][string]$PublicHost
)
$ErrorActionPreference = 'Stop'
$base = "https://$PublicHost"

function Get-Status([string]$Method, [string]$Path) {
    $out = & curl.exe --silent --show-error --max-time 20 --output NUL --write-out '%{http_code}' --request $Method "$base$Path"
    if ($LASTEXITCODE -ne 0) { return "curl-error-$LASTEXITCODE" }
    return $out
}

# Method, path, expected status. /api/ and the websocket must be invisible; only the login and Google paths answer.
$checks = @(
    @{ Method = 'GET';  Path = '/api/';                Expect = '404' },
    @{ Method = 'GET';  Path = '/api/websocket';       Expect = '404' },
    @{ Method = 'GET';  Path = '/api/config';          Expect = '404' },
    @{ Method = 'GET';  Path = '/config';              Expect = '404' },
    @{ Method = 'GET';  Path = '/auth/authorize';      Expect = '200' },
    @{ Method = 'POST'; Path = '/api/google_assistant'; Expect = '401' }
)

$failed = 0
foreach ($c in $checks) {
    $got = Get-Status $c.Method $c.Path
    if ($got -eq $c.Expect) {
        Write-Host ("PASS  {0,-4} {1,-24} {2}" -f $c.Method, $c.Path, $got)
    } else {
        Write-Host ("FAIL  {0,-4} {1,-24} got {2}, expected {3}" -f $c.Method, $c.Path, $got, $c.Expect) -ForegroundColor Red
        $failed++
    }
}

# The ip_ban lockout guard: HA must log the caller's real public address, not a 172.30.0.x container address.
# The rejected request above (POST /api/google_assistant, no token) is logged by HA with the address it saw.
Push-Location (Join-Path $PSScriptRoot '..')
try {
    $haLog = & docker compose logs --since 2m homeassistant 2>&1 | Out-String
} finally {
    Pop-Location
}
$seen = [regex]::Matches($haLog, 'invalid authentication from (\d{1,3}(?:\.\d{1,3}){3}|[0-9a-fA-F:]+)') | ForEach-Object { $_.Groups[1].Value }
if (-not $seen) {
    Write-Host 'FAIL  HA log shows no rejected request from the check above; cannot confirm the real client address.' -ForegroundColor Red
    $failed++
} elseif ($seen | Where-Object { $_ -like '172.30.0.*' }) {
    Write-Host ('FAIL  HA logged a container address ({0}) as the caller; ip_ban would lock out every Google request. Check trusted_proxies and the Caddyfile servers block.' -f (($seen | Select-Object -Unique) -join ', ')) -ForegroundColor Red
    $failed++
} else {
    Write-Host ('PASS  HA logs the real client address ({0})' -f (($seen | Select-Object -Unique) -join ', '))
}

if ($failed -gt 0) {
    Write-Host "$failed check(s) failed. Do NOT configure Google yet; look at caddy/Caddyfile and docker compose logs caddy." -ForegroundColor Red
    exit 1
}
Write-Host 'All exposure checks passed.'
