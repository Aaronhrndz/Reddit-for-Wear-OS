# ⌚ Red for Reddit (Wear OS & Mobile Companion)

> **Disclaimer**: *Red for Reddit is an unofficial, community-driven open-source client. It is not affiliated with, endorsed, sponsored, or associated with Reddit Inc. in any way.*

Cliente moderno, fluido y nativo para smartwatches con **Wear OS 4 / 5** (optimizado para **Galaxy Watch Ultra 2 / 7 / 6 / 5 / 4**, Pixel Watch y TicWatch) y app complementaria para smartphones Android.

Diseñado desde cero con **Jetpack Compose for Wear OS**, soporte para **bisel rotatorio/corona**, reproductor de vídeo nativo, modo de lectura continuo en **Always On Display monocromo** y sincronización en 1 toque.

---

## ✨ Características Principales

### ⌚ En el Reloj (Wear OS)
* **📱 Lectura Continua en Always On Display (AOD)**:
  * Al bajar la muñeca, **se mantiene en pantalla exactamente el post, noticia o feed que estabas leyendo** (sin pantallazos intermediarios ni blur de Samsung).
  * **Modo Monocromo GPU Puro**: Toda la interfaz pasa instantáneamente a escala de grises de alto contraste sobre fondo negro puro (`#000000`) para ahorrar batería en paneles LTPO/Super AMOLED a 1 Hz.
* **▶️ Reproductor de Vídeos Integrado con Audio HLS (`WearVideoPlayer`)**:
  * Reproducción fluida con ExoPlayer (Media3) priorizando streams oficiales **HLS (`HLSPlaylist.m3u8`)** con audio muxed CMAF, con fallback automático a MP4 progresivo.
  * Soporte para tap-to-pause, bucle infinito y control de audio (Mute/Unmute).
* **🖼️ Carrusel de Galerías de Fotos (`PostImageCarousel`)**:
  * Soporte nativo para posts con álbumes múltiples (`gallery_data` + `media_metadata`), con indicador `1 / N` en el feed y visor interactivo con controles de avance/retroceso en el detalle del post.
* **🕒 Subtítulos con Tiempo Relativo (`formatRelativeTime`)**:
  * Todas las tarjetas de publicaciones (`FeedScreen`, `SavedScreen`, `SearchScreen`), vista de detalle (`PostDetailScreen`) y árbol de comentarios muestran hace cuánto se publicó (`ahora`, `hace 15m`, `hace 4h`, `hace 3d`, etc.) junto al subreddit y autor (`r/subreddit • hace 3h • u/autor`).
* **✏️ Gestión Completa de Comentarios (Responder, Editar y Borrar)**:
  * Responde a publicaciones o comentarios con teclado en pantalla o dictado por voz, y edita o elimina tus propios comentarios con confirmación en 2 toques (`¿Borrar?`).
* **🔎 Buscador con Filtros de Ordenación y Tiempo**:
  * Busca comunidades (`Feeds`) o publicaciones (`Posts`) filtrando en 1 toque por **Orden** (*Relevancia, Nuevos, Destacados, Top, Comentarios*) y **Rango de Tiempo** (*Siempre, Año, Mes, Semana, Hoy*).
* **⌚ 3 Tiles Interactivos para Wear OS (Sin métricas de Karma innecesarias)**:
  * **🔥 Feed Personal (`RedditTileService`)**: Muestra los 3 posts destacados de tu feed actual con puntuación, subreddit y tiempo relativo.
  * **🔖 Mis Guardados (`SavedTileService`)**: Acceso rápido a tus últimas 3 publicaciones guardadas directamente desde el carrusel de Tiles.
  * **⚡ Titulares Flash (`NewsTileService`)**: Últimas noticias globales en tiempo real.
* **🔄 Botón de Refresco Rápido**:
  * Acceso directo con icono `🔄` en la cabecera para actualizar el feed sin recargar la app ni perder la posición.
* **👑 Feed Personal y Guardados 100% Funcionales**:
  * Carga todas tus suscripciones, subreddits favoritos, posts guardados (con resolución automática de usuario y reintento inteligente) y ordenación por Popular / All / Home.
* **🎨 Tarjetas Elevadas de Alto Contraste**:
  * Diseño AMOLED (`#232326`) con borde sutil (`#38383E`) y pastillas independientes para votación y recuento de comentarios.
