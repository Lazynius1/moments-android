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
- [x] i18n en **8 idiomas** (en, es, ca, de, fr, it, pt-BR, pt-PT)
- [x] Toolchain CLI: JDK17, gradle wrapper 8.9, emulador `Moments_API_35`

⚠️ **Pendiente para release:** añadir la SHA-1 de la build firmada (o Play App Signing) a Firebase, o Google Sign-In fallará en producción.

---

## ✅ Login / Auth  (carpeta `Views/Login` — COMPLETA)

| iOS (`Views/Login/`) | Android (`ui/login/`) | Estado |
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
- [ ] Verificar en pantalla las pantallas de cuenta **desactivada/suspendida** (necesita un usuario con `isActive=false` / `isSuspended=true` en Firestore).
- [ ] El link "Ver Estado de Apelaciones" de la pantalla suspendida se omitió (necesita backend de apelaciones).
- [ ] Icono de app custom (ahora sale el robot de Android por defecto en la splash del sistema).

---

## ⏳ Pendiente (por carpeta, nº de archivos iOS como pista de tamaño)

Orden sugerido (de más núcleo a más periférico):

- [ ] **Feed** (47) — `ui/feed/FeedScreen.kt` es placeholder; falta el feed real, tarjetas de post, historias. *Parcial.*
- [ ] **Profile** (50) — perfil propio y de otros, editor, etc.
- [ ] **Creator** (70) — creación de moments/stories (cámara, editor). *Grande.*
- [ ] **Messaging** (85) — chat/DMs. *La más grande.*
- [ ] **Settings** (36) — ajustes.
- [ ] **Nova** (32) — asistente/IA.
- [ ] **story** (27) — visor de historias.
- [ ] **Explore** (9) — descubrir/buscar.
- [ ] **Permission / Permissions** (12+2) — primers de permisos (cámara, micro, etc.).
- [ ] **comments** (3), **Echoes** (3), **Misc** (1)
- [ ] **Components / Shared** (22+10) — se portan a demanda según los vaya necesitando cada pantalla.

### Capas de datos / lógica (no-UI) — `Moments/*` fuera de `Views/`
Hasta ahora se han portado **a demanda** (inline en cada pantalla, p. ej. `EmailRegistration.kt` cubre parte de `Services/Auth`). Falta portarlas de forma estructurada:

- [ ] **Services** (69) — Auth, Firestore repos, Storage, etc. La columna vertebral. *Parcial (auth/registro inline).*
- [ ] **Models** (21) — `User`, `Moment`, `Story`, … Ahora se usan `HashMap` ad-hoc; convendría modelos Kotlin tipados.
- [ ] **Notifications** (24) — FCM / push (aún sin tocar en Android).
- [ ] **Utilities** (11) — helpers varios.
- [ ] **Coordinators** (5) — coordinación de flujos.
- [ ] **Extensions** (5) — extensiones Swift → equivalentes Kotlin a demanda.
- [ ] **ad** (4) — AdMob + ATT (en iOS ya integrado; en Android sin empezar).
- [ ] **Reportes** (7), **Moderation** (2), **Activities** (3), **ViewModels** (1)

### Navegación / estructura
- [ ] `MomentsApp.kt` tiene la barra inferior (Inicio/Buscar/Crear/Actividad/Perfil) con placeholders — falta conectar cada destino a su pantalla real conforme se porten.

---

## Convenciones del port
- Textos **siempre** por recursos (`strings.xml`), nunca hardcodeados; los 24 intereses guardan el valor español en Firestore (igual que iOS) y muestran el traducido.
- Liquid Glass de iOS → Material plano + acentos de marca (halo aurora, degradado en CTAs).
- Esquema Firestore/Storage **idéntico a iOS** para que los datos sirvan cross-plataforma.
- ⚠️ iCloud crea duplicados `"* 2.kt/.png/.dex"` que rompen el build → `find app/src -iname "* *"` y borrar.
