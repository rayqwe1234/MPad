$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$version = (Get-Content -Raw -LiteralPath (Join-Path $projectRoot 'VERSION')).Trim()
$adb = 'D:\DevTools\Android\Sdk\platform-tools\adb.exe'
$apk = Join-Path $projectRoot "dist\android\MPad-$version.apk"
$env:ANDROID_USER_HOME = 'D:\DevTools\Android\UserHome'
if (-not (Test-Path -LiteralPath $apk)) { throw "找不到 APK，请先运行 scripts\build.ps1：$apk" }
& $adb install -r $apk
if ($LASTEXITCODE -ne 0) { throw 'ADB 安装失败。请确认手机已开启 USB 调试并授权这台电脑。' }
