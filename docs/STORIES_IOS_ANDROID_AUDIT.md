# Stories: auditoría de paridad iOS → Android

Fecha: 2026-07-30  
Fuente de verdad: `Moments` (Swift/iOS). Alcance: anillo del feed, apertura del viewer, carga del reel, privacidad, media y protección de captura.

## Flujo de referencia

`FeedStoryRingCoordinator` → `StoryRingAvatarView` → `FeedPresentationModifier` → `StoriesView` → `StoryViewModel` → `StoryTrayService.getAuthorStoryBundle` → `StoryRepository.decodeBackendStory` → `StoryViewerScreen` / media.

| Etapa | iOS | Android | Estado |
|---|---|---|---|
| Bandeja y orden del ring | `Views/Feed/Stories/FeedStoryRingCoordinator.swift` | `views/feed/stories/FeedStoryRingCoordinator.kt` | Paridad. Ambos usan la CF, cache, segmentos vistos y fuerzan al usuario actual al inicio. |
| Ring visual y estado | `Views/story/StoryRingAvatarView.swift` | `views/story/StoryRingAvatarView.kt` | Paridad visual/estado: 50 pt/dp, trazo 3, hueco 1.5, snapshots cacheados y segmentos. Falta solo la transición de zoom de perfil, que no tiene equivalente Android todavía. |
| Apertura desde feed | `Views/Feed/Core/FeedPresentationModifier.swift` | `views/feed/core/FeedPresentationModifier.kt` | Paridad de ruta: ambos entregan `startAtUserId` y el orden bloqueado del ring. |
| Selección del autor pulsado | `Views/story/StoriesView.swift` | `views/story/StoriesView.kt` | **Rota en Android.** Ver hallazgo A. |
| Carga con privacidad | `Views/story/StoryViewModel.swift` | `views/story/StoryViewModel.kt` | Contrato y fallback equivalentes: CF primero; Firestore + `canUserViewStoryEnhanced` como respaldo. |
| Decodificación de la historia | `Views/story/StoryRepository.swift` | `views/story/StoryRepository.kt` | Paridad de campos y fallbacks `mediaItem` → `imagePath`/`videoUrl`. |
| Canvas de foto/vídeo | `StoryViewer/StoryViewerScreen.swift` | `storyviewer/StoryViewerScreen.kt`, `StoryViewerMedia.kt` | Estructura equivalente (blur de fondo, fill/fit, póster, ExoPlayer). Android no muestra placeholder/error de Coil para imágenes: puede parecer un canvas negro mientras falla o carga la URL. Ver hallazgo B. |
| Protección de contenido | `ScreenshotProtectedView.swift` | `views/shared/ScreenshotProtectedView.kt` | Diferencia de plataforma. iOS protege el subárbol; Android aplica `FLAG_SECURE` a toda la ventana. Las capturas ADB/screen recording salen negras para una historia no `everyone`, aunque el contenido se debe ver en el dispositivo. Ver hallazgo C. |
| Visto, reacciones y viewers | `StoryViewModel.swift`, `StoriesView.swift` | `StoryViewModel.kt`, `StoriesView.kt` | Paridad funcional revisada: se carga el estado de visto y se elige la primera historia no vista. |

## Hallazgos que bloquean la apertura

### A. El viewer pide el reel equivocado al abrir un ring

En ambos puertos, al abrir desde el feed se fija el orden y se llama a `applyStoryIndexForUser`.

- En Swift, `userIds = lockedRingNavigationUserIds` queda disponible inmediatamente; por tanto se precarga el autor pulsado y sus vecinos.
- En Compose, `val userIds = hostUserIds` es una instantánea de la composición actual. Justo después de `hostUserIds = lockedRingNavigationUserIds`, la función local todavía lee la lista anterior. La petición termina contra el usuario actual, no contra el autor que se ha pulsado.

Evidencia observada en dispositivo: al pulsar el usuario `k95…`, la ruta ya lo marcaba como actual, pero el único reel cargado era `IKz…` (el usuario actual). Tras el timeout de tres segundos se mostraba “Error al cargar historia”.

