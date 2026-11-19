# Pruebas y cobertura — app Android

## Suites

```powershell
$env:JAVA_HOME = 'C:\Users\jorge\.jdks\jbr-21.0.11'
.\gradlew.bat testDebugUnitTest
```

**79 pruebas, 0 fallos**. La app necesita JDK 21: con la JBR 25 de Android Studio, Robolectric falla con `Unsupported class file major version 69`.

## Cobertura medida (no representativa)

Con JaCoCo inyectado por un *init script* externo (sin modificar este repositorio):

| Métrica | Cubierto / total | Porcentaje |
| --- | --- | --- |
| Instrucciones | 2.479 / 104.852 | **2,4 %** |
| Ramas | 177 / 4.763 | **3,7 %** |
| Líneas | 347 / 13.143 | **2,6 %** |
| Métodos | 129 / 2.462 | **5,2 %** |
| Clases | 32 / 433 | **7,4 %** |

**Por qué no sirve como puerta de calidad:** la mayoría de las pruebas corre bajo Robolectric, que instrumenta las clases en su *sandbox* y no pasa por el agente de JaCoCo. Repositorios con pruebas en verde aparecen en 0 %:

- `AuthRepository` (66 líneas).
- `CatalogoRepository` (82 líneas).
- `AccreditationRepository` (85 líneas).

Solo 32 de 427 clases registran alguna línea (las que se ejercitan fuera de Robolectric). Por eso el número mide un subconjunto y **no se usa como umbral**.

## Comando usado para medir

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:jacocoUnitTestReport `
  --init-script C:\Users\jorge\AppData\Local\Temp\opencode\jacoco-app-init.gradle
```

Reporte: `app/build/reports/jacoco/jacocoUnitTestReport/html/index.html`.

## Límites

- Pruebas instrumentadas de UI: no ejecutadas en esta entrega.
- Cámara/QR físico: no verificado.
- Cobertura real con Robolectric: requiere instrumentación offline o un runner compatible.
- El consolidado de las tres suites está en [pruebas-cobertura.md](https://github.com/jorgeCisternasDev/syncpass-api/blob/main/docs/pruebas-cobertura.md).
