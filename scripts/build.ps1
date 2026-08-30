param(
    [ValidateSet('Debug', 'Release')]
    [string]$Configuration = 'Release'
)

$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$version = (Get-Content -Raw -LiteralPath (Join-Path $projectRoot 'VERSION')).Trim()
$dotnet = 'D:\DevTools\DotNet\dotnet.exe'
$javaHome = 'D:\DevTools\Java\jdk-17.0.20.101-hotspot'
$androidSdk = 'D:\DevTools\Android\Sdk'
$gradleHome = 'D:\DevTools\GradleCache'
$signingRoot = 'D:\DevTools\MPadSigning'
$releaseKeystore = Join-Path $signingRoot 'mpad-release.jks'
$releaseSecret = Join-Path $signingRoot 'release-secret.txt'

foreach ($required in @(
    $dotnet,
    (Join-Path $javaHome 'bin\java.exe'),
    (Join-Path $androidSdk 'platform-tools\adb.exe'),
    $releaseKeystore,
    $releaseSecret
)) {
    if (-not (Test-Path -LiteralPath $required)) { throw "缺少构建或签名文件：$required" }
}

$env:DOTNET_ROOT = Split-Path $dotnet
$env:DOTNET_CLI_HOME = 'D:\DevTools\DotNetHome'
$env:DOTNET_CLI_TELEMETRY_OPTOUT = '1'
$env:NUGET_PACKAGES = 'D:\DevTools\NuGetCache'
$env:JAVA_HOME = $javaHome
$env:ANDROID_HOME = $androidSdk
$env:ANDROID_SDK_ROOT = $androidSdk
$env:ANDROID_USER_HOME = 'D:\DevTools\Android\UserHome'
$env:GRADLE_USER_HOME = $gradleHome

$encryptedPassword = (Get-Content -Raw -LiteralPath $releaseSecret).Trim()
$securePassword = $encryptedPassword | ConvertTo-SecureString
$credential = [System.Management.Automation.PSCredential]::new('mpad', $securePassword)
$plainPassword = $credential.GetNetworkCredential().Password
$env:MPAD_RELEASE_KEYSTORE = $releaseKeystore
$env:MPAD_RELEASE_STORE_PASSWORD = $plainPassword
$env:MPAD_RELEASE_KEY_ALIAS = 'mpad'
$env:MPAD_RELEASE_KEY_PASSWORD = $plainPassword

Push-Location $projectRoot
try {
    & $dotnet test '.\MPad.slnx' --configuration $Configuration
    if ($LASTEXITCODE -ne 0) { throw 'Windows 测试失败。' }

    $distRoot = [System.IO.Path]::GetFullPath((Join-Path $projectRoot 'dist'))
    $windowsOutput = [System.IO.Path]::GetFullPath((Join-Path $distRoot "windows\MPad-$version"))
    if (-not $windowsOutput.StartsWith($distRoot + [System.IO.Path]::DirectorySeparatorChar)) {
        throw "发布目录逃逸项目 dist：$windowsOutput"
    }
    if (Test-Path -LiteralPath $windowsOutput) {
        Remove-Item -LiteralPath $windowsOutput -Recurse -Force
    }
    New-Item -ItemType Directory -Path $windowsOutput -Force | Out-Null

    & $dotnet publish '.\windows\MPad.Companion\MPad.Companion.csproj' `
        --configuration $Configuration --runtime win-x64 --self-contained true --output $windowsOutput
    if ($LASTEXITCODE -ne 0) { throw 'Windows 伴侣发布失败。' }

    & $dotnet publish '.\windows\MPad.Tester\MPad.Tester.csproj' `
        --configuration $Configuration --runtime win-x64 --self-contained true --output $windowsOutput
    if ($LASTEXITCODE -ne 0) { throw '手柄测试器发布失败。' }

    Push-Location (Join-Path $projectRoot 'android')
    try {
        & '.\gradlew.bat' testDebugUnitTest assembleRelease
        if ($LASTEXITCODE -ne 0) { throw 'Android 正式版构建失败。' }
    } finally { Pop-Location }

    $androidOutput = Join-Path $distRoot 'android'
    New-Item -ItemType Directory -Path $androidOutput -Force | Out-Null
    $apkOutput = Join-Path $androidOutput "MPad-$version.apk"
    Copy-Item -LiteralPath (Join-Path $projectRoot 'android\app\build\outputs\apk\release\app-release.apk') `
        -Destination $apkOutput -Force

    $driverOutput = Join-Path $windowsOutput 'drivers'
    New-Item -ItemType Directory -Path $driverOutput -Force | Out-Null
    foreach ($file in @('ViGEmBus_1.22.0_x64_x86_arm64.exe', 'ViGEmBus-LICENSE.txt', 'SHA256SUMS.txt')) {
        $source = Join-Path $projectRoot "drivers\$file"
        if (Test-Path -LiteralPath $source) { Copy-Item -LiteralPath $source -Destination $driverOutput -Force }
    }

    $companionAsset = Join-Path $distRoot "windows\MPad.Companion-$version.exe"
    $testerAsset = Join-Path $distRoot "windows\MPad.Tester-$version.exe"
    $driverAsset = Join-Path $distRoot 'windows\ViGEmBus_1.22.0_x64_x86_arm64.exe'
    Copy-Item -LiteralPath (Join-Path $windowsOutput 'MPad.Companion.exe') -Destination $companionAsset -Force
    Copy-Item -LiteralPath (Join-Path $windowsOutput 'MPad.Tester.exe') -Destination $testerAsset -Force
    Copy-Item -LiteralPath (Join-Path $projectRoot 'drivers\ViGEmBus_1.22.0_x64_x86_arm64.exe') -Destination $driverAsset -Force

    $releaseAssets = @($apkOutput, $companionAsset, $testerAsset, $driverAsset)
    $checksums = $releaseAssets | ForEach-Object {
        $hash = Get-FileHash -LiteralPath $_ -Algorithm SHA256
        "$($hash.Hash.ToLowerInvariant())  $([System.IO.Path]::GetFileName($hash.Path))"
    }
    $checksums | Set-Content -LiteralPath (Join-Path $distRoot 'SHA256SUMS.txt') -Encoding utf8

    Write-Host "MPad $version 正式版构建完成：$distRoot" -ForegroundColor Green
} finally {
    Pop-Location
    $plainPassword = $null
    Remove-Item Env:\MPAD_RELEASE_KEYSTORE -ErrorAction SilentlyContinue
    Remove-Item Env:\MPAD_RELEASE_STORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:\MPAD_RELEASE_KEY_ALIAS -ErrorAction SilentlyContinue
    Remove-Item Env:\MPAD_RELEASE_KEY_PASSWORD -ErrorAction SilentlyContinue
}
