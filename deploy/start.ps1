$ErrorActionPreference = "Stop"

$deployRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $deployRoot

if (!(Test-Path -LiteralPath ".env")) {
    Copy-Item -LiteralPath ".env.example" -Destination ".env"
    Write-Host "Created .env from .env.example. Review passwords before production use."
}

$imageTar = Get-ChildItem -LiteralPath "images" -Filter "*.tar" -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if ($imageTar) {
    Write-Host "Loading Docker images from $($imageTar.FullName)..."
    docker load -i $imageTar.FullName
}

docker compose --env-file .env -f docker-compose.yml up -d
docker compose --env-file .env -f docker-compose.yml ps
