param (
    [Parameter(Mandatory=$false)]
    [string]$PhoneIp = "",
    [Parameter(Mandatory=$false)]
    [int]$Port = 5555,
    [Parameter(Mandatory=$false)]
    [string]$ApkPath = "$PSScriptRoot\mobile\build\outputs\apk\debug\mobile-debug.apk"
)

$toolsDir = "$env:USERPROFILE\tools\platform-tools"
$adb = "$toolsDir\adb.exe"

if (-not (Test-Path $adb)) {
    Write-Host "Instalando ADB platform-tools..." -ForegroundColor Cyan
    & "$PSScriptRoot\setup_build_env.ps1"
}

if (-not (Test-Path $ApkPath)) {
    Write-Host "Compilando app Companion para móvil..." -ForegroundColor Yellow
    & "$PSScriptRoot\build-apk.ps1"
}

if ([string]::IsNullOrWhiteSpace($PhoneIp)) {
    Write-Host "=== Instalador Reddit for Wear Companion (Teléfono) ===" -ForegroundColor Green
    Write-Host "1. En tu Smartphone:" -ForegroundColor Yellow
    Write-Host "   Ajustes -> Opciones de desarrollador -> Depuración inalámbrica"
    Write-Host "   Mira la IP y el Puerto que aparecen en pantalla."
    Write-Host ""
    $PhoneIp = Read-Host "Introduce la IP del teléfono (ej. 192.168.1.45)"
    $portInput = Read-Host "Introduce el Puerto (Enter para 5555)"
    if (-not [string]::IsNullOrWhiteSpace($portInput)) {
        $Port = [int]$portInput
    }
}

$target = "${PhoneIp}:${Port}"
Write-Host "Conectando con Teléfono en $target ..." -ForegroundColor Cyan
& $adb disconnect
& $adb connect $target

Start-Sleep -Seconds 2

$devices = & $adb devices
Write-Host $devices

if ($devices -match $PhoneIp) {
    Write-Host "Teléfono conectado. Instalando Companion ($ApkPath)..." -ForegroundColor Green
    & $adb -s $target install -r $ApkPath
    Write-Host "¡Instalación completada en teléfono!" -ForegroundColor Green
    Write-Host "Lanzando Reddit for Wear Companion..." -ForegroundColor Cyan
    & $adb -s $target shell am start -n com.reddit.wearos.companion.debug/com.reddit.wearos.companion.CompanionMainActivity
} else {
    Write-Host "Error: No se pudo conectar al teléfono en $target." -ForegroundColor Red
}
