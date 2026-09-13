[CmdletBinding()]
param(
    [string[]]$BaseUrls = @(),
    [int]$TimeoutSeconds = 5
)

$ErrorActionPreference = 'Stop'
$compose = Join-Path $PSScriptRoot 'docker-compose.yml'
$envFile = Join-Path $PSScriptRoot '.env'

function Assert-Result([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "FAIL: $Message" }
    Write-Host "PASS: $Message" -ForegroundColor Green
}

Assert-Result (Test-Path $envFile) 'deploy/p0/.env exists'

if ($BaseUrls.Count -eq 0) {
    $ports = @{}
    foreach ($line in Get-Content $envFile) {
        if ($line -match '^(API_[AB]_PORT)=(\d+)$') { $ports[$matches[1]] = $matches[2] }
    }
    $apiAPort = if ($ports.ContainsKey('API_A_PORT')) { $ports['API_A_PORT'] } else { '9191' }
    $apiBPort = if ($ports.ContainsKey('API_B_PORT')) { $ports['API_B_PORT'] } else { '9192' }
    $BaseUrls = @(
        "http://localhost:$apiAPort",
        "http://localhost:$apiBPort"
    )
}
$serviceIds = @{}
foreach ($service in @('api-a', 'api-b')) {
    $id = (docker compose --env-file $envFile -f $compose ps -q $service 2>$null | Select-Object -First 1).Trim()
    $serviceIds[$service] = $id
}
Assert-Result (($serviceIds['api-a']) -and ($serviceIds['api-b'])) 'two API containers are present'

$normalizedBaseUrls = @($BaseUrls | ForEach-Object { $_ -split ',' } | ForEach-Object {
    $value = $_.Trim()
    if ($value) { $value }
})
Assert-Result ($normalizedBaseUrls.Count -gt 0) 'at least one API base URL is configured'

foreach ($service in @('api-a', 'api-b')) {
    $id = $serviceIds[$service]
    $health = (docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $id 2>$null | Select-Object -First 1).Trim()
    Assert-Result ($health -eq 'healthy' -or $health -eq 'running') "$service is healthy/running"
}

foreach ($url in $normalizedBaseUrls) {
    $uri = "$url/"
    try {
        $response = Invoke-WebRequest -Uri $uri -TimeoutSec $TimeoutSeconds -UseBasicParsing
        Assert-Result ($response.StatusCode -lt 500) "$url responds without 5xx"
    } catch {
        throw "FAIL: $url is unreachable: $($_.Exception.Message)"
    }
}

Write-Host 'P0 smoke checks passed. This is local two-instance validation only.' -ForegroundColor Cyan
