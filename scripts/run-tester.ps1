$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$dotnet = 'D:\DevTools\DotNet\dotnet.exe'

if (-not (Test-Path -LiteralPath $dotnet)) {
    throw "缺少 .NET SDK：$dotnet"
}

$env:DOTNET_ROOT = Split-Path $dotnet
$env:DOTNET_CLI_HOME = 'D:\DevTools\DotNetHome'
$env:DOTNET_CLI_TELEMETRY_OPTOUT = '1'
$env:NUGET_PACKAGES = 'D:\DevTools\NuGetCache'

Push-Location $projectRoot
try {
    & $dotnet run --project '.\windows\MPad.Tester\MPad.Tester.csproj'
} finally {
    Pop-Location
}
