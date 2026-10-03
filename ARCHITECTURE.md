# Arquitectura del Sistema

## 1. Stack Técnico
- **Lenguaje:** Kotlin 2.x
- **UI:** Jetpack Compose + Material 3 + WindowSizeClass
- **SDKs:** Min SDK 26 | Target SDK 35
- **DI:** Koin (ligero, sin generación de código invasiva)
- **Concurrencia:** Kotlin Coroutines + Flow / StateFlow
- **Configuración:** Jetpack DataStore Preferences
- **I/O Engine:** `InputStream` / `OutputStream` canalizados con buffer de 64KB y canal de progreso.

## 2. Módulos y Estructura de Paquetes
```text
com.qtekfun.ultimatefiles/
├── core/
│   ├── model/             # FileItem, StorageVolume, TransferProgress, TransferStatus, ConflictResolution
│   ├── datastore/         # Persistencia de preferencias (rutas, ordenación, vistas)
│   └── util/              # Formatters de bytes, hashes (MD5/SHA), MimeTypes
├── data/
│   ├── repository/        # FileSystemRepository (LocalFileRepo, SafUsbRepo)
│   ├── io/                # FileStreamCopier con reporte de bytes emitidos
│   └── service/           # FileTransferForegroundService + NotificationManager
├── domain/
│   ├── usecase/           # BatchCopyUseCase, BatchMoveUseCase, DeleteUseCase, HashCalcUseCase
│   └── clipboard/         # ClipboardManager (Estado global de corte/copia entre paneles)
└── ui/
    ├── main/              # Scaffold principal con NavigationDrawer y WindowSizeClass
    ├── dualpanel/         # Orquestador: HorizontalPager (Compact) vs Row 50/50 (Expanded)
    ├── browser/           # Panel individual (ViewModel, FileList, FileItemRow, CAB)
    ├── components/        # BreadcrumbBar, DockedPasteBar, ConflictDialog, PropertiesBottomSheet
    └── theme/             # Material3 Theme
```

## 3. Contratos de dominio
```kotlin
data class ClipboardState(
    val operation: OperationType, // COPY o CUT
    val sourcePath: String,
    val items: List<FileItem>
)

interface FileSystemRepository {
    suspend fun listFiles(uriOrPath: String): Result<List<FileItem>>
    suspend fun createDirectory(parentUriOrPath: String, name: String): Result<FileItem>
    suspend fun createFile(parentUriOrPath: String, name: String, mimeType: String): Result<FileItem>
    suspend fun delete(items: List<FileItem>): Result<Unit>
    suspend fun rename(item: FileItem, newName: String): Result<FileItem>
}
```
El contrato base se amplía con lo necesario para el motor de copia (`volumes`, `stat`, `parentOf`, `openInput`, `openOutput`), de modo que `FileStreamCopier` funcione con cualquier backend.

## 4. Ficheros enormes y red (Fase 7)
- `TransferEngine` escribe los ficheros ≥ 64 MiB como `<nombre>.ultimatefiles-part` y los renombra al terminar; en movimientos el origen solo se borra tras copiar (y verificar SHA-256 si está activado en Ajustes). Buffer de 1 MiB para ficheros grandes.
- `data/network/`: cliente WebDAV sobre OkHttp (`dav://<cuenta>/<ruta>`), enrutado por `RoutingFileSystemRepository`. Nextcloud: subida por trozos de 10 MB (chunked v2); descarga reanudable con `Range`. Solo HTTPS. Permiso `INTERNET` añadido.

## 5. Pausa, historial en vivo y copias de seguridad
- `PauseGate` (domain/transfer): el copiador y la verificación esperan en él entre bloques; `TransferCoordinator` expone
  `TransferState` con la tarea activa, la cola y si está en pausa (`active`, `queuedTasks`, `paused`), que usan la
  notificación, la barra de progreso y la pantalla de Historial.
- Notificación en el canal `transfers_lockscreen` (visibilidad pública), con acciones Pausar/Reanudar y Cancelar.
- `data/network/NextcloudLoginFlow`: Login Flow v2 (sondeo hasta que el usuario aprueba en el navegador).
- `data/backup/BackupManager`: JSON con ajustes y, cifrado con PBKDF2-SHA256 + AES-256-GCM, las cuentas; `BackupFiles`
  lee/escribe vía el selector de documentos (sin permisos de almacenamiento).
- Vista lista/cuadrícula: `ViewMode` compartido por ambos paneles mediante las preferencias.
- Subidas reanudables: `WebDavUploadStream` guarda en `UploadResumeStore` (fichero en `filesDir`) el id de subida del servidor y el
  SHA-256 de cada trozo enviado. Al repetir la copia al mismo destino (el nombre temporal `.ultimatefiles-part` es estable) se relee el
  origen y se salta cada trozo cuyo hash coincide y que el servidor sigue teniendo; el resto se sube y los trozos sobrantes se
  borran. No se confía en fechas ni tamaños del origen. Las subidas guardadas caducan a las 20 h.
- Versión: `appVersion` en `gradle.properties` es la fuente de verdad y el `versionCode` se deriva (ver RELEASING.md); las releases oficiales salen de etiquetas `vX.Y.Z` y las nightlies de `master` con otro identificador.
  `versionCode = código * 100000 + run`.
