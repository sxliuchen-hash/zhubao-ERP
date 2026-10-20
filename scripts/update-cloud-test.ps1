# 把当前编译产物更新到腾讯云测试服务器。
# 不上传业务源码，不执行数据库迁移。有新 SQL 时不要用本脚本代替升级。
param(
    [ValidateSet("both", "frontend", "backend")]
    [string]$Part = "both",
    [string]$Tag = ""
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$jdk = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -match "^jdk-(1[7-9]|[2-9]\d)" } |
    Sort-Object Name -Descending |
    Select-Object -First 1
if (-not $jdk) {
    throw "未找到 JDK 17 或更高版本，无法编译后端。"
}

if (-not $Tag) {
    $Tag = "cloud-" + (Get-Date -Format "yyyyMMdd-HHmm")
}
if ($Tag -notmatch "^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$") {
    throw "镜像标签只能包含字母、数字、点、下划线和连字符。"
}

$env:JAVA_HOME = $jdk.FullName
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path

if ($Part -ne "frontend") {
    Write-Host "正在打包后端（复用本次已通过的定向测试，跳过重复测试）"
    & powershell -ExecutionPolicy Bypass -File (Join-Path $repoRoot "scripts\maven.ps1") -f (Join-Path $repoRoot "backend\pom.xml") -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw "后端打包失败" }
}
if ($Part -ne "backend") {
    Write-Host "正在构建前端"
    pnpm --dir $repoRoot --filter zhubao-erp-frontend build
    if ($LASTEXITCODE -ne 0) { throw "前端构建失败" }
}

$stage = Join-Path $repoRoot ".deploy-artifacts\$Tag"
if (Test-Path -LiteralPath $stage) {
    Remove-Item -LiteralPath $stage -Recurse -Force
}
New-Item -ItemType Directory -Force -Path (Join-Path $stage "docker") | Out-Null
Copy-Item -Path (Join-Path $repoRoot "docker\*") -Destination (Join-Path $stage "docker") -Recurse -Force
if ($Part -ne "backend") {
    $dist = Join-Path $stage "frontend\dist"
    New-Item -ItemType Directory -Force -Path $dist | Out-Null
    Copy-Item -Path (Join-Path $repoRoot "frontend\dist\*") -Destination $dist -Recurse -Force
}
if ($Part -ne "frontend") {
    $jarSource = Join-Path $repoRoot "backend\target\zhubao-erp-backend-0.0.1-SNAPSHOT.jar"
    if (-not (Test-Path -LiteralPath $jarSource)) { throw "未找到后端 JAR" }
    $jarTarget = Join-Path $stage "backend\target"
    New-Item -ItemType Directory -Force -Path $jarTarget | Out-Null
    Copy-Item -LiteralPath $jarSource -Destination (Join-Path $jarTarget "zhubao-erp-backend-0.0.1-SNAPSHOT.jar")
}

$archive = Join-Path $repoRoot ".deploy-artifacts\$Tag.tar.gz"
if (Test-Path -LiteralPath $archive) { Remove-Item -LiteralPath $archive -Force }
tar.exe -czf $archive -C $stage .
if ($LASTEXITCODE -ne 0) { throw "打包上传文件失败" }

Write-Host "正在上传 $Tag"
scp -o BatchMode=yes $archive "zhubao-cloud:/home/ubuntu/${Tag}.tar.gz"
if ($LASTEXITCODE -ne 0) { throw "上传失败" }
scp -o BatchMode=yes (Join-Path $repoRoot "scripts\apply-cloud-test.sh") "zhubao-cloud:/home/ubuntu/apply-cloud-test.sh"
if ($LASTEXITCODE -ne 0) { throw "上传切换脚本失败" }

ssh -o BatchMode=yes zhubao-cloud "sed -i 's/\r$//' /home/ubuntu/apply-cloud-test.sh; bash /home/ubuntu/apply-cloud-test.sh $Tag $Part"
if ($LASTEXITCODE -ne 0) { throw "测试服务器切换失败，服务器应已尝试恢复上一版配置" }

$index = Join-Path $repoRoot "frontend\dist\index.html"
if (Test-Path -LiteralPath $index) {
    $localHash = (Get-FileHash -LiteralPath $index -Algorithm SHA256).Hash.ToLower()
    Write-Host "本地首页 SHA-256 $localHash"
}
Write-Host "测试服务器已切换到 $Tag"
