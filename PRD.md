# Product Requirement Document (PRD) - Open Source File Manager

## 1. Visión del Producto
Explorador de archivos de código abierto y alta productividad para Android, centrado en la navegación fluida con doble panel (inspirado en Solid Explorer), compatible con almacenamiento interno y USB OTG, preparado para ser publicado en F-Droid.

## 2. Alcance del MVP (In Scope)

### 2.1 Navegación y UI
- **Dual Panel:**
  - *Portrait:* Pager horizontal con indicador de pestañas (Tab 1 / Tab 2) con deslizamiento lateral.
  - *Landscape:* Pantalla dividida al 50% mostrando ambos paneles simultáneamente.
- **Explorador por panel:**
  - Lista/cuadrícula de directorios y ficheros con icono por tipo, nombre, tamaño y fecha.
  - Breadcrumb o barra superior para navegación hacia directorios padre.
  - Pull-to-refresh y botón de "ir a la raíz".

### 2.2 Operaciones de Archivo
- Portapapeles contextual por panel: Copiar, Cortar (Mover), Pegar y Eliminar.
- Diálogo de confirmación antes de eliminar.
- Diálogo o barra de progreso para operaciones largas de I/O (copia/movimiento entre volúmenes).

### 2.3 Fuentes de Almacenamiento
- Almacenamiento interno (`MANAGE_EXTERNAL_STORAGE` o SAF según versión de Android).
- Discos/Pendrives USB (OTG) mediante Storage Access Framework (`DocumentFile` / `DocumentsContract`).
- Acción de expulsión segura (Unmount / Safely Remove intent) para dispositivos USB.

### 2.4 Preferencias
- Persistencia de última ruta abierta por panel.
- Persistencia del modo de vista (lista/cuadrícula) y ordenación (nombre, tamaño, fecha).

## 3. Fuera de Alcance para el MVP (Out of Scope)
- Conectores de red (WebDAV, Nextcloud, SMB, FTP).
- Visores o reproductores multimedia integrados (se delega a aplicaciones del sistema mediante intents).
- Descompresión/compresión de archivos (ZIP, 7z, RAR).
- Acceso Root.
- Búsqueda recursiva con indexación pesada.