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
│   ├── repository/        # FileSystemRepository (local, SAF, WebDAV, SFTP, SMB, archives) and the router
│   ├── system/            # IntentFactory, volume monitor, RemovableStorage (mounted USB/SD volumes)
│   ├── thumbnail/         # ThumbnailLoader and ThumbnailPolicy (which files get one, sampling)
│   ├── io/                # FileStreamCopier reporting bytes written
│   └── service/           # FileTransferForegroundService + NotificationManager
├── domain/
│   ├── usecase/           # BatchCopyUseCase, BatchMoveUseCase, DeleteUseCase, HashCalcUseCase
│   └── clipboard/         # ClipboardManager (global cut/copy state shared by both panels)
└── ui/
    ├── main/              # Main scaffold with NavigationDrawer and WindowSizeClass
    ├── dualpanel/         # Orchestrator: PanelBar + HorizontalPager (Compact) vs two fixed slots (Expanded)
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
- Notification on the `transfers_progress` channel (public visibility), with Pause/Resume and Cancel actions. When the
  queue is drained the service waits for the job that mirrors the state into the notification before removing it, and
  refuses later updates, so a cancelled copy cannot leave its notification behind next to the result.
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

## 8. Panels
- `PanelId` identifies a panel (the first two keep the ids, and saved keys, of the original left and right panels).
  `MainViewModel` owns the open panels, the two that are visible in landscape (`startPanel`, `endPanel`) and the active one,
  and keeps the invariant that the active panel is always visible: a panel that is not on screen takes the place of the
  active one. The list of panels and the folder of each go to DataStore.
- Every panel has a `BrowserViewModel` in a `ViewModelStore` of its own, held by `MainViewModel`, so closing a panel
  disposes of just its state and everything survives rotation. Closing can be undone: the panel is opened again where it
  was, in the folder it had.
- `PanelBar` (drop-down of the open panels with their folders, add, close) goes above or below the panels according to
  the setting. Portrait uses a `HorizontalPager` keyed by panel id; landscape uses two slots, and each slot has its own bar.

## 9. Thumbnails
- `ThumbnailPolicy` decides what gets one: photos and videos, not inside archives, and on network accounts only photos of
  up to 8 MB when the setting allows it. `ThumbnailLoader` decodes photos through `FileSystemRepository` with an
  `inSampleSize` that keeps the shorter side at least the requested size, applies the EXIF rotation, and takes a frame
  of a video with `MediaMetadataRetriever` (local and SAF only, since Android needs a path or a URI for that).
- Results are cached in memory bounded by bytes (`SizedLruCache`), at most three decodes run at once, loading is cancelled
  when a row scrolls out of view, and failures are remembered so a broken file is not retried on every scroll.

## 10. Removable Storage
- From Android 11 the all-files access the app already has reaches the volumes the system mounts under `/storage`.
  `RemovableStorage` lists them with `StorageManager` and `LocalFileSystemRepository` exposes them as extra roots, so they
  are used by path: faster, and names are kept as they are (a documents provider adds the extension of the MIME type to a
  name that lacks it, which once broke the temporary name of big copies).
- A folder granted through the system picker (SAF) for a drive already shown by path is not listed twice. Before Android
  11, and for drives Android does not mount, SAF remains the way in.

## 11. Size Analysis
- `SizeAnalyzer` (domain/usecase) walks a folder only through `FileSystemRepository.listFiles` and returns a tree of
  `SizeNode` (bytes and file count of everything below each entry). Memory stays bounded: of each folder only the 200 biggest
  entries are kept and the rest become one "Other (N items)" node, while every total stays exact. A depth limit of 48 stops
  symbolic link loops, a folder that cannot be listed is counted as unreadable instead of failing the analysis, progress is
  reported at most every 150 ms and cancelling the coroutine stops the walk.
- `SizeAnalysisViewModel` runs it on `Dispatchers.Default` and holds Idle / Scanning / Done / Failed plus the trail of folders the
  user has gone down. `MainScreen` shows it as `AppScreen.ANALYSIS`, where back goes up one folder before it leaves.
  "Show in panel" sends `BrowserEvent.Reveal` to the active panel, which opens the item's folder and selects it.
- The entry is offered only where `SizeAnalyzer.supports(path)` is true: not for `dav://`, `sftp://`, `smb://` or `archive://`.

## 12. Editing Accounts
- An account is edited in place: `AccountRepository.update(account, password)` keeps the id (paths are `dav://<id>/...`,
  `sftp://<id>/...`, `smb://<id>/...`, and saved panel folders and history refer to it) and its place in the list; a null
  password keeps the stored secret, which the form never shows.
- `SftpAccountService.update`, `SmbAccountService.update` and `WebDavAccountService` (`replacing`) check the new data first and
  only then store it, so a failed edit leaves the account as it was. Then `onAccountChanged` drops the connections kept for the
  id (`forget` on the repositories) and `MainViewModel` tells the screen to reload the panels that show it.
- `AccountEditing` holds the pure rules: how stored data maps back to form fields, that a pinned SSH host key or a certificate
  trust only carries over to the same host and port or server (anything else is asked about again, and a changed key at the
  same host is still refused), and what the stored SFTP secret becomes (`key NUL passphrase` is kept unless another is given).

