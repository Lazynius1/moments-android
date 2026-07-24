# Moments — Checklist del port a Android (Compose)

Port nativo de la app iOS (SwiftUI) a Android (Jetpack Compose) sobre el **mismo Firebase** (`glowsy-6a40e`). Este archivo es el estado vivo de qué está hecho y qué falta.

> Origen iOS: `../Moments/Moments/Views/…` · Destino Android: `app/src/main/java/com/moments/android/…`

---

## ✅ Infra / setup (hecho)

- [x] Proyecto Compose (`com.moments.android`, minSdk 26, targetSdk 35)
- [x] Firebase conectado: Auth + Firestore + Storage (`google-services.json`)
- [x] Google Sign-In (Credential Manager) — SHA-1 debug registrada
- [x] Fuente **Inter** (variable) como sustituto de SF Pro
- [x] Safe area (`systemBarsPadding`) — equivalente al safe area de iOS
- [x] i18n en **8 idiomas** (en, es, ca, de, fr, it, pt-BR, pt-PT) — **regla fija:** todo texto user-facing se porta desde los mismos `*.lproj/Localizable.strings` de iOS a los 8 `values-*/strings.xml` (nunca solo EN/ES)
- [x] Toolchain CLI: JDK17, gradle wrapper 8.9, emulador `Moments_API_35`

⚠️ **Pendiente para release:** añadir la SHA-1 de la build firmada (o Play App Signing) a Firebase, o Google Sign-In fallará en producción.

---

## ✅ Login / Auth  (carpeta `Views/Login` — COMPLETA)

| iOS (`Views/Login/`) | Android (`views/login/`) | Estado |
|---|---|---|
| `LoginView.swift` | `LoginScreen.kt` | ✅ |
| `ProfileOnboardingView.swift` (registro 5 pasos) | `OnboardingScreen.kt` | ✅ |
| `AuthUIComponents.swift` + `LiquidGlassComponents.swift` | `AuthComponents.kt` + `AuthTheme.kt` | ✅ |
| `RegisterView.swift` | (→ `OnboardingScreen`) | ✅ |
| `CreatingProfileView.swift` | `CreatingProfileScreen.kt` | ✅ |
| `SplashScreen.swift` | `SplashScreen.kt` | ✅ |
| `PrivacyPolicyView.swift` | `PrivacyPolicyScreen.kt` | ✅ |
| `DeactivatedAccountView.swift` | `AccountStateScreens.kt` | ✅ (compila; sin verificar en pantalla) |
| `SuspendedAccount.swift` | `AccountStateScreens.kt` | ✅ (compila; sin verificar en pantalla) |
| `SocialProfileCompletionView.swift` | — | N/A (era Apple; Google crea perfil directo) |
| `Interestview.swift` | — | N/A (vacío) |
| — | `GoogleAuthHelper.kt`, `EmailRegistration.kt`, `Interests.kt`, `AuthErrors.kt` | ✅ (soporte) |

**Notas / logs que quedan de login:**
- [x] Icono de app custom (mipmap desde `AppIcon` iOS) + assets de `Assets.xcassets` en `res/drawable*` (Nova, attachment, audience, splash, etc.)
- [ ] Verificar en pantalla las pantallas de cuenta **desactivada/suspendida** (necesita un usuario con `isActive=false` / `isSuspended=true` en Firestore).
- [ ] El link "Ver Estado de Apelaciones" de la pantalla suspendida se omitió (necesita backend de apelaciones).

---

## ⏳ Pendiente (por carpeta, nº de archivos iOS como pista de tamaño)

Orden sugerido (de más núcleo a más periférico):

