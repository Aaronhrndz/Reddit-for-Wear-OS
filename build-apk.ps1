$toolsDir = "$env:USERPROFILE\tools"
$jdkPath = "$toolsDir\jdk-17"
$sdkPath = "$toolsDir\android-sdk"

if (-not (Test-Path "$jdkPath\bin\java.exe") -or -not (Test-Path "$toolsDir\platform-tools\adb.exe")) {
    Write-Host "Configurando entorno de compilación (JDK 17 + SDK CLI + ADB)..." -ForegroundColor Cyan
    & "$PSScriptRoot\setup_build_env.ps1"
}

$env:JAVA_HOME = $jdkPath
$env:ANDROID_HOME = $sdkPath
$env:PATH = "$jdkPath\bin;$toolsDir\platform-tools;$sdkPath\cmdline-tools\latest\bin;$env:PATH"

# Descargar gradle 8.7 bin
$gradleHome = "$toolsDir\gradle-8.7"
$gradleBat = "$gradleHome\bin\gradle.bat"
if (-not (Test-Path $gradleBat)) {
    Write-Host "Descargando Gradle 8.7..." -ForegroundColor Cyan
    $gradleZip = "$toolsDir\gradle-8.7-bin.zip"
    & curl.exe -L "https://services.gradle.org/distributions/gradle-8.7-bin.zip" -o "$gradleZip"
    Expand-Archive -Path $gradleZip -DestinationPath "$toolsDir\gradle-temp" -Force
    $extracted = Get-ChildItem "$toolsDir\gradle-temp" -Directory | Select-Object -First 1
    Move-Item "$($extracted.FullName)" "$gradleHome" -Force
    Remove-Item "$toolsDir\gradle-temp" -Recurse -Force -ErrorAction SilentlyContinue
    Remove-Item $gradleZip -Force -ErrorAction SilentlyContinue
}

Write-Host "Generando Gradle Wrapper 8.7..." -ForegroundColor Cyan
Set-Location $PSScriptRoot
& $gradleBat wrapper --gradle-version 8.7

Write-Host "Compilando ambas APKs (Wear OS + Companion Móvil)..." -ForegroundColor Green
& "$PSScriptRoot\gradlew.bat" assembleDebug --no-daemon

if ($LASTEXITCODE -eq 0) {
    Write-Host "==================================================" -ForegroundColor Green
    Write-Host "¡APKs compiladas con éxito!" -ForegroundColor Green
    Write-Host "1. Reloj:    $PSScriptRoot\wear\build\outputs\apk\debug\wear-debug.apk" -ForegroundColor Cyan
    Write-Host "2. Teléfono: $PSScriptRoot\mobile\build\outputs\apk\debug\mobile-debug.apk" -ForegroundColor Cyan
    Write-Host "==================================================" -ForegroundColor Green
} else {
    Write-Host "Error en la compilación. Revisa los logs arriba." -ForegroundColor Red
}
