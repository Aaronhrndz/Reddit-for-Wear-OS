$env:JAVA_HOME = "C:\Users\aaron\tools\jdk-17"
$env:ANDROID_HOME = "C:\Users\aaron\tools\android-sdk"
$env:PATH = "$env:JAVA_HOME\bin;C:\Users\aaron\tools\platform-tools;$env:ANDROID_HOME\cmdline-tools\latest\bin;$env:PATH"

$sdkManager = "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat"
if (-not (Test-Path "$env:ANDROID_HOME\platforms\android-35")) {
    Write-Host "Instalando Android SDK Platform 35..." -ForegroundColor Cyan
    & $sdkManager "platforms;android-35"
}

Write-Host "Ejecutando compilacion assembleDebug..."
& ".\gradlew.bat" assembleDebug --stacktrace
