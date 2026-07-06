$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)

Write-Host "== M1 backend tests =="
& (Join-Path $repoRoot "scripts\maven.ps1") -f (Join-Path $repoRoot "backend\pom.xml") test

Write-Host "== M1 frontend install =="
Push-Location $repoRoot
try {
    $env:npm_config_cache = Join-Path $repoRoot ".npm-cache"
    if (!(Test-Path -LiteralPath "node_modules")) {
        pnpm install --store-dir (Join-Path $repoRoot ".pnpm-store")
    }

    Write-Host "== M1 frontend tests =="
    pnpm --filter zhubao-erp-frontend test

    Write-Host "== M1 frontend build =="
    pnpm --filter zhubao-erp-frontend build
}
finally {
    Pop-Location
}

Write-Host "M1 check completed."
