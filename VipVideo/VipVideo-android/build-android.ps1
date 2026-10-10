$ErrorActionPreference = "Stop"

$javaOutput = & java -version 2>&1
if ($LASTEXITCODE -ne 0 -or ($javaOutput -join " ") -notmatch 'version "(17|18|19|20|21)') {
    throw "JDK 17 or newer is required. Current Java: $($javaOutput -join ' ')"
}

if (-not $env:ANDROID_HOME -and -not $env:ANDROID_SDK_ROOT) {
    throw "Android SDK not found. Install Android Studio and set ANDROID_HOME or ANDROID_SDK_ROOT."
}

if (-not (Get-Command gradle -ErrorAction SilentlyContinue)) {
    throw "Gradle not found. Install Gradle 8.9, or open this directory with Android Studio."
}

Push-Location $PSScriptRoot
try {
    gradle --no-daemon assembleDebug
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed with exit code $LASTEXITCODE"
    }
    $apk = Join-Path $PSScriptRoot "app\build\outputs\apk\debug\app-debug.apk"
    Write-Host "APK created: $apk"
} finally {
    Pop-Location
}
