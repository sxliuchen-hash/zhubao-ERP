$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
& (Join-Path $repoRoot "scripts\maven.ps1") -f (Join-Path $repoRoot "backend\pom.xml") spring-boot:run
