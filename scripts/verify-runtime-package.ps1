param(
    [string]$PackagePath,
    [switch]$AllowMissingImages,
    [switch]$Help
)

$ErrorActionPreference = "Stop"

if ($Help) {
    Write-Host "Verify a source-free zhubao ERP runtime package directory or zip."
    Write-Host ""
    Write-Host "Usage:"
    Write-Host "  powershell -ExecutionPolicy Bypass -File scripts\verify-runtime-package.ps1 -PackagePath .deploy-artifacts\zhubao-erp-runtime-m3.59.zip"
    Write-Host ""
    Write-Host "Options:"
    Write-Host "  -PackagePath         Runtime package directory or .zip path."
    Write-Host "  -AllowMissingImages  Allow packages generated with docker-build-package.ps1 -NoDockerBuild."
    Write-Host "  -Help                Show this help."
    exit 0
}

if ([string]::IsNullOrWhiteSpace($PackagePath)) {
    throw "Missing required parameter: -PackagePath"
}

$repoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$artifactRoot = Join-Path $repoRoot ".deploy-artifacts"
$verificationRoot = Join-Path $artifactRoot "_verify"

function Resolve-FullPath([string]$path) {
    if (Test-Path -LiteralPath $path) {
        return (Resolve-Path -LiteralPath $path).Path
    }
    return [System.IO.Path]::GetFullPath($path)
}

