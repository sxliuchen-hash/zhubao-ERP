param(
    [string]$RepositoryRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = "Stop"

function Assert-CommandSucceeded([string]$step) {
    if ($LASTEXITCODE -ne 0) {
        throw "$step 失败，退出码：$LASTEXITCODE"
    }
}

Push-Location $RepositoryRoot
try {
    Write-Host "执行后端全量测试"
    & (Join-Path $RepositoryRoot "scripts\maven.ps1") -f (Join-Path $RepositoryRoot "backend\pom.xml") test
    Assert-CommandSucceeded "后端测试"

    $env:npm_config_cache = Join-Path $RepositoryRoot ".npm-cache"
    if (!(Test-Path -LiteralPath "node_modules")) {
        pnpm install --store-dir (Join-Path $RepositoryRoot ".pnpm-store")
        Assert-CommandSucceeded "前端依赖安装"
    }

    Write-Host "执行前端全量测试"
    pnpm --filter zhubao-erp-frontend test
    Assert-CommandSucceeded "前端测试"

    Write-Host "执行前端构建"
    pnpm --filter zhubao-erp-frontend build
    Assert-CommandSucceeded "前端构建"
}
finally {
    Pop-Location
}

Write-Host "项目检查通过。"
