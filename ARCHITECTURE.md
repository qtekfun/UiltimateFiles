# Arquitectura del Sistema

## 1. Stack Técnico
- **Lenguaje:** Kotlin 2.x
- **UI Toolkit:** Jetpack Compose + Material 3 (soporte Adaptive Layouts / WindowSizeClass).
- **Min SDK:** 26 (Android 8.0) | **Target SDK:** 35 (Android 15)
- **Inyección de Dependencias:** Hilt o Koin (preferible Koin para menor fricción de código generado en F-Droid).
- **Asincronía:** Kotlin Coroutines + StateFlow.
- **Persistencia de Configuración:** Jetpack DataStore (Preferences).
- **Acceso a Archivos:** Storage Access Framework (`DocumentFile`) + Abstracción `FileSystemRepository`.

## 2. Estructura de Módulos / Paquetes
```text
com.example.filemanager/
├── core/
│   ├── model/             # Modelos de dominio (FileItem, StorageVolume, OperationProgress)
│   ├── datastore/         # Preferencias (UserPreferencesRepository)
│   └── util/              # Extensiones y helpers de URI / MimeTypes
├── data/
│   ├── repository/        # Implementación de FileSystemRepository (LocalFileRepo, SafFileRepo)
│   └── filesystem/        # Drivers I/O y operaciones en segundo plano
├── domain/
│   └── usecase/           # CopyFilesUseCase, MoveFilesUseCase, DeleteFilesUseCase, ListFilesUseCase
└── ui/
    ├── dualpanel/         # Orquestador del layout Portrait (Pager) vs Landscape (Split)
    ├── browser/           # Componente reutilizable del explorador de archivos (ViewModel + Screen)
    ├── operations/        # Diálogos de progreso de I/O y confirmaciones
    └── theme/             # Material3 Theme