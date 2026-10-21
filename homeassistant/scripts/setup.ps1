# Prepares the voice-control stack on the Alienware. Writes only files under homeassistant/; sends nothing to the projector.
# Run from anywhere: powershell -ExecutionPolicy Bypass -File homeassistant\scripts\setup.ps1
$ErrorActionPreference = 'Stop'
$ha = Split-Path -Parent $PSScriptRoot

# 1. Docker must be installed and its engine running.
& docker version --format '{{.Server.Version}}' | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Docker is not running. Start Docker Desktop (WSL2) and run this script again.' }
Write-Host 'Docker: OK'

# 2. secrets/ with the Platform-Tools adb key the projector already trusts (copied, never regenerated).
$secrets = Join-Path $ha 'secrets'
New-Item -ItemType Directory -Force -Path $secrets | Out-Null
$keyDir = Join-Path $env:USERPROFILE '.android'
foreach ($name in 'adbkey', 'adbkey.pub') {
    $src = Join-Path $keyDir $name
    $dst = Join-Path $secrets $name
    if (Test-Path $dst) { Write-Host "$name already in secrets/: kept"; continue }
    if (-not (Test-Path $src)) { throw "$src not found. Run adb once on this PC against the projector first, so the key exists and is trusted." }
    Copy-Item $src $dst
    Write-Host "$name copied to secrets/"
}

# 3. .env from the example, asking for the Tailscale auth key and the time zone.
$envFile = Join-Path $ha '.env'
if (Test-Path $envFile) {
    Write-Host '.env already exists: kept'
} else {
    $key = Read-Host 'Tailscale auth key (single-use, tagged tag:ha, from the Tailscale admin console)'
    if ($key -notlike 'tskey-*') { throw 'That does not look like a Tailscale auth key (it starts with tskey-).' }
    $tz = Read-Host 'IANA time zone (for example Europe/Berlin)'
    if ([string]::IsNullOrWhiteSpace($tz)) { throw 'A time zone is required.' }
    $lines = Get-Content (Join-Path $ha '.env.example') | ForEach-Object {
        if ($_ -like 'TS_AUTHKEY=*') { "TS_AUTHKEY=$key" }
        elseif ($_ -like 'TZ=*') { "TZ=$tz" }
        else { $_ }
    }
    Set-Content -Path $envFile -Value $lines -Encoding ascii
    Write-Host '.env written (gitignored)'
}

# 4. config/secrets.yaml from the example, if missing.
$secretsYaml = Join-Path $ha 'config\secrets.yaml'
if (Test-Path $secretsYaml) {
    Write-Host 'config\secrets.yaml already exists: kept'
} else {
    Copy-Item (Join-Path $ha 'config\secrets.yaml.example') $secretsYaml
    Write-Host 'config\secrets.yaml created: put your google_project_id in it (RUNBOOK step 7)'
}

Write-Host 'Done. Next: RUNBOOK step 3, then docker compose up -d in the homeassistant folder.'
