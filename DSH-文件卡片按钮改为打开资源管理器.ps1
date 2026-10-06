#requires -Version 5.1
<#
    DSH 行为修改：把产物卡片上的"文件夹"按钮改成
    —— 始终用系统资源管理器打开该文件所在目录（不再被"默认程序打开"顶掉）。

    用法（需要管理员权限，脚本会自己请求提权）：
        右键本文件 → "使用 PowerShell 运行"
    撤销：
        右键 → 使用 PowerShell 运行，然后在窗口里加 -Revert
        或：  powershell -ExecutionPolicy Bypass -File "<本文件>" -Revert

    改完在 DSH 网页里按 F5 刷新即可生效（客户端 bundle 用内容哈希做版本，会自动拉新版）。
#>
[CmdletBinding()]
param([switch]$Revert)

$ErrorActionPreference = 'Stop'

$Target = 'D:\Program Files\DeepSeekHarness\resources\dsh-runtime\node_modules\@deepseek-ai\dsh-client-ui-open-in-app\lib\client.js'

$Old1 = 'const revealDefault = kind === "file" && preferred === void 0 && props.loading !== true;'
$New1 = 'const revealDefault = kind === "file";'
$Old2 = 'const primaryLabel = preferred === void 0 ? t("path.reveal") : t("open.title", { app: preferred.name });'
$New2 = 'const primaryLabel = revealDefault || preferred === void 0 ? t("path.reveal") : t("open.title", { app: preferred.name });'

# ---- 提权 ----
$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Host '需要管理员权限，正在请求提权...' -ForegroundColor Yellow
    $argList = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', ('"' + $PSCommandPath + '"'))
    if ($Revert) { $argList += '-Revert' }
    Start-Process -FilePath (Get-Process -Id $PID).Path -ArgumentList $argList -Verb RunAs
    exit
}

if (-not (Test-Path -LiteralPath $Target)) {
    Write-Host "找不到目标文件：`n  $Target" -ForegroundColor Red
    Write-Host 'DSH 可能装在别的位置，请把 $Target 改成实际路径后重试。' -ForegroundColor Red
    Read-Host '按回车退出'
    exit 1
}

$text = [System.IO.File]::ReadAllText($Target)

function Count-Of([string]$hay, [string]$needle) {
    ([regex]::Matches($hay, [regex]::Escape($needle))).Count
}

$nOld1 = Count-Of $text $Old1
$nNew1 = Count-Of $text $New1
$nOld2 = Count-Of $text $Old2
$nNew2 = Count-Of $text $New2
$already = ($nNew1 -ge 1) -and ($nOld1 -eq 0)

Write-Host ''
Write-Host '=== 当前状态 ===' -ForegroundColor Cyan
Write-Host ("  原始写法命中 : {0}" -f $nOld1)
Write-Host ("  已改写法命中 : {0}" -f $nNew1)

if ($Revert) {
    if ($nNew1 -lt 1) { Write-Host '  已经是原始状态，无需撤销。' -ForegroundColor Green; Read-Host '按回车退出'; exit 0 }
    $new = $text.Replace($New1, $Old1).Replace($New2, $Old2)
    $bak = "$Target.bak-$(Get-Date -Format yyyyMMdd-HHmmss)"
    Copy-Item -LiteralPath $Target -Destination $bak -Force
    [System.IO.File]::WriteAllText($Target, $new, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host '  已撤销。备份：' -NoNewline; Write-Host $bak -ForegroundColor DarkGray
    Write-Host '  到 DSH 网页按 F5 刷新生效。' -ForegroundColor Green
    Read-Host '按回车退出'
    exit 0
}

if ($already) {
    Write-Host '  已经是目标状态，无需修改。' -ForegroundColor Green
    Read-Host '按回车退出'
    exit 0
}

if ($nOld1 -ne 1 -or $nOld2 -ne 1) {
    Write-Host '  目标代码片段与预期不符（可能 DSH 已升级改版）。为安全起见不做修改。' -ForegroundColor Red
    Read-Host '按回车退出'
    exit 1
}

$bak = "$Target.bak-$(Get-Date -Format yyyyMMdd-HHmmss)"
Copy-Item -LiteralPath $Target -Destination $bak -Force
$new = $text.Replace($Old1, $New1).Replace($Old2, $New2)
[System.IO.File]::WriteAllText($Target, $new, (New-Object System.Text.UTF8Encoding($false)))

$check = [System.IO.File]::ReadAllText($Target)
Write-Host ''
Write-Host '=== 修改结果 ===' -ForegroundColor Cyan
Write-Host ("  原始写法残留 : {0}" -f (Count-Of $check $Old1))
Write-Host ("  新写法命中   : {0}" -f (Count-Of $check $New1))
Write-Host ("  备份文件     : {0}" -f $bak) -ForegroundColor DarkGray
Write-Host ''
Write-Host '  完成。请到 DSH 网页按 F5 刷新。' -ForegroundColor Green
Write-Host '  效果：产物卡片上的文件夹按钮将始终打开资源管理器并定位到该文件；' -ForegroundColor Green
Write-Host '        想用别的程序打开，仍可用右侧下拉箭头选择。' -ForegroundColor Green
Read-Host '按回车退出'