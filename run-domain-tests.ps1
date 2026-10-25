param([string]$GradleCache = $env:GRADLE_USER_HOME)
$ErrorActionPreference = 'Stop'
if (-not $GradleCache) { $GradleCache = Join-Path $env:USERPROFILE '.gradle' }
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to JDK 17 before running this script.' }
$javaExe = Join-Path $env:JAVA_HOME 'bin/java.exe'
$compiledMain = Join-Path $PSScriptRoot 'app/build/tmp/kotlin-classes/debug'
$compiledTests = Join-Path $PSScriptRoot 'app/build/tmp/kotlin-classes/debugUnitTest'
if (-not (Test-Path $compiledTests)) {
    throw 'First run: .\gradlew.bat compileDebugUnitTestKotlin assembleDebug'
}
function Find-CachedJar([string]$relative) {
    $base = Join-Path $GradleCache "caches/modules-2/files-2.1/$relative"
    $item = Get-ChildItem -LiteralPath $base -Filter '*.jar' -Recurse | Select-Object -First 1
    if (-not $item) { throw "Missing dependency: $relative" }
    return $item.FullName
}
$junit = Find-CachedJar 'junit/junit/4.13.2'
$hamcrest = Find-CachedJar 'org.hamcrest/hamcrest-core/1.3'
$stdlib = Find-CachedJar 'org.jetbrains.kotlin/kotlin-stdlib/2.0.21'
$classpath = @($compiledMain, $compiledTests, $junit, $hamcrest, $stdlib) -join [IO.Path]::PathSeparator
& $javaExe -cp $classpath org.junit.runner.JUnitCore io.curblink.demo.DomainTest
if ($LASTEXITCODE -ne 0) { throw 'JUnit tests failed.' }