**Arreglo propuesto:** no depender de una recomposición para la carga inicial. Obtener el `targetId` directamente en la rama de apertura y solicitar `loadAuthorReelIfNeeded(targetId, viewerId)`; después precargar vecinos usando explícitamente `lockedRingNavigationUserIds`. Mantener `applyStoryIndexForUser` para cambios de página posteriores.

### B. Segundo diagnóstico: media negra una vez que el reel sí llega

En una prueba temporal, Android llegó a decodificar `1/1` historias del autor correcto. Eso separa la carga del reel de la pintura del media: no se debe dar por resuelto el viewer solo porque desaparezca el error.

Antes de tocar el renderer hay que probar en el móvil (no vía captura protegida) una historia pública y otra privada y registrar:

1. `story.mediaItem.type`, URL y `audience` ya decodificados.
2. Éxito/error de Coil para imagen o estado de ExoPlayer para vídeo.
3. Si la historia privada solo es negra en ADB, clasificarlo como el hallazgo C y no como fallo de canvas.

Mejora necesaria aunque la URL sea válida: añadir un placeholder/progreso y estado de error a las dos `AsyncImage` de `StoryViewerMedia.kt`, equivalente al `KFImage.placeholder` de iOS. Hoy un error de Coil se ve exactamente como un fondo negro sin explicación.

### D. Compositor exclusivo del Deck Pass (confirmado en dispositivo)

El media no fallaba: Coil descargó y decodificó correctamente la URL pública de la historia que el feed mostraba negra. Esa misma historia se veía al abrir el viewer fuera del feed.

La diferencia era `StoryUserDeckPager.kt`: monta los viewers vecino y central en un `Row` con capas gráficas personalizadas. En el dispositivo de prueba, ese compositor dejaba visible el chrome pero pintaba negro el bitmap del autor ajeno. El viewer individual no tiene esa capa y se ve correctamente.

Se mantiene un fallback funcional: el feed usa el viewer individual y conserva el avance entre autores al terminar/tocar la historia. El carrusel visual lateral queda desactivado hasta sustituir el pager personalizado por una implementación Android nativa y volver a probarlo en el dispositivo.

### C. `FLAG_SECURE` invalida las capturas de depuración

`ScreenshotProtectedView.kt` activa `WindowManager.LayoutParams.FLAG_SECURE` en toda la Activity cuando la audiencia no es `everyone`. Por diseño, `adb exec-out screencap` y grabaciones del sistema quedan negras. iOS usa `UITextField.isSecureTextEntry` sobre el contenido protegido y puede seguir mostrando UI no protegida.

Esto no requiere retirar la protección. Para verificar Stories se deben usar historias `everyone` o inspección visual directa en el dispositivo. Si se necesita una herramienta interna de QA, ha de ser un flag de compilación explícito, nunca un bypass en release.

## Orden de corrección y pruebas

1. Corregir A con una carga inicial explícita y sin capturas de estado Compose obsoletas.
2. Probar en el móvil una historia pública de imagen y una de vídeo; confirmar apertura, progreso, avance y cierre.
3. Probar una historia privada visualmente; verificar que la captura ADB sea negra por seguridad pero la pantalla física no.
4. Añadir placeholder/error visible de media (B) y repetir las pruebas.
5. Revisar la transición de perfil del ring como mejora de interacción, no como bloqueo de Stories.

## Inventario de `StoryRingAvatarView`

El componente se usa en feed/explore, perfil, comentarios, mensajería, mapas, actividad, visitas, guardados/ajustes, notificaciones y viewer. La mayoría tienen equivalente Android. Antes de cambiar su API hay que conservar estos tres contratos: `userId`, `allowOwnStories` y `onTap(hasStory)`; son los que evitan abrir un viewer vacío y mantienen el ring consistente entre superficies.

### Cruce de usos

Hay 28 superficies consumidoras en cada plataforma (sin contar el archivo que define el propio componente). No hay una familia de pantallas ausente.

