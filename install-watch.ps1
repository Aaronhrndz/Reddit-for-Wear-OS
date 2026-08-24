param (
    [Parameter(Mandatory=$false)]
    [string]$WatchIp = "",
    [Parameter(Mandatory=$false)]
    [int]$Port = 5555,
    [Parameter(Mandatory=$false)]
    [string]$ApkPath = "$PSScriptRoot\app\build\outputs\apk\debug\app-debug.apk"
)

$toolsDir = "$env:USERPROFILE\tools\platform-tools"
$adb = "$toolsDir\adb.exe"

if (-not (Test-Path $adb)) {
    Write-Host "Instalando ADB platform-tools..." -ForegroundColor Cyan
    & "$PSScriptRoot\setup_build_env.ps1"
}

if (-not (Test-Path $ApkPath)) {
    Write-Host "APK no encontrada en $ApkPath. Compilando primero..." -ForegroundColor Yellow
    & "$PSScriptRoot\build-apk.ps1"
}

if ([string]::IsNullOrWhiteSpace($WatchIp)) {
    Write-Host "=== Instalador Reddit Wear OS para Galaxy Watch ===" -ForegroundColor Green
    Write-Host "1. En tu Galaxy Watch Ultra:" -ForegroundColor Yellow
    Write-Host "   Ajustes -> Opciones de desarrollador -> Depuración inalámbrica (Activar)"
    Write-Host "   Mira la IP y el Puerto que aparecen en pantalla."
    Write-Host ""
    $WatchIp = Read-Host "Introduce la IP del reloj (ej. 192.168.1.50)"
    $portInput = Read-Host "Introduce el Puerto (Enter para 5555)"
    if (-not [string]::IsNullOrWhiteSpace($portInput)) {
        $Port = [int]$portInput
    }
}

$target = "${WatchIp}:${Port}"
Write-Host "Conectando con Galaxy Watch en $target ..." -ForegroundColor Cyan
& $adb disconnect
& $adb connect $target

Start-Sleep -Seconds 2

$devices = & $adb devices
Write-Host $devices

if ($devices -match $WatchIp) {
    Write-Host "Reloj conectado. Instalando Reddit Wear OS ($ApkPath)..." -ForegroundColor Green
    & $adb -s $target install -r $ApkPath
    Write-Host "¡Instalación completada con éxito en Galaxy Watch!" -ForegroundColor Green
    Write-Host "Lanzando aplicación en el reloj..." -ForegroundColor Cyan
    & $adb -s $target shell am start -n com.reddit.wearos.debug/com.reddit.wearos.MainActivity
} else {
    Write-Host "Error: No se pudo conectar al reloj en $target." -ForegroundColor Red
    Write-Host "Asegúrate de aceptar el mensaje de 'Permitir depuración' en la pantalla del reloj." -ForegroundColor Yellow
}
