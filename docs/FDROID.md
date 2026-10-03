# Publishing on F-Droid

Status: the app already complies with the F-Droid inclusion policy (GPL-3.0-or-later, no proprietary
dependencies, no analytics, no Google Play Services, no dependency-info blob in the APK). What is missing
is listed below. The recipe is a **draft that has not been run on the F-Droid build server**.

## Store listing

`fastlane/metadata/android/<locale>/` holds the title, short and full description and the icon for `en-US`
and `es-ES`. `docs/icon.svg` is the source of the icon (`images/icon.png` is its 512×512 render).
Still to add: phone screenshots in `images/phoneScreenshots/` and per-version changelogs in
`changelogs/<versionCode>.txt` (see the versioning point below).

## Open points before submitting

1. **Version in the source.** `versionCode` / `versionName` are injected by CI (`-PversionCode`,
   `-PversionName`) and are *not* committed, so a tag checked out by F-Droid builds `versionCode 1`.
   Pick one: commit the version (for example a `version.properties` bumped on each release), or have the
   recipe set it with `prebuild`.
2. **Signing.** F-Droid signs with its own key. Users moving from the GitHub APK (debug key, or your own key
   once the `SIGNING_*` secrets exist) must uninstall first, unless reproducible builds are set up.
3. **Toolchain.** AGP 9.4 needs Gradle 9.8 (wrapper) and JDK 17+, and the build uses `compileSdk 37`;
   check that the build server image provides that platform.

## Draft recipe for fdroiddata (`metadata/com.qtekfun.fexplo.yml`)

```yaml
Categories:
  - System
License: GPL-3.0-or-later
SourceCode: https://github.com/qtekfun/UiltimateFiles
IssueTracker: https://github.com/qtekfun/UiltimateFiles/issues

AutoName: UltimateFiles

RepoType: git
Repo: https://github.com/qtekfun/UiltimateFiles.git

Builds:
  - versionName: 0.1.7
    versionCode: 7
    commit: v0.1.7
    subdir: app
    gradle:
      - yes

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 0.1.7
CurrentVersionCode: 7
```

Submit it as a merge request to https://gitlab.com/fdroid/fdroiddata after resolving the points above.
