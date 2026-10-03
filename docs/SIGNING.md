# Firmar las releases

Hasta ahora las releases se firman con la clave *debug* porque no hay clave propia en GitHub. Para tener una clave real y
poder entrar en F-Droid con ella, hay que hacer esto **una vez, en tu máquina** (la clave no debe generarse ni pegarse en
ningún otro sitio: ni en el repo ni en un chat).

## 1. Crear la clave

```bash
./scripts/generate-keystore.sh            # crea ultimatefiles-release.jks en el directorio actual
```

Pide una contraseña (≥ 12 caracteres) y crea un almacén PKCS12 con una clave RSA de 4096 bits válida unos 27 años.
Al terminar imprime todo lo que necesitas en los pasos siguientes.

**Haz copia del `.jks` ya** (gestor de contraseñas y una copia sin conexión) junto con la contraseña. Si se pierde, no se
puede publicar ninguna actualización que Android acepte sobre lo ya instalado.

## 2. Secretos en GitHub

*Settings → Secrets and variables → Actions → New repository secret*:

| Secreto | Valor |
| --- | --- |
| `SIGNING_KEYSTORE_BASE64` | la línea larga en base64 que imprime el script |
| `SIGNING_STORE_PASSWORD` | la contraseña |
| `SIGNING_KEY_ALIAS` | `ultimatefiles` (o el alias que usaras) |
| `SIGNING_KEY_PASSWORD` | la misma contraseña |

No hay que cambiar nada más: `release.yml` y `app/build.gradle.kts` ya leen esos cuatro valores.

## 3. Cómo se publica ahora

- **Release oficial:** etiqueta `vX.Y.Z` (los tres números iguales a `version.properties`). El workflow compila con
  exactamente esa versión, **falla si faltan los secretos** (nunca publica una oficial con la clave debug), y adjunta
  `UltimateFiles-X.Y.Z.apk`. Muestra el SHA-256 del certificado en el log.

  ```bash
  git tag v0.3.0 && git push origin v0.3.0
  ```
- **Nightly:** cada push a `master` publica una pre-release `nightly-<n>` con otro identificador
  (`com.qtekfun.ultimatefiles.nightly`), así que se instala **al lado** de la oficial y no compite con su `versionCode`.
  Usa la clave del repositorio si está configurada, y la de debug si no.

## 4. Primera instalación con la clave nueva

Android no deja actualizar una app con otra firma: quien tenga instalada una versión firmada con la clave debug debe
desinstalarla una vez (exporta antes una copia de seguridad desde *Ajustes → Copia de seguridad*; incluye cuentas).
Desde ahí, las actualizaciones encajan siempre que se firmen con esta clave.

## 5. Comprobar la firma de un APK

```bash
apksigner verify --print-certs UltimateFiles-0.3.0.apk | grep SHA-256
```
El valor debe coincidir con el que imprimió el script.
