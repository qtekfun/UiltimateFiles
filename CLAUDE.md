# Directrices de Desarrollo - Android File Manager

## Reglas Críticas para el Agente
- **Cero Google Play Services:** La app debe ser 100% compatible con F-Droid. No añadas dependencias privativas, analytics ni Firebase.
- **Validación obligatoria:** Tras modificar código, ejecuta `./gradlew testDebugUnitTest` o `./gradlew compileDebugKotlin`. Nunca des una tarea por completada sin compilar.
- **Abstracción de I/O:** Nunca uses `java.io.File` en capas de dominio o UI. Usa siempre la interfaz abstracta `FileSystemRepository` para permitir futuros conectores (WebDAV/SAF).
- **Corrutinas:** Toda operación de I/O (copiar, mover, listar, eliminar) debe ejecutarse estrictamente en `Dispatchers.IO` fuera del hilo principal.
- **Modo de trabajo:** Modifica un fichero a la vez. No generes placeholders tipo `// TODO: implement later`.

## Entorno y Comandos
- Java: OpenJDK 21
- Build: `./gradlew assembleDebug`
- Tests: `./gradlew testDebugUnitTest`
- Linter / Verificación: `./gradlew lintDebug`
- Logs locales: `adb logcat -s "FileManagerApp"`

## Estilo y Convenciones
- Kotlin puro + Jetpack Compose con Material 3.
- Arquitectura MVI o MVVM con `StateFlow` y eventos inmutables.
- Strings localizables en `strings.xml`. No uses strings literales hardcodeadas en UI.