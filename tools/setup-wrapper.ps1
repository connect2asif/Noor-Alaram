$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $root 'gradle\wrapper\gradle-wrapper.jar'
$expected = '498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17'
$uri = 'https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar'
if (-not (Test-Path $jar)) {
    Write-Host 'Downloading official Gradle 8.9 wrapper...'
    Invoke-WebRequest -Uri $uri -OutFile $jar -UseBasicParsing
}
$hash = (Get-FileHash -Path $jar -Algorithm SHA256).Hash.ToLowerInvariant()
if ($hash -ne $expected) {
    Remove-Item $jar -Force
    throw "Wrapper checksum mismatch. Expected: $expected. Received: $hash. Download only from the official Gradle repository."
}
Write-Host 'Gradle wrapper verified successfully.' -ForegroundColor Green
Write-Host 'Now open the TilawatAlarm folder in Android Studio.'
