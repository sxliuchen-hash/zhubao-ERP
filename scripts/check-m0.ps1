$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)

Write-Host "== Backend tests =="
& (Join-Path $repoRoot "scripts\maven.ps1") -f (Join-Path $repoRoot "backend\pom.xml") test

Write-Host "== Frontend install =="
Push-Location $repoRoot
try {
    $env:npm_config_cache = Join-Path $repoRoot ".npm-cache"
    if (!(Test-Path -LiteralPath "node_modules")) {
        pnpm install --store-dir (Join-Path $repoRoot ".pnpm-store")
    }

    Write-Host "== Frontend tests =="
    pnpm --filter zhubao-erp-frontend test

    Write-Host "== Frontend build =="
    pnpm --filter zhubao-erp-frontend build
}
finally {
    Pop-Location
}

Write-Host "M0 check completed."
