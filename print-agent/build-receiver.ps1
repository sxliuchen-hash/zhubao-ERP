# 用本机 .NET Framework 编译打印接收程序，并跑一次不访问服务器的自测。
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$csc = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
if (-not (Test-Path -LiteralPath $csc)) {
    $csc = Join-Path $env:WINDIR 'Microsoft.NET\Framework\v4.0.30319\csc.exe'
}
$out = Join-Path $root 'ZhubaoPrintReceiver.exe'
& $csc /nologo /optimize+ /target:exe /platform:anycpu /utf8output `
    /r:System.Drawing.dll /r:System.Web.Extensions.dll /r:System.Management.dll `
    /out:$out (Join-Path $root 'LabelDrawing.cs') (Join-Path $root 'ReceivePrintJobs.cs')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $out -SelfTest
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$download = Join-Path $root '..\frontend\public\downloads'
New-Item -ItemType Directory -Force -Path $download | Out-Null
Copy-Item -LiteralPath $out -Destination (Join-Path $download 'ZhubaoPrintReceiver.exe') -Force
$hash = (Get-FileHash -LiteralPath $out -Algorithm SHA256).Hash.ToLower()
$source = Get-Content -LiteralPath (Join-Path $root 'ReceivePrintJobs.cs') -Raw
$matched = [regex]::Match($source, 'const string AgentRelease = "([^"]+)"')
if (-not $matched.Success) { throw '找不到打印程序版本号' }
$release = $matched.Groups[1].Value
$jsonPath = Join-Path $download 'print-agent.json'
$json = "{`n  `"version`": `"$release`",`n  `"file`": `"ZhubaoPrintReceiver.exe`",`n  `"sha256`": `"$hash`"`n}`n"
[System.IO.File]::WriteAllText($jsonPath, $json, (New-Object System.Text.UTF8Encoding $false))
Write-Host "已放入网页下载 $release $hash"
