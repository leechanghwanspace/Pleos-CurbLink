param(
    [ValidateSet('Auto','Simulation','Integrated','Apk')][string]$Mode = 'Auto',
    [string]$Serial,
    [int]$AndroidUser = 10,
    [ValidateRange(1024,65535)][int]$Port = 8000,
    [switch]$OpenBrowser,
    [switch]$Check
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$branch = (& git -C $projectRoot branch --show-current).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Could not determine the current branch.' }
if ($Mode -eq 'Auto') {
    $Mode = switch ($branch) {
        'simulation' { 'Simulation' }
        'integration' { 'Integrated' }
        'apk' { 'Apk' }
        default { throw 'Specify -Mode Simulation, Integrated or Apk on this branch.' }
    }
}
$needsApk = $Mode -in @('Integrated','Apk')
$needsWeb = $Mode -in @('Simulation','Integrated')
$apk = Join-Path $projectRoot 'release/CurbLink-Drive-0.5-arrival-studio.apk'
$python = Get-Command python -ErrorAction SilentlyContinue
$adb = Get-Command adb -ErrorAction SilentlyContinue
if (-not $adb -and $env:ANDROID_HOME) {
    $adbPath = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
    if (Test-Path -LiteralPath $adbPath) { $adb = Get-Item -LiteralPath $adbPath }
}
$webUrl = "http://127.0.0.1:$Port/simulator/"
[pscustomobject]@{
    Branch = $branch; Mode = $Mode; Apk = if($needsApk){$apk}else{'not used'}
    Web = if($needsWeb){$webUrl}else{'not used'}
    PythonAvailable = [bool]$python; AdbAvailable = [bool]$adb
    Serial = $Serial; AndroidUser = $AndroidUser
    Synchronization = 'Concurrent execution only; live state synchronization is not implemented.'
} | Format-List
if ($Check) { return }
# Validate everything before installing APK or starting the HTTP server.
if ($needsWeb -and -not $python) { throw 'Install Python 3 to run the web simulation.' }
if ($needsApk) {
    if (-not (Test-Path -LiteralPath $apk)) { throw 'Build the APK with scripts/build-drive.ps1 first.' }
    if (-not $adb) { throw 'Set ANDROID_HOME or add adb to PATH. Start Pleos separately.' }
    if (-not $Serial) { throw 'Pass -Serial from adb devices. No device is selected automatically.' }
    & $adb.Source -s $Serial get-state
    if ($LASTEXITCODE -ne 0) { throw 'The selected device is not connected.' }
    $badging = & $adb.Source -s $Serial shell pm list features
    if ($LASTEXITCODE -ne 0 -or $badging -notmatch 'android.hardware.type.automotive') { throw 'The selected device must provide Android Automotive.' }
    & $adb.Source -s $Serial install -r $apk
    if ($LASTEXITCODE -ne 0) { throw 'APK installation failed. Existing data was not deleted.' }
    & $adb.Source -s $Serial shell am start --user $AndroidUser -n io.curblink.demo/.MainActivity
    if ($LASTEXITCODE -ne 0) { throw 'Could not start APK. Check the Android user ID.' }
}
if ($needsWeb) {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,$Port)
    try { $listener.Start() } catch { throw "Port $Port is busy. Choose another -Port; the existing process was not changed." }
    finally { $listener.Stop() }
    if ($OpenBrowser) { Start-Process $webUrl }
    Write-Output "Web simulation: $webUrl (Ctrl+C stops the server)."
    & $python.Source -m http.server $Port --bind 127.0.0.1 --directory $projectRoot
    if ($LASTEXITCODE -ne 0) { throw 'The web server stopped with an error.' }
}
