# System Architecture

## 1. Tech Stack
- **Language:** Kotlin 2.x
- **UI:** Jetpack Compose + Material 3 + WindowSizeClass
- **SDKs:** Min SDK 26 | Target SDK 35
- **DI:** Koin (lightweight, no invasive code generation)
- **Concurrency:** Kotlin Coroutines + Flow / StateFlow
- **Configuration:** Jetpack DataStore Preferences
- **I/O engine:** `InputStream` / `OutputStream` piped through a 64 KB buffer with a progress channel.

## 2. Modules and Package Structure
```text
com.qtekfun.ultimatefiles/
├── core/
│   ├── model/             # FileItem, StorageVolume, TransferProgress, TransferStatus, ConflictResolution
│   ├── datastore/         # Preference persistence (paths, sorting, views)
│   └── util/              # Byte formatters, hashes (MD5/SHA), MimeTypes
├── data/
│   ├── repository/        # FileSystemRepository (LocalFileRepo, SafUsbRepo)
│   ├── io/                # FileStreamCopier reporting bytes written
│   └── service/           # FileTransferForegroundService + NotificationManager
├── domain/
│   ├── usecase/           # BatchCopyUseCase, BatchMoveUseCase, DeleteUseCase, HashCalcUseCase
│   └── clipboard/         # ClipboardManager (global cut/copy state shared by both panels)
└── ui/
    ├── main/              # Main scaffold with NavigationDrawer and WindowSizeClass
    ├── dualpanel/         # Orchestrator: HorizontalPager (Compact) vs 50/50 Row (Expanded)
    ├── browser/           # Single panel (ViewModel, FileList, FileItemRow, CAB)
    ├── components/        # BreadcrumbBar, DockedPasteBar, ConflictDialog, PropertiesBottomSheet
    └── theme/             # Material 3 theme
```

## 3. Domain Contracts
```kotlin
data class ClipboardState(
    val operation: OperationType, // COPY or CUT
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
The base contract is extended with what the copy engine needs (`volumes`, `stat`, `parentOf`, `openInput`, `openOutput`), so
that `FileStreamCopier` works with any backend. `RoutingFileSystemRepository` picks the backend by scheme: `dav://`,
`sftp://`, `smb://`, `archive://`, `content://`, or a local path.

## 4. Huge Files and Network
- `TransferEngine` writes files of 64 MiB or more as `<name>.ultimatefiles-part` and renames them when done. In a move the
  source is deleted only after copying (and after SHA-256 verification if enabled in Settings). 1 MiB buffer for large files.
- `data/network/`: WebDAV client on OkHttp (`dav://<account>/<path>`). Nextcloud uses 10 MB chunked uploads (chunked v2) and
  resumable downloads with `Range`. HTTPS by default; self-signed servers are supported through a pinned SHA-256
  certificate fingerprint per account, and plain HTTP needs an explicit confirmation.
- SFTP (`sshj`, `sftp://`) pins the server's host key fingerprint per account and supports password or PEM private key
  authentication. SMB (`smbj`, `smb://host:port/share`) supports an optional domain. Both are only reachable through
  `FileSystemRepository`.

## 5. Archives
- `ArchiveEngine` compresses (ZIP) and extracts (ZIP, 7z, TAR, TAR.GZ) through the same transfer queue as copies, rejecting
  entries that escape the target folder (zip-slip) and rolling back a failed extraction.
- `ArchiveFileSystemRepository` exposes an archive as a read-only folder: `archive://<url-encoded source>!/<inner path>`.
  Archives that are not local are cached for 3 days. `IncomingFiles` copies archives sent by other apps into the cache.

## 6. Pause, Live History and Backups
- `PauseGate` (domain/transfer): the copier and the verification wait on it between blocks; `TransferCoordinator` exposes
  `TransferState` with the active task, the queue and whether it is paused (`active`, `queuedTasks`, `paused`), used by the
  notification, the progress bar and the History screen.
- Notification on the `transfers_lockscreen` channel (public visibility), with Pause/Resume and Cancel actions.
- `data/network/NextcloudLoginFlow`: Login Flow v2 (polls until the user approves in the browser).
- `data/backup/BackupManager`: JSON with settings and, encrypted with PBKDF2-SHA256 + AES-256-GCM, the accounts;
  `BackupFiles` reads and writes through the document picker (no storage permissions).
- List/grid view: `ViewMode`, shared by both panels through preferences.
- Resumable uploads: `WebDavUploadStream` saves the server's upload id and the SHA-256 of every chunk sent in
  `UploadResumeStore` (a file in `filesDir`). When the copy is repeated to the same destination (the `.ultimatefiles-part`
  temporary name is stable), the source is read again and every chunk whose hash matches and that the server still holds is
  skipped; the rest is uploaded and leftover chunks are deleted. Source dates and sizes are never trusted. Saved uploads
  expire after 20 h.

## 7. Versioning and Releases
`appVersion` in `gradle.properties` is the source of truth and `versionCode` is derived from it (see
[RELEASING.md](RELEASING.md)): `(MAJOR*10000 + MINOR*100 + PATCH) * 100 + N`, where N is 99 for a final release. Official
releases come from `vX.Y.Z` tags; nightlies come from `master` with a different application id and the workflow run number
as `versionCode`.