- [~] **Feed** (47) — **lote cerrado:** presentaciones Feed sin placeholders (Explore/Stories/Messaging/Profile/Edit/Comments/Echo). *Pulido abierto:* vídeo global, card vs iOS, LocationDetail swipe/prefetch.
- [x] **comments** (2/3) — `ModernCommentsView` + `CommentMentionSearchOverlay` ✅; `CommentsView` legacy omitido (iOS también prioriza Modern).
- [~] **Echoes** (3) — `EchoHistoryView` + `EchoInvitationView` ✅; falta `EchoViewerUI`.
- [~] **Shared/MomentDetail** (3) — Container + Context ✅; `SingleMomentDetailView` [~] (Edit + Comments + Explore reales; Profile stubs); falta `ModernMomentDetailView` (carousel perfil).
- [~] **Profile** (50) — **sheet usuario + tab propio MVP** (`UserProfileView`); editor/theme/highlights/incognito = abiertos. (`ContextMenu` + `EditMomentView` [~] cableados).
- [~] **Creator** (70) — type + gallery + edit + caption + StoryCamera + StoryEditing chunk7 (texto+fonts+colors+dibujo+filtros+stickers emoji+selfie+GIF+frame+reveal+audio); stickers interactivos/motion/lenses pending.
- [~] **Messaging** (85) — **inbox + chat texto MVP** (`MessagingView` + `ChatService.fetchConversations`); glass/media/requests/new chat = abiertos.
- [~] **Settings** (36) — **En revisión** (SettingsView, ViewModel, NotificationSettings, OnlineStatus, PersonalInfo, AccountManagement, PasswordChange, BlockedUsers, MuteSettings, ChatStorage, ContentVisibility, DataExport, LoginActivity, QRCode, RestMode, DailyLimit, SavedMoments, SearchHistory, SetPassword, SettingsNavigationComponents).
  - **Familia UserActivity** (~8,1k líneas iOS, el mayor bloque sin contraparte). Capa de datos + lógica portada, **en revisión** (`[~]`, nada cerrado hasta tu repaso): `UserActivityTypes` (iconos + `AnimatedReactionIcon`/`AnimatedCommentIcon` con su animación real), `UserActivityModels`, `UserActivityBackendModels` (contrato de las Cloud Functions), `UserActivityCache` (SharedPreferences con las mismas claves `activityCache_*` que UserDefaults en iOS), `UserActivityDetailViewModel` (las 11 categorías, borrado por lotes, paginación por cursor) y `UserActivitySummaryViewModel` (contadores). Strings en los 8 idiomas (`strings_user_activity*.xml`). **UI portada (MVP, `[~]`):** `UserActivityTypes`(+StringRes), `UserActivityComponents`, `UserActivityRows` (7 componentes), `UserActivityDetailView` (grid/lista+filtros+selección+borrado por lotes; drag-select/zoom/rango-custom diferidos), `UserActivityView` (raíz con 5 secciones, **enganchada en Settings** vía ruta `user_activity`), `AccountHistoryActivityView` (timeline+filtros, fetch con join sintético) y `TimeSpentDetailsView`+`TimeSpentCardView` (media diaria + barras 7 días + filas a DailyLimit/RestMode). Las 15 filas de categoría navegan a su destino real. **Familia UserActivity cerrada de punta a punta** (queda solo pulido: drag-select, zoom compartido en las celdas, rango de fechas custom). `SettingsSections.swift` (1579) NO es UserActivity — es la composición del propio Settings y **se solapa con el `SettingsView.kt` ya portado**; es una reconciliación aparte, no un port limpio.
  - Nuevo `services/network/CloudFunctionsClient.kt`: POST autenticado a Cloud Functions (`HttpURLConnection` + `getIdToken`), extraído para dejar de copiar ese bloque en cada servicio. Los servicios ya portados conservan su copia — migrarlos es limpieza aparte.
- [ ] **Nova** (32) — asistente/IA.
- [~] **story** (27) — **viewer MVP usable** (`StoriesView` + `StoryViewerScreen`); frame Polaroid con shake-to-reveal y Reveal scratch persistente ya cableados; efectos de Reveal/reply/ads/chains/archived siguen abiertos.
- [~] **Explore** (9) — **lote usable:** tab + búsqueda + sugeridos + grid + detalle; Profile sheet / bento exacto / ModernExploreDetailHeader / SuggestedUsers paginado = abiertos.
- [ ] **Permission / Permissions** (12+2) — primers de permisos (cámara, micro, etc.).
- [ ] **comments** (3), **Echoes** (3), **Misc** (1)
- [ ] **Components / Shared** (22+10) — se portan a demanda según los vaya necesitando cada pantalla.