| iOS | Android equivalente | Estado |
|---|---|---|
| Feed, compartir, Discover Map, Map Place, Map bottom sheet y detalle de ubicación | `feed/core/sections`, `feed/sharing`, `feed/maps/*` | Portado. El detalle de ubicación se reparte entre `LocationMomentCard` y `LocationMomentDetailSections`. |
| Explore y cabecera de detalle | `explore/ExploreView`, `ModernExploreDetailHeader`, filas de comentario modernas | Portado; el avatar de `MomentDetailView.swift` vive en `EnhancedModernCommentRow.kt`, no en un archivo homónimo. |
| Comentarios clásico y moderno | `comments/ModernCommentsView`, `EnhancedModernCommentRow` | Portado y consolidado en la UI moderna Android. |
| Perfil: overview, badges, lista, conexiones, actividad compartida y menú contextual | `profile/**` | Portado. |
| Mensajería: bandeja, ubicación y media de una visualización | `GlassmorphicConversationRow`, `ChatLocationMessageBubble`, `ViewOnceImmersiveViewer` | Portado; la bandeja cambió de composición y por eso no aparece directamente en `MessagingView.kt`. |
| Ajustes: almacenamiento de chat y actividad | `settings/ChatStorageSettingsView`, `UserActivity*` | Portado. |
| Archivo: viewers y reacciones | `story/StoryStatsView.kt` | Portado, aunque la pantalla de archivo no contiene el avatar directamente: sus filas de estadísticas sí. |
| Visitas y notificaciones agrupadas | `models/VisitsView`, `notifications/NotificationGroupedFollowersOverlay` | Portado. |

**Única divergencia concreta encontrada:** `ConversationSettingsView.swift` usa `StoryRingAvatarView` para el avatar del remitente en el detalle de media compartida (40 pt). `ConversationSettingsView.kt` todavía usa la imagen de perfil normal para esa zona. Es un hueco visual/funcional pequeño y aislado; se puede portar después de estabilizar el viewer del feed.

## Auditoría de superficies Stories

El árbol específico de Stories está portado de forma casi completa: `StoriesView`, cadena, playback, gestos, overlays de texto/stickers, quick actions, respuestas, viewer, layout, anillo segmentado y repositorio tienen su par Android. También hay pantalla equivalente de archivadas (`archived stories.swift` → `ArchivedStoriesView.kt`) y de respuestas/estadísticas.

| Superficie que abre una historia | Ruta iOS | Ruta Android | Resultado |
|---|---|---|---|
| Ring del feed | `FeedPresentationModifier` con `startAtUserId` + orden del ring | Igual | Afectada por A; es el caso que fallaba. |
| Avatar en perfil, comentarios, exploración, mapas y detalle | `StoriesView(startWithUserId:)` | `StoriesView(startWithUserId = ...)` | Paridad de ruta; no comparte el bug de la lista del ring porque carga un único autor. |
| Mensajería y reels | `StoriesView(startWithUserId:)`, y cadena cuando procede | Igual | Paridad de entrada; verificar media con el mismo caso público/vídeo. |
| Highlight/cadena | `StoriesView(chainStories:startAtIndex:)` | `StoriesView(explicitStories,startAtIndex)` | Paridad estructural; queda prueba funcional pendiente. |
| Archivo y actividad | `ArchivedStoriesView` / rutas de actividad | `ArchivedStoriesView` / rutas de ajustes | Port presente; no bloquea la apertura del feed. |
| Deep link/notificación | ruta de historia → viewer de autor | `AppRouter.Destination.Story` / `NotificationNavigationService` | Requiere prueba de extremo a extremo, pero no hay ausencia de componente. |

## Conclusión de esta pasada

No falta “otro StoryRingAvatarView” ni una Cloud Function distinta en Android: el contrato, el ring y los modelos se corresponden. El problema del feed es un error de adaptación del modelo de estado SwiftUI a Compose (A), y el negro posterior debe verificarse como media/protección de captura antes de atribuirlo a la CF. La corrección de A está compilada localmente pero **no instalada**; queda deliberadamente sin validar hasta terminar las pruebas del audit.
