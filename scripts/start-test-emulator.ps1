$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$sdkDirectory = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$avdHome = Join-Path $projectRoot '.local/avd'
$logDirectory = Join-Path $projectRoot '.local/emulator'
New-Item -ItemType Directory -Force -Path $avdHome, $logDirectory | Out-Null
$env:ANDROID_AVD_HOME = $avdHome
$avdDirectory = Join-Path $avdHome 'ToolkitTest.avd'
if (-not (Test-Path -LiteralPath $avdDirectory)) {
    'no' | & (Join-Path $sdkDirectory 'cmdline-tools/latest/bin/avdmanager.bat') create avd --name ToolkitTest --package 'system-images;android-36.1;google_apis_playstore;x86_64' --path $avdDirectory --device pixel_6
    if ($LASTEXITCODE -ne 0) { throw 'Could not create the isolated Toolkit emulator.' }
}
$adb = Join-Path $sdkDirectory 'platform-tools/adb.exe'
$devices = & $adb devices
if ($devices -match '^emulator-5556\s') { throw 'Port 5556 is already in use. Verify its emulator before using it.' }
$process = Start-Process -FilePath (Join-Path $sdkDirectory 'emulator/emulator.exe') -ArgumentList @('-avd', 'ToolkitTest', '-port', '5556', '-no-window', '-no-audio', '-no-boot-anim', '-no-snapshot', '-gpu', 'host', '-feature', '-Vulkan', '-memory', '3072', '-cores', '4') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logDirectory 'stdout.log') -RedirectStandardError (Join-Path $logDirectory 'stderr.log')
Write-Output "Started isolated ToolkitTest emulator (PID $($process.Id)), serial emulator-5556."
