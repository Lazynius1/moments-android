# Revisión estética y adaptación Android — Moments

Fork de trabajo de [`IOS_PORT_CHECKLIST.md`](IOS_PORT_CHECKLIST.md) para revisar la app **pantalla por pantalla** una vez alcanzada la paridad funcional.

Este documento no sustituye al checklist del port:

- `IOS_PORT_CHECKLIST.md` responde: **¿está portado lo que hace iOS?**
- Este checklist responde: **¿se ve, se siente y se comporta como una buena app Android sin perder la identidad de Moments?**

Fecha de creación: 2026-07-29.

## Estado

| Estado | Significado |
|---|---|
| `[ ]` | No revisado |
| `[~]` | Revisión o corrección en curso |
| `[!]` | Hallazgo confirmado; requiere corrección |
| `[x]` | Revisado, corregido y verificado |
| `[⏸]` | Aplazado: su fila de paridad iOS → Android todavía no está `[x]` |
| `N/A` | No aplica en Android, con motivo documentado |

Una fila sólo se cierra con `[x]` cuando incluye evidencia visual y de interacción. La existencia del `.kt`, la compilación o la paridad 1:1 con Swift no bastan.

### Puerta de entrada: sólo paridad `[x]`

La cola activa de esta auditoría se alimenta exclusivamente de filas cerradas con `[x]` en [`IOS_PORT_CHECKLIST.md`](IOS_PORT_CHECKLIST.md):

1. Antes de revisar una pantalla, localizar todos sus archivos Swift/Kotlin en el checklist del port.
2. Si todos los archivos necesarios están `[x]`, la pantalla entra en la cola estética Android.
3. Si algún archivo esencial está `[~]` o `[ ]`, marcar aquí `[⏸]` y no corregir su estética todavía.
4. Componentes compartidos ya `[x]` sí pueden revisarse aunque otra pantalla consumidora siga en curso.

Las tablas largas de este documento son el inventario de cobertura final. **La tabla “Cola activa” es la lista de trabajo actual.**

### Cola activa inicial

| Orden | Paridad | Pantalla / flujo | Motivo |
|---:|:---:|---|---|
| 1 | `[x]` | Login + registro + onboarding de perfil | Define formularios, botones, campos, teclado y permisos para el resto de la app |
| 2 | `[x]` | Feed principal | Define shell, navegación, cards, listas, refresh y edge-to-edge |
| 3 | `[x]` | Stories | Define media fullscreen, gestos, overlays y barras del sistema |
| 4 | `[x]` | Explore | Define búsqueda, chips, grids y navegación a contenido/perfiles |
| 5 | `[x]` | Perfil propio y perfil ajeno | Define headers colapsables, tabs, grids y sheets |
| 6 | `[x]` | Creator | Define cámara, Photo Picker, editores, IME y flujos fullscreen |
| 7 | `[x]` | Bandeja de mensajería y Chat | Define listas, conversación, composer, IME y acciones contextuales |
| 8 | `[x]` | Ajustes | Define navegación jerárquica, formularios, switches y diálogos |
| — | `[~]` | Comments | Fuera de la cola hasta cerrar la paridad y QA pendiente |
| — | `[~]` | Nova | Fuera de la cola; no se conecta ni se rediseña mientras siga en curso |

### Resumen vivo

| Área | Pendiente | En curso | Hallazgos | Aplazado | Cerrado |
|---|---:|---:|---:|---:|---:|
| Fundamentos y shell | 6 | 0 | 2 | 0 | 0 |
| Acceso y onboarding | 7 | 0 | 0 | 0 | 0 |
| Feed, Stories y comentarios | 12 | 0 | 0 | 2 | 0 |
| Explore, mapas y Echoes | 8 | 0 | 0 | 0 | 0 |
| Perfil y conexiones | 14 | 0 | 0 | 0 | 0 |
| Creator | 18 | 0 | 0 | 0 | 0 |
| Mensajería | 15 | 0 | 0 | 0 | 0 |
| Ajustes y actividad | 17 | 1 | 0 | 0 | 0 |
| Nova, notificaciones y misc. | 7 | 0 | 0 | 1 | 0 |

> Los conteos se actualizan al cambiar estados. Una fila puede representar una pantalla completa o un subflujo visual inseparable.

## Qué preservamos y qué adaptamos

### Se conserva desde iOS

- Jerarquía de producto, contenido, marca y tono.
- Funciones, datos, reglas de negocio, estados y destinos.
- Assets propios, composición editorial y rasgos visuales distintivos de Moments.
- Privacidad, contenido efímero, seguridad y comportamiento de publicación.

### Se adapta a Android

