# Publicar en F-Droid

Estado: la app cumple la política de inclusión de F-Droid (GPL-3.0-or-later, sin dependencias propietarias, sin
analíticas ni Google Play Services, sin el bloque de metadatos de dependencias en el APK; sshj, smbj, BouncyCastle,
commons-compress, xz, OkHttp y el resto son Apache-2.0/MIT/BSD/dominio público). La receta de abajo es un **borrador que no
se ha ejecutado en el servidor de compilación de F-Droid**.

## Decisión de firma (hay que elegir)

F-Droid puede publicar la app de dos maneras:

| | A. Firma F-Droid (por defecto) | B. Compilación reproducible, firma tuya |
| --- | --- | --- |
| Quién firma | F-Droid, con su clave | tú; F-Droid comprueba que su compilación es idéntica a tu APK y publica el tuyo |
| Actualizar entre GitHub y F-Droid | no se puede (firmas distintas): hay que desinstalar | sí, es la misma firma |
| Esfuerzo | ninguno | requiere que el build sea reproducible y la clave de [SIGNING.md](SIGNING.md) |

**Recomendación: B**, si el build es reproducible. El workflow `reproducible.yml` lo comprueba en cada PR que toca el
build (compila dos veces desde directorios distintos, sin caché, y compara todo salvo `META-INF`). Mientras ese
workflow esté en verde, B es viable; si no, A funciona sin más cambios.

## Ficha de la tienda

`fastlane/metadata/android/<locale>/` tiene título, descripciones corta y larga e icono de `en-US` y `es-ES`.
`docs/icon.svg` es el origen del icono (`images/icon.png` es su render de 512×512). Los registros de cambios por versión
están en `changelogs/<versionCode>.txt`. **Faltan las capturas de pantalla** (`images/phoneScreenshots/`).

## Versiones

`version.properties` es la única fuente de la versión (`versionName=X.Y.Z`, `versionCode=X*10000+Y*100+Z`;
`scripts/check-version.sh` lo exige en CI). Un checkout de cualquier commit compila con la versión correcta.

- **Release oficial:** subir las dos líneas de `version.properties`, añadir `changelogs/<versionCode>.txt` en cada
  idioma, mergear y etiquetar el commit `vX.Y.Z` (solo tres números). El workflow `release.yml` compila **esa** versión
  (sin sufijos), la firma con tu clave y adjunta `UltimateFiles-X.Y.Z.apk`: es el APK que F-Droid compara o recoge.
- **Nightly (GitHub):** cada merge a `master` publica una pre-release con el identificador
  `com.qtekfun.ultimatefiles.nightly`, que no interfiere con la oficial ni con F-Droid (su filtro de etiquetas solo
  acepta `vX.Y.Z`).

## Pasos pendientes (tuyos)

1. Generar la clave y poner los secretos ([SIGNING.md](SIGNING.md)).
2. Decidir A o B.
3. Etiquetar `v0.3.0` cuando quieras publicar (la versión actual de `version.properties`).
4. Capturas de pantalla.
5. Enviar la receta como merge request a https://gitlab.com/fdroid/fdroiddata y atender la revisión.

Puntos técnicos a vigilar en la revisión de F-Droid: el build usa AGP 9.4 (Gradle 9.8 del wrapper, JDK 17+; el CI usa
21) y `compileSdk 37`, así que el servidor debe tener esa plataforma y un JDK compatible; si no, la receta necesita
`sudo:` para instalarlo.

## Receta para fdroiddata (`metadata/com.qtekfun.ultimatefiles.yml`)

### Opción B (reproducible; sustituye el SHA-256 por el de tu certificado)

```yaml
Categories:
  - System
License: GPL-3.0-or-later
SourceCode: https://github.com/qtekfun/UltimateFiles
IssueTracker: https://github.com/qtekfun/UltimateFiles/issues

AutoName: UltimateFiles

RepoType: git
Repo: https://github.com/qtekfun/UltimateFiles.git

Binaries: https://github.com/qtekfun/UltimateFiles/releases/download/v%v/UltimateFiles-%v.apk

Builds:
  - versionName: 0.3.0
    versionCode: 300
    commit: v0.3.0
    subdir: app
    gradle:
      - yes

AllowedAPKSigningKeys: <sha256 del certificado, minúsculas, sin dos puntos>

AutoUpdateMode: Version
UpdateCheckMode: Tags ^v[0-9]+\.[0-9]+\.[0-9]+$
UpdateCheckData: version.properties|versionCode=(\d+)|.|versionName=(.*)
CurrentVersion: 0.3.0
CurrentVersionCode: 300
```

### Opción A (firma F-Droid): la misma receta sin `Binaries:` ni `AllowedAPKSigningKeys:`.

La receta es un **borrador que no se ha ejecutado en el servidor de compilación de F-Droid**.
