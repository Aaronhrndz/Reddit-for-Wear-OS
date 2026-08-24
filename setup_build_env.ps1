$toolsDir = "$env:USERPROFILE\tools"
if (-not (Test-Path $toolsDir)) {
    New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null
}

$adbExe = "$toolsDir\platform-tools\adb.exe"
if (-not (Test-Path $adbExe)) {
    Write-Host "Descargando Android platform-tools..."
    $zipPath = "$toolsDir\platform-tools.zip"
    & curl.exe -L "https://dl.google.com/android/repository/platform-tools-latest-windows.zip" -o "$zipPath"
    Expand-Archive -Path $zipPath -DestinationPath $toolsDir -Force
    Remove-Item $zipPath -Force -ErrorAction SilentlyContinue
}

Write-Host "ADB Status:"
& $adbExe version

# Check JDK
$javaExe = "$toolsDir\jdk-17\bin\java.exe"
if (-not (Test-Path $javaExe)) {
    Write-Host "Descargando OpenJDK 17 portable..."
    $jdkZip = "$toolsDir\openjdk-17.zip"
    Remove-Item $jdkZip -Force -ErrorAction SilentlyContinue
    & curl.exe -L "https://aka.ms/download-jdk/microsoft-jdk-17.0.12-windows-x64.zip" -o "$jdkZip"
    
    if (Test-Path "$toolsDir\jdk-temp") { Remove-Item "$toolsDir\jdk-temp" -Recurse -Force }
    Expand-Archive -Path $jdkZip -DestinationPath "$toolsDir\jdk-temp" -Force
    $extracted = Get-ChildItem "$toolsDir\jdk-temp" -Directory | Select-Object -First 1
    Move-Item "$($extracted.FullName)" "$toolsDir\jdk-17" -Force
    Remove-Item "$toolsDir\jdk-temp" -Recurse -Force -ErrorAction SilentlyContinue
    Remove-Item $jdkZip -Force -ErrorAction SilentlyContinue
}

Write-Host "Java Status:"
& $javaExe -version

# Android cmdline-tools
$sdkDir = "$env:USERPROFILE\tools\android-sdk"
$sdkManager = "$sdkDir\cmdline-tools\latest\bin\sdkmanager.bat"
if (-not (Test-Path $sdkManager)) {
    Write-Host "Descargando Android SDK cmdline-tools..."
    $cmdlineZip = "$toolsDir\cmdline-tools.zip"
    & curl.exe -L "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip" -o "$cmdlineZip"
    
    if (Test-Path "$toolsDir\cmdline-temp") { Remove-Item "$toolsDir\cmdline-temp" -Recurse -Force }
    Expand-Archive -Path $cmdlineZip -DestinationPath "$toolsDir\cmdline-temp" -Force
    New-Item -ItemType Directory -Force -Path "$sdkDir\cmdline-tools\latest" | Out-Null
    Copy-Item "$toolsDir\cmdline-temp\cmdline-tools\*" "$sdkDir\cmdline-tools\latest\" -Recurse -Force
    Remove-Item "$toolsDir\cmdline-temp" -Recurse -Force -ErrorAction SilentlyContinue
    Remove-Item $cmdlineZip -Force -ErrorAction SilentlyContinue
}

Write-Host "Entorno CLI listo."
