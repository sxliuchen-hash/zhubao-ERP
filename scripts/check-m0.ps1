# 保留历史命令，统一执行当前项目检查。
& (Join-Path $PSScriptRoot "check.ps1") @args
