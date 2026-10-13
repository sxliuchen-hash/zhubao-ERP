param(
    [string]$EnvFile = ".env",
    [string]$ComposeFile = "docker-compose.yml",
    [string]$BackupName,
    [string]$ExportDir = "runtime-backups",
    [switch]$UploadCos,
    [string]$CosPrefix,
    [switch]$Help
)

$ErrorActionPreference = "Stop"

if ($Help) {
    Write-Host "Create a MySQL backup from the Docker Compose mysql service and optionally upload it to Tencent COS."
    Write-Host ""
    Write-Host "Usage:"
    Write-Host "  powershell -ExecutionPolicy Bypass -File .\backup-mysql.ps1"
    Write-Host "  powershell -ExecutionPolicy Bypass -File .\backup-mysql.ps1 -UploadCos -CosPrefix prod/mysql/"
    Write-Host ""
    Write-Host "Options:"
    Write-Host "  -EnvFile      Compose env file. Default: .env"
    Write-Host "  -ComposeFile  Compose file. Default: docker-compose.yml"
    Write-Host "  -BackupName   Optional safe backup base name, without extension."
    Write-Host "  -ExportDir    Host directory for exported backup files. Default: runtime-backups"
    Write-Host "  -UploadCos    Upload exported .sql/.sha256 files with coscmd."
    Write-Host "  -CosPrefix    COS object prefix. Defaults to ZHUBAO_COS_BACKUP_PREFIX from .env."
    Write-Host "  -Help         Show this help."
    exit 0
}

$deployRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $deployRoot

function Read-DotEnvValue([string]$path, [string]$key) {
    if (-not (Test-Path -LiteralPath $path)) {
        return $null
    }
    foreach ($line in Get-Content -LiteralPath $path) {
        if ($line -match "^\s*$([regex]::Escape($key))\s*=\s*(.*)\s*$") {
            return $Matches[1].Trim().Trim('"').Trim("'")
        }
    }
    return $null
}

function Assert-SafeName([string]$name, [string]$label) {
    if ($name -notmatch '^[A-Za-z0-9_.-]+$') {
        throw "$label contains unsafe characters. Allowed: A-Z a-z 0-9 _ . -"
    }
}

if (-not (Test-Path -LiteralPath $EnvFile)) {
    if (Test-Path -LiteralPath ".env.example") {
        Copy-Item -LiteralPath ".env.example" -Destination $EnvFile
        Write-Host "Created $EnvFile from .env.example. Review passwords before production use."
    } else {
        throw "Env file not found: $EnvFile"
    }
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backupBase = if ([string]::IsNullOrWhiteSpace($BackupName)) {
    "zhubao_erp_mysql_$timestamp"
} else {
    $BackupName
}
Assert-SafeName $backupBase "BackupName"

$remoteSql = "$backupBase.sql"
$remoteSha = "$remoteSql.sha256"

$backupCommand = @'
set -eu
target="/backups/$BACKUP_NAME"
dump_stderr="$(mktemp "$target.stderr.XXXXXX")"
trap 'rm -f "$dump_stderr"' 0
dump_status=0
mysqldump --no-tablespaces --single-transaction --routines --triggers -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" > "$target" 2> "$dump_stderr" || dump_status=$?
cat "$dump_stderr" >&2
# 某些版本的表空间权限错误仍返回零，不能将这种导出标记为成功。
if [ "$dump_status" -ne 0 ] || grep -E "PROCESS privilege.*tablespaces" "$dump_stderr" >/dev/null; then
  rm -f "$target" "$target.sha256"
  [ "$dump_status" -ne 0 ] || dump_status=1
  exit "$dump_status"
fi
sha256sum "$target" > "$target.sha256"
ls -lh "$target" "$target.sha256"
'@

Write-Host "== Create MySQL backup in Docker volume =="
& docker compose --env-file $EnvFile -f $ComposeFile exec -T -e "BACKUP_NAME=$remoteSql" mysql sh -c $backupCommand
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

New-Item -ItemType Directory -Force -Path $ExportDir | Out-Null
$exportRoot = (Resolve-Path -LiteralPath $ExportDir).Path
$localSql = Join-Path $exportRoot $remoteSql
$localSha = Join-Path $exportRoot $remoteSha

Write-Host "== Export backup files to host =="
& docker cp "zhubao-erp-mysql:/backups/$remoteSql" $localSql
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
& docker cp "zhubao-erp-mysql:/backups/$remoteSha" $localSha
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

$expectedHash = ((Get-Content -LiteralPath $localSha | Select-Object -First 1) -split '\s+')[0].ToLowerInvariant()
$actualHash = (Get-FileHash -LiteralPath $localSql -Algorithm SHA256).Hash.ToLowerInvariant()
if ($expectedHash -ne $actualHash) {
    throw "Exported backup checksum mismatch: $localSql"
}
Write-Host "Backup checksum verified: $localSql"

if ($UploadCos) {
    $resolvedPrefix = if ([string]::IsNullOrWhiteSpace($CosPrefix)) {
        Read-DotEnvValue $EnvFile "ZHUBAO_COS_BACKUP_PREFIX"
    } else {
        $CosPrefix
    }
    if ([string]::IsNullOrWhiteSpace($resolvedPrefix)) {
        throw "CosPrefix is required when -UploadCos is used and ZHUBAO_COS_BACKUP_PREFIX is empty."
    }
    $resolvedPrefix = $resolvedPrefix.TrimStart("/")
    if (-not $resolvedPrefix.EndsWith("/")) {
        $resolvedPrefix += "/"
    }
    $cosPathSql = "$resolvedPrefix$remoteSql"
    $cosPathSha = "$resolvedPrefix$remoteSha"

    Write-Host "== Upload backup files to Tencent COS with coscmd =="
    & coscmd upload $localSql $cosPathSql
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
    & coscmd upload $localSha $cosPathSha
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
    Write-Host "COS upload completed: $cosPathSql"
}

Write-Host "Backup completed."
Write-Host "Container path: /backups/$remoteSql"
Write-Host "Host export: $localSql"
