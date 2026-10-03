# UltimateFiles

Explorador de archivos libre (GPL-3.0-or-later) de **doble panel** para Android, pensado para trabajar con ficheros
grandes (footage de vídeo, copias de seguridad) y para mover cosas a Nextcloud. Compatible con F-Droid: sin Google
Play Services, sin analíticas, sin dependencias cerradas.

## Qué hace
- **Dos paneles**: pestañas deslizables en vertical, 50/50 en horizontal o pantallas anchas; arrastrar y soltar entre
  paneles con confirmación "¿Copiar o mover?".
- **Navegación**: ruta segmentada (breadcrumbs), búsqueda por nombre, ordenación, vista en lista o cuadrícula,
  cajón lateral con volúmenes (interno, USB OTG, tarjeta SD) y accesos rápidos.
- **Acciones sin FAB**: barra superior contextual al seleccionar, menú por elemento, barra de pegado acoplada abajo.
- **Copias y movimientos en segundo plano** con servicio en primer plano: notificación con progreso, velocidad y
  tiempo restante, visible en la pantalla de bloqueo, con **pausar/reanudar** y cancelar.
- **Ficheros enormes**: se escriben con nombre temporal (`*.ultimatefiles-part`) y se renombran al terminar; verificación
  SHA-256 opcional antes de borrar el origen en un movimiento; conflictos (sobrescribir, omitir, renombrar, aplicar a todos).
- **Nextcloud / WebDAV**: inicio de sesión con *Login Flow* en el navegador (nunca ves ni guardas tu contraseña,
  solo una contraseña de aplicación revocable, cifrada con el Keystore), subida por trozos y descarga reanudable.
  Solo HTTPS.
- **Historial** de tareas, incluidas las que están en curso o en cola.
- **Propiedades** con permisos y hashes MD5/SHA-256.
- **Ajustes**: tema (sistema, claro, oscuro, AMOLED), colores dinámicos, verificar copias, y **copia de seguridad**
  exportable/importable (ajustes y cuentas; las contraseñas se cifran con una frase que eliges).
- **Abrir APK**: pide una vez el permiso "instalar apps desconocidas" y delega en el instalador del sistema.

## Permisos
Acceso a todos los archivos (`MANAGE_EXTERNAL_STORAGE`), servicio en primer plano de sincronización de datos,
notificaciones, `WAKE_LOCK` (copias largas con la pantalla apagada), `INTERNET` (solo para Nextcloud/WebDAV) y
`REQUEST_INSTALL_PACKAGES` (abrir APK). No hay nada que se envíe a terceros.

## Desarrollo
```
./gradlew testDebugUnitTest lintDebug assembleDebug
```
Java 21, Kotlin 2.x, Jetpack Compose + Material 3, Koin, DataStore, OkHttp. Ver [PRD.md](PRD.md),
[ARCHITECTURE.md](ARCHITECTURE.md) y [TASK_PLAN.md](TASK_PLAN.md).

Las pruebas de Nextcloud corren contra un servidor simulado en memoria, y la copia de 8 GiB contra un flujo
generado: no sustituyen una prueba real en un dispositivo.

## CI/CD
- `ci.yml`: tests, lint y APK debug en cada PR y push a `master` y a ramas `claude/**`.
- GitGuardian: escaneo de secretos mediante su app de GitHub, sin workflow ni API key.
- `release.yml`: en cada push a `master` publica una release con el APK `UltimateFiles-<versión>.apk`
  (`versionName = <version.properties>.<run_number>`, p. ej. `0.2.0.57`). Para firmar con tu clave: secrets `SIGNING_KEYSTORE_BASE64`,
  `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`. Sin ellos se usa la clave debug.
- Dependabot: Gradle y GitHub Actions, semanal.
- Versión: `version.properties` (`versionName` y `versionCode`). Para publicar una versión "oficial": subir ambos valores,
  añadir `changelogs/<versionCode>.txt` en fastlane, mergear y etiquetar `vX.Y.Z`. Detalles y receta de F-Droid en
  [docs/FDROID.md](docs/FDROID.md).

## Nombre y paquete
La app se llama **UltimateFiles**, el identificador de la aplicación es `com.qtekfun.ultimatefiles` y el repositorio es
`qtekfun/UltimateFiles`.

Las versiones anteriores a este cambio se publicaron con otro identificador, así que Android las trata como otra app: la
nueva se instala al lado y no hereda sus ajustes ni sus cuentas. Para pasar los datos, exporta antes una copia de seguridad
desde la versión antigua (Ajustes → Copia de seguridad) e impórtala en la nueva.
