$ErrorActionPreference = 'Stop'
if (-not $env:CURBLINK_GRADLE) { throw 'CURBLINK_GRADLE must point to the verified Gradle 8.9 executable.' }
$env:JAVA_OPTS='-Djava.net.preferIPv4Stack=true -Xmx2048m -Dfile.encoding=UTF-8'
& $env:CURBLINK_GRADLE --no-daemon '-Dorg.gradle.jvmargs=' '-Pkotlin.compiler.execution.strategy=in-process' compileDebugUnitTestKotlin assembleDebug --console plain
if ($LASTEXITCODE -ne 0) { throw 'Compilation/APK build failed' }
& (Join-Path (Get-Location) 'run-domain-tests.ps1')
if ($LASTEXITCODE -ne 0) { throw 'Domain tests failed' }
