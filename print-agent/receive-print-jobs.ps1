# 启动旁边的打印接收程序。配置文件 print-agent.properties 与 exe 放在同一目录。
$exe = Join-Path $PSScriptRoot 'ZhubaoPrintReceiver.exe'
if (-not (Test-Path -LiteralPath $exe)) {
    Write-Host "缺少 $exe"
    exit 2
}
& $exe @args
if ($null -eq $LASTEXITCODE) { exit 0 }
exit $LASTEXITCODE
