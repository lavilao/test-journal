# Plan de Implementación: Corrección Integral y Modos Google / Samsung

Este plan aborda todos los problemas reportados por el usuario, agrupados en módulos arquitectónicos y de experiencia de usuario.

---

## 1. Clima Real y Widget Pixel "At a Glance" (Transparente y Borderless)

### Problemas a resolver:
* La información del clima no era real.
* El widget At a Glance tenía bordes y fondo gris en lugar de ser transparente y sin bordes como el de los Pixel.
* Quitar el icono de experimento (matraz) en la cabecera.

### Solución técnica:
1. **Permiso de Ubicación & Clima en Vivo**:
   * Declarar `ACCESS_FINE_LOCATION` y `ACCESS_COARSE_LOCATION` en `AndroidManifest.xml`.
   * Crear `WeatherService` que consulta la API pública y gratuita de **Open-Meteo** (`https://api.open-meteo.com/v1/forecast?current=temperature_2m,weather_code`) usando la ubicación GPS real del dispositivo (o Madrid/CDMX como fallback si el usuario no otorga permiso).
   * Al pulsar el clima en el widget, abrir la app del clima nativa del sistema mediante `Intent(Intent.ACTION_VIEW, Uri.parse("https://weather.com/..."))` o `Intent("android.intent.action.VIEW")`.
2. **Widget Pixel At a Glance**:
   * Rediseñar `AtAGlanceBar.kt` para que sea **100% transparente y sin bordes** (`color = Color.Transparent`, `elevation = 0.dp`), con tipografía nítida y alineación idéntica a Google Pixel.
   * Eliminar el icono de matraz/laboratorio de la barra superior.

---

## 2. Modos Google vs. Samsung (NowBrief) y Reubicación del Briefing

### Problemas a resolver:
* Mover el widget de briefing a su propia ventana/modo.
* Ofrecer un "Modo Google" y un "Modo Samsung" configurable desde Ajustes.
* Eliminar los 3 botones debajo de la barra de búsqueda (IA, audio, foto) ya que sus funciones están en la propia barra y en el FAB.

### Solución técnica:
1. **Configuración en Ajustes**:
   * Crear enum `AppInterfaceMode { GOOGLE, SAMSUNG }` persistido en `SharedPreferences`.
   * En `SettingsAndModelsScreen.kt`, añadir un selector visual para alternar entre:
     * **Modo Google**: Pantalla de inicio con widget Pixel At a Glance transparente, barra de búsqueda limpia y feed de noticias estilo Google Discover.
     * **Modo Samsung (NowBrief)**: Pantalla de inicio centrada en la tarjeta dinámica de NowBrief (Briefing matutino/vespertino/nocturno, citas rotativas y progreso de actividad).
2. **Limpieza de cabecera**:
   * Eliminar los botones redundantes de "Modo IA", "Audio" y "Foto" bajo la barra de búsqueda.
   * Corregir el bug visual de la sombra rectangular en la barra de búsqueda aplicando `Modifier.clip(RoundedCornerShape(32.dp))` y `graphicsLayer` apropiado.

---

## 3. Integración con Calendario de Android y Selector de Hora en Tareas

### Problemas a resolver:
* La app no se vinculaba con el calendario.
* Las notificaciones/tareas no permitían asignar hora exacta.
* No se podían eliminar las tareas locales creadas.

### Solución técnica:
1. **Permisos y Sincronización de Calendario**:
   * Añadir `READ_CALENDAR` y `WRITE_CALENDAR` en `AndroidManifest.xml`.
   * Crear `CalendarSyncManager` que lee eventos reales del calendario del sistema (`CalendarContract.Events` / `CalendarContract.Instances`).
   * Mostrar el próximo evento real del calendario en el widget At a Glance.
2. **Selector de Fecha y Hora en Tareas**:
   * Actualizar el diálogo de tareas para incluir tanto selector de fecha (`DatePickerDialog`) como selector de hora (`TimePickerDialog`).
   * Al guardar una tarea con fecha/hora, registrarla tanto en Room local como en el calendario del dispositivo si se otorga el permiso.
   * Añadir botón de eliminación (`Icons.Default.Delete`) y acción para borrar tareas de la base de datos local.

---

## 4. Búsqueda Real de Contactos y Archivos del Dispositivo

### Problemas a resolver:
* La búsqueda no incluía los archivos y contactos reales del usuario ni pedía los permisos necesarios.

### Solución técnica:
1. **Permisos del Sistema**:
   * Añadir `READ_CONTACTS`, `READ_MEDIA_IMAGES`, `READ_MEDIA_AUDIO` y `READ_MEDIA_VIDEO` (o `READ_EXTERNAL_STORAGE` en Android < 13).
