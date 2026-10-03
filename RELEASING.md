# Releasing

## Versions

- The version lives in one place: `appVersion` in `gradle.properties`, as SemVer (`1.2.3`), or `1.2.3-rc.N` for a release candidate.
- The Android version code is derived from it, never set by hand: `(MAJOR*10000 + MINOR*100 + PATCH) * 100 + N`, with `N = 99` for a final release. So `0.3.0` is `30099` and `1.0.0-rc.1` is `1000001`: a final version always sorts after its release candidates, and nothing depends on dates or the machine (reproducible builds). `./scripts/check-version.sh` prints it.
- Before 1.0.0 the app is `0.x`.

## Signing (one time)

Releases are signed with the project's own key, and the builds are reproducible: F-Droid builds the same source, checks that its APK matches the one published here and then ships ours. That way the app can be updated from F-Droid or GitHub interchangeably.

1. Create the key, and keep the file and passwords somewhere safe and **backed up**: if the key is lost, users would have to uninstall to update. Run this on your own machine; the key must not be generated or pasted anywhere else.
   ```sh
   keytool -genkeypair -v -keystore ultimatefiles-release.jks -alias ultimatefiles \
     -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Add these secrets to the GitHub repository (Settings → Secrets and variables → Actions):
   - `UF_KEYSTORE_BASE64`: `base64 -w0 ultimatefiles-release.jks`
   - `UF_KEYSTORE_PASSWORD`, `UF_KEY_ALIAS` (`ultimatefiles`), `UF_KEY_PASSWORD`
3. For F-Droid, give them the certificate fingerprint (`AllowedAPKSigningKeys` in its metadata), lower case without colons:
   ```sh
   keytool -list -v -keystore ultimatefiles-release.jks -alias ultimatefiles | grep SHA256 | sed 's/.*SHA256: //' | tr -d ':' | tr 'A-F' 'a-f'
   ```

Without these variables, `./gradlew assembleRelease` builds an unsigned APK, which is what F-Droid does before comparing.
(`-PdebugSigning=true` signs it with the debug key instead; only the nightly builds use it when the secrets are missing.)

## Making a release

1. Move the `[Unreleased]` notes in `CHANGELOG.md` under `## [X.Y.Z] - YYYY-MM-DD`, and add the fastlane changelog `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt` (en-US and es-ES).
2. Set `appVersion=X.Y.Z` in `gradle.properties`.
3. Commit (`chore: release X.Y.Z`), merge to `master`, then tag and push the tag:
   ```sh
   git tag vX.Y.Z && git push origin vX.Y.Z
   ```
4. The **Release** workflow checks that the tag matches `appVersion` and that `CHANGELOG.md` has its section, runs `./gradlew check`, builds the signed APK and publishes a GitHub Release with the notes of that version. It fails if the signing secrets are missing. Release candidates (`-rc.N`) are marked as pre-releases.
5. F-Droid picks the new tag up by itself (`UpdateCheckMode: Tags`, final versions only).

Every other push to `master` publishes a **nightly** pre-release (`UltimateFiles-X.Y.Z-nightly.<run>.apk`) with the application id `com.qtekfun.ultimatefiles.nightly`, so it installs next to the official app and never competes with its version code.

## F-Droid

`fdroid/com.qtekfun.ultimatefiles.yml` is the app's metadata as submitted to [fdroiddata](https://gitlab.com/fdroid/fdroiddata) (`metadata/com.qtekfun.ultimatefiles.yml`). It has no comments because fdroiddata's tools remove them. F-Droid builds each tagged version with JDK 21, like CI, checks that its APK matches ours (`Binaries`, `AllowedAPKSigningKeys`) and then publishes ours. The `reproducible.yml` workflow checks the same thing in CI: it builds twice from different directories and compares the unsigned APKs the way F-Droid does (`apksigcopier`).
