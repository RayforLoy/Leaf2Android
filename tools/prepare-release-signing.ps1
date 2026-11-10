param(
    [string]$Jdk = $env:JAVA_HOME,
    [string]$SigningDirectory = (Join-Path $env:USERPROFILE '.android\Leaf2Android')
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $Jdk -or -not (Test-Path -LiteralPath (Join-Path $Jdk 'bin\keytool.exe'))) {
    $cached = Join-Path $projectRoot 'tmp\android-tooling\jdk21-path.txt'
    if (Test-Path -LiteralPath $cached) { $Jdk = Get-Content -LiteralPath $cached }
}
if (-not $Jdk -or -not (Test-Path -LiteralPath (Join-Path $Jdk 'bin\keytool.exe'))) { throw 'Set -Jdk to a full JDK 21 installation.' }
$resolvedDirectory = [IO.Path]::GetFullPath($SigningDirectory)
$resolvedProject = [IO.Path]::GetFullPath($projectRoot).TrimEnd('\')
if ($resolvedDirectory.StartsWith($resolvedProject+'\', [StringComparison]::OrdinalIgnoreCase) -or $resolvedDirectory -eq $resolvedProject) {
    throw 'Release signing files must be outside the project directory.'
}
New-Item -ItemType Directory -Path $resolvedDirectory -Force | Out-Null
$configuration = Join-Path $resolvedDirectory 'release-signing.json'
if (Test-Path -LiteralPath $configuration) {
    $existing = Get-Content -LiteralPath $configuration -Raw | ConvertFrom-Json
    if (-not (Test-Path -LiteralPath $existing.keystore)) { throw 'Signing configuration exists but the referenced keystore is missing.' }
    Write-Output "Existing release signing configuration: $configuration"
    exit 0
}
$keystore = Join-Path $resolvedDirectory 'leaf2android-release.p12'
if (Test-Path -LiteralPath $keystore) { throw 'A keystore exists without its configuration; it has not been overwritten.' }
$secret = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$env:LEAF2ANDROID_STORE_PASSWORD = $secret
$env:LEAF2ANDROID_KEY_PASSWORD = $secret
try {
    & (Join-Path $Jdk 'bin\keytool.exe') -genkeypair -keystore $keystore -storetype PKCS12 -alias leaf2android -keyalg RSA -keysize 4096 -validity 10000 -dname 'CN=Leaf2Android, OU=Mobile, O=Leaf2Android' -storepass:env LEAF2ANDROID_STORE_PASSWORD -keypass:env LEAF2ANDROID_KEY_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'Release key generation failed.' }
    @{ keystore=$keystore; alias='leaf2android'; storePassword=$secret; keyPassword=$secret } | ConvertTo-Json | Set-Content -LiteralPath $configuration -Encoding utf8
} finally {
    Remove-Item Env:\LEAF2ANDROID_STORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:\LEAF2ANDROID_KEY_PASSWORD -ErrorAction SilentlyContinue
}
Write-Output "Release keystore retained outside repository: $keystore"
Write-Output "Signing configuration retained outside repository: $configuration"
