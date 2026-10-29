param([string]$Serial = 'emulator-5554')
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'use-home-env.ps1')
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $projectRoot
$adb = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
$avd = (& $adb -s $Serial emu avd name) -join "`n"
if ($LASTEXITCODE -ne 0 -or $avd -notmatch '(?m)^CurbLink_Pleos_2_1\r?$') {
    throw 'These tests reset simulated app journeys. Run only on the dedicated CurbLink_Pleos_2_1 AVD.'
}
foreach ($apk in @('app/build/outputs/apk/debug/app-debug.apk','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')) {
    if (-not (Test-Path -LiteralPath $apk)) { throw 'Build assembleDebug assembleDebugAndroidTest first.' }
    & $adb -s $Serial install -r -g $apk
    if ($LASTEXITCODE -ne 0) { throw "APK installation failed: $apk" }
}
New-Item -ItemType Directory -Path work -Force | Out-Null
& $adb -s $Serial shell am instrument -w -r 'io.curblink.demo.test/io.curblink.demo.VehicleAppInstrumentation' | Tee-Object -FilePath 'work/native-tests.txt'
if (-not (Select-String -LiteralPath 'work/native-tests.txt' -Pattern 'PASS: 18 vehicle flows' -Quiet)) { throw 'Vehicle application instrumentation failed. See work/native-tests.txt.' }
$androidUser = (& $adb -s $Serial shell am get-current-user).Trim()
if ($androidUser -notmatch '^\d+$') { throw 'Could not determine Android foreground user.' }
& cmd.exe /c "`"$adb`" -s $Serial exec-out run-as io.curblink.demo --user $androidUser tar -C files/native-journey-tests -cf - . > work\native-captures.tar"
if ($LASTEXITCODE -ne 0) { throw 'Could not export native screenshots.' }
New-Item -ItemType Directory -Path work/native-captures -Force | Out-Null
tar -xf work/native-captures.tar -C work/native-captures
if ($LASTEXITCODE -ne 0) { throw 'Could not unpack native screenshots.' }
