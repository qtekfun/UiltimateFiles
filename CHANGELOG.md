# Changelog

All notable changes. Versions follow [SemVer](https://semver.org/); the notes of each release are published with it.

## [Unreleased]

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
