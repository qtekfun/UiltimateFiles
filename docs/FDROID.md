# Publishing on F-Droid

Status: the app already complies with the F-Droid inclusion policy (GPL-3.0-or-later, no proprietary
dependencies, no analytics, no Google Play Services, no dependency-info blob in the APK). What is missing
is listed below. The recipe is a **draft that has not been run on the F-Droid build server**.

## Store listing

`fastlane/metadata/android/<locale>/` holds the title, short and full description and the icon for `en-US`
and `es-ES`. `docs/icon.svg` is the source of the icon (`images/icon.png` is its 512×512 render).
Per-version changelogs live in `changelogs/<versionCode>.txt`; phone screenshots are still missing.

## Versioning

`version.properties` is the single source of truth (`versionName=X.Y.Z`, `versionCode=X*10000+Y*100+Z`;
`scripts/check-version.sh` enforces the relation in CI). A checkout of any commit therefore builds with the right
version, which is what F-Droid needs.

- **Release for F-Droid:** bump both lines in `version.properties`, add `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt`,
  merge, then tag the merge commit `vX.Y.Z` (three numbers only).
- **GitHub builds:** every merge to `master` publishes `UltimateFiles-X.Y.Z.<run>.apk` with
  `versionCode = <committed code> * 100000 + <run>`, so each build upgrades the previous one. Those four-number tags
  are ignored by F-Droid (its tag filter below only matches `vX.Y.Z`).

## Open points before submitting

1. **Signing.** F-Droid signs with its own key. Users moving from the GitHub APK (debug key, or your own key
   once the `SIGNING_*` secrets exist) must uninstall first, unless reproducible builds are set up.
2. **Toolchain.** AGP 9.4 needs Gradle 9.8 (wrapper) and JDK 17+, and the build uses `compileSdk 37`;
   check that the build server image provides that platform.
3. **Screenshots** in `fastlane/metadata/android/<locale>/images/phoneScreenshots/`.

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
  - versionName: 0.2.0
    versionCode: 200
    commit: v0.2.0
    subdir: app
    gradle:
      - yes

AutoUpdateMode: Version
UpdateCheckMode: Tags ^v[0-9]+\.[0-9]+\.[0-9]+$
UpdateCheckData: version.properties|versionCode=(\d+)|.|versionName=(.*)
CurrentVersion: 0.2.0
CurrentVersionCode: 200
```

Submit it as a merge request to https://gitlab.com/fdroid/fdroiddata after resolving the points above.
The recipe is a **draft that has not been run on the F-Droid build server**.
