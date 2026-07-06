$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$mavenVersion = "3.9.11"
$toolsDir = Join-Path $repoRoot ".tools"
$mavenHome = Join-Path $toolsDir "apache-maven-$mavenVersion"
$mavenBin = Join-Path $mavenHome "bin\mvn.cmd"

if (!(Test-Path -LiteralPath $mavenBin)) {
    New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null
    $zipPath = Join-Path $toolsDir "apache-maven-$mavenVersion-bin.zip"
    $downloadUrl = "https://archive.apache.org/dist/maven/maven-3/$mavenVersion/binaries/apache-maven-$mavenVersion-bin.zip"

    if (!(Test-Path -LiteralPath $zipPath)) {
        Write-Host "Downloading Maven $mavenVersion..."
        Invoke-WebRequest -Uri $downloadUrl -OutFile $zipPath
    }

    Write-Host "Extracting Maven $mavenVersion..."
    Expand-Archive -Path $zipPath -DestinationPath $toolsDir -Force
}

& $mavenBin @args
exit $LASTEXITCODE
