param(
    [string]$BackupFile,
    [string]$EnvFile = ".env",
    [string]$ComposeFile = "docker-compose.yml",
    [string]$RestoreDatabase,
    [int]$MinimumTableCount = 1,
    [switch]$KeepDatabase,
    [switch]$Help
)

$ErrorActionPreference = "Stop"

if ($Help) {
    Write-Host "Verify that a MySQL backup can be restored into a temporary database without touching production data."
    Write-Host ""
    Write-Host "Usage:"
    Write-Host "  powershell -ExecutionPolicy Bypass -File .\verify-restore.ps1 -BackupFile .\runtime-backups\zhubao_erp_mysql_20260707-090000.sql"
    Write-Host ""
    Write-Host "Options:"
    Write-Host "  -BackupFile          Backup .sql file exported by backup-mysql.ps1."
    Write-Host "  -EnvFile             Compose env file. Default: .env"
    Write-Host "  -ComposeFile         Compose file. Default: docker-compose.yml"
    Write-Host "  -RestoreDatabase     Optional temporary database name."
    Write-Host "  -MinimumTableCount   Minimum restored table count. Default: 1"
    Write-Host "  -KeepDatabase        Keep the temporary database for manual inspection."
    Write-Host "  On success, writes verify-restore-<timestamp>.log next to the backup file and copies it into Docker /backups for Operations Center visibility."
    Write-Host "  -Help                Show this help."
    exit 0
}

$deployRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $deployRoot

function Assert-SafeName([string]$name, [string]$label) {
    if ($name -notmatch '^[A-Za-z0-9_]+$') {
        throw "$label contains unsafe characters. Allowed: A-Z a-z 0-9 _"
    }
}

if ([string]::IsNullOrWhiteSpace($BackupFile)) {
    throw "Missing required parameter: -BackupFile"
}
if (-not (Test-Path -LiteralPath $BackupFile -PathType Leaf)) {
    throw "Backup file not found: $BackupFile"
}
if ($MinimumTableCount -lt 1) {
    throw "MinimumTableCount must be greater than 0."
}

$backupPath = (Resolve-Path -LiteralPath $BackupFile).Path
$shaPath = "$backupPath.sha256"
if (Test-Path -LiteralPath $shaPath -PathType Leaf) {
    $expectedHash = ((Get-Content -LiteralPath $shaPath | Select-Object -First 1) -split '\s+')[0].ToLowerInvariant()
    $actualHash = (Get-FileHash -LiteralPath $backupPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($expectedHash -ne $actualHash) {
        throw "Backup checksum mismatch: $backupPath"
    }
    Write-Host "Backup checksum verified: $backupPath"
} else {
    Write-Host "Backup checksum sidecar not found; continuing without checksum verification."
}

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$restoreDb = if ([string]::IsNullOrWhiteSpace($RestoreDatabase)) {
    "zhubao_restore_check_$timestamp"
} else {
    $RestoreDatabase
}
Assert-SafeName $restoreDb "RestoreDatabase"

$remoteSql = "/tmp/$restoreDb.sql"
Write-Host "== Copy backup into MySQL container =="
& docker cp $backupPath "zhubao-erp-mysql:$remoteSql"
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

$restoreCommand = @'
set -eu
mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "DROP DATABASE IF EXISTS \`$RESTORE_DB\`; CREATE DATABASE \`$RESTORE_DB\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$RESTORE_DB" < "$RESTORE_SQL"
table_count="$(mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N "$RESTORE_DB" -e "SHOW TABLES;" | wc -l | tr -d " ")"
echo "Restored table count: $table_count"
if [ "$table_count" -lt "$MINIMUM_TABLE_COUNT" ]; then
  echo "Restored table count is below expected minimum." >&2
  exit 1
fi
if [ "$KEEP_DATABASE" != "true" ]; then
  mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "DROP DATABASE IF EXISTS \`$RESTORE_DB\`;"
  rm -f "$RESTORE_SQL"
fi
'@

Write-Host "== Restore backup into temporary database: $restoreDb =="
& docker compose --env-file $EnvFile -f $ComposeFile exec -T `
    -e "RESTORE_DB=$restoreDb" `
    -e "RESTORE_SQL=$remoteSql" `
    -e "MINIMUM_TABLE_COUNT=$MinimumTableCount" `
    -e "KEEP_DATABASE=$($KeepDatabase.IsPresent.ToString().ToLowerInvariant())" `
    mysql sh -c $restoreCommand
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host "Restore verification passed: $restoreDb"
if ($KeepDatabase) {
    Write-Host "Temporary restore database was kept for inspection."
} else {
    Write-Host "Temporary restore database was removed."
}

$markerName = "verify-restore-$timestamp.log"
$markerLocal = Join-Path -Path (Split-Path -Parent $backupPath) -ChildPath $markerName
$markerContent = @(
    "status=passed",
    "checkedAt=$(Get-Date -Format s)",
    "backupFile=$backupPath",
    "restoreDatabase=$restoreDb",
    "minimumTableCount=$MinimumTableCount",
    "keepDatabase=$($KeepDatabase.IsPresent.ToString().ToLowerInvariant())"
)
Set-Content -LiteralPath $markerLocal -Value $markerContent -Encoding UTF8
$markerHash = (Get-FileHash -LiteralPath $markerLocal -Algorithm SHA256).Hash.ToLowerInvariant()
Set-Content -LiteralPath "$markerLocal.sha256" -Value "$markerHash  $markerName" -Encoding UTF8

Write-Host "== Publish restore drill marker =="
& docker cp $markerLocal "zhubao-erp-mysql:/backups/$markerName"
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
& docker cp "$markerLocal.sha256" "zhubao-erp-mysql:/backups/$markerName.sha256"
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
Write-Host "Restore drill marker written: $markerLocal"
Write-Host "Operations Center marker path: /backups/$markerName"
