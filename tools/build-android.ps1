param(
    [string]$Jdk = $env:JAVA_HOME,
    [string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk",
    [switch]$SkipChecks,
    [switch]$Release,
    [string]$SigningConfig = (Join-Path $env:USERPROFILE '.android\Leaf2Android\release-signing.json')
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $Jdk -or -not (Test-Path -LiteralPath (Join-Path $Jdk 'bin\javac.exe'))) {
    $cachedJdk = Join-Path $projectRoot 'tmp\android-tooling\jdk21-path.txt'
    if (Test-Path -LiteralPath $cachedJdk) { $Jdk = Get-Content -LiteralPath $cachedJdk }
}
if (-not $Jdk -or -not (Test-Path -LiteralPath (Join-Path $Jdk 'bin\javac.exe'))) {
    throw 'A full JDK 21 is required. Pass -Jdk C:\path\to\jdk-21 (a JRE is insufficient).'
}
$env:JAVA_HOME = $Jdk
if ($Release -and -not $env:LEAF2ANDROID_KEYSTORE_PATH) {
    if (-not (Test-Path -LiteralPath $SigningConfig)) { throw 'Run tools/prepare-release-signing.ps1 or provide LEAF2ANDROID_* signing environment variables first.' }
    $signing = Get-Content -LiteralPath $SigningConfig -Raw | ConvertFrom-Json
    $env:LEAF2ANDROID_KEYSTORE_PATH = $signing.keystore
    $env:LEAF2ANDROID_STORE_PASSWORD = $signing.storePassword
    $env:LEAF2ANDROID_KEY_ALIAS = $signing.alias
    $env:LEAF2ANDROID_KEY_PASSWORD = $signing.keyPassword
}
if ($Release) {
    if (-not (Test-Path -LiteralPath $env:LEAF2ANDROID_KEYSTORE_PATH) -or -not $env:LEAF2ANDROID_STORE_PASSWORD -or -not $env:LEAF2ANDROID_KEY_PASSWORD) {
        throw 'Release signing configuration is incomplete.'
    }
}
if (-not (Test-Path -LiteralPath (Join-Path $projectRoot 'local.properties'))) {
    if (-not (Test-Path -LiteralPath (Join-Path $Sdk 'platforms\android-36\android.jar'))) { throw 'Install Android SDK Platform 36 and Build Tools 36.0.0 first.' }
    ('sdk.dir=' + $Sdk.Replace('\', '/')) | Set-Content -LiteralPath (Join-Path $projectRoot 'local.properties') -Encoding ascii
}
Push-Location $projectRoot
try {
    if ($Release) {
        if ($SkipChecks) { & .\gradlew.bat :app:assembleRelease --console plain }
        else { & .\gradlew.bat :protocol:test :app:lintRelease :app:assembleRelease --console plain }
    } else {
        if ($SkipChecks) { & .\gradlew.bat :app:assembleDebug --console plain }
        else { & .\gradlew.bat :protocol:test :app:lintDebug :app:assembleDebug --console plain }
    }
    if ($LASTEXITCODE -ne 0) { throw 'Android build/checks failed.' }
    $output = Join-Path $projectRoot 'dist'
    New-Item -ItemType Directory -Path $output -Force | Out-Null
    $artifactName = if ($Release) { 'Leaf2Android-0.3.0.apk' } else { 'Leaf2Android-0.3.0-debug.apk' }
    $builtApk = if ($Release) { '.\app\build\outputs\apk\release\app-release.apk' } else { '.\app\build\outputs\apk\debug\app-debug.apk' }
    $apk = Join-Path $output $artifactName
    Copy-Item -LiteralPath $builtApk -Destination $apk
    $checksum = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
    "$checksum  $artifactName" | Set-Content -LiteralPath ($apk+'.sha256') -Encoding ascii
    Write-Output "APK: $apk"
    Write-Output "SHA-256: $checksum"
} finally { Pop-Location }
