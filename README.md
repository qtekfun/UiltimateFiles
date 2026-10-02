# Fexplo

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
