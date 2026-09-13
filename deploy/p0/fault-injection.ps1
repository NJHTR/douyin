[CmdletBinding()]
param(
    [ValidateSet('status', 'stop-api-b', 'restore-api-b', 'stop-api-a', 'restore-api-a')]
    [string]$Action = 'status',
    [switch]$AllowDependencyStop
)

$ErrorActionPreference = 'Stop'
$compose = Join-Path $PSScriptRoot 'docker-compose.yml'
$envFile = Join-Path $PSScriptRoot '.env'
if (-not (Test-Path $envFile)) { throw 'Missing deploy/p0/.env; copy .env.example first.' }

function Invoke-Compose([string[]]$Arguments) {
    & docker compose --env-file $envFile -f $compose @Arguments
    if ($LASTEXITCODE -ne 0) { throw "docker compose failed with exit code $LASTEXITCODE" }
}

switch ($Action) {
    'status' {
        Invoke-Compose @('ps')
        Write-Host 'Dependency fault injection is intentionally non-destructive.' -ForegroundColor Cyan
    }
    'stop-api-a' { Invoke-Compose @('stop', 'api-a') }
    'restore-api-a' { Invoke-Compose @('start', 'api-a') }
    'stop-api-b' { Invoke-Compose @('stop', 'api-b') }
    'restore-api-b' { Invoke-Compose @('start', 'api-b') }
}

if ($AllowDependencyStop) {
    Write-Warning 'This stack uses external dependencies; stopping Redis/Kafka/MySQL must be done manually and only in a disposable environment.'
}
