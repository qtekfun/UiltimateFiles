# Product Requirements Document (PRD) - UltimateFiles, an open source file manager

## 1. Product Vision
A functional and visual take on Solid Explorer for Android, optimized for productivity with dual-panel navigation, high
fidelity in file operations (internal storage and USB OTG), a non-blocking background architecture, and ready for F-Droid.

## 2. MVP Scope (In Scope)

### 2.1 Navigation and Dual Panel
- **Adaptive layout:**
  - *Portrait / Compact:* a 2-panel `HorizontalPager` with synchronized top tabs and lateral swipe.
  - *Landscape / Expanded:* a fixed 50/50 split view showing both panels at once.
- **Drag & drop:**
  - In split view, drag files/folders from one panel and drop them in the other (or into a visible subfolder).
  - On drop, show a quick confirmation dialog: "Copy or Move?".
- **Interactive breadcrumbs:**
  - A segmented path bar in the top bar. Clicking any segment jumps to that directory.
- **Side navigation drawer:**
  - Access to volumes (internal storage, detected USB OTG).
  - Predefined shortcuts: Downloads, Documents, Photos (DCIM).

### 2.2 Actions and Context Menus (No FAB)
- **Resting top bar:** drawer menu icon, interactive breadcrumb, search, and an overflow menu with: *New folder*, *New file*,
  *Sort by*, *Select all*.
- **Contextual top bar (CAB):** activates when 1 or more items are selected. Shows a counter, *Copy*, *Cut*, *Delete*,
  *Share* icons and an overflow (*Rename*, *Properties / Hash*).
- **Item context menu:** long press or the 3-dot menu on each row for quick actions on that single file without selecting it
  first (*Open with*, *Copy*, *Cut*, *Rename*, *Delete*, *Properties*).
- **Docked paste bar:** a fixed bottom bar that does not float over the content and appears only when there are items on
  the clipboard. It contains a summary of the buffered items, a *Paste here* button and a *Cancel* button.

### 2.3 I/O Engine and Background
- **Foreground service:** heavy copies and moves run in a `ForegroundService` with a notification showing the current file,
  total percentage and estimated speed (MB/s).
- **Conflict resolution:** a dialog when duplicates are found: *Overwrite*, *Skip*, *Rename*, with an *Apply to all* option.
- **USB OTG storage:** integration through the Storage Access Framework (`DocumentFile`), including a safe-eject intent.

### 2.4 Properties and Metadata
- **Properties bottom sheet:** name, full path, exact size (bytes and human readable), modification date,
  attributes/permissions and asynchronous hash calculation (MD5 and SHA-256).

## 3. Out of Scope for the MVP
- Explicit SMB encryption/signing settings.
- Creating 7z archives and password-protected archives (ZIP, 7z, TAR and TAR.GZ can be browsed and extracted, see
  section 4).
- Viewers for formats other than image, text, PDF, audio or video (everything else is delegated to external apps through
  Intents).
- Root access.

## 4. Post-MVP Additions (implemented)
- Task history (including running and queued tasks), ejected-volume detection, theme settings with AMOLED mode.
- Robust copies of huge files (temporary name, optional SHA-256 verification), pause/resume, lock-screen notification.
- Nextcloud/WebDAV with *Login Flow v2*, chunked uploads and resumable downloads (HTTPS by default).
- Self-signed certificates with a fingerprint pinned per account, and optional HTTP with explicit confirmation.
- Reliable copies with the screen off (battery exemption, Wi-Fi lock, journal of interrupted copies, reliability checks).
- Compress to ZIP, extract ZIP/7z/TAR/TAR.GZ through the transfer queue and browse them as read-only folders; "Open with"
  from other apps.
- SMB accounts (user/password, not verified against a real server) and SFTP accounts (password or private key, server host
  key pinned per account).
- Built-in viewers: image (zoom), text, PDF, audio and video.
- Grid view, opening APKs, exporting/importing settings and accounts.
- Interrupted Nextcloud uploads (for example when the app dies) resume when the copy is repeated: only the chunks that
  changed are sent again.
- Release pipeline: signed releases from tags, nightlies, reproducible-build check and an F-Droid recipe.

## 5. Pending / Ideas
- Test SMB against real servers (see section 3).
- Real-device testing with multi-GB files, SFTP/SMB on Android and the intent filters.
- F-Droid screenshots and submission of the recipe to fdroiddata.
