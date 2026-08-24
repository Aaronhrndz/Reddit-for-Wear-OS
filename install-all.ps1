$adb = "C:\Users\aaron\tools\platform-tools\adb.exe"
$watchTarget = "192.168.1.176:42387"
$phoneTarget = "192.168.1.222:42775"

$wearApk = "$PSScriptRoot\wear\build\outputs\apk\debug\wear-debug.apk"
$mobileApk = "$PSScriptRoot\mobile\build\outputs\apk\debug\mobile-debug.apk"

Write-Host "=== Instalando en Galaxy Watch Ultra ($watchTarget) ===" -ForegroundColor Cyan
& $adb -s $watchTarget install -r $wearApk

Write-Host "=== Instalando en Smartphone ($phoneTarget) ===" -ForegroundColor Cyan
& $adb -s $phoneTarget install -r $mobileApk

Write-Host "=== Lanzando apps en ambos dispositivos ===" -ForegroundColor Green
& $adb -s $watchTarget shell am start -n com.reddit.wearos.debug/com.reddit.wearos.MainActivity
& $adb -s $phoneTarget shell am start -n com.reddit.wearos.companion.debug/com.reddit.wearos.companion.CompanionMainActivity

Write-Host "=== ¡Instalación y Despliegue Completados! ===" -ForegroundColor Green
