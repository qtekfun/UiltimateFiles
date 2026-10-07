# Changelog

All notable changes. Versions follow [SemVer](https://semver.org/); the notes of each release are published with it.

## [Unreleased]

## [0.6.2] - 2026-10-07

### Fixed
- After cancelling a copy, the notification of the copy (paused or part-way) could stay next to the "cancelled" one. It is now always removed when the transfer ends.

## [0.6.1] - 2026-10-07

### Changed
- The copy bar in the app has the file name and the figures on one line, the progress bar below them, and the Pause and Cancel buttons on a line of their own, as outlined buttons with an icon. A long name or longer figures can no longer push the buttons onto several lines.

## [0.6.0] - 2026-10-06

### Added
- An **About** section at the end of Settings with the version of the app.

### Changed
- USB drives and SD cards that Android has mounted now show up in the drawer by themselves and are read and written directly (Android 11 and later), instead of through a folder granted with **Add USB / SD folder**. It is faster, keeps file names as they are, and a folder you had granted for the same drive is no longer listed twice. Before Android 11, or for drives Android does not mount, the folder grant still works.

### Fixed
- Copying a big file (64 MB or more) into a folder added with **Add USB / SD folder** failed with "Temporary file … vanished": the storage renamed the temporary file while it was being written. The temporary file now uses the generic type, and is still found if a backend renames it anyway.

## [0.5.0] - 2026-10-05

### Added
- Thumbnails of photos and videos in the list and grid, so you can tell what you are about to copy. Videos show a frame and a play badge. Photos on network accounts only get them when you turn on **Thumbnails on network accounts** in Settings (off by default; small photos only).
- The menu of a file or folder keeps it highlighted and has a **Select** entry that starts selecting, so you can add more items.
- Rename a network account: three-dot menu of the account in the side drawer.

### Fixed
- The Connect a server dialog squeezed the SMB option into a column of letters; the options now wrap, the form scrolls on short screens and SFTP and SMB confirm with **Connect** instead of "Sign in with browser".

## [0.4.1] - 2026-10-04

### Fixed
- With two panels side by side, tapping a folder in a side where you had chosen another panel sent it back to the previous panel.

## [0.4.0] - 2026-10-04

### Added
- Dynamic panels: add as many as you need with **+** and close them from the panel menu (with undo). Panels and their folders are remembered.
- Panel bar showing each panel's folder, with a drop-down to pick which panel a side shows; the active panel is highlighted.
- Setting to put the panel bar at the top or the bottom.

### Changed
- Landscape shows two fixed panels; portrait still swipes between panels.

## [0.3.0] - 2026-10-03

### Added
- Copies keep running with the screen off (battery exemption, Wi-Fi lock, journal of interrupted copies); reliability checks in Settings.
- Self-signed servers with a pinned certificate fingerprint per account, and plain HTTP behind an explicit confirmation.
- Compress to ZIP; browse ZIP, 7z, TAR and TAR.GZ as read-only folders; extract them; open archives sent by other apps.
- Built-in viewers for images, text, PDF, audio and video.
- SFTP accounts (password or private key, host key pinned per account) and SMB accounts.
- Instrumented UI tests.

### Changed
- Official releases come from `vX.Y.Z` tags and are signed with the project key; master builds are nightlies with their own application id.