### Capas de datos / lógica (no-UI) — `Moments/*` fuera de `Views/`
Hasta ahora se han portado **a demanda** (inline en cada pantalla, p. ej. `EmailRegistration.kt` cubre parte de `Services/Auth`). Falta portarlas de forma estructurada:

- [x] **Services** (69/69) — COMPLETA. Auditoría: [docs/SERVICES_FIDELITY_AUDIT.md](docs/SERVICES_FIDELITY_AUDIT.md) — **66 OK / 0 PARTIAL / 0 GAP / 3 N/A**. N/A: Passkey, ChatSendMessageIntentHandler, SnapCameraKit. Compila GREEN.
- [x] **Models** (21) — COMPLETA (100%, leer + escribir): User/AppUser, Models.kt (Moment, MediaItem, Comment, follow, hidden layers, Story, StickerData, Notification, Questions…), Echo, ChatSecurity, StickerItem (datos), UserAffinity, AccountHistoryItem, OutboxPayloads, y `models/cache/*` (entidades). Cada modelo Firestore tiene `from(map)` **y** `toMap()`/`asFirestoreData()`. Descartado: badges/Plus/ProfileTheme. Pendiente (capa de caché, no del modelo): Room + conversiones from/to de `cache/*` (algunas dependen de Messaging: Conversation/EnhancedMessage). UI helpers (displayName/iconos de enums) → capa de UI.
- [x] **Notifications** (24) — FCM via `google-services.json` + `firebase-messaging`; carpeta `notifications/` completa (services, core, screens, row, components).
- [x] **Utilities** (11/11) — COMPLETA (`com.moments.android.utilities`).
- [x] **Coordinators** (5) — coordinación de flujos.
- [x] **Extensions** (5/5) — `com.moments.android.extensions` (ColorHex, DateExtensions, AvAssetThumbnail, InterestEmojiHelper, LiquidGlass).
- [x] **ad** (4) — AdMob + UMP consent; ATT → no-op Android (`notApplicable_android`). Test IDs en debug; placeholders `REPLACE_WHEN_YOU_HAVE_GOOGLE_KEY`.
- [x] **Reportes** (7/7) — apelaciones, reportes, revisiones de moderación; strings en 8 idiomas (`strings_reportes.xml`).
- [x] **Moderation** (2/2) — `CommentsModerationService` + `MediaModerationService`; mismas rutas Firestore/Cloud Functions.
- [x] **Activities** (3/3) — ActivityKit → `UploadProgressNotificationHelper` (notificación ongoing); N/A widget Live Activity UI.
- [x] **ViewModels** (1/1) — `EchoViewModel` con StateFlow.

**Capa non-UI completa** (Services, Models, Notifications, Utilities, Coordinators, Extensions, ad, Reportes, Moderation, Activities, ViewModels). Pendiente: carpetas UI (`Views/*`). Services auditado — ver [docs/SERVICES_FIDELITY_AUDIT.md](docs/SERVICES_FIDELITY_AUDIT.md).

### Navegación / estructura
- [x] `MomentsApp.kt` (raíz, espejo `MomentsApp.swift`) usa `TabBarScreen` (home/nova/create/explore/profile) con `FeedView` real y placeholders para el resto.

---

## Convenciones del port
- **Árbol Android = espejo de `Moments/Moments/`** (misma jerarquía mental): `Services/`→`services/`, `Views/`→`views/`, `Shared/`→`shared/`, etc. Paquetes Kotlin en minúsculas. **No** meter lógica de `Views/` dentro de `services/`.
- Textos **siempre** por recursos (`strings.xml`), nunca hardcodeados; los 24 intereses guardan el valor español en Firestore (igual que iOS) y muestran el traducido.
- Liquid Glass de iOS → Material plano + acentos de marca (halo aurora, degradado en CTAs).
- Esquema Firestore/Storage **idéntico a iOS** para que los datos sirvan cross-plataforma.
- ⚠️ iCloud crea duplicados `"* 2.kt/.png/.dex"` que rompen el build → `find app/src -iname "* *"` y borrar.
