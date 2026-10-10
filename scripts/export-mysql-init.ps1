param(
    [Parameter(Mandatory = $true)][string]$Destination,
    [string]$Source = (Join-Path (Split-Path -Parent $PSScriptRoot) "backend\src\main\resources\db\mysql")
)

$ErrorActionPreference = "Stop"

# MySQL 入口按文件名字典序执行；只转换交付副本名，不修改已发布迁移。
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
    Copy-Item -LiteralPath $migration.File.FullName -Destination (Join-Path $Destination $name)
}
