param(
    [ValidateSet("plan", "adopt", "upgrade")]
    [string]$Command = "plan",
    [string]$Url,
    [string]$Username,
    [string]$Password = "",
    [string]$Scripts = "",
    [switch]$BackupConfirmed,
    [switch]$SelfTest
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)

function Assert-BackupConfirmed {
    if ($Command -ne "plan" -and -not $BackupConfirmed) {
        throw "升级前必须确认已经完成数据库备份。请先备份，再加 -BackupConfirmed。"
    }
}

if ($SelfTest) {
    $rejected = $false
    try {
        & $PSCommandPath -Command upgrade -Url "jdbc:h2:mem:unused" -Username sa -Password ""
    } catch {
        $rejected = $_.Exception.Message -match "备份"
    }
    if (-not $rejected) {
        throw "未确认备份时没有拒绝升级"
    }
    Write-Host "升级脚本自检通过：未确认备份时拒绝执行。自检未连接数据库。"
    exit 0
}

Assert-BackupConfirmed
if ([string]::IsNullOrWhiteSpace($Url) -or [string]::IsNullOrWhiteSpace($Username)) {
    throw "必须提供 -Url 和 -Username。该入口不会在应用启动时自动执行。"
}

if (-not $env:JAVA_HOME) {
    throw "请先设置 JAVA_HOME。本脚本只在当前进程使用它，不修改系统环境变量。"
}

& (Join-Path $repoRoot "scripts\maven.ps1") -f (Join-Path $repoRoot "backend\pom.xml") -DskipTests compile
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

$classpathFile = Join-Path $repoRoot "backend\target\upgrade-classpath.txt"
& (Join-Path $repoRoot "scripts\maven.ps1") -f (Join-Path $repoRoot "backend\pom.xml") -q dependency:build-classpath "-DincludeScope=runtime" "-Dmdep.outputFile=$classpathFile"
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
$classpath = (Get-Content -LiteralPath $classpathFile -Raw).Trim()
$java = Join-Path $env:JAVA_HOME "bin\java.exe"
$argumentList = @(
    "-cp", ((Join-Path $repoRoot "backend\target\classes") + ";" + $classpath),
    "com.zhubao.erp.ops.app.DatabaseUpgradeMain",
    $Command,
    "--url", $Url,
    "--username", $Username,
    "--password", $Password
)
if (-not [string]::IsNullOrWhiteSpace($Scripts)) {
    $argumentList += @("--scripts", $Scripts)
} else {
    $argumentList += @("--scripts", (Join-Path $repoRoot "backend\src\main\resources\db\mysql"))
}
if ($BackupConfirmed) {
    $argumentList += "--backup-confirmed"
}

& $java @argumentList
exit $LASTEXITCODE
