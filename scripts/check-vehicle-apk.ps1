param([string]$Apk = 'app/build/outputs/apk/debug/app-debug.apk')
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'use-home-env.ps1')
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $projectRoot
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $Apk))
try {
    if ($archive.Entries.FullName | Where-Object { $_ -like 'assets/scenarios/*' }) { throw 'Simulation fixtures are packaged in the product APK.' }
} finally { $archive.Dispose() }
$analyzer = Join-Path $env:ANDROID_HOME 'cmdline-tools/latest/bin/apkanalyzer.bat'
$packages = (& $analyzer dex packages --defined-only $Apk) -join "`n"
if ($LASTEXITCODE -ne 0) { throw 'Could not inspect APK classes.' }
if ($packages -match 'JourneyEngine|JourneyRun|JourneyViewport|ScenarioCatalog|SimulationDriver|DemoClock|VehicleAppInstrumentation|NativeJourneyInstrumentation|CurbLinkMainScreen|CurbLinkViewModel|LocalVehicleBridge|DemoPaymentAdapter') {
    throw 'Simulation or legacy demonstration classes are packaged in the product APK.'
}
$badging = (& (Join-Path $env:ANDROID_HOME 'build-tools/35.0.0/aapt.exe') dump badging $Apk) -join "`n"
if ($LASTEXITCODE -ne 0 -or $badging -notmatch "(?m)^\s*uses-feature: name='android.hardware.type.automotive'") { throw 'Automotive feature must be required.' }
'PASS: vehicle APK requires automotive; no simulation engine, renderer, scenarios or presenter classes.'
