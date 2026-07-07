param(
    [string]$Version = ("m3.41-" + (Get-Date -Format "yyyyMMddHHmm")),
    [switch]$SkipTests,
    [switch]$NoDockerBuild,
    [switch]$Help
)

$ErrorActionPreference = "Stop"

if ($Help) {
    Write-Host "Build runtime Docker images and a source-free deployment package."
    Write-Host ""
    Write-Host "Usage:"
    Write-Host "  powershell -ExecutionPolicy Bypass -File scripts\docker-build-package.ps1 -Version m3.41.0"
    Write-Host ""
    Write-Host "Options:"
    Write-Host "  -Version       Docker tag and package version. Default: m3.41-yyyyMMddHHmm"
    Write-Host "  -SkipTests     Package backend without running Maven tests."
    Write-Host "  -NoDockerBuild Prepare package layout without docker build/save."
    Write-Host "  -Help          Show this help."
    exit 0
}

$repoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$artifactRoot = Join-Path $repoRoot ".deploy-artifacts"
$packageName = "zhubao-erp-runtime-$Version"
$packageDir = Join-Path $artifactRoot $packageName
$imagesDir = Join-Path $packageDir "images"
$mysqlInitDir = Join-Path $packageDir "mysql-init"
$zipPath = Join-Path $artifactRoot "$packageName.zip"

function Assert-InsideRepo([string]$path) {
    $resolved = if (Test-Path -LiteralPath $path) {
        (Resolve-Path -LiteralPath $path).Path
    } else {
        [System.IO.Path]::GetFullPath($path)
    }
    $root = (Resolve-Path -LiteralPath $repoRoot).Path
    if (-not $resolved.StartsWith($root)) {
        throw "Refusing to operate outside repository: $resolved"
    }
}

Assert-InsideRepo $artifactRoot
New-Item -ItemType Directory -Force -Path $artifactRoot | Out-Null
if (Test-Path -LiteralPath $packageDir) {
    Assert-InsideRepo $packageDir
    Remove-Item -Recurse -Force -LiteralPath $packageDir
}
if (Test-Path -LiteralPath $zipPath) {
    Assert-InsideRepo $zipPath
    Remove-Item -Force -LiteralPath $zipPath
}
New-Item -ItemType Directory -Force -Path $packageDir, $imagesDir, $mysqlInitDir | Out-Null

Write-Host "== Build backend jar =="
$mavenArgs = @("-f", "backend\pom.xml", "package")
if ($SkipTests) {
    $mavenArgs += "-DskipTests"
}
& (Join-Path $repoRoot "scripts\maven.ps1") @mavenArgs
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host "== Build frontend dist =="
pnpm --dir (Join-Path $repoRoot "frontend") build
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

$jar = Get-ChildItem -LiteralPath (Join-Path $repoRoot "backend\target") -Filter "*.jar" |
    Where-Object { $_.Name -notlike "*sources*" -and $_.Name -notlike "*javadoc*" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $jar) {
    throw "Backend jar not found under backend\target. Run Maven package first."
}

Write-Host "== Prepare source-free deploy folder =="
Copy-Item -LiteralPath (Join-Path $repoRoot "deploy\docker-compose.yml") -Destination $packageDir
Copy-Item -LiteralPath (Join-Path $repoRoot "deploy\.env.example") -Destination $packageDir
Copy-Item -LiteralPath (Join-Path $repoRoot "deploy\start.ps1") -Destination $packageDir
Copy-Item -LiteralPath (Join-Path $repoRoot "deploy\start.sh") -Destination $packageDir
$dockerReadmeByPrefix = Get-ChildItem -LiteralPath (Join-Path $repoRoot "docs") -Filter "19_Docker*.md" |
    Sort-Object Name |
    Select-Object -First 1
if (-not $dockerReadmeByPrefix) {
    throw "Docker deployment README not found under docs\19_Docker*.md"
}
Copy-Item -LiteralPath $dockerReadmeByPrefix.FullName -Destination (Join-Path $packageDir "README_DOCKER.md")
Get-ChildItem -LiteralPath (Join-Path $repoRoot "backend\src\main\resources\db\mysql") -Filter "*.sql" |
    Sort-Object Name |
    Copy-Item -Destination $mysqlInitDir

$envExampleInPackage = Join-Path $packageDir ".env.example"
(Get-Content -LiteralPath $envExampleInPackage) -replace '^ZHUBAO_IMAGE_TAG=.*$', "ZHUBAO_IMAGE_TAG=$Version" |
    Set-Content -LiteralPath $envExampleInPackage -Encoding UTF8
$envFile = Join-Path $packageDir ".env"
Copy-Item -LiteralPath $envExampleInPackage -Destination $envFile
(Get-Content -LiteralPath $envFile) -replace '^ZHUBAO_IMAGE_TAG=.*$', "ZHUBAO_IMAGE_TAG=$Version" |
    Set-Content -LiteralPath $envFile -Encoding UTF8

if (-not $NoDockerBuild) {
    Write-Host "== Docker build runtime images =="
    docker build -f (Join-Path $repoRoot "docker\backend.Dockerfile") -t "zhubao-erp-backend:$Version" $repoRoot
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
    docker build -f (Join-Path $repoRoot "docker\frontend.Dockerfile") -t "zhubao-erp-frontend:$Version" $repoRoot
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }

    $tarPath = Join-Path $imagesDir "zhubao-erp-images-$Version.tar"
    Write-Host "== Export Docker images =="
    docker save -o $tarPath "zhubao-erp-backend:$Version" "zhubao-erp-frontend:$Version"
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
} else {
    Write-Host "== Skip docker build/save =="
}

Write-Host "== Create zip package =="
Compress-Archive -Path (Join-Path $packageDir "*") -DestinationPath $zipPath -Force
Write-Host "Package ready: $zipPath"
