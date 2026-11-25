# SyncPass â€” app Android

App de acreditaciÃ³n offline de SyncPass: catÃ¡logo en Room, acreditaciÃ³n por QR o cÃ³digo, encuestas y sincronizaciÃ³n con la API por WorkManager.

Paquete `cl.syncpass.app`. VersiÃ³n 1.1.1 (`versionCode 3`).

## Stack

- Kotlin + Jetpack Compose
- Room, Retrofit, WorkManager
- CameraX + ML Kit para QR
- Gradle 9.6, JDK 21

## Sistema en lÃ­nea (entrega 2026-11-24)

| Pieza | URL o repositorio |
| --- | --- |
| Panel + API | <https://syncpass-production.up.railway.app> |
| Salud | <https://syncpass-production.up.railway.app/api/health> |
| Backend | [syncpass-api](https://github.com/jorgeCisternasDev/syncpass-api) |
| Web | [syncpass-web](https://github.com/jorgeCisternasDev/syncpass-web) |

La base es PostgreSQL en Railway y el panel web consume la misma API. La app usa
`MODO_DEMO=false` para que el login y la sincronizaciÃ³n vayan siempre al servidor.
En **Ajustes â†’ Actualizar catÃ¡logo** se descargan proyectos y eventos sin esperar al worker.

## ConfiguraciÃ³n de la API

La URL se define en `app/build.gradle.kts`:

```kotlin
buildConfigField("String", "API_BASE_URL", "\"https://syncpass-production.up.railway.app/\"")
buildConfigField("boolean", "MODO_DEMO", "false")
```

`MODO_DEMO=false` obliga a autenticar contra la API pÃºblica. Para trabajar sin internet,
cambiar a `true` solo en una copia local y volver a `false` antes de generar el APK de entrega.

## Compilar e instalar

```powershell
$env:JAVA_HOME = 'C:\Users\jorge\.jdks\jbr-21.0.11'
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

El release firmado requiere el KeyStore fuera del repositorio (`SYNCPASS_KEYSTORE_PROPS`). El APK de la entrega estÃ¡ en `entregas\SyncPass-1.1.0-h3-release-firmado.apk`.

## Pruebas

```powershell
$env:JAVA_HOME = 'C:\Users\jorge\.jdks\jbr-21.0.11'
.\gradlew.bat testDebugUnitTest
```

**80 pruebas, 0 fallos.** Con la JBR 25 de Android Studio, Robolectric falla por versiÃ³n de bytecode; usar JDK 21.

La cobertura JaCoCo se midiÃ³ con un init script externo: **2,6 % de lÃ­neas**, no representativo por el sandbox de Robolectric. Detalle en [docs/pruebas-app.md](docs/pruebas-app.md).

## DocumentaciÃ³n

- [Arquitectura de la app](docs/arquitectura-app.md)
- [Vistas y navegaciÃ³n](docs/vistas-app.md)
- [Demo de la app](docs/demo-app.md)
- [Pruebas y cobertura](docs/pruebas-app.md)
- [Arquitectura, contrato y requisitos del sistema](https://github.com/jorgeCisternasDev/syncpass-api/tree/main/docs)

## Demo

El kit preparado usa el evento **Demo Defensa** y tres participantes ficticios (Ana, Bruno y Carla). El reset y los cÃ³digos estÃ¡n en [docs/demo-app.md](docs/demo-app.md).