function Assert-InsidePath([string]$path, [string]$root) {
    $resolvedPath = Resolve-FullPath $path
    $resolvedRoot = (Resolve-FullPath $root).TrimEnd([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar)
    $comparison = [System.StringComparison]::OrdinalIgnoreCase
    if ($resolvedPath -ne $resolvedRoot -and -not $resolvedPath.StartsWith($resolvedRoot + [System.IO.Path]::DirectorySeparatorChar, $comparison)) {
        throw "Path escapes expected root. Path=$resolvedPath Root=$resolvedRoot"
    }
}

function Get-RelativePackagePath([string]$basePath, [string]$fullPath) {
    $base = (Resolve-FullPath $basePath).TrimEnd([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar)
    $full = Resolve-FullPath $fullPath
    return $full.Substring($base.Length + 1).Replace("\", "/")
}

function Test-ZipChecksum([string]$zipPath) {
    $shaPath = "$zipPath.sha256"
    if (-not (Test-Path -LiteralPath $shaPath)) {
        Write-Host "Zip checksum sidecar not found; skipping .zip.sha256 verification."
        return
    }

    $line = (Get-Content -LiteralPath $shaPath | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -First 1)
    if ($line -notmatch '^([a-fA-F0-9]{64})\s+(.+)$') {
        throw "Invalid zip checksum file: $shaPath"
    }
    $expectedHash = $Matches[1].ToLowerInvariant()
    $expectedName = $Matches[2].Trim()
    $actualName = Split-Path -Leaf $zipPath
    if ($expectedName -ne $actualName) {
        throw "Zip checksum filename mismatch. Expected '$expectedName', actual '$actualName'."
    }

    $actualHash = (Get-FileHash -LiteralPath $zipPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualHash -ne $expectedHash) {
        throw "Zip checksum mismatch: $zipPath"
    }
}

function Open-Package([string]$path) {
    $resolved = Resolve-FullPath $path
    if (-not (Test-Path -LiteralPath $resolved)) {
        throw "Runtime package not found: $path"
    }

    if ((Get-Item -LiteralPath $resolved).PSIsContainer) {
        return @{
            Root = $resolved
            Cleanup = $null
        }
    }

    if ($resolved -notlike "*.zip") {
        throw "PackagePath must be a directory or .zip file: $resolved"
    }

    Test-ZipChecksum $resolved
    New-Item -ItemType Directory -Force -Path $verificationRoot | Out-Null
    Assert-InsidePath $verificationRoot $artifactRoot

    $tempDir = Join-Path $verificationRoot ([System.Guid]::NewGuid().ToString("N"))
    New-Item -ItemType Directory -Force -Path $tempDir | Out-Null
    Assert-InsidePath $tempDir $verificationRoot
    Expand-Archive -LiteralPath $resolved -DestinationPath $tempDir -Force

    return @{
        Root = $tempDir
        Cleanup = $tempDir
    }
}

function Assert-RequiredFile([string]$packageRoot, [string]$relativePath) {
    $path = Join-Path $packageRoot $relativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Required runtime package file missing: $relativePath"
    }
}

function Assert-NoForbiddenContent([string]$packageRoot) {
    $forbiddenPatterns = @(
        '(^|/)\.git(/|$)',
        '(^|/)backend/src(/|$)',
        '(^|/)frontend/src(/|$)',
        '(^|/)node_modules(/|$)',
        '(^|/)\.pnpm-store(/|$)',
        '(^|/)\.npm-cache(/|$)',
        '(^|/)\.tools(/|$)',
        '(^|/)backend/target(/|$)',
        '(^|/)frontend/dist(/|$)'
    )

    $items = Get-ChildItem -LiteralPath $packageRoot -Force -Recurse
    foreach ($item in $items) {
        $relativePath = Get-RelativePackagePath $packageRoot $item.FullName
        foreach ($pattern in $forbiddenPatterns) {
            if ($relativePath -match $pattern) {
                throw "Forbidden source or development artifact found in runtime package: $relativePath"
            }
        }
    }
}

function Assert-Manifest([string]$packageRoot) {
    $manifestPath = Join-Path $packageRoot "MANIFEST.sha256"
    Assert-RequiredFile $packageRoot "MANIFEST.sha256"

    $manifestPaths = New-Object 'System.Collections.Generic.HashSet[string]' ([System.StringComparer]::OrdinalIgnoreCase)
    foreach ($line in Get-Content -LiteralPath $manifestPath) {
        if ([string]::IsNullOrWhiteSpace($line)) {
            continue
        }
        if ($line -notmatch '^([a-fA-F0-9]{64})\s+(.+)$') {
            throw "Invalid manifest line: $line"
        }
        $expectedHash = $Matches[1].ToLowerInvariant()
        $relativePath = $Matches[2].Trim()
        if ($relativePath -eq ".env") {
            throw "MANIFEST.sha256 must not include mutable customer config file: .env"
        }

        $targetPath = Join-Path $packageRoot $relativePath.Replace("/", [System.IO.Path]::DirectorySeparatorChar)
        Assert-InsidePath $targetPath $packageRoot
        if (-not (Test-Path -LiteralPath $targetPath -PathType Leaf)) {
            throw "Manifest file missing: $relativePath"
        }
        $actualHash = (Get-FileHash -LiteralPath $targetPath -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actualHash -ne $expectedHash) {
            throw "Manifest hash mismatch: $relativePath"
        }
        [void]$manifestPaths.Add($relativePath)
    }

    $files = Get-ChildItem -LiteralPath $packageRoot -File -Recurse -Force
    foreach ($file in $files) {
        $relativePath = Get-RelativePackagePath $packageRoot $file.FullName
        if ($relativePath -eq ".env" -or $relativePath -eq "MANIFEST.sha256") {
            continue
        }
        if (-not $manifestPaths.Contains($relativePath)) {
            throw "Runtime package file is not covered by MANIFEST.sha256: $relativePath"
        }
    }
}

function Assert-ComposeRuntimeOnly([string]$packageRoot) {
    $composePath = Join-Path $packageRoot "docker-compose.yml"
    $compose = Get-Content -LiteralPath $composePath -Raw
    if ($compose -match '(?m)^\s*build\s*:') {
        throw "Runtime docker-compose.yml must reference images and must not contain build contexts."
    }
    if ($compose -match '\.\./|backend/src|frontend/src') {
        throw "Runtime docker-compose.yml references source or parent-directory paths."
    }
}

function Test-RuntimePackage([string]$packageRoot) {
    $requiredFiles = @(
        "docker-compose.yml",
        ".env.example",
        "start.ps1",
        "start.sh",
        "backup-mysql.ps1",
        "backup-mysql.sh",
        "verify-restore.ps1",
        "verify-restore.sh",
        "README_DOCKER.md",
        "MANIFEST.sha256"
    )
    foreach ($requiredFile in $requiredFiles) {
        Assert-RequiredFile $packageRoot $requiredFile
    }

    $mysqlInitDir = Join-Path $packageRoot "mysql-init"
    if (-not (Test-Path -LiteralPath $mysqlInitDir -PathType Container)) {
        throw "mysql-init directory missing from runtime package."
    }
    $sqlFiles = Get-ChildItem -LiteralPath $mysqlInitDir -Filter "*.sql" -File
    if ($sqlFiles.Count -eq 0) {
        throw "mysql-init directory does not contain SQL initialization files."
    }

    $imagesDir = Join-Path $packageRoot "images"
    $imageTars = @()
    if (Test-Path -LiteralPath $imagesDir -PathType Container) {
        $imageTars = @(Get-ChildItem -LiteralPath $imagesDir -Filter "*.tar" -File)
    }
    if ($imageTars.Count -eq 0 -and -not $AllowMissingImages) {
        throw "Docker image tar is missing. Use -AllowMissingImages only for -NoDockerBuild layout checks."
    }
    if ($imageTars.Count -eq 0) {
        Write-Host "Docker image tar not found; allowed because -AllowMissingImages was specified."
    }

    Assert-NoForbiddenContent $packageRoot
    Assert-Manifest $packageRoot
    Assert-ComposeRuntimeOnly $packageRoot
}

$openedPackage = $null
try {
    $openedPackage = Open-Package $PackagePath
    Test-RuntimePackage $openedPackage.Root
    Write-Host "Runtime package verification passed: $PackagePath"
}
finally {
    if ($openedPackage -and $openedPackage.Cleanup) {
        Assert-InsidePath $openedPackage.Cleanup $verificationRoot
        Remove-Item -Recurse -Force -LiteralPath $openedPackage.Cleanup
    }
}
