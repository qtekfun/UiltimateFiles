# Product Requirement Document (PRD) - UltimateFiles, gestor de archivos open source

## 1. Visión del Producto
Clon funcional y visual de Solid Explorer para Android, optimizado para productividad con navegación dual panel, alta fidelidad en operaciones de archivo (interno y USB OTG), arquitectura en segundo plano no bloqueante y preparado para F-Droid.

## 2. Alcance del MVP (In Scope)

### 2.1 Navegación y Dual Panel
- **Layout Adaptativo:**
  - *Portrait / Compact:* `HorizontalPager` de 2 paneles con pestañas superiores sincronizadas y swipe lateral.
  - *Landscape / Expanded:* Split view fija 50/50 mostrando ambos paneles de forma simultánea.
- **Drag & Drop:**
  - En split view, arrastrar archivos/carpetas de un panel y soltarlos en el otro (o dentro de una subcarpeta visible).
  - Al soltar, mostrar diálogo rápido de confirmación: "¿Copiar o Mover?".
- **Breadcrumbs Interactivos:**
  - Barra de ruta segmentada en la Top Bar. Clic en cualquier segmento salta a ese directorio.
- **Navigation Drawer lateral:**
  - Acceso a volúmenes (Memoria Interna, USB OTG detectados).
  - Accesos directos predefinidos: Descargas, Documentos, Fotos (DCIM).

### 2.2 Acciones y Menús Contextuales (Sin FAB)
- **Top Bar en reposo:** Icono de menú lateral (Drawer), breadcrumb interactivo, búsqueda y menú overflow con: *Nueva carpeta*, *Nuevo archivo*, *Ordenar por*, *Seleccionar todo*.
- **Top Bar contextual (CAB):** Se activa al seleccionar 1 o más elementos. Muestra contador, iconos de *Copiar*, *Cortar*, *Eliminar*, *Compartir* y overflow (*Renombrar*, *Propiedades / Hash*).
- **Menú contextual de ítem:** Pulsación larga o menú de 3 puntos en cada fila para acciones rápidas sobre ese fichero individual, sin seleccionarlo antes (*Abrir con*, *Copiar*, *Cortar*, *Renombrar*, *Eliminar*, *Propiedades*).
- **Docked Paste Bar (Barra de Pegado acoplada):** Barra inferior fija, que no flota sobre el contenido, y que aparece solo si hay elementos en el portapapeles. Incluye: resumen de elementos en buffer, botón *Pegar aquí* y botón *Cancelar*.

### 2.3 Motor de I/O y Segundo Plano
- **Servicio en Foreground:** Copias y traslados pesados corren en un `ForegroundService` con notificación que muestra: fichero actual, porcentaje total y velocidad estimada (MB/s).
- **Resolución de Conflictos:** Diálogo al encontrar duplicados: *Sobrescribir*, *Omitir*, *Renombrar*, con opción de *Aplicar a todos*.
- **Almacenamiento USB OTG:** Integración mediante Storage Access Framework (`DocumentFile`), incluyendo intent de extracción segura.

### 2.4 Propiedades y Metadatos
- **Bottom Sheet de Propiedades:** Nombre, ruta completa, tamaño exacto (bytes y formato legible), fecha modificación, atributos/permisos y cálculo asíncrono de hash (MD5 y SHA-256).

## 3. Fuera de Alcance para el MVP (Out of Scope)
- Conectores de red SMB/SFTP - *Fase 2*. (WebDAV/Nextcloud ya incluido, ver Fase 7 del plan.)
- Crear 7z y archivos comprimidos con contraseña (se pueden explorar y extraer ZIP, 7z, TAR y TAR.GZ, ver sección 4).
- Visores para formatos que no sean imagen, texto, PDF, audio o vídeo (el resto se delega mediante Intents a apps externas).
- Acceso Root.

## 4. Ampliaciones posteriores al MVP (implementadas)
- Historial de tareas (también en curso y en cola), detección de volúmenes expulsados, ajustes de tema con modo AMOLED.
- Copias robustas de ficheros enormes (nombre temporal, verificación SHA-256 opcional), pausar/reanudar, notificación en pantalla de bloqueo.
- Nextcloud/WebDAV con *Login Flow v2*, subida por trozos y descarga reanudable (solo HTTPS).
- Certificados autofirmados con huella fijada por cuenta y HTTP opcional, con confirmación explícita.
- Copias fiables con la pantalla apagada (exención de batería, Wi-Fi lock, diario de copias interrumpidas, ajustes de fiabilidad).
- Comprimir en ZIP, extraer ZIP/7z/TAR/TAR.GZ por la cola de transferencias y explorarlos como carpetas de solo lectura; "Abrir con" desde otras apps.
- Visores integrados: imagen (zoom), texto, PDF, audio y vídeo.
- Vista en cuadrícula, abrir APK, exportar/importar ajustes y cuentas.
- Las subidas a Nextcloud interrumpidas (p. ej. la app muere) se reanudan al repetir la copia: solo se vuelven a enviar los trozos que cambian.

## 5. Pendiente / ideas
- SMB (SFTP ya está) y ver sección 3.
- Pruebas instrumentadas de UI y prueba real en dispositivo con ficheros de varios GB.
