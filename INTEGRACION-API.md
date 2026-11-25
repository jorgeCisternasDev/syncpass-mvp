# Integracion SyncPassMVP con syncpass-api

Fecha: 2026-11-16
App activa: `C:\Users\jorge\AndroidStudioProjects\SyncPassMVP` (`cl.syncpass.app`)
App archivada: `DSY1105\archivo\SyncPass-cl.duoc` (`cl.duoc.syncpass`)

## Configuracion

`app/build.gradle.kts`:

```kotlin
buildConfigField("String", "API_BASE_URL", "\"https://syncpass-production.up.railway.app/\"")
buildConfigField("boolean", "MODO_DEMO", "false")
```

- `MODO_DEMO=false`: el login y la sincronización van siempre contra la API pública; si no responde, se muestra el error y se reintenta.
- `INTERNET` declarado en `AndroidManifest.xml`.
- HTTPS no necesita una excepción en `network_security_config.xml`; la configuración HTTP solo queda para desarrollo local.

## Contrato remoto

- `POST api/auth/login`
  - Request: `{"email":"...","password":"..."}`
  - Response plana: `{"accessToken":"...","refreshToken":"...","idToken":"..."}`
- `POST api/sync/lote`
  - Request: `{"asistencias":[...],"encuestas":[...]}`
  - Response plana: `{"aceptados":N}`
  - Asistencia: `eventoId, usuarioId, timestamp, validado, latitud, longitud`
  - Encuesta: `eventoId, usuarioId, satisfaccion, utilidad, comentario, fecha`

El `accessToken` viaja como `Authorization: Bearer <token>` en cada request.

## Sesion

- `SessionStore` guarda accessToken, refreshToken, idToken, email, nombre y rol en
  `SharedPreferences` (`syncpass_session`).
- `TokenInterceptor` lee el token desde SharedPreferences en cada request; no hay
  cache en memoria que se pierda al reiniciar.
- Al abrir la app, `AuthViewModel` restaura la sesion. Si el accessToken esta
  expirado, se descarta y vuelve el login.
- Logout limpia las preferencias.

## Sincronizacion

- Cada acreditacion se inserta en Room con `sincronizado = 0`.
- Cada encuesta de satisfaccion se inserta con `sincronizado = 0`.
- En **Ajustes > Sincronizacion con la API** se ve el conteo pendiente y el boton
  **Sincronizar**.
- El boton envia solo lo pendiente, y tras un 2xx marca esas filas con
  `sincronizado = 1`.
- Si falla la red o el servidor, no marca nada: el siguiente intento reenvia el
  mismo lote. El backend es idempotente por `(eventoId, usuarioId)`, por lo que
  reenviar no duplica.
- Si no hay pendientes, el boton queda deshabilitado y el mensaje lo indica.

Nota: `eventoId` y `usuarioId` son los ids locales de Room. El backend actual no
valida esas claves contra su catalogo, solo las usa como clave de idempotencia.

## Pruebas

Comando:

```powershell
$env:JAVA_HOME="C:\Users\jorge\.jdks\jbr-21.0.11"
.\gradlew.bat testDebugUnitTest
```

Resultado: **55 pruebas, 0 fallos, 0 errores**.

Cobertura nueva:

- `ApiModelsTest`: serializacion/deserializacion exacta de login y sync/lote,
  incluyendo respuestas planas.
- `JwtRolesTest`: Coordinador/Monitor/Asistente, precedencia de roles, `exp`,
  nombre/email y tokens invalidos.
- `SessionStoreTest` (Robolectric): el token y el rol sobreviven a una nueva
  instancia del store; logout limpia; sesion local de respaldo persiste.
- `AuthRepositoryTest` (Robolectric): login remoto persiste token y rol del JWT,
  restauracion tras "reinicio", token expirado se descarta, fallback demo solo
  con `MODO_DEMO=true` y credenciales invalidas no usan fallback.
- `SyncMappingTest`: mapeo exacto de entidades Room a los DTO remotos.

Compilacion: `.\gradlew.bat :app:assembleDebug` -> BUILD SUCCESSFUL.

## Verificación en producción (2026-11-24)

- APK release `SyncPass-1.1.1-online-release.apk` compilado con
  `API_BASE_URL=https://syncpass-production.up.railway.app/` y `MODO_DEMO=false`.
- `POST /api/auth/login` con `coordinador@syncpass.cl / Coord.2026` devolvió token.
- `POST /api/sync/lote` con una asistencia y una encuesta devolvió `aceptados=2`.
- `GET /api/dashboard/resumen` pasó de 0 a 1 asistencia y 1 encuesta.
- El reenvío del mismo lote devolvió `duplicados=2` y no cambió los totales.
- El panel web mostró los mismos totales; la base PostgreSQL sobrevivió a un reinicio del servicio.

## Pendiente para la prueba en emulador

Para probar contra producción, usar la URL pública y omitir los pasos 1 y 2; el APK de entrega ya apunta ahí.

1. Levantar `syncpass-api` en `8080` con perfil `local` y `JWT_SECRET` (solo para la prueba local).
2. Arrancar el emulador, verificar red y que alcance `192.168.100.191:8080`.
3. Instalar `app-debug.apk` y hacer login con `monitor@syncpass.cl / Monitor.2026`.
4. Verificar que entra a la interfaz de Monitor (rol leido del JWT).
5. Crear o abrir un evento, inscribir/acreditar un participante y confirmar el
   pendiente en Ajustes.
6. Pulsar **Sincronizar** y verificar en la API (`/api/dashboard/resumen`) que
   `asistencias` sube en 1.
7. Para idempotencia, volver a marcar la misma fila local como pendiente
   (`UPDATE attendance SET sincronizado=0 WHERE ...`) y sincronizar de nuevo;
   el dashboard debe seguir con el mismo numero de asistencias.
8. Cerrar y reabrir la app: la sesion debe seguir activa y el token persistido
   debe permitir sincronizar sin volver a hacer login.
9. Probar el respaldo demo: detener la API (con `MODO_DEMO=true`) e iniciar con
   `monitor / 1234`; debe entrar a la interfaz de Monitor.
