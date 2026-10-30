param([string]$Gradle = $env:CURBLINK_GRADLE)
$ErrorActionPreference = 'Stop'
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to a JDK 17 installation.' }
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $projectRoot
if ($env:ANDROID_HOME) { [IO.File]::WriteAllText((Join-Path $projectRoot 'local.properties'), ('sdk.dir=' + $env:ANDROID_HOME.Replace('\','/')) + [Environment]::NewLine, [Text.UTF8Encoding]::new($false)) }
if (-not $Gradle) { $Gradle = Join-Path $projectRoot 'gradlew.bat' }
$env:JAVA_OPTS = '-Djava.net.preferIPv4Stack=true -Xmx2048m -Dfile.encoding=UTF-8'
& $Gradle --no-daemon --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8' '-Pkotlin.compiler.execution.strategy=in-process' '-Pandroid.overridePathCheck=true' assembleDebug compileDebugUnitTestKotlin lintDebug --console plain
if ($LASTEXITCODE -ne 0) { throw 'Android build or lint failed.' }
& (Join-Path $projectRoot 'run-domain-tests.ps1')
if ($LASTEXITCODE -ne 0) { throw 'Domain tests failed.' }
$apk = Join-Path $projectRoot 'release/CurbLink-Drive-0.5-arrival-studio.apk'
Copy-Item -LiteralPath 'app/build/outputs/apk/debug/app-debug.apk' -Destination $apk -Force
((Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash + '  CurbLink-Drive-0.5-arrival-studio.apk') | Set-Content -Encoding ascii 'release/CurbLink-Drive-0.5-arrival-studio.apk.sha256'
Write-Output $apk
