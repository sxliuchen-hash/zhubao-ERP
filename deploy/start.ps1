$ErrorActionPreference = "Stop"

$deployRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $deployRoot

$manifest = Join-Path $deployRoot "MANIFEST.sha256"
if (Test-Path -LiteralPath $manifest) {
    Write-Host "Verifying runtime package manifest..."
    foreach ($line in Get-Content -LiteralPath $manifest) {
        if ([string]::IsNullOrWhiteSpace($line)) {
            continue
        }
        if ($line -notmatch '^([a-fA-F0-9]{64})\s+(.+)$') {
            throw "Invalid manifest line: $line"
        }
        $expectedHash = $Matches[1].ToLowerInvariant()
        $relativePath = $Matches[2].Replace('/', [System.IO.Path]::DirectorySeparatorChar)
        $targetPath = Join-Path $deployRoot $relativePath
        if (!(Test-Path -LiteralPath $targetPath)) {
            throw "Manifest file missing: $relativePath"
        }
        $actualHash = (Get-FileHash -LiteralPath $targetPath -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actualHash -ne $expectedHash) {
            throw "Manifest hash mismatch: $relativePath"
        }
    }
    Write-Host "Manifest verification passed."
}

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
Write-Host "Existing databases are not upgraded by this startup. Back up first, then run upgrade-database.ps1."
Write-Host "已有数据库不会随本次启动升级。请先备份，再执行 upgrade-database.ps1。"
