# Dot-source from PowerShell: . ./scripts/use-home-env.ps1
$ErrorActionPreference = 'Stop'
$jdkHome = $env:JAVA_HOME
if (-not $jdkHome) { $jdkHome = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User') }
if (-not $jdkHome -or -not (Test-Path -LiteralPath (Join-Path $jdkHome 'bin/java.exe'))) {
    throw 'Install JDK17 and set JAVA_HOME first.'
}
$env:JAVA_HOME = $jdkHome
$sdkHome = $env:ANDROID_HOME
if (-not $sdkHome) { $sdkHome = [Environment]::GetEnvironmentVariable('ANDROID_HOME', 'User') }
if (-not $sdkHome) { $sdkHome = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
if (-not (Test-Path -LiteralPath $sdkHome)) { throw 'Android SDK not found.' }
$env:ANDROID_HOME = $sdkHome
$env:Path = "$jdkHome/bin;$sdkHome/platform-tools;$sdkHome/emulator;C:/Program Files/Git/cmd;$env:Path"
$env:JAVA_OPTS = '-Djava.net.preferIPv4Stack=true -Xmx2048m -Dfile.encoding=UTF-8'
& (Join-Path $jdkHome 'bin/java.exe') -version
if ($LASTEXITCODE -ne 0) { throw 'JDK could not start.' }
Write-Output "ANDROID_HOME=$sdkHome"
