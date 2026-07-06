$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Push-Location $repoRoot
try {
    $env:npm_config_cache = Join-Path $repoRoot ".npm-cache"
    if (!(Test-Path -LiteralPath "node_modules")) {
        pnpm install --store-dir (Join-Path $repoRoot ".pnpm-store")
    }
    pnpm --filter zhubao-erp-frontend dev
}
finally {
    Pop-Location
}
