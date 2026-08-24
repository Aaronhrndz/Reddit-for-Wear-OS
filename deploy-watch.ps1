$env:JAVA_HOME = "C:\Users\aaron\tools\jdk-17"
$env:ANDROID_HOME = "C:\Users\aaron\tools\android-sdk"
$env:PATH = "$env:JAVA_HOME\bin;C:\Users\aaron\tools\platform-tools;$env:ANDROID_HOME\cmdline-tools\latest\bin;$env:PATH"

Write-Host "Compilando modulo :wear..." -ForegroundColor Cyan
& ".\gradlew.bat" :wear:assembleDebug

if ($LASTEXITCODE -eq 0) {
    Write-Host "Desplegando en Galaxy Watch..." -ForegroundColor Green
    & ".\adb-cmd.ps1" connect 192.168.1.176:42387
    & ".\adb-cmd.ps1" -s 192.168.1.176:42387 install -r "wear\build\outputs\apk\debug\wear-debug.apk"
    & ".\adb-cmd.ps1" -s 192.168.1.176:42387 shell am start -n com.reddit.wearos.debug/com.reddit.wearos.MainActivity
    Write-Host "¡Despliegue completado con éxito!" -ForegroundColor Green
}
