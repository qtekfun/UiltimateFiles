# Plan de Tareas de Implementación

- [x] **Fase 1: Base del Proyecto y Dominio Core**
  - [x] 1.1 Configurar `build.gradle.kts` con Compose, Material3, Koin, DataStore y Coroutines.
  - [x] 1.2 Modelos de datos: `FileItem`, `StorageVolume`, `ClipboardState`, `TransferProgress`.
  - [x] 1.3 Contrato de interfaz `FileSystemRepository` y `ClipboardManager` compartido.

- [x] **Fase 2: Motor de I/O y Foreground Service**
  - [x] 2.1 Implementar `FileStreamCopier` con buffer de 64KB y canal de reporte de velocidad/bytes.
  - [x] 2.2 Crear `FileTransferForegroundService` con notificación persistente actualizable.
  - [x] 2.3 Implementar `SafFileSystemRepository` para gestionar Almacenamiento Interno y USB OTG vía Storage Access Framework.
  - [x] 2.4 Lógica de resolución de colisiones de nombres (sobrescribir, omitir, autorenombramiento).

- [x] **Fase 3: Componentes de UI y Sistema de Menús**
  - [x] 3.1 Implementar `BreadcrumbBar` interactivo con saltos de ruta en la Top Bar.
  - [x] 3.2 Implementar barra superior en reposo con overflow (*Nueva carpeta*, *Ordenar*, *Seleccionar todo*).
  - [x] 3.3 Implementar Contextual Action Bar (CAB) cuando hay selección activa (*Copiar*, *Cortar*, *Eliminar*).
  - [x] 3.4 Implementar `DockedPasteBar` inferior que se activa según el estado del `ClipboardManager`.
  - [x] 3.5 Crear `PropertiesBottomSheet` con cálculo de hash MD5/SHA-256 en corrutina background.

- [x] **Fase 4: Navegador de Archivos (Single Panel)**
  - [x] 4.1 Crear `BrowserViewModel` para gestionar listado, ordenación, carga asíncrona y selección.
  - [x] 4.2 Crear `FileList` y filas con iconos representativos según MIME type y tamaño legible.
  - [x] 4.3 Menú contextual por ítem (pulsación larga / botón 3 puntos en fila).

- [x] **Fase 5: Integración Dual Panel y Drag & Drop**
  - [x] 5.1 Implementar `DualPanelScaffold` con detección de tamaño de pantalla (Compact vs Expanded).
  - [x] 5.2 Configurar `HorizontalPager` para modo retrato y layout split 50/50 para apaisado.
  - [x] 5.3 Integrar APIs de Drag & Drop entre paneles en modo split con diálogo modal "¿Copiar o Mover?".
  - [x] 5.4 Navigation Drawer lateral con volúmenes detectados (Interno, USB).

- [x] **Fase 6: Feedback de la primera prueba (v0.1.5)**
  - [x] 6.1 Historial de tareas completadas (copiar, mover, eliminar) persistente y accesible desde el cajón.
  - [x] 6.2 Detección de discos expulsados o desconectados: el panel que estaba en ese volumen vuelve a una carpeta disponible y avisa.
  - [x] 6.3 Pantalla de ajustes con tema (sistema, claro, oscuro, AMOLED) y colores dinámicos.

- [x] **Fase 7: Ficheros enormes y Nextcloud**
  - [x] 7.1 Copias robustas de ficheros grandes: escritura a `*.ultimatefiles-part` + renombrado, verificación SHA-256 opcional antes de borrar el origen en movimientos, buffer de 1 MiB, wake lock, ETA.
  - [x] 7.2 Test de transferencia de 8 GiB simulada (flujo generado, CRC32) sin necesidad de disco.
  - [x] 7.3 Cuentas WebDAV/Nextcloud (HTTPS, contraseña de aplicación cifrada con Android Keystore), subida por trozos de Nextcloud (chunked v2) y descarga reanudable (Range).
  - [x] 7.4 Pausar/reanudar copias, tareas en curso en el historial y notificación visible en la pantalla de bloqueo.
  - [x] 7.5 Exportar/importar ajustes y cuentas (cifrado con frase de contraseña) y renombrado de la app a UltimateFiles.
  - [x] 7.6 Vista en cuadrícula compartida por los dos paneles.
  - [x] 7.7 Subidas a Nextcloud reanudables tras morir la app (hash por trozo).
  - [x] 7.9 Certificados autofirmados (huella SHA-256 por cuenta) y HTTP opcional con confirmación.
  - [x] 7.10 Copias fiables con la pantalla apagada: exención de batería, Wi-Fi lock, diario y reanudación, comprobaciones en Ajustes.
  - [x] 7.11 Comprimir en ZIP y extraer ZIP/TAR/TAR.GZ (`ArchiveEngine`, protección zip-slip).
  - [x] 7.12 Visores integrados (imagen, texto, PDF, audio/vídeo) en `ViewerActivity`.
  - [x] 7.13 Archivos comprimidos como carpetas (`archive://`), extracción de 7z y apertura desde otras apps.
  - [x] 7.8 Versión en el código (`version.properties`) compatible con F-Droid; releases de CI con `versionCode` creciente.