2. **Buscador de Contactos y Archivos (`DeviceSearchManager`)**:
   * Crear consultas mediante `ContentResolver`:
     * `ContactsContract.CommonDataKinds.Phone` para buscar contactos reales por nombre y número con acción directa de llamar/enviar mensaje.
     * `MediaStore.Files` / `MediaStore.Images` / `MediaStore.Audio` para buscar archivos locales almacenados en el teléfono (descargas, documentos, fotos).
   * En `GoogleUniversalSearchScreen.kt`, mostrar pestañas claras: *Todo*, *Contactos*, *Archivos del teléfono*, *Notas* y *Noticias*.

---

## 5. Vista de Actividades Real: Apps Abiertas y Archivos Recientes

### Problemas a resolver:
* La vista de actividades sólo mostraba notas o botones estáticos para abrir apps, sin mostrar la actividad real reciente del usuario.

### Solución técnica:
1. **Apps Recientemente Abiertas (`UsageStatsManager`)**:
   * Utilizar `UsageStatsManager.queryUsageStats()` con el permiso `PACKAGE_USAGE_STATS`.
   * Si el permiso no está otorgado, mostrar un banner amigable que permite abrir `Settings.ACTION_USAGE_ACCESS_SETTINGS`.
   * Mostrar la lista real de las aplicaciones más utilizadas y abiertas recientemente por el usuario con su tiempo de uso y fecha de última apertura.
2. **Archivos Recientes del Dispositivo**:
   * Consultar `MediaStore.Files` ordenado por `DATE_MODIFIED DESC` limitando a los últimos 15 archivos modificados o creados en el almacenamiento del teléfono.
   * Permitir abrirlos directamente con la aplicación correspondiente mediante `Intent.ACTION_VIEW` con `FileProvider`.

---

## 6. Corrección de Bugs en Modelos ML y Dictado por Voz

### Problemas a resolver:
* El modelo acústico de dictado decía estar instalado pero al dictar aparecía "no disponible".
* El benchmark mostraba que el modelo de entidades funcionaba, pero salía la opción de descargarlo como si no estuviera y la app no lo reconocía hasta pulsar descargar.

### Solución técnica:
1. **Bug de ML Kit Entity Extraction**:
   * Corregir `MlKitAnalyzer.isEntityModelDownloaded()`: Actualmente validaba una clave incorrecta o no reactiva frente al `RemoteModelManager`. Enlazar el estado directamente al gestor oficial de ML Kit y actualizarlo tras cualquier análisis exitoso.
2. **Dictado por Voz Offline**:
   * Implementar `OfflineSpeechEngine`: Configurar `SpeechRecognizer` para solicitar `EXTRA_PREFER_OFFLINE = true` junto con `RecognizerIntent.LANGUAGE_MODEL_FREE_FORM`.
   * Si el motor de reconocimiento local no está instalado en el dispositivo, proporcionar un transcriptor offline integrado y feedback claro sin bloquear al usuario.

---

## 7. Guardado de Artículos Completos RSS y Pull to Refresh

### Problemas a resolver:
* Las noticias guardadas sólo incluían el resumen, no el artículo completo.
* No había "Pull to Refresh" en el feed.
* Eliminar el texto "(Discover)" y dejar sólo "Noticias".

### Solución técnica:
1. **Extracción del Artículo Completo**:
   * Al guardar o leer un artículo, extraer el campo `content:encoded` del feed RSS o realizar una lectura en segundo plano del HTML limpio del artículo para guardarlo completo en la nota de Room.
2. **Pull to Refresh**:
   * Envolver la lista principal con `PullToRefreshBox` de Material 3 en `TimelineScreen.kt`.
3. **Limpieza de texto**:
   * Reemplazar "(Discover)" por "Noticias".
   * Eliminar todas las menciones repetitivas de "100% offline", "0 bytes enviados a la nube", simplificando el diseño visual.

---

## 8. Limpieza de Botones de Ajustes y Desacople de Navegación

### Problemas a resolver:
* Había 2 botones para ir a Ajustes.
* La vista de ajustes seguía vinculada al indicador de actividades.

### Solución técnica:
1. Dejar un único botón de acceso a Ajustes en el avatar superior derecho.
2. Corregir el enum y la navegación de `MainNavTab` para que la pestaña `ACTIVIDAD` tenga su propio estado e icono (`Icons.Default.History`), y `SETTINGS` sea una pantalla de ajuste independiente que no altere el tab activo de actividades.

---

## Plan de Verificación

* **Compilación**: Ejecutar `compile_applet` para confirmar la ausencia de errores de sintaxis y tipos.
* **Pruebas Unitarias**: Ejecutar `gradle :app:testDebugUnitTest` para validar compatibilidad.
* **Flujos Clave (CUJs)**:
  1. Cambio de Modo Google <-> Modo Samsung en Ajustes.
  2. Obtención de clima real e inspección de widget At a Glance transparente.
  3. Búsqueda de contactos y archivos reales con solicitud de permisos.
  4. Vista de actividades con apps reales mediante UsageStats.
  5. Creación de tarea con fecha y hora, y eliminación de la misma.
  6. Guardado de noticia RSS con contenido completo.
