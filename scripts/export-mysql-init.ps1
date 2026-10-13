param(
    [Parameter(Mandatory = $true)][string]$Destination,
    [string]$Source = (Join-Path (Split-Path -Parent $PSScriptRoot) "backend\src\main\resources\db\mysql")
)

$ErrorActionPreference = "Stop"

# MySQL 入口按文件名字典序执行；交付副本可修正已知语法冲突，已发布迁移原文只读。
$migrations = @(Get-ChildItem -LiteralPath $Source -Filter "*.sql" -File | ForEach-Object {
    if ($_.Name -notmatch '^V(\d+)__(.+)\.sql$') {
        throw "无法识别迁移版本：$($_.Name)"
    }
    [PSCustomObject]@{ Version = [int]$Matches[1]; Description = $Matches[2]; File = $_ }
} | Sort-Object Version)
if ($migrations.Count -eq 0) { throw "迁移目录为空：$Source" }
if (@($migrations | Group-Object Version | Where-Object Count -gt 1).Count -gt 0) {
    throw "迁移版本重复，不能生成初始化包"
}
if ((Test-Path -LiteralPath $Destination) -and @(Get-ChildItem -LiteralPath $Destination -Force).Count -gt 0) {
    throw "初始化目标目录必须为空：$Destination"
}
New-Item -ItemType Directory -Force -Path $Destination | Out-Null
$width = [Math]::Max(4, ([string]$migrations[-1].Version).Length)
foreach ($migration in $migrations) {
    $name = "V" + $migration.Version.ToString("D$width") + "__" + $migration.Description + ".sql"
    $target = Join-Path $Destination $name
    if ($migration.Version -eq 36 -and $migration.File.Name -ceq 'V36__m3_finance_operations.sql') {
        # 与 MigrationCatalog 使用相同的定向修正，不修改其他版本或历史源文件。
        $sql = [IO.File]::ReadAllText($migration.File.FullName, [Text.Encoding]::UTF8)
        $sql = [regex]::Replace($sql, '(?<![A-Za-z0-9_`])year_month(?![A-Za-z0-9_`])', '`year_month`')
        [IO.File]::WriteAllText($target, $sql, (New-Object Text.UTF8Encoding($false)))
    } else {
        Copy-Item -LiteralPath $migration.File.FullName -Destination $target
    }
}
