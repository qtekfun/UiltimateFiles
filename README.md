# UltimateFiles

Explorador de archivos open source de doble panel para Android (F-Droid compatible, sin Google Play Services).
Ver [PRD.md](PRD.md), [ARCHITECTURE.md](ARCHITECTURE.md) y [TASK_PLAN.md](TASK_PLAN.md).

Licencia: GPL-3.0-or-later.

## Desarrollo
```
./gradlew testDebugUnitTest lintDebug assembleDebug
```

## CI/CD
- `ci.yml`: tests, lint y APK debug en cada PR y push a `master`.
- GitGuardian: escaneo de secretos mediante su app de GitHub (check "GitGuardian Security Checks"), sin workflow ni API key.
- `release.yml`: en cada push a `master` publica una GitHub Release con el APK (`versionName = VERSION.<run_number>`).
  Para firmar: secrets `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.
  Sin ellos el APK usa la clave debug y la release se marca como pre-release.
- Dependabot: Gradle y GitHub Actions, semanal.

## Nombre y paquete
La app se llama **UltimateFiles**. El identificador técnico (`com.qtekfun.fexplo`) y el nombre del repositorio se mantienen para que las instalaciones existentes se actualicen sin perder datos.

## Copia de seguridad
Ajustes → Copia de seguridad exporta e importa los ajustes y las cuentas de Nextcloud a un archivo JSON (`ultimatefiles-backup.json`). Las contraseñas de aplicación de las cuentas se cifran en el archivo con una frase de contraseña (PBKDF2-SHA256 + AES-256-GCM); sin ella no se pueden restaurar.
