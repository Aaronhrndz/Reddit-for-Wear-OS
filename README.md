# ⌚ Reddit Wear OS (Galaxy Watch Ultra / 7 / 6 / 5 / 4)

Cliente nativo y moderno de Reddit para smartwatches con **Wear OS 4 / 5**, diseñado específicamente para pantallas redondas (AMOLED) y control mediante **bisel rotatorio** (físico y táctil) con Jetpack Compose y Horologist.

---

## ✨ Características

* **Navegación Circular & Scroll por Bisel**:
  * Control total con la corona / bisel táctil rotatorio del Galaxy Watch (`rotaryWithScroll`) y respuesta háptica.
  * Curvatura y adaptación de márgenes para pantallas redondas.
* **Autenticación Oficial & BYOK (Bring Your Own Key)**:
  * 100% gratuito sin límites comerciales utilizando tu propio Client ID personal.
  * Login delegable a teléfono móvil emparejado mediante `RemoteAuthClient`.
  * Modo Invitado (solo lectura) disponible sin cuenta.
* **Experiencia de Lectura Completa**:
  * **Feeds**: Home, Popular, Todos y Subreddits personalizados.
  * **Visualizador de Posts**: Renderizado de texto formateado, previsualización de imágenes y recuento de votos.
  * **Árbol de Comentarios**: Comentarios anidados con código de color de profundidad optimizados para muñeca.
  * **Acciones Rápidas**: Upvote / Downvote con guardado de estado en vivo.
* **Wear OS Tile**:
  * Tile glanceable en el carrusel del reloj con acceso rápido a tus comunidades favoritas.

---

## 🔑 Configuración de tu API Key de Reddit (2 minutos, Gratis)

1. Entra en tu navegador en [reddit.com/prefs/apps](https://www.reddit.com/prefs/apps).
2. Haz clic en **"create another app..."** (o *are you a developer? create an app...*).
3. Rellena los campos:
   * **name**: `RedditWear` (o el nombre que quieras).
   * **type**: Selecciona **installed app**.
   * **description**: App para Wear OS.
   * **about url**: Puedes dejarlo vacío o tu perfil.
   * **redirect uri**: Introduce exactamente: `redditwear://auth`
4. Haz clic en **create app**.
5. Copia el string de caracteres que aparece debajo del nombre de tu app (debajo de *installed app*). Ese es tu **`Client ID`**.

---

## 📲 Instalación en Galaxy Watch (Vía Wireless ADB)

### Paso 1: Activar Depuración en el Reloj
1. En tu Galaxy Watch, ve a **Ajustes** -> **Información del reloj** -> **Software** -> Pulsa 7 veces sobre **Versión de software** hasta activar *Opciones de desarrollador*.
2. Vuelve a **Ajustes** -> **Opciones de desarrollador**.
3. Activa **Depuración ADB** y **Depuración inalámbrica (Wireless Debugging)**.
4. Conecta el reloj a la misma red WiFi que tu PC y anota la **IP** y el **Puerto** que muestra la pantalla.

### Paso 2: Instalar la App desde tu PC
Ejecuta en PowerShell:

```powershell
.\install-watch.ps1 -WatchIp <IP_DE_TU_RELOJ> -Port <PUERTO>
```

El script se encarga automáticamente de:
1. Conectar ADB inalámbrico con el Galaxy Watch.
2. Compilar e instalar la APK directamente.
3. Lanzar la aplicación en tu pantalla.

---

## 🛠️ Compilación Local (Sin Android Studio)

No necesitas instalar Android Studio. El proyecto incluye scripts CLI automatizados:

```powershell
# Prepara herramientas portables y compila APK
.\build-apk.ps1
```

O si prefieres compilar en la nube, el repositorio incluye **GitHub Actions** (`.github/workflows/build.yml`) que compila la APK en cada commit y la deja lista para descargar.

---

## 🏗️ Arquitectura Técnica

* **Lenguaje**: Kotlin 2.0
* **UI Toolkit**: Jetpack Compose for Wear OS + Wear Material 3 + Horologist 0.6.x
* **Networking**: Ktor HTTP Client + Kotlinx Serialization
* **Auth**: OAuth2 PKCE + AndroidX Wear Remote Interactions
* **Caché y Almacenamiento**: EncryptedSharedPreferences (AES256)
* **Carga de Imágenes**: Coil Compose
