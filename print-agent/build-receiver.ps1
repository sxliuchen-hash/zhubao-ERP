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
exit $LASTEXITCODE
