# ⌚ Reddit for Wear OS & Mobile Companion

Cliente moderno, fluido y nativo de Reddit para smartwatches con **Wear OS 4 / 5** (optimizado para **Galaxy Watch Ultra 2 / 7 / 6 / 5 / 4**) y app complementaria para smartphones Android.

Diseñado desde cero con **Jetpack Compose for Wear OS**, soporte para **bisel rotatorio/corona**, reproductor de vídeo nativo, modo de lectura continuo en **Always On Display monocromo** y sincronización en 1 toque.

---

## ✨ Características Principales

### ⌚ En el Reloj (Wear OS)
* **📱 Lectura Continua en Always On Display (AOD)**:
  * Al bajar la muñeca, **se mantiene en pantalla exactamente el post, noticia o feed que estabas leyendo** (sin pantallazos intermediarios ni blur de Samsung).
  * **Modo Monocromo GPU Puro**: Toda la interfaz pasa instantáneamente a escala de grises de alto contraste sobre fondo negro puro (`#000000`) para ahorrar batería en paneles LTPO/Super AMOLED a 1 Hz.
* **▶️ Reproductor de Vídeos Integrado (`WearVideoPlayer`)**:
  * Reproducción fluida de vídeos de Reddit (`v.redd.it`, MP4, GIFV) directamente en la muñeca.
  * Soporte para tap-to-pause, bucle infinito y botón de activación/silenciado de audio (Mute/Unmute).
* **🔄 Botón de Refresco Rápido**:
  * Acceso directo con icono `🔄` en la cabecera para actualizar el feed sin recargar la app ni perder la posición.
* **👑 Feed Personal 100% Funcional**:
  * Carga todas tus suscripciones, subreddits favoritos y ordenación por Popular / All / Home.
* **🎨 Tarjetas Elevadas de Alto Contraste**:
  * Diseño AMOLED (`#232326`) con borde sutil (`#38383E`) y pastillas independientes para votación y recuento de comentarios.
* **🔖 Marcapáginas Vectorial**:
  * Icono de marcapáginas real (`RedditBookmarkIcon`) para guardar posts en tu cuenta de Reddit con feedback visual inmediato.
* **🌀 Control Total por Bisel Rotatorio & Corona**:
  * Scroll háptico con aceleración suave mediante Horologist Rotary.
* **💬 Árbol de Comentarios con Profundidad de Color**:
  * Comentarios anidados formateados para lectura ultra-rápida en pantallas circulares.

### 📱 En el Teléfono (Mobile Companion)
* **🚀 Sincronización Automática con el Reloj**:
  * Inicio de sesión web seguro en 1 toque que transfiere la sesión automáticamente a tu reloj vía Bluetooth (`Wearable Data Layer API`).
* **📋 Copiar y Pegar de Tokens**:
  * Botón directo **`📋 Copiar Todo`** y campo multilínea expandido para inspeccionar o pegar sesiones y Bearer tokens manualmente.
* **🎨 Nuevo Icono Oficial**:
  * Icono minimalista con la emblemática **`R` en blanco puro sobre fondo naranja Reddit (`#FF4500`)** de alta definición vectorial.

---

## 📲 Instalación Rápida (Vía Wireless ADB)

### 1. Activar Depuración Inalámbrica en tus dispositivos
1. En tu **Galaxy Watch** y en tu **teléfono**, entra en **Ajustes** -> **Opciones de desarrollador**.
2. Activa **Depuración ADB** y **Depuración inalámbrica**.
3. Anota la IP y el puerto de 5 dígitos de cada dispositivo.

### 2. Instalar en Teléfono y Reloj

```powershell
# En el Teléfono Android
.\adb-cmd.ps1 connect 192.168.1.222:<PUERTO_MOVIL>
.\adb-cmd.ps1 -s 192.168.1.222:<PUERTO_MOVIL> install -r mobile\build\outputs\apk\debug\mobile-debug.apk

# En el Galaxy Watch Ultra
.\adb-cmd.ps1 connect 192.168.1.176:<PUERTO_RELOJ>
.\adb-cmd.ps1 -s 192.168.1.176:<PUERTO_RELOJ> install -r wear\build\outputs\apk\debug\wear-debug.apk
```

---

## 🛠️ Compilación Local

El proyecto incluye un entorno de compilación totalmente portátil y automatizado en PowerShell:

```powershell
# Compila los módulos Mobile y Wear OS generando los APKs
.\build.ps1
```

---

## 🏗️ Arquitectura Técnica

* **Lenguaje**: Kotlin 2.0
* **UI Toolkit**: Jetpack Compose for Wear OS + Wear Material 3 + Horologist 0.6.x + Compose Material 3 (Mobile)
* **Networking**: Ktor HTTP Client (CIO engine) + OkHttp + Kotlinx Serialization
* **Video & Media**: Android Native `VideoView` / `MediaPlayer` SurfaceView + Coil Compose
* **Display Engine**: BlendMode GPU Grayscale Shader + AmbientModeSupport
* **Almacenamiento Seguro**: EncryptedSharedPreferences (AES-256-GCM)
* **Inter-device Sync**: Google Play Services Wearable API (`MessageClient` + `DataClient`)

---

## 📄 Licencia

Desarrollado bajo licencia libre para uso personal y comunitario.
