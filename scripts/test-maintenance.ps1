$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$cacheRoot = Join-Path $repoRoot ".cache"
$testRoot = Join-Path $cacheRoot ("maintenance-" + [Guid]::NewGuid().ToString("N"))
$encoding = New-Object System.Text.UTF8Encoding($false)

function Assert-True([bool]$condition, [string]$message) {
    if (-not $condition) { throw $message }
}

New-Item -ItemType Directory -Force -Path $testRoot | Out-Null
try {
    $source = Join-Path $testRoot "sql"
    $target = Join-Path $testRoot "init"
    New-Item -ItemType Directory -Force -Path $source | Out-Null
    foreach ($version in @(1, 2, 7, 10, 19, 100)) {
        [IO.File]::WriteAllText((Join-Path $source "V${version}__m3_test.sql"), "SELECT $version;", $encoding)
    }
    & (Join-Path $PSScriptRoot "export-mysql-init.ps1") -Source $source -Destination $target
    $versions = @(Get-ChildItem -LiteralPath $target -File | Sort-Object Name | ForEach-Object {
        Assert-True ($_.Name -match '^V(\d+)__') "初始化副本缺少版本"
        [int]$Matches[1]
    })
    Assert-True (($versions -join ',') -eq '1,2,7,10,19,100') "初始化文件未按数字版本执行"
    foreach ($file in Get-ChildItem -LiteralPath $source -File) {
        $file.Name -match '^V(\d+)__(.+)$' | Out-Null
        $copy = Join-Path $target ("V" + ([int]$Matches[1]).ToString("D4") + "__" + $Matches[2])
        Assert-True ((Get-FileHash -LiteralPath $file.FullName).Hash -eq (Get-FileHash -LiteralPath $copy).Hash) "历史迁移内容被改变"
    }

    # 真实迁移只允许 V36 的交付副本修正保留字，历史原文与其他副本必须保持不变。
    $realSource = Join-Path $repoRoot 'backend\src\main\resources\db\mysql'
    $realTarget = Join-Path $testRoot 'real-init'
    $v36Source = Join-Path $realSource 'V36__m3_finance_operations.sql'
    $v36Hash = (Get-FileHash -LiteralPath $v36Source).Hash
    & (Join-Path $PSScriptRoot 'export-mysql-init.ps1') -Source $realSource -Destination $realTarget
    foreach ($file in Get-ChildItem -LiteralPath $realSource -Filter '*.sql' -File) {
        $file.Name -match '^V(\d+)__(.+)$' | Out-Null
        $copy = Join-Path $realTarget ('V' + ([int]$Matches[1]).ToString('D4') + '__' + $Matches[2])
        if ($file.Name -ceq 'V36__m3_finance_operations.sql') {
            $converted = [IO.File]::ReadAllText($copy, [Text.Encoding]::UTF8)
            $original = [IO.File]::ReadAllText($file.FullName, [Text.Encoding]::UTF8)
            Assert-True ($converted.Contains('`year_month` CHAR(7)')) 'V36 月份列没有引用'
            Assert-True ($converted.Contains('(customer_id, `year_month`)')) 'V36 月份索引没有引用'
            Assert-True ($converted.Replace('`year_month`', 'year_month') -ceq $original) 'V36 副本包含额外修改'
        } else {
            Assert-True ((Get-FileHash -LiteralPath $file.FullName).Hash -eq (Get-FileHash -LiteralPath $copy).Hash) '非 V36 迁移副本被修改'
        }
    }
    Assert-True ((Get-FileHash -LiteralPath $v36Source).Hash -eq $v36Hash) 'V36 历史原文被改写'
    $rejected = $false
    try { & (Join-Path $PSScriptRoot "export-mysql-init.ps1") -Source $source -Destination $target } catch { $rejected = $true }
    Assert-True $rejected "已有初始化目录被覆盖"
    [IO.File]::WriteAllText((Join-Path $source "V2__duplicate.sql"), "SELECT 2;", $encoding)
    $rejected = $false
    try { & (Join-Path $PSScriptRoot "export-mysql-init.ps1") -Source $source -Destination (Join-Path $testRoot 'duplicate') } catch { $rejected = $true }
    Assert-True $rejected "重复迁移版本未被阻断"

    $invalidPackage = Join-Path $testRoot "invalid-package"
    $invalidSql = Join-Path $invalidPackage "mysql-init"
    New-Item -ItemType Directory -Force -Path $invalidSql | Out-Null
    foreach ($file in @('docker-compose.yml', '.env.example', 'start.ps1', 'start.sh', 'backup-mysql.ps1',
        'backup-mysql.sh', 'verify-restore.ps1', 'verify-restore.sh', 'upgrade-database.ps1', 'upgrade-database.sh',
        'README_DOCKER.md', 'MANIFEST.sha256')) {
        [IO.File]::WriteAllText((Join-Path $invalidPackage $file), '', $encoding)
    }
    foreach ($version in @(2, 19)) {
        [IO.File]::WriteAllText((Join-Path $invalidSql "V${version}__m3_test.sql"), "SELECT $version;", $encoding)
    }
    $rejected = $false
    try {
        & (Join-Path $PSScriptRoot "verify-runtime-package.ps1") -PackagePath $invalidPackage -AllowMissingImages
    } catch {
        $rejected = $_.Exception.Message -match '字典序与版本顺序不一致'
    }
    Assert-True $rejected "结构门禁没有拒绝错序 SQL"

    # 用替身模拟失败退出，验证检查入口不会在某一步失败后继续或误报通过。
    $fakeRepo = Join-Path $testRoot "repo"
    New-Item -ItemType Directory -Force -Path (Join-Path $fakeRepo "scripts"), (Join-Path $fakeRepo "node_modules") | Out-Null
    [IO.File]::WriteAllText((Join-Path $fakeRepo "scripts\maven.ps1"), 'exit 17', $encoding)
    $ErrorActionPreference = "Continue"
    $output = & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "check.ps1") -RepositoryRoot $fakeRepo 2>&1
    $ErrorActionPreference = "Stop"
    Assert-True ($LASTEXITCODE -ne 0) "后端失败没有传播退出码"
    Assert-True (($output -join "`n") -notmatch '项目检查通过|执行前端') "后端失败后仍继续检查"
    Write-Host "维护脚本回归通过：数字排序、V36 定向兼容、历史内容校验、重复版本、覆盖保护、错序门禁和失败退出。"
}
finally {
    $resolvedRoot = [IO.Path]::GetFullPath($testRoot)
    $allowedRoot = [IO.Path]::GetFullPath($cacheRoot).TrimEnd('\') + '\'
    if (-not $resolvedRoot.StartsWith($allowedRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw "测试清理路径越界：$resolvedRoot"
    }
    Remove-Item -LiteralPath $resolvedRoot -Recurse -Force
}
