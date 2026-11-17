# SyncPass 1.1.0 - actualización de Encuestas

Aplicación Android offline-first para eventos, acreditación y encuestas. Interfaz en español, Room local y sincronización opcional con la API SyncPass. Sin cuentas pagadas ni secretos embebidos.

## Entrega

- Proyecto: `C:\Users\jorge\AndroidStudioProjects\SyncPassMVP`
- APK 1.1.0: `C:\Users\jorge\AndroidStudioProjects\SyncPassMVP\SyncPass-1.1.0-debug.apk`
- APK con nombre estable: `C:\Users\jorge\AndroidStudioProjects\SyncPassMVP\SyncPass-debug.apk`
- Paquete: `cl.syncpass.app`
- Versión: `1.1.0` (`versionCode 2`)
- minSdk 27 · targetSdk/compileSdk 37
- Debug firmado con clave debug de Android
- SHA-256 del APK 1.1.0:
  `9C20FDFC63EEB08F548CAB42714B85173B99BA6A696AD52620AB1400CBA63E85`

## Novedad 1.1.0: Encuestas

La pestaña **Encuestas** ahora abre con dos acciones principales:

1. **Crear encuesta**
   - Título y descripción.
   - Asociación opcional a un evento o encuesta general.
   - Hasta 20 preguntas.
   - Preguntas de **texto corto** o **escala 1–5**.
   - Preguntas obligatorias u opcionales.
   - Guardado local en Room.

2. **Hacer una encuesta**
   - Pestaña **Satisfacción**: encuestas de satisfacción por evento, pendientes y respondidas.
   - Pestaña **Creadas**: encuestas personalizadas activas.
   - Si no hay satisfacción pendiente y sí encuestas creadas, abre directamente en **Creadas**.
   - Responder una encuesta propia con nombre opcional.
   - Las respuestas se guardan en el dispositivo.

Además:
- Resultados por encuesta creada: preguntas, respuestas, nombre, fecha y valores.
- Pausar/reactivar una encuesta para bloquear o permitir respuestas.
- Eliminar una encuesta con sus preguntas y respuestas (confirmación).
- Migración de base de datos **v1 → v2**, conservando eventos, participantes, acreditaciones y encuestas existentes.
- Las encuestas de satisfacción anteriores siguen funcionando y sus indicadores por evento no cambian.

## Pruebas ejecutadas

Comandos:

```powershell
$env:JAVA_HOME="C:\Users\jorge\.jdks\jbr-21.0.11"
.\gradlew.bat testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat assembleDebug
```

Resultados finales:

- **55 pruebas unitarias, 0 fallos** (incluye contrato DTO, roles JWT, token persistido y mapeo de sincronización).
- **7 pruebas instrumentadas, 0 fallos** en emulador Android API 37.
- `assembleDebug`: **BUILD SUCCESSFUL**.

Cobertura nueva:
- Crear encuesta, responderla y consultar resultados en UI Compose.
- Crear/responder/pausar/borrar encuestas a nivel de repositorio y Room.
- Validación de título, preguntas, tipos y respuestas obligatorias.
- Migración v1→v2 verificada sobre una base real con 1 evento, 1 participante, 1 acreditación y 1 encuesta de satisfacción.
- Instalación sucesiva 1.0.0 → 1.1.0 verificada en el emulador.
- Suite anterior (acreditación, QR, CSV, persistencia) sigue pasando.

## Verificación manual

- APK 1.1.0 instalado y abierto en emulador API 37.
- Revisadas visualmente: pantalla de Encuestas con “Crear encuesta” / “Hacer una encuesta”, formulario de creación, pestañas de “Hacer una encuesta”.
- El flujo completo de crear, responder y ver resultados está verificado por la prueba instrumentada, no por interacción manual completa.

## Limitaciones reales

- **Encuestas locales**: no hay backend ni enlace público; las encuestas creadas se responden en el mismo dispositivo.
- No se pueden editar las preguntas de una encuesta ya creada; sí pausarla, reactivarla o eliminarla.
- Los resultados de encuestas personalizadas no se mezclan con los indicadores de satisfacción del evento.
- La sincronización cubre acreditaciones y encuestas de satisfacción. Las encuestas personalizadas siguen siendo locales.
- El login y la sincronización requieren la API levantada; con `MODO_DEMO=true` el login local es respaldo si la API no responde.
- Integración con la API validada por pruebas unitarias y compilación; falta la corrida end-to-end en emulador.
- Escaneo QR con cámara física no verificado; sí se verificó generación ZXing + decodificación ML Kit.
- GPS/permiso de ubicación no probado manualmente.
- APK **debug**, no release firmada para tienda.

## Instalar o actualizar

Con el teléfono conectado por USB y depuración USB activa:

```powershell
adb install -r "C:\Users\jorge\AndroidStudioProjects\SyncPassMVP\SyncPass-1.1.0-debug.apk"
```

También puedes copiar el APK al teléfono y abrirlo. Al instalar sobre 1.0.0 se conservan los datos por la migración v1→v2.
