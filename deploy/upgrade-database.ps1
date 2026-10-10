param(
    [ValidateSet("plan", "adopt", "upgrade")]
    [string]$Command = "plan",
    [switch]$BackupConfirmed,
    [switch]$SelfTest
)

$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $MyInvocation.MyCommand.Path)

if ($SelfTest) {
    $rejected = $false
    try {
        & $PSCommandPath -Command upgrade
    } catch {
        $rejected = $_.Exception.Message -match "备份"
    }
    if (-not $rejected) {
        throw "未确认备份时没有拒绝升级"
    }
    Write-Host "升级脚本自检通过：未确认备份时拒绝执行。自检未启动容器，也未连接数据库。"
    exit 0
}

if ($Command -ne "plan" -and -not $BackupConfirmed) {
    throw "升级前必须确认已经完成数据库备份。请先执行 backup-mysql.ps1，再加 -BackupConfirmed。"
}

$confirmed = if ($BackupConfirmed) { "--backup-confirmed" } else { "" }
$shell = "java -Dloader.main=com.zhubao.erp.ops.app.DatabaseUpgradeMain -cp /app/app.jar org.springframework.boot.loader.launch.PropertiesLauncher $Command --url `"`$ZHUBAO_DB_URL`" --username `"`$ZHUBAO_DB_USERNAME`" --password `"`$ZHUBAO_DB_PASSWORD`" $confirmed"
docker compose --env-file .env -f docker-compose.yml run --rm --no-deps --entrypoint sh backend -c $shell
exit $LASTEXITCODE
