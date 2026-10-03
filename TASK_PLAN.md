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

- [ ] **Fase 4: Navegador de Archivos (Single Panel)**
  - [ ] 4.1 Crear `BrowserViewModel` para gestionar listado, ordenación, carga asíncrona y selección.
  - [ ] 4.2 Crear `FileList` y filas con iconos representativos según MIME type y tamaño legible.
  - [ ] 4.3 Menú contextual por ítem (pulsación larga / botón 3 puntos en fila).

- [ ] **Fase 5: Integración Dual Panel y Drag & Drop**
  - [ ] 5.1 Implementar `DualPanelScaffold` con detección de tamaño de pantalla (Compact vs Expanded).
  - [ ] 5.2 Configurar `HorizontalPager` para modo retrato y layout split 50/50 para apaisado.
  - [ ] 5.3 Integrar APIs de Drag & Drop entre paneles en modo split con diálogo modal "¿Copiar o Mover?".
  - [ ] 5.4 Navigation Drawer lateral con volúmenes detectados (Interno, USB).
