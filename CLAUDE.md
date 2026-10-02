# Directrices de Desarrollo - Android Dual-Panel File Manager

## Reglas Críticas para el Agente
- **Cero Google Play Services:** Compatibilidad estricta con F-Droid. Cero analytics, crashlytics o dependencias de Google closed-source.
- **Sin FABs:** La UI utiliza Contextual Action Bars (CAB) superiores y barras inferiores acopladas (docked bottom bars). No uses Floating Action Buttons.
- **Validación obligatoria:** Tras cada cambio, compila con `./gradlew assembleDebug` o corre tests con `./gradlew testDebugUnitTest`. No asumas que compila.
- **I/O no bloqueante:** Ninguna lectura/escritura en hilo principal. Operaciones largas de I/O (copia, movimiento) se gestionan mediante un `ForegroundService` con notificación persistente.
- **Abstracción del Storage:** Todo acceso a ficheros debe pasar por la interfaz `FileSystemRepository`. Prohibido el uso directo de `java.io.File` en capas UI o Domain.

## Entorno y Comandos
- Java: OpenJDK 21 | Kotlin 2.x
- Compilar: `./gradlew assembleDebug`
- Tests: `./gradlew testDebugUnitTest`
- Linter: `./gradlew lintDebug`
- Logs: `adb logcat -s "FileManagerApp"`

## Estilo y UI
- Jetpack Compose con Material 3 y Adaptive Layouts (`WindowWidthSizeClass`).
- Strings estrictamente en `res/values/strings.xml`. Cero textos hardcodeados en Composable.
- Estado desacoplado e inmutable con `StateFlow` y eventos tipo Sealed Interface.