- Navegación atrás del sistema y predictive back donde aplique.
- Barras de sistema edge-to-edge, cutouts, gestos y teclado/IME.
- Bottom sheets, diálogos, menús, snackbars, selección y permisos.
- Touch targets, feedback táctil, foco, TalkBack y escalado de fuente.
- Cámara, selector de fotos, compartir, mapas y ajustes del sistema.
- Convenciones de Material 3 cuando mejoran comprensión o accesibilidad, sin convertir Moments en una plantilla genérica.

## Contrato visual: Moments sobre Material 3

Referencias de trabajo:

- [Android Skills — repositorio oficial](https://github.com/android/skills)
- [Material Design 3 en Jetpack Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [Design systems en Compose](https://developer.android.com/develop/ui/compose/designsystems)
- [Skill oficial edge-to-edge](https://github.com/android/skills/tree/main/system/edge-to-edge)

### Principio

**Material 3 aporta el lenguaje de interacción y la infraestructura; Moments conserva la dirección de arte.**

No se hará un reemplazo masivo de componentes sólo para obtener la apariencia Material por defecto. Se revisará cada pantalla y se elegirá entre:

1. Componente Material 3 tematizado, cuando su comportamiento y anatomía encajen.
2. Componente propio construido sobre tokens de `MaterialTheme`, cuando Moments necesite una composición distintiva.
3. API de plataforma, cuando Android ya ofrece el patrón correcto (Photo Picker, Sharesheet, permisos, Settings, predictive back).

### Reglas concretas

- **Color:** conservar paleta y contraste de Moments mediante `lightColorScheme` y `darkColorScheme` completos. Dynamic Color no sustituye la marca por defecto; sólo se añadirá si se decide como opción de producto.
- **Tipografía:** conservar Inter, mapeada a los roles M3 `display`, `headline`, `title`, `body` y `label`; evitar tamaños/pesos sueltos en cada pantalla.
- **Formas:** trasladar radios y siluetas recurrentes a `MaterialTheme.shapes`; las formas narrativas propias pueden seguir siendo custom.
- **Glass/transparencia:** sustituir blur y capas translúcidas por superficies sólidas o tonales (`surface`, `surfaceContainer*`), `outlineVariant`, scrims y elevación tonal. Mantener la jerarquía, no el material de iOS.
- **Componentes:** preferir `Scaffold`, app bars, `NavigationBar`, `ModalBottomSheet`, snackbars, dialogs, cards, buttons, text fields, switches y menus M3 cuando cubran el caso sin perder identidad.
- **Estados:** enabled, pressed, focused, selected, disabled, loading y error deben usar feedback coherente de Android; ripple/state layer no se elimina salvo razón de interacción documentada.
- **Navegación:** back del sistema, predictive back, restauración de estado y deep links forman parte de la estética percibida y del cierre.
- **Edge-to-edge:** una sola fuente de insets; `Scaffold`/componentes M3 cuando proceda, `consumeWindowInsets` al propagar padding y tratamiento explícito del IME.
- **Accesibilidad:** targets mínimos, semántica, contraste, TalkBack y escalado de fuente son parte del diseño, no una pasada posterior.
- **Motion:** mantener el carácter de Moments, usando duraciones y easing coherentes con Android y respetando la escala de animación del sistema.

### Decisiones deliberadas

- No se adoptará por ahora la skill `jetpack-compose/theming/styles`: Google la marca experimental, requiere APIs alpha y todavía no soporta estilos de componentes Material. El proyecto usará el theming M3 estable ya presente.
- No se migrará automáticamente a Navigation 3 dentro de esta pasada estética. Primero se audita la navegación existente; cualquier migración arquitectónica necesitará una tarea separada.
- No se copiarán literalmente materiales iOS como Liquid Glass. Se preservarán su función, contraste, agrupación y profundidad usando recursos Android.

## Definición de cierre por pantalla

Antes de marcar una fila `[x]`:

- [ ] Entrada, salida, deep link y back del sistema funcionan.
- [ ] Estados loading, vacío, contenido, error, offline y retry están cubiertos si aplican.
- [ ] No hay solapes con status bar, navigation bar, cutout ni IME.
- [ ] Se revisó modo claro y oscuro.
- [ ] Se revisó tamaño de fuente 1.0× y grande (mínimo 1.3×).
- [ ] Touch targets interactivos alcanzan 48 dp o tienen hit target equivalente.
- [ ] TalkBack anuncia nombre, rol, estado y orden lógico.
- [ ] Gestos no compiten con scroll, back lateral o elementos interactivos.
- [ ] Animaciones respetan reducción/desactivación de movimiento.
- [ ] Haptics son intencionales y no duplican el feedback del sistema.
- [ ] Textos, plurales, truncado y RTL-safe layout se comprueban en los locales afectados.
- [ ] Capturas “antes / después” y notas de dispositivo quedan enlazadas en la fila o en el PR.

Formato recomendado para la columna **Evidencia / notas**:

```text
Pixel 9 · API 36 · claro/oscuro · font 1.0/1.3
Antes: docs/ui-audit/.../before.png
Después: docs/ui-audit/.../after.png
Decisión Android: ...
```

## Hallazgos transversales iniciales

| Estado | Hallazgo | Evidencia actual | Próxima comprobación |
|---|---|---|---|
| `[!]` | El tema raíz sólo declara `lightColorScheme`, mientras distintas superficies consultan `isSystemInDarkTheme()`. Puede producir una mezcla de colores claros y oscuros. | `views/shared/Theme.kt`, `views/feed/AdaptiveColors.kt` | Ejecutar shell, Feed, Creator, Perfil y Chat en modo oscuro; definir un único contrato de color. |
| `[!]` | La navegación principal conserva un dock inspirado en iOS/Instagram. Falta decidir y verificar si su geometría, feedback y relación con la navegación por gestos son correctos en Android. | `coordinators/TabBarView.kt` | Probar 3-button y gesture navigation; revisar insets, selección, reselect y botón Creator. |
| `[⏸]` | Nova existe en Kotlin, pero la pestaña principal renderiza `CoordinatorPlaceholderScreen` en vez de `NovaView`; la paridad de `NovaView` sigue `[~]`. | `coordinators/TabBarView.kt` → `TabContent`, `IOS_PORT_CHECKLIST.md` | No intervenir en esta auditoría hasta que el port cierre su paridad. |
| `[ ]` | `MainActivity` solicita `POST_NOTIFICATIONS` al arrancar aunque existe un primer contextual propio. | `MainActivity.kt`, `views/permission/notifications/NotificationsPermissionView.kt` | Verificar intención de producto y adaptar al flujo recomendado de Android. |
| `[ ]` | Existen varias familias de color/chrome (`MomentsTheme`, `AdaptiveColors`, `AuthTheme`, `ChatAdaptiveColors`, `NovaTheme`). | distintos paquetes UI | Inventariar tokens duplicados y decidir qué es global y qué pertenece a una feature. |
| `[ ]` | Varias pantallas completas se presentan mediante `Dialog(usePlatformDefaultWidth = false)`. | `coordinators/TabBarView.kt` y presentadores de feature | Revisar back, lifecycle, animación, restauración de estado y accesibilidad frente a navegación real. |

## Orden de revisión

El orden prioriza lo que condiciona a todas las demás pantallas:

1. Fundamentos, tema, shell, navegación e insets.
2. Login/onboarding y permisos.
3. Feed, detalle, Stories y comentarios.
4. Explore, mapas y Echoes.
5. Perfil propio, perfil ajeno y conexiones.
6. Creator completo.
7. Mensajería.
8. Ajustes, actividad y notificaciones.
9. Nova y superficies secundarias.

---

## 0. Fundamentos y shell

| Estado | Pantalla / sistema | Android | Referencia iOS | Revisar | Evidencia / notas |
|---|---|---|---|---|---|
| `[!]` | Tema global claro/oscuro | `views/shared/Theme.kt`, `Color.kt` | estilos compartidos y `AdaptiveColors` | Esquemas, tokens, contraste, superficies y barras del sistema | Ver hallazgo transversal |
| `[!]` | Navegación inferior | `coordinators/TabBarView.kt` | `Coordinators/TabBarView.swift` | Insets, 3-button/gestos, selección, badges, reselect, Creator y a11y | Ver hallazgo transversal |
| `[ ]` | Auth gate y arranque | `MomentsApp.kt`, `SplashScreen.kt` | `MomentsApp.swift`, `SplashScreen.swift` | Restauración, splash nativo, saltos visuales, sesión y deep links | |
| `[ ]` | Presentación full-screen | `TabBarView.kt`, `AppRouter.kt` | coordinadores iOS | Back, predictive back, lifecycle, estado restaurable y transiciones | |
| `[ ]` | Sheets, diálogos y menús | `views/shared/MomentsModalSheet.kt` y call sites | sheets y presentation coordinators SwiftUI | Drag handle, detents, dismiss, scrim, IME y TalkBack | |
| `[ ]` | Banners globales | `InAppBannerView.kt`, `OfflineBannerModifier.kt` | equivalentes Swift | Prioridad, apilado, status bar, swipe, tiempo y acciones | |
| `[ ]` | Tipografía y escalado | `Theme.kt`, `LegacyTypographyScale.kt` | escalas iOS | Roles tipográficos, font padding, truncado, 1.3×/2.0× | |
| `[ ]` | Motion y haptics | `MotionPolicy.kt`, `HapticManager.kt`, modifiers | equivalentes iOS | Duración, springs, reduce motion y feedback duplicado | |

## 1. Acceso, onboarding y permisos

| Estado | Pantalla / flujo | Android | Referencia iOS | Foco Android | Evidencia / notas |
|---|---|---|---|---|---|
| `[ ]` | Login | `views/login/LoginView.kt` | `Views/Login/LoginView.swift` | Teclado, autofill, errores inline, Google Sign-In, back | |
| `[ ]` | Registro por email | `views/login/RegisterView.kt` | `Views/Login/RegisterView.swift` | Tipos de teclado, password manager, validación y scroll con IME | |
| `[ ]` | Completar perfil social | `views/login/ProfileOnboardingView.kt`, `CreatingProfileView.kt` | `SocialProfileCompletionView.swift` | Corregir el caso Google sin `users/{uid}`/username anotado en el port | |
| `[ ]` | Política de privacidad | `views/login/PrivacyPolicyView.kt` | equivalente Swift | Navegación, enlaces, legibilidad y back | |
| `[ ]` | Permiso de notificaciones | `views/permission/notifications/NotificationsPermissionView.kt` | primer iOS | Momento contextual y diálogo nativo único | |
| `[ ]` | Permisos de cámara/micrófono/fotos | `views/permission/{camera,microphone,photos}` y boundaries | primers iOS | Photo Picker, permisos parciales, “no volver a preguntar”, Settings | |
| `[ ]` | Permiso de ubicación | `views/permission/location/LocationPermissionView.kt` | primer iOS | Precisa/aproximada, while-in-use, rationale y Settings | |

## 2. Feed, Stories y comentarios

| Estado | Pantalla / flujo | Android | Referencia iOS | Foco Android | Evidencia / notas |
|---|---|---|---|---|---|
| `[ ]` | Feed principal | `views/feed/core/FeedView.kt` | `Views/Feed/Core/FeedView.swift` | Header, tabs, scroll, refresh, pagination, insets y reselect | Primera pasada estática aplazada: glass, doble shadow, sin state layer, targets 36 dp y tipografía legacy. |
| `[ ]` | Feed vacío / error / offline | `ModernEmptyFeedView.kt`, `AppErrorBanner.kt`, banners | estados Swift | Acciones recuperables, skeletons, layout estable | |
| `[ ]` | Post de imagen/carrusel | `views/feed/moments/*` | `Views/Feed/Moments/*` | Pager, indicadores, doble tap, captions, tags y menús | |
| `[ ]` | Vídeo y Reels | `views/feed/video/*` | `Views/Feed/Video/*` | Lifecycle, audio focus, scrub, fullscreen, PiP si aplica | |
| `[ ]` | Detalle de Moment | `views/shared/momentdetail/*` | `Views/Shared/MomentDetail/*` | Transición, back, compartir, Stories y velocidad de dismiss | |
| `[ ]` | Reacciones | `views/feed/reactions/*` | `Views/Feed/Reactions/*` | Long press, picker, lista, haptics y scroll | |
| `[ ]` | Compartir | `views/feed/sharing/*` | `Views/Feed/Sharing/*` | Android Sharesheet, targets, permisos y preview | |
| `[ ]` | Capas ocultas | `HiddenLayersOverlayView.kt` | equivalente Swift | Gestos, hit testing, reveal y reduce motion | |
| `[ ]` | Stories: bandeja y entrada | `views/story/StoriesView.kt`, rings en Feed | `Views/story/StoriesView.swift` | Estado visto, pager, precarga y back | |
| `[ ]` | Story viewer | `views/story/storyviewer/StoryViewerScreen.kt` | mismo nombre Swift | Tap zones, hold, swipe, system gestures, chrome e IME | |
| `[ ]` | Stickers interactivos | `views/story/storystickers/*` | misma carpeta Swift | Conflicto de gestos, estados de voto, a11y y feedback | |
| `[ ]` | Cadenas, respuestas y archivo | `StoryChainView.kt`, `QuestionResponsesView.kt`, `ArchivedStoriesView.kt` | equivalentes Swift | Sheets, grids, paginación, calendario y mapa | |
| `[⏸]` | Comentarios | `views/comments/ModernCommentsView.kt`, `ModernCommentsSheet.kt` | `ModernCommentsView.swift` | Composer, IME, menciones, orden, edit/delete y sheets | Paridad `[~]`; pendiente de QA |
| `[⏸]` | Búsqueda de menciones | `CommentMentionSearchOverlay.kt` | equivalente Swift | Foco, debounce, teclado, selección y TalkBack | Paridad `[~]` |

## 3. Explore, mapas y Echoes

| Estado | Pantalla / flujo | Android | Referencia iOS | Foco Android | Evidencia / notas |
|---|---|---|---|---|---|
| `[ ]` | Explore | `views/explore/ExploreView.kt` | `Views/Explore/ExploreView.swift` | Search, tabs/chips, scroll, loading y navegación | |
| `[ ]` | Detalle desde Explore | `ExploreMomentDetailView.kt` | equivalente Swift | Transición, back y continuidad de scroll | |
| `[ ]` | Usuarios sugeridos | `SuggestedUsersView.kt` | equivalente Swift | Filas, follow state, feedback y listas grandes | |
| `[ ]` | Mapa de descubrimiento | `views/feed/maps/DiscoverMapView.kt` | mapas Swift | Maps SDK, permisos, markers, clusters y lifecycle | |
| `[ ]` | Lugar y Moment en mapa | `LocationMapView.kt`, `LocationMomentDetailView.kt`, `MapPlaceBottomSheet.kt` | equivalentes Swift | Bottom sheet + mapa, gestos, back y contenido parcial | |
| `[ ]` | Invitación Echo | `views/echoes/EchoInvitationView.kt` | equivalente Swift | Entrada por deep link, confirmación, error y dismiss | |
| `[ ]` | Visor Echo | `views/echoes/EchoViewerUI.kt` | equivalente Swift | Pager, estados, media y back | |
| `[ ]` | Historial Echo | `views/echoes/EchoHistoryView.kt` | equivalente Swift | Lista, vacío, refresh y navegación | |

## 4. Perfil y conexiones

| Estado | Pantalla / flujo | Android | Referencia iOS | Foco Android | Evidencia / notas |
|---|---|---|---|---|---|
| `[ ]` | Perfil propio | `views/profile/core/ProfileView.kt` | `Views/Profile/Core/ProfileView.swift` | Edge-to-edge, collapse, tabs, refresh y scroll restoration | |
| `[ ]` | Perfil ajeno | `views/profile/userprofile/UserProfileView.kt` | mismo nombre Swift | Back, follow/message, privado/bloqueado/offline | |
| `[ ]` | Header y estadísticas | `views/profile/**/sections/*Header*`, shared views | secciones Swift | Jerarquía, targets, bio expandible y font scale | |
| `[ ]` | Grid/Bento de Moments | `views/profile/core/sections/*Grid*`, `*MomentsSection*` | equivalentes Swift | Aspect ratios, skeleton, press, scroll y media | |
| `[ ]` | Transición grid → detalle | `ProfileGridHeroTransition.kt`, zoom navigation | equivalentes Swift | Shared transition, interrupción, back y reduce motion | |
| `[ ]` | Detalle y menú de Moment | `views/profile/momentsview/*` | `Views/Profile/MomentsView/*` | Menú owner/visitor, sheets y acciones destructivas | |
| `[ ]` | Highlights | `views/profile/highlights/*` | misma carpeta Swift | Rail, creación, edición, viewer y sheets | |
| `[ ]` | Guardados | `views/settings/savedmoments/*`, `ProfileSavedSection.kt` | equivalentes Swift | Filtros, grid, detalle y protección de captura | |
| `[ ]` | Editar perfil | `views/profile/editor/*` | `Views/Profile/Editor/*` | Formulario, cámara/Photo Picker, crop, IME y errores | |
| `[ ]` | Conexiones sociales | `SocialConnectionsView.kt`, `UserListView.kt` | equivalentes Swift | Tabs, búsqueda, sort, refresh y filas | |
| `[ ]` | Actividad compartida | `SharedActivityView.kt`, `SharedActivityDetailView.kt` | equivalentes Swift | Gráficas/listas, estados y back | |
| `[ ]` | Modo incógnito | `views/profile/incognito/*` | misma carpeta Swift | Sheet, countdown, overlay global y notificación Android | |
| `[ ]` | QR y compartir perfil | `views/settings/QRCode.kt`, acciones de header | `QRCode.swift` | Sharesheet, guardar imagen, permisos y deep link | |
| `[ ]` | Bloqueo/captura segura | `ScreenshotProtectedView.kt` y call sites | equivalente Swift | FLAG_SECURE, lifecycle, ventanas superpuestas y accesibilidad | |

## 5. Creator

| Estado | Pantalla / flujo | Android | Referencia iOS | Foco Android | Evidencia / notas |
|---|---|---|---|---|---|
| `[ ]` | Entrada Creator | `views/creator/CreatorView.kt` | `Views/Creator/CreatorView.swift` | Presentación, back, restauración y salida segura | |
| `[ ]` | Selección de tipo | `ContentTypeSelectionView.kt` | equivalente Swift | Jerarquía, targets, cámara/galería y permisos | |
| `[ ]` | Selector multimedia | `MediaSelectionView.kt`, `AlbumPickerView.kt` | equivalentes Swift | Photo Picker/MediaStore, permisos parciales, selección múltiple | |
| `[ ]` | Cámara Story/Moment | `StoryCameraView.kt`, `creatoruikit/*Camera*` | equivalentes Swift | Preview crop, shutter, flip, flash, zoom y lifecycle | |
| `[ ]` | Edición multimedia | `MediaEditingView.kt`, `EditableImageView.kt` | equivalentes Swift | Crop/pan/zoom, barras, IME y rendimiento | |
| `[ ]` | Recorte de vídeo | `StoryVideoTrimEditorView.kt` | equivalente Swift | Timeline, precisión, audio y feedback | |
| `[ ]` | Editor de Story | `StoryEditorView.kt`, overlays y controls | equivalentes Swift | Canvas, capas, orden Z, undo y salida | |
| `[ ]` | Texto en Story | `StoryTextEditor.kt`, componentes de texto | equivalentes Swift | IME, selección, fuentes, contraste y resize | |
| `[ ]` | Dibujo | `StoryDrawingEditorOverlay.kt`, `DrawingView.kt` | equivalentes Swift | Latencia, colores, undo y gestos del sistema | |
| `[ ]` | Stickers | `StickerPicker*`, `StickerOverlayView.kt`, inputs | equivalentes Swift | Picker, búsqueda, drag/scale/rotate, límites y a11y | |
| `[ ]` | Ubicación | `StickerLocationInputView.kt`, `LocationPickerView.kt` | equivalentes Swift | Permiso, mapa, búsqueda y estados sin ubicación | |
| `[ ]` | Etiquetar personas | `PhotoTagSelectionView.kt`, `UserSearchView.kt` | equivalentes Swift | Búsqueda, chips, posición y TalkBack | |
| `[ ]` | Capas ocultas | `HiddenLayersEditorView.kt` | equivalente Swift | Creación, preview, gestos y affordance | |
| `[ ]` | Caption y detalles | `CaptionAndDetailsView.kt` | equivalente Swift | IME, scroll, menciones, ubicación y validación | |
| `[ ]` | Audiencia | `views/creator/audienceselector/*` | misma carpeta Swift | Selección, listas custom, sheet y estados | |
| `[ ]` | Cadenas | `ChainConfigurationView.kt`, `ChainContinuationSelectorView.kt` | equivalentes Swift | Flujo anidado, back y confirmaciones | |
| `[ ]` | Publicación/progreso | background upload + `FloatingMomentUploadOverlay.kt` | equivalentes Swift | WorkManager/notificación, cancel/retry, offline y proceso muerto | |
| `[ ]` | Errores y borrador | estados de `CreatorView` y stores | equivalentes Swift | Recuperación, confirmación al salir y process death | |

## 6. Mensajería

| Estado | Pantalla / flujo | Android | Referencia iOS | Foco Android | Evidencia / notas |
|---|---|---|---|---|---|
| `[ ]` | Bandeja | `views/messaging/screens/MessagingView.kt` | equivalente Swift | Search, swipe actions, FAB, refresh y back | |
| `[ ]` | Chat | `screens/chat/GlassmorphicChatView.kt` | equivalente Swift | Insets, IME, scroll anchor, clusters y lifecycle | |
| `[ ]` | Composer | `ChatInputViews.kt`, chrome/composer | equivalentes Swift | IME, attachments, audio, multiline y send state | |
| `[ ]` | Burbujas de texto | `ChatSpeechBubbleViews.kt`, support views | equivalentes Swift | Markdown, links, spoilers, reacciones y font scale | |
| `[ ]` | Media en chat | `ChatMediaViews.kt`, `ConversationFullScreenMediaView.kt` | equivalentes Swift | Descarga, zoom, vídeo, error y content description | |
| `[ ]` | View once / vanish | `ViewOnce*`, `ChatVanishModeViews.kt` | equivalentes Swift | Seguridad, captura, timer, confirmaciones y replay | |
| `[ ]` | Acciones de mensaje | interaction modifiers y options menu | equivalentes Swift | Long press, swipe-to-reply, selección, haptic y back | |
| `[ ]` | Adjuntos | `ChatAttachmentSheet.kt` | equivalente Swift | Bottom sheet, Photo Picker, permisos y drag | |
| `[ ]` | Cámara del chat | `ChatCameraView.kt`, `CameraPickerView.kt` | equivalentes Swift | Cámara/mic, lifecycle, vídeo y back | |
| `[ ]` | GIF/stickers | `ChatGiphyPickerSheet.kt`, sticker bubbles | equivalentes Swift | Search, paging, teclado y estados | |
| `[ ]` | Ubicación | `ChatLocationSheet.kt`, live location | equivalentes Swift | Permiso, mapa, sharing activo y notificación | |
| `[ ]` | Solicitudes | `MessageRequestsView.kt` | equivalente Swift | Accept/reject, loading, empty y errores | |
| `[ ]` | Archivadas | `ArchivedConversationsView.kt` | equivalente Swift | Swipe/menu, restore y empty | |
| `[ ]` | Ajustes de conversación | `ConversationSettingsView.kt` | equivalente Swift | Shared media, mute, privacy, acciones destructivas | |
| `[ ]` | Reenvío/búsqueda/recuperación | sheets y recovery views | equivalentes Swift | Selección, foco, PIN/autofill y errores | |

## 7. Ajustes y actividad

| Estado | Pantalla / flujo | Android | Referencia iOS | Foco Android | Evidencia / notas |
|---|---|---|---|---|---|
| `[~]` | Ajustes raíz | `views/settings/SettingsView.kt`, `SettingsFormView.kt` | equivalentes Swift | Jerarquía Material, scroll, divisores, back y estados | Prueba Material 3 validada en dispositivo: ventana edge-to-edge, grupos de ancho amplio, retícula común 16/28/14 dp, superficies sólidas, ripple, switches e insets. Pendiente contraste final con iOS y estados extremos. |
| `[ ]` | Información personal | `settingssections/PersonalInfoSettingsViews.kt` | equivalente Swift | Formularios, IME, validación y cooldown | |
| `[ ]` | Contraseña | `PasswordChangeView.kt`, `SetPasswordView.kt` | equivalentes Swift | Autofill, visibilidad, errores y confirmación | |
| `[ ]` | Cuenta | `AccountManagement.kt` | equivalente Swift | Google link/unlink, logout/delete y diálogos | |
| `[~]` | Privacidad/visibilidad | `ContentVisibilityView.kt`, privacy sections | equivalentes Swift | Switches, dependencias, guardado y feedback | Host de subsección, Back y cierre animado de los selectors corregidos; pendiente contraste visual de todos los estados de audiencia. |
| `[ ]` | Bloqueados | `BlockedUsersView.kt` | equivalente Swift | Empty, refresh, unblock y confirmación | |
| `[~]` | Silenciados | `MuteSettingsView.kt` | equivalente Swift | Estados, horarios, selectors y feedback | Sheets migrados al cierre animado común con insets de navegación e IME; pendiente QA funcional de búsqueda/guardado. |
| `[~]` | Notificaciones | `NotificationSettingsView.kt` | equivalente Swift | Canales Android, permiso, horarios y deep link a Settings | Horario, tipos y avanzadas agrupados en cajas Moments de 8/20 dp; shell e insets Material 3 unificados. Pendiente permiso/canales del sistema. |
| `[ ]` | Estado online | `OnlineStatusSection.kt` | equivalente Swift | Menús, explicación y persistencia | |
| `[ ]` | Almacenamiento de chat | `ChatStorageSettingsView.kt` | equivalente Swift | Tamaños, progreso, borrar caché y confirmación | |
| `[~]` | Exportación de datos | `DataExportView.kt` | equivalente Swift | Progreso largo, background, share/save y errores | Shell sólido, edge-to-edge, IME y cajas anchas 8/20 dp aplicados. Pendiente prueba de exportación real y estados de progreso/error. |
| `[~]` | Actividad de usuario | `UserActivityView.kt` | equivalente Swift | Menú, rutas internas, estados y back | Secciones convertidas a cajas comunes; rutas internas respetan Back jerárquico y ya no invaden status bar. |
| `[~]` | Detalle de actividad | `UserActivityDetailView.kt`, rows | equivalente Swift | Filtros, listas grandes, gráficas y loading | Insets y Back corregidos; filtro de autor usa cierre animado del sheet. Pendiente datasets grandes. |
| `[~]` | Sheets de Ajustes | `MomentsModalSheet.kt` y callers de Settings | equivalentes Swift `.sheet` | Detents, padding, IME, navegación y dismiss | Host común sólido con radio 28 dp, navigation/IME insets y callback de cierre animado. Contrato documentado en `ANDROID_SETTINGS_UI_STYLE.md`. |
| `[ ]` | Historial de cuenta | `AccountHistoryActivityView.kt` | equivalente Swift | Timeline, filtros, fechas y refresh | |
| `[ ]` | Historial de búsqueda | `SearchHistoryActivityView.kt` | equivalente Swift | Empty, delete, confirmación y feedback | |
| `[ ]` | Actividad de login | `LoginActivityView.kt` | equivalente Swift | Dispositivo actual, ubicación, sesiones y revoke | |
| `[ ]` | Tiempo y descanso | `DailyLimitView.kt`, `RestModeView.kt`, `TimeSpentDetailsView.kt` | equivalentes Swift | Pickers Android, notificaciones y bienestar digital | |
| `[ ]` | Guardados | `SavedMomentsView.kt`, detalle | equivalente Swift | Filtros, grid, back y estados | |

## 8. Nova, notificaciones y superficies secundarias

| Estado | Pantalla / flujo | Android | Referencia iOS | Foco Android | Evidencia / notas |
|---|---|---|---|---|---|
| `[⏸]` | Nova principal | `views/nova/NovaView.kt` | `Views/Nova/NovaView.swift` | Conectar ruta real; después revisar conversación, IME y estados | Paridad `[~]`; tab principal muestra placeholder |
| `[ ]` | Adjuntos y confirmaciones Nova | `NovaAttachmentSheet.kt`, `NovaActionConfirmationOverlay.kt` | equivalentes Swift | Sheet, permisos, confirmación y acciones peligrosas | |
| `[ ]` | Memoria Nova | `NovaMemoryManagementView.kt` | equivalente Swift | Listas, delete, empty y explicación de privacidad | |
| `[ ]` | Centro de notificaciones | `notifications/screens/NotificationsView.kt` | equivalente Swift | Tabs, swipe, read state, deep links y empty | |
| `[ ]` | Notificación in-app / quick reply | `InAppBannerView.kt`, `InAppMessageQuickReplyPanel.kt` | equivalentes Swift | Prioridad, IME, acciones, dismiss y a11y | |
| `[ ]` | What's New | `views/misc/WhatsNewView.kt` | equivalente Swift | Gate por versión, pager/scroll, dismiss y back | |
| `[ ]` | Errores/reportes/moderación | `reportes/*`, UI de reportar | equivalentes Swift | Formularios, adjuntos, progreso, confirmación y retry | |
| `[ ]` | Ads nativos | `ad/*` y call sites | equivalentes iOS | Disclosure, tamaños, loading, privacidad y accesibilidad | |

## Registro de decisiones Android

Usar esta tabla para que las adaptaciones no se vuelvan inconsistentes entre pantallas.

| Fecha | Área | Decisión | Motivo | Archivos afectados |
|---|---|---|---|---|
| 2026-07-29 | Auditoría | Separar paridad funcional de adaptación visual Android | Evita considerar “terminada” una UI sólo porque replica Swift | Este checklist |
| 2026-07-29 | Alcance | Auditar únicamente pantallas con paridad `[x]` | Las filas `[~]`/`[ ]` todavía pueden cambiar durante el port | Este checklist + `IOS_PORT_CHECKLIST.md` |
| 2026-07-29 | Sistema visual | Material 3 estable como lenguaje de interacción; dirección de arte Moments como capa de marca | Android nativo sin homogeneizar la app ni copiar materiales iOS | Theme y componentes compartidos |
| 2026-07-29 | Materiales | Sustituir glass/blur/transparencias por superficies sólidas o tonales | Mejor legibilidad, rendimiento y coherencia Android | Chrome, sheets, menus, overlays |

## Registro de sesiones de revisión

| Fecha | Lote | Dispositivo/API | Resultado | Issues / PR |
|---|---|---|---|---|
| 2026-07-29 | Inventario inicial | Inspección estática | Checklist creado; todavía sin certificación visual | — |
| 2026-07-29 | Feed: header/selector/empty | Inspección estática | Lote elegido; pendiente baseline visual y corrección Material 3 | — |
| 2026-07-29 | Ajustes raíz y subpantalla Descargar datos | Android físico, Android 14, tema claro/oscuro | Primera adaptación Material 3 aplicada; scroll, edge-to-edge y navegación fullscreen verificados | — |
| 2026-07-29 | Ajustes, subsecciones y sheets | Android físico, Android 14 + compilación Kotlin | Shell Material 3, Back jerárquico, cajas comunes, IME/insets y cierre animado de sheets unificados; guía reutilizable creada | — |
