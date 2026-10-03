# Publicar en F-Droid

El proceso de firma y de publicación (igual que en UltimateDeck) está en [RELEASING.md](../RELEASING.md); la metadata para
fdroiddata, en [`fdroid/com.qtekfun.ultimatefiles.yml`](../fdroid/com.qtekfun.ultimatefiles.yml). Aquí, el estado:

- **Política de F-Droid:** GPL-3.0-or-later, sin dependencias propietarias, sin analíticas ni Google Play Services y sin el
  bloque de metadatos de dependencias en el APK. sshj, smbj, BouncyCastle, commons-compress, xz y OkHttp son
  Apache-2.0/MIT/BSD o dominio público.
- **Compilación reproducible:** el workflow `reproducible.yml` compila dos veces, sin caché y desde directorios distintos, y
  compara los APK sin firmar con `apksigcopier`, que es lo que hace F-Droid. Sin clave, `assembleRelease` deja el APK sin
  firmar. No sustituye a la revisión de F-Droid: su servidor usa su propio entorno (la receta instala JDK 21).
- **Ficha:** `fastlane/metadata/android/<locale>/` (en-US y es-ES) con textos, icono y `changelogs/<versionCode>.txt`.
  **Faltan las capturas de pantalla** (`images/phoneScreenshots/`).
- **Pendiente tuyo:** crear la clave y los secretos, etiquetar `v0.3.0`, rellenar `commit:` (SHA de la etiqueta) y
  `AllowedAPKSigningKeys` en la receta y enviarla como merge request a fdroiddata.

La receta es un **borrador que no se ha ejecutado en el servidor de compilación de F-Droid**.
