# Implementation Task Plan

- [x] **Phase 1: Project Base and Core Domain**
  - [x] 1.1 Configure `build.gradle.kts` with Compose, Material 3, Koin, DataStore and Coroutines.
  - [x] 1.2 Data models: `FileItem`, `StorageVolume`, `ClipboardState`, `TransferProgress`.
  - [x] 1.3 `FileSystemRepository` interface contract and a shared `ClipboardManager`.

- [x] **Phase 2: I/O Engine and Foreground Service**
  - [x] 2.1 Implement `FileStreamCopier` with a 64 KB buffer and a speed/bytes reporting channel.
  - [x] 2.2 Create `FileTransferForegroundService` with an updatable persistent notification.
  - [x] 2.3 Implement `SafFileSystemRepository` to handle internal storage and USB OTG through the Storage Access Framework.
  - [x] 2.4 Name collision resolution logic (overwrite, skip, auto-rename).

- [x] **Phase 3: UI Components and Menu System**
  - [x] 3.1 Interactive `BreadcrumbBar` with path jumps in the top bar.
  - [x] 3.2 Resting top bar with overflow (*New folder*, *Sort*, *Select all*).
  - [x] 3.3 Contextual Action Bar (CAB) while there is an active selection (*Copy*, *Cut*, *Delete*).
  - [x] 3.4 Bottom `DockedPasteBar` driven by the `ClipboardManager` state.
  - [x] 3.5 `PropertiesBottomSheet` with MD5/SHA-256 hashing in a background coroutine.

- [x] **Phase 4: File Browser (Single Panel)**
  - [x] 4.1 `BrowserViewModel` handling listing, sorting, async loading and selection.
  - [x] 4.2 `FileList` and rows with icons by MIME type and human-readable sizes.
  - [x] 4.3 Per-item context menu (long press / 3-dot button on the row).

- [x] **Phase 5: Dual Panel Integration and Drag & Drop**
  - [x] 5.1 `DualPanelScaffold` with screen size detection (Compact vs Expanded).
  - [x] 5.2 `HorizontalPager` for portrait and a 50/50 split layout for landscape.
  - [x] 5.3 Drag & drop between panels in split mode with a modal "Copy or Move?" dialog.
  - [x] 5.4 Side navigation drawer with detected volumes (internal, USB).

- [x] **Phase 6: Feedback from the first test (v0.1.5)**
  - [x] 6.1 Persistent history of completed tasks (copy, move, delete), reachable from the drawer.
  - [x] 6.2 Detection of ejected or disconnected disks: a panel on that volume returns to an available folder and notifies.
  - [x] 6.3 Settings screen with theme (system, light, dark, AMOLED) and dynamic colors.

- [x] **Phase 7: Huge Files, Remote Locations and Releases**
  - [x] 7.1 Robust copies of large files: writing to `*.ultimatefiles-part` + rename, optional SHA-256 verification before
    deleting the source in moves, 1 MiB buffer, wake lock, ETA.
  - [x] 7.2 Simulated 8 GiB transfer test (generated stream, CRC32) with no disk needed.
  - [x] 7.3 WebDAV/Nextcloud accounts (HTTPS, app password encrypted with Android Keystore), Nextcloud chunked uploads
    (chunked v2) and resumable downloads (Range).
  - [x] 7.4 Pause/resume copies, running tasks in the history and a notification visible on the lock screen.
  - [x] 7.5 Export/import settings and accounts (encrypted with a passphrase) and rename of the app to UltimateFiles.
  - [x] 7.6 Grid view shared by both panels.
  - [x] 7.7 Nextcloud uploads that resume after the app dies (per-chunk hash).
  - [x] 7.8 Version in `gradle.properties` (`appVersion`, derived `versionCode`); official releases by tag signed with the
    project key, separate nightlies, F-Droid recipe and reproducible-build check.
  - [x] 7.9 Self-signed certificates (SHA-256 fingerprint per account) and optional HTTP with confirmation.
  - [x] 7.10 Reliable copies with the screen off: battery exemption, Wi-Fi lock, journal and resume, checks in Settings.
  - [x] 7.11 Compress to ZIP and extract ZIP/TAR/TAR.GZ (`ArchiveEngine`, zip-slip protection).
  - [x] 7.12 Built-in viewers (image, text, PDF, audio/video) in `ViewerActivity`.
  - [x] 7.13 Archives as folders (`archive://`), 7z extraction and opening from other apps.
  - [x] 7.14 SFTP accounts with password and pinned host key (sshj); R8 and release build in CI.
  - [x] 7.15 SMB accounts (smbj): list, create, rename, delete and copy; verified only by compilation and path tests.
  - [x] 7.16 SFTP authentication with a private key (PEM, optional passphrase).
  - [x] 7.17 Instrumented Compose UI tests.

- [x] **Phase 8: Panels, thumbnails and polish (v0.4 to v0.7)**
  - [x] 8.1 Dynamic panels (`PanelId`, panels saved in DataStore, a view model store per panel, close with undo).
  - [x] 8.2 `PanelBar` with a drop-down of the open panels, placed at the top or the bottom; two fixed slots in landscape.
  - [x] 8.3 Thumbnails of photos and videos (`ThumbnailLoader`, `ThumbnailPolicy`, a byte-bounded cache) and the setting for
    network accounts.
  - [x] 8.4 Item menu that highlights its item and has *Select*; renaming network accounts.
  - [x] 8.5 Mounted USB drives and SD cards used by path from Android 11 (`RemovableStorage`).
  - [x] 8.6 Fix: big copies into SAF folders (temporary name renamed by the provider) and the notification left behind after
    cancelling.
  - [x] 8.7 Reworked in-app copy bar and a refined interface: Settings in grouped cards with an About section, a richer
    drawer, tonal file icons and proper empty and error states.
  - [x] 8.9 Editing network accounts: `AccountRepository.update`, `update` in the SFTP/SMB account services, replacing a Nextcloud
    account through the Login Flow, the edit mode of the account form and dropping cached connections (`AccountEditing`).
  - [x] 8.10 Notices for unavailable servers: `ConnectionProblems` classifier, `ConnectionHealth`, a Snackbar with Retry and Edit
    account, a drawer mark and the reason in failed copies, in the history and in the result notification.
  - [x] 8.8 Size analysis (`SizeAnalyzer`, `SizeAnalysisViewModel`, `SizeAnalysisScreen`) from a panel's menu and from the drawer.
