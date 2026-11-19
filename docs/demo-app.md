# Demo — app Android

Duración: 6 a 8 minutos. La demo completa del sistema está en el repo de la API:
[guion completo](https://github.com/jorgeCisternasDev/syncpass-api/blob/main/docs/demo.md).

## Kit fijo (emulador preparado)

| Campo | Valor |
| --- | --- |
| Evento | **Demo Defensa**, 10 cupos, ventana abierta |
| Ana Demo | `DEMO-001` — **SPGY6PW8** |
| Bruno Demo | `DEMO-002` — **SPPPTLFK** |
| Carla Demo | `DEMO-003` — **SPTJL9EA** |

Reset desde la carpeta de la entrega:

```powershell
cd "C:\Users\jorge\Documents\empresas\Taller de Codigo\demo"
.\reset-demo.ps1
```

El script restaura la base, la app y refresca la ventana del evento; deja 0 acreditados y los mismos códigos.

## Guion

1. **Login Monitor.** `monitor@syncpass.cl / Monitor.2026`. La app muestra el inicio y los eventos descargados.
2. **Catálogo.** Abrir Inicio: 3 eventos, incluido Demo Defensa. Abrir Proyectos para mostrar el catálogo local.
3. **Acreditación manual.** Acreditar → Demo Defensa → escribir `SPGY6PW8` → Acreditar. Contador 1/3.
4. **QR.** Abrir el detalle de un participante y mostrar su QR; con permiso de cámara, abrir el escáner.
5. **Encuesta.** Acreditar a Bruno (`SPPPTLFK`) y responder la encuesta de satisfacción desde Encuestas.
6. **Offline.** Activar modo avión; acreditar a Carla (`SPTJL9EA`). En Ajustes sube "Pendientes por enviar".
7. **Sync.** Quitar modo avión; Sincronizar o esperar al worker. Pendientes 0 y el backend suma asistencias.
8. **Rechazo.** (Opcional) Provocar un rechazo de negocio y mostrar "Rechazados" y "Descartar rechazados".
9. **Roles.** En el panel web, el Monitor no ve Usuarios ni formularios; la API responde 403.

## Cámara y permisos

- El escáner abre con permiso concedido; el permiso denegado muestra la alternativa **Escribir código**.
- La lectura de un QR físico en un teléfono real no se verificó; el camino manual es el respaldo.
- Revocar permiso:
  ```powershell
  adb shell pm revoke cl.syncpass.app android.permission.CAMERA
  ```

## Evidencia

- Pruebas: `.\gradlew.bat testDebugUnitTest` (79 casos).
- Capturas y límites de cobertura en [pruebas-app.md](pruebas-app.md).
- Evidencia de emulador del sistema completo: repo de la API, `docs/demo.md`.
