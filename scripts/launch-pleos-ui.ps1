$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $projectRoot
. (Join-Path $PSScriptRoot 'use-home-env.ps1')

$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
$emu = Join-Path $env:ANDROID_HOME 'emulator\emulator.exe'
$apk = Join-Path $projectRoot 'release\CurbLink-Drive-0.5.2-arrival-preview.apk'

Write-Host "`n[CurbLink Drive 0.5.2] Checking Pleos Connect 2.1.0 emulator..." -ForegroundColor Cyan
$devices = (& $adb devices) -join "`n"
if ($devices -notmatch 'emulator-5554\s+device') {
    Write-Host "Starting Pleos Connect 2.1.0 emulator window (AVD: CurbLink_Pleos_2_1)..." -ForegroundColor Yellow
    Start-Process -FilePath $emu -ArgumentList '-avd','CurbLink_Pleos_2_1','-gpu','host' -WindowStyle Hidden
    Write-Host "Waiting for emulator device connection..." -ForegroundColor Yellow
    & $adb -s emulator-5554 wait-for-device
} else {
    Write-Host "Emulator (emulator-5554) is already running!" -ForegroundColor Green
}

Write-Host "Checking Android boot completion status..." -ForegroundColor Cyan
$bootReady = $false
for ($i = 0; $i -lt 60; $i++) {
    try {
        $boot = ((& $adb -s emulator-5554 shell getprop sys.boot_completed 2>$null) -join '').Trim()
        if ($boot -eq '1') {
            Write-Host "Emulator boot confirmed (sys.boot_completed=1)." -ForegroundColor Green
            $bootReady = $true
            break
        }
    } catch {}
    Write-Host -NoNewline "."
    Start-Sleep -Seconds 2
}
Write-Host ""
if (-not $bootReady) { throw 'Pleos did not finish booting. Existing app data was not changed.' }
Write-Host "Installing CurbLink Drive 0.5.2 APK and launching app..." -ForegroundColor Green
& $adb -s emulator-5554 install -r -g $apk
if ($LASTEXITCODE -ne 0) { throw 'APK update failed. Existing app and data were preserved; check signing compatibility.' }
& $adb -s emulator-5554 shell pm grant --user 10 io.curblink.demo android.car.permission.CAR_SPEED 2>$null
& $adb -s emulator-5554 shell pm grant --user 10 io.curblink.demo android.permission.ACCESS_FINE_LOCATION 2>$null
& $adb -s emulator-5554 shell pm grant --user 10 io.curblink.demo android.permission.ACCESS_COARSE_LOCATION 2>$null
& $adb -s emulator-5554 shell am force-stop io.curblink.demo
& $adb -s emulator-5554 shell am start --user 10 -W -n io.curblink.demo/.MainActivity
Write-Host "`nDone! Check the Pleos Connect emulator window on your screen." -ForegroundColor Cyan
