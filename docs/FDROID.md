# Publishing on F-Droid

The signing and release process (the same as in UltimateDeck) is described in [RELEASING.md](../RELEASING.md); the
metadata for fdroiddata is in [`fdroid/com.qtekfun.ultimatefiles.yml`](../fdroid/com.qtekfun.ultimatefiles.yml). Status:

- **F-Droid policy:** GPL-3.0-or-later, no proprietary dependencies, no analytics or Google Play Services, and no
  dependency metadata block in the APK. sshj, smbj, BouncyCastle, commons-compress, xz and OkHttp are Apache-2.0, MIT, BSD or
  public domain.
- **Reproducible build:** the `reproducible.yml` workflow builds twice, without cache and from different directories, and
  compares the unsigned APKs with `apksigcopier`, which is what F-Droid does. Without a key, `assembleRelease` leaves the
  APK unsigned. It does not replace F-Droid's review: their server uses its own environment (the recipe installs JDK 21).
- **Listing:** `fastlane/metadata/android/<locale>/` (en-US and es-ES) with texts, icon and `changelogs/<versionCode>.txt`.
  **Screenshots are still missing** (`images/phoneScreenshots/`).
- **Done:** signing key and secrets created, `v0.3.0` tagged and released, `commit:` and `AllowedAPKSigningKeys` filled in
  the recipe.
- **Still to do:** add the screenshots and submit the recipe as a merge request to fdroiddata.

The recipe is a **draft that has not been run on F-Droid's build server**.
