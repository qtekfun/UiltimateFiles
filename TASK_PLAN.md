# Plan de Tareas de Implementación

- [x] **Fase 1: Setup del Proyecto y Core Domain**
  - [x] 1.1 Configurar build.gradle.kts con dependencias F-Droid compliant (Compose, Material3, Coroutines, DataStore).
  - [x] 1.2 Definir modelos de dominio: `FileItem`, `StorageVolume`, `FileOperation`.
  - [x] 1.3 Diseñar la interfaz `FileSystemRepository` y `UserPreferencesRepository`.

- [ ] **Fase 2: Implementación de I/O y Permisos**
  - [ ] 2.1 Implementar `LocalFileSystemRepository` usando SAF / `DocumentFile` para compatibilidad universal (Interno + USB).
  - [ ] 2.2 Implementar gestión de permisos (`MANAGE_EXTERNAL_STORAGE` / SAF document tree picker).
  - [ ] 2.3 Implementar UseCases atómicos: `ListDirectoryUseCase`, `DeleteFilesUseCase`, `CopyFilesUseCase`.

- [ ] **Fase 3: UI - Panel Individual (Browser)**
  - [ ] 3.1 Crear `BrowserViewModel` con gestión de navegación, carga de ficheros y selección múltiple.
  - [ ] 3.2 Crear componentes Compose: `FileList`, `FileListItem`, `BreadcrumbBar`, `BottomActionBar`.

- [ ] **Fase 4: UI - Dual Panel (Portrait / Landscape)**
  - [ ] 4.1 Implementar `DualPanelScaffold` que detecte orientación/WindowSizeClass.
  - [ ] 4.2 En portrait: HorizontalPager con 2 instancias independientes de `BrowserScreen`.
  - [ ] 4.3 En landscape: Row 50/50 con 2 instancias independientes de `BrowserScreen`.

- [ ] **Fase 5: Portapapeles y Operaciones entre Paneles**
  - [ ] 5.1 Implementar `ClipboardManager` compartido para operaciones Copiar/Mover del Panel A al Panel B.
  - [ ] 5.2 Diálogo de progreso en tiempo real para copia/movimiento de ficheros pesados.
  - [ ] 5.3 Intent de expulsión segura de unidades USB detectadas.