* **🔖 Marcapáginas Vectorial**:
  * Icono de marcapáginas real (`RedditBookmarkIcon`) para guardar posts en tu cuenta de Reddit con feedback visual inmediato.
* **🌀 Control Total por Bisel Rotatorio & Corona**:
  * Scroll háptico con aceleración suave mediante Horologist Rotary.

### 📱 En el Teléfono (Mobile Companion Simplificado) & Persistencia de Sesión
* **✨ Interfaz Minimalista en 1 Toque**:
  * Pantalla principal limpia con estado del reloj Bluetooth, cuenta activa (`u/usuario`), botón directo **"Sincronizar con el Reloj Ahora"** y acceso a login/cambio de cuenta. Las herramientas técnicas (inspección de cookie/token) quedan recogidas bajo un desplegable opcional *"Opciones avanzadas"*.
* **🔐 Sesión Persistente de Larga Duración (`reddit_session` — 6 a 12 meses)**:
  * **Extracción Inteligente de Cookies**: Al iniciar sesión en `old.reddit.com/login`, la app companion fuerza automáticamente la casilla *"Remember Me"* (`rem = true`) y prioriza la cookie firmada de largo plazo `reddit_session` (válida de 6 a 12 meses) en lugar del JWT efímero `token_v2` (que caduca a las 24 horas).
  * **Renovación Autónoma en el Reloj (0 dependencia del móvil)**: El reloj almacena `reddit_session` y su fecha real de expiración (`exp` decodificado del JWT) en `TokenStorage`, y refresca por sí mismo el `modhash` (`uh`) y los datos de sesión contra `https://old.reddit.com/api/me.json` sin necesitar conexión con el teléfono en los días o meses siguientes.
  * **Auto-Recuperación y Sincronización Bluetooth**: Al abrir la app del móvil, recupera automáticamente la cookie activa del `CookieManager` del sistema y la sincroniza al reloj conectado vía `Wearable Data Layer API` (`/reddit/auth_sync`).
* **🎨 Nuevo Icono Oficial**:
  * Icono minimalista con la emblemática **`R` en blanco puro sobre fondo naranja Reddit (`#FF4500`)** de alta definición vectorial.

---

## 📲 Instalación Rápida (Vía USB / Wireless ADB)

### 1. Activar Depuración Inalámbrica en tus dispositivos
1. En tu **Galaxy Watch** y en tu **teléfono**, entra en **Ajustes** -> **Opciones de desarrollador**.
2. Activa **Depuración ADB** y **Depuración inalámbrica**.
3. Anota la IP y el puerto de 5 dígitos de cada dispositivo.

### 2. Instalar en Teléfono y Reloj

```powershell
# En el Teléfono Android (por USB o Wi-Fi)
adb -s <SERIAL_O_IP_MOVIL> install -r mobile\build\outputs\apk\debug\mobile-debug.apk

# En el Galaxy Watch (Ultra / 6 / 5 / 4)
adb connect 192.168.1.173:<PUERTO_RELOJ>
adb -s 192.168.1.173:<PUERTO_RELOJ> install -r wear\build\outputs\apk\debug\wear-debug.apk
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
* **UI Toolkit**: Jetpack Compose for Wear OS + Wear Material + Horologist 0.6.x + Compose Material 3 (Mobile)
* **Networking**: Ktor HTTP Client (CIO engine) + OkHttp + Kotlinx Serialization (`old.reddit.com` / `oauth.reddit.com` dual-mode)
* **Autenticación Persistente**: Cookie firmada `reddit_session` + `modhash` (`uh`) CSRF + soporte dual OAuth2 `Bearer`
* **Video & Media**: Media3 ExoPlayer (`HlsMediaSource` + `ProgressiveMediaSource`) + Coil Compose
* **Display Engine**: BlendMode GPU Grayscale Shader + AmbientModeSupport
* **Almacenamiento Seguro**: `TokenStorage` (`SharedPreferences` con decodificación de expiración JWT `exp`)
* **Inter-device Sync**: Google Play Services Wearable API (`MessageClient` + `DataClient` + `CapabilityClient`)

---

## 📄 Licencia

Este proyecto está protegido bajo la Licencia **GNU General Public License v3.0 (GPLv3)**. Consulta el archivo [LICENSE](LICENSE) para más detalles. Todo derivado o modificación de este software debe mantenerse de código abierto y bajo esta misma licencia.
