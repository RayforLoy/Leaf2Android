param([string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk")
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$avdRoot = Join-Path $projectRoot 'tmp\avd'
$avdDirectory = Join-Path $avdRoot 'Leaf2Android_API36.avd'
$image = Join-Path $Sdk 'system-images\android-36\default\x86_64\system.img'
if (-not (Test-Path -LiteralPath $image)) { throw 'Install system-images;android-36;default;x86_64 with Android SDK Manager first.' }
New-Item -ItemType Directory -Path $avdDirectory -Force | Out-Null
@"
avd.ini.encoding=UTF-8
path=$avdDirectory
target=android-36
"@ | Set-Content -LiteralPath (Join-Path $avdRoot 'Leaf2Android_API36.ini') -Encoding utf8
@'
AvdId=Leaf2Android_API36
avd.ini.encoding=UTF-8
abi.type=x86_64
tag.id=default
tag.display=Default Android System Image
image.sysdir.1=system-images\android-36\default\x86_64\
hw.cpu.arch=x86_64
hw.cpu.ncore=4
hw.ramSize=2048
hw.lcd.width=1080
hw.lcd.height=2520
hw.lcd.density=420
hw.gpu.enabled=yes
hw.gpu.mode=swiftshader_indirect
hw.keyboard=yes
hw.mainKeys=no
hw.audioInput=no
hw.audioOutput=no
hw.camera.back=none
hw.camera.front=none
disk.dataPartition.size=2G
fastboot.forceColdBoot=yes
showDeviceFrame=no
'@ | Set-Content -LiteralPath (Join-Path $avdDirectory 'config.ini') -Encoding utf8
$env:ANDROID_AVD_HOME = $avdRoot
$process = Start-Process -FilePath (Join-Path $Sdk 'emulator\emulator.exe') -ArgumentList @('-avd', 'Leaf2Android_API36', '-no-window', '-no-snapshot', '-no-audio', '-no-boot-anim', '-port', '5554') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $projectRoot 'tmp\emulator-out.log') -RedirectStandardError (Join-Path $projectRoot 'tmp\emulator-error.log')
Write-Output "API 36 emulator started; process $($process.Id); adb serial emulator-5554."
