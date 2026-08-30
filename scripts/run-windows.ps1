$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$env:DOTNET_ROOT = 'D:\DevTools\DotNet'
$env:DOTNET_CLI_HOME = 'D:\DevTools\DotNetHome'
$env:DOTNET_CLI_TELEMETRY_OPTOUT = '1'
$env:NUGET_PACKAGES = 'D:\DevTools\NuGetCache'
& 'D:\DevTools\DotNet\dotnet.exe' run --project (Join-Path $projectRoot 'windows\MPad.Companion\MPad.Companion.csproj')

