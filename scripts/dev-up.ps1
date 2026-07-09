<#
.SYNOPSIS
    一键启动珠宝 ERP 前后端本地开发环境。

.DESCRIPTION
    本脚本一次性拉起后端（Spring Boot，端口 8080）和前端（Vite，端口 5173）。
    - 后端复用 scripts/maven.ps1（首次会自动下载内置 Maven）以 spring-boot:run 启动。
    - 前端复用 pnpm dev，首次会自动 pnpm install。
    - 前端已配置 /api 代理到后端 8080，浏览器打开 http://localhost:5173 即可。

    默认在两个独立的 PowerShell 窗口中分别启动前后端，便于分别查看日志与单独停止（关闭窗口即停止）。
    使用 -SameWindow 可改为在当前窗口后台任务方式启动（日志混合，Ctrl+C 停止本脚本时会一并停止）。

.PARAMETER BackendOnly
    仅启动后端。

.PARAMETER FrontendOnly
    仅启动前端。

.PARAMETER SameWindow
    在当前窗口以后台作业方式启动前后端（而非新开窗口）。

.PARAMETER NoBrowser
    启动后不自动打开浏览器。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts/dev-up.ps1
    同时启动前后端并打开浏览器。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts/dev-up.ps1 -BackendOnly
    仅启动后端。
#>
[CmdletBinding()]
param(
    [switch]$BackendOnly,
    [switch]$FrontendOnly,
    [switch]$SameWindow,
    [switch]$NoBrowser
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$scriptsDir = Join-Path $repoRoot "scripts"
$backendScript = Join-Path $scriptsDir "dev-backend.ps1"
$frontendScript = Join-Path $scriptsDir "dev-frontend.ps1"

$startBackend = -not $FrontendOnly
$startFrontend = -not $BackendOnly

if ($BackendOnly -and $FrontendOnly) {
    throw "不能同时指定 -BackendOnly 和 -FrontendOnly。"
}

# 检查前端依赖 pnpm 是否可用（仅在需要启动前端时）
if ($startFrontend) {
    $pnpm = Get-Command pnpm -ErrorAction SilentlyContinue
    if ($null -eq $pnpm) {
        throw "未检测到 pnpm，请先安装 pnpm（npm i -g pnpm）后重试。"
    }
}

$backgroundJobs = @()

function Start-InNewWindow {
    param(
        [string]$Title,
        [string]$ScriptPath
    )
    # 在新的 PowerShell 窗口中运行指定脚本，窗口标题便于识别
    $command = "`$host.UI.RawUI.WindowTitle = '$Title'; & '$ScriptPath'"
    Start-Process -FilePath "powershell.exe" `
        -ArgumentList @("-NoExit", "-ExecutionPolicy", "Bypass", "-Command", $command) | Out-Null
    Write-Host "已在新窗口启动：$Title" -ForegroundColor Green
}

function Start-InBackgroundJob {
    param(
        [string]$Name,
        [string]$ScriptPath
    )
    $job = Start-Job -Name $Name -ScriptBlock {
        param($path)
        & powershell.exe -ExecutionPolicy Bypass -File $path
    } -ArgumentList $ScriptPath
    Write-Host "已启动后台作业：$Name（Job Id = $($job.Id)）" -ForegroundColor Green
    return $job
}

Write-Host "==============================================" -ForegroundColor Cyan
Write-Host " 珠宝 ERP 本地开发一键启动" -ForegroundColor Cyan
Write-Host "  后端： http://localhost:8080" -ForegroundColor Cyan
Write-Host "  前端： http://localhost:5173  （/api 已代理到后端）" -ForegroundColor Cyan
Write-Host "==============================================" -ForegroundColor Cyan

if ($SameWindow) {
    # 后台作业模式：日志混合，脚本退出时清理作业
    if ($startBackend) {
        $backgroundJobs += Start-InBackgroundJob -Name "zhubao-backend" -ScriptPath $backendScript
    }
    if ($startFrontend) {
        $backgroundJobs += Start-InBackgroundJob -Name "zhubao-frontend" -ScriptPath $frontendScript
    }

    if (-not $NoBrowser -and $startFrontend) {
        Write-Host "等待前端就绪后将打开浏览器..." -ForegroundColor Yellow
        Start-Sleep -Seconds 8
        Start-Process "http://localhost:5173"
    }

    Write-Host "`n前后端已在后台作业中运行。实时日志：" -ForegroundColor Yellow
    Write-Host "  Receive-Job -Name zhubao-backend -Keep" -ForegroundColor Yellow
    Write-Host "  Receive-Job -Name zhubao-frontend -Keep" -ForegroundColor Yellow
    Write-Host "按 Ctrl+C 停止本脚本并清理后台作业。" -ForegroundColor Yellow
    try {
        while ($true) {
            foreach ($job in $backgroundJobs) {
                Receive-Job -Job $job
            }
            Start-Sleep -Seconds 2
        }
    }
    finally {
        Write-Host "`n正在停止后台作业..." -ForegroundColor Yellow
        foreach ($job in $backgroundJobs) {
            Stop-Job -Job $job -ErrorAction SilentlyContinue
            Remove-Job -Job $job -Force -ErrorAction SilentlyContinue
        }
    }
}
else {
    # 独立窗口模式（默认）：便于分别查看日志、单独关闭
    if ($startBackend) {
        Start-InNewWindow -Title "zhubao-backend :8080" -ScriptPath $backendScript
    }
    if ($startFrontend) {
        Start-InNewWindow -Title "zhubao-frontend :5173" -ScriptPath $frontendScript
    }

    if (-not $NoBrowser -and $startFrontend) {
        Write-Host "等待前端就绪后将打开浏览器..." -ForegroundColor Yellow
        Start-Sleep -Seconds 8
        Start-Process "http://localhost:5173"
    }

    Write-Host "`n前后端已在独立窗口启动。关闭对应窗口即可停止服务。" -ForegroundColor Green
}
