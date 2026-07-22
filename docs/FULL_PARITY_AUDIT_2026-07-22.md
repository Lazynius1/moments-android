# Android ↔ iOS Full Parity Audit

**Date:** 2026-07-22  
**Source of truth:** `../Moments/Moments/`  
**Android target:** `app/src/main/`  
**Row-level ledger:** [`../PORT_FILES.md`](../PORT_FILES.md). It lists every iOS Swift source; its 421 unchecked View entries are currently unimplemented unless this report explicitly calls them partial or N/A.

> **Audit state:** This report began as a structural/UI audit. It is not a 100% parity certification. The chat E2E contract review below found critical mismatches in code that was previously recorded as service-complete; no area is certified until its schema, rules, state transitions, failure paths, localization and emulator flows have been compared.

## Verification evidence

- `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :app:assembleDebug` completed successfully after the current source corrections (2026-07-22).
- The resulting debug APK was installed and launched on `emulator-5554`; `com.moments.android/.MainActivity` remained the resumed activity.
- A runtime crash from `FeedView.kt` was corrected: Compose rejected the SwiftUI-style negative top padding. The header now uses `offset(y = (-8).dp)`; a post-fix launch remained in `MainActivity` for six seconds and `logcat -b crash` was empty.
- The live Feed renders data, story ring, a post and the tab bar. The first post header currently overlaps the `Siguiendo / Para Ti` selector; this is a visual parity defect.

## Inventory baseline

| Surface | iOS Swift files | Android Kotlin files | Audit status |
|---|---:|---:|---|
| Entire app target | 574 | 254 | **Partial** |
| `Views/` | 421 | 88 | **Partial** |
| Services | 69 | 76 service files | **Reopened** — the chat E2E/recovery/read-receipt contract has critical gaps; prior structural audit is insufficient |
| Feed | 47 | 54 | **Partial** — every source basename exists, but several paths are stubs |
| Login | 12 | 12 | **Mostly implemented**; needs interaction and locale verification |
| Creator | 70 | 3 | **Missing UI** — only background-upload support exists |
| Messaging | 85 | 8 | **Missing UI** — service support only |
| Profile | 50 | 0 | **Missing** |
| Settings | 36 | 0 | **Missing** |
| Nova | 32 | 1 | **Missing** — `NovaEvents` is a no-op stub |
| Story viewer | 27 | 1 | **Missing** — avatar component only |
| Explore | 9 | 0 | **Missing** |
| Permissions / Permission | 14 | 0 | **Missing UI** |
| Echoes / comments / misc | 7 | 0 | **Missing** |
| Shared components | 10 | 5 | **Partial** |
| Reusable components | 22 | 4 | **Partial** |

## File-by-file findings

### Root, app lifecycle, and non-UI layers

| iOS area | Android counterpart | Status | Evidence / required follow-up |
|---|---|---|---|
| `MomentsApp.swift` | `MomentsApp.kt`, `MomentsApplication.kt`, `MainActivity.kt` | **Partial** | Firebase boot, auth gating and FCM are wired. iOS launch behavior also restores live location, performs maintenance and shows the What's New flow; those need direct behavior checks. |
| Activities (3) | `activities/` (4) | **Mostly implemented** | Android correctly uses an ongoing notification instead of ActivityKit. |
| Coordinators (5) | `coordinators/` (6) | **Partial** | `TabBarScreen.kt` is the critical gap: only Home renders a real view; Nova, Explore and Profile call `CoordinatorPlaceholderScreen`; Creator, Echoes and Messaging use dialogs/placeholders. |
| Extensions (5), Utilities (11), Moderation (2), Reportes (7), notifications (24), ads (4), ViewModels (1) | mirrored folders | **Structurally present** | Need behavioral regression checks as their screens become reachable. Android still has the debug AdMob app ID, which is not release parity. |
| Models (21) | `models/` (19 files) | **Partial / needs serialization sweep** | Android consolidates and splits several iOS model sources. The existing checklist claims read/write coverage, but this audit has not yet proven each Firestore round-trip against fixtures. |
| Services (69) | `services/` | **Reopened** | The previous audit established broad source coverage but did not validate the live Firestore contract. The E2E identity, recovery and read-receipt path is currently incompatible with iOS; see the critical contract findings below. |

### Critical E2E chat contract findings — confirmed by source and rules

| Contract | iOS source of truth | Android implementation | Status |
|---|---|---|---|
| Participant identity | Reads/writes `users/{uid}.chatKey` with `keyId`, `publicKeyBase64`, `algorithm`, `updatedAt`. | The local correction now reads/writes the same field through `ChatIdentityContract`; no runtime cross-device verification yet. | **Correction pending verification** — the former unsupported `chatIdentities` path was removed. |
| Conversation-key wrapping/rotation | Resolves each participant from `users/{uid}.chatKey`. | The local correction resolves each participant through `fetchChatIdentity`, which reads the canonical user field. | **Correction pending verification** — must test an iOS-created conversation and an Android-created conversation. |
| Recovery bundle | Creates, fetches and restores `users/{uid}/chatRecovery/default`; PIN attempts are locally rate-limited; identity is re-synced after restore. | The local correction implements the same Firestore location, access-state decision, PIN bundle creation/fetch/restore, re-sync and 5-attempt/5-minute local lockout. | **Correction pending verification** — Firestore rules and iOS↔Android recovery need device-level validation. |
| Decryption failure behavior | `ChatService.decryptMessageContent` returns the original encrypted content when decryption fails; the UI layer decides presentation. | `ChatMessageMapper` now returns the raw content on decrypt failure, matching the Swift service behavior. | **Aligned in source** — message UI still needs a visual/error-state comparison. |
| Read receipts | iOS batches `readBy`, conditionally updates `isRead`/`status`, writes `readStatus`, `lastReadAt` and optional `lastMessageSeenAt`, respecting global and per-conversation preferences and incognito mode. | `ChatService.kt` now contains the same batch mutation and preference precedence. No reachable Android conversation UI invokes it yet. | **Partial** — data mutation is aligned; its caller/UI is still missing. |
| Push privacy and notification content | The iOS notification extension resolves encrypted previews locally and retains a generic body if privacy is disabled or decryption fails. | The FCM system path now resolves embedded/fetched ciphertext locally, honors `ChatPreviewPrivacy`, and falls back to a generic localized body when the supplied body is ciphertext. | **Correction pending verification** — validate locked/unlocked, vanish, disabled preview and no-network states. |

The Firestore rules explicitly validate `users/{uid}.chatKey` and `users/{uid}/chatRecovery/default`; they contain no `chatIdentities` rule. Therefore the Android identity path is not merely different: it is unsupported by the deployed schema.

### Services: direct audit results so far

The old `69/69` service count is a filename count, not a parity result. The following files have been compared by implementation rather than name:

| iOS Swift | Kotlin | Direct source finding | Status |
| --- | --- | --- | --- |
| `Services/Messaging/EncryptionService.swift` (3,050 lines) | `services/messaging/EncryptionService.kt` (983 lines before the local correction) | The identity/recovery/wrapped-key mismatch above was real despite matching names. The correction is present locally but still needs iOS-device validation. | **Reopened** |
| `Services/Privacy/PrivacyService.swift` (1,424 lines) + `PrivacyServiceExtension.swift` | `services/privacy/PrivacyService.kt` (423 lines) + `PrivacyServiceExtension.kt` (353 lines) | The custom lists, visibility checks for Moment/Story/Explore, hidden/muted checks and `canShareMoment` are implemented in Android's extension file. This is a structural split (the Swift main file owns most of that code), not a confirmed behavior omission. Android now exposes explicit `Moment`/`Story` adapters for the generic custom-list contract, matching Swift's model conformances. | **Compiles; detailed branch/error audit pending** |
| `Services/Firestore/FirestoreSearchRepository.swift` (306 lines) | `services/firestore/FirestoreSearchRepository.kt` (97 lines) | Search, batch fetch, suggested users and mute filtering are present. Kotlin consolidates `fetchUsers`/`fetchMutedUserIds` in `FirestoreService` and two Swift search overloads into one API. The recent-chat lookup now degrades to mutual/following suggestions on failure, matching Swift's useful-result behavior. | **Source-aligned for inspected paths** |
| `Services/Firestore/FirestoreCommentsRepository.swift` (807 lines) | `services/firestore/FirestoreCommentsRepository.kt` (342 lines) | CRUD, pagination, reactions and audience checks exist. Android does not notify reply authors, resolve/send mention notifications on add/edit, or derive mentions from raw text when validated mention entities are absent. | **Not mirror** |
| `Services/Firestore/FirestoreProfilesRepository.swift` (473 lines) | `services/firestore/FirestoreProfilesRepository.kt` (232 lines) | The documented public profile, username, interest, mutual and availability operations are present. iOS additionally recovers the username-index e-mail from Firebase Auth when the user document lacks it and falls back to manual legacy decoding; Android currently fails/omits those recovery paths. | **Partial: legacy-data behavior differs** |
| `Services/Firestore/FirestoreStoriesRepository.swift` | `services/firestore/FirestoreStoriesRepository.kt` | Creation (including custom lists/visibility), active/archive pagination, collection-group fallback, story summaries, highlights and prefetch all have direct Kotlin counterparts. | **Public API aligned; field/error branch audit pending** |
| `Services/Firestore/FirestoreMomentsRepository.swift` | `services/firestore/FirestoreMomentsRepository.kt` | The public CRUD, archive/pin/save, feed paging and visibility-creation APIs are present. The custom-audience creation path generated two IDs in Android, so its list was written against a different document; the local correction now passes one resolved ID to both writes. `MapVisibilityPolicy` is also aligned locally to Swift's `public`/`hidden` values. | **Compiles; Firebase/device verification pending** |
| `Views/Feed/maps/MapDiscoverSupport.swift` + `Services/Firestore/FirestoreStoriesRepository.swift` | `models/Models.kt` → `MapVisibilityPolicy` + `services/firestore/FirestoreStoriesRepository.kt` | Android did not materialize a location sticker into the `mapLocation` Firestore object and used different visibility strings. The local port now writes `latitude`, `longitude`, `locationName`, and Swift-compatible visibility. | **Compiles; Firebase/device verification pending** |
| `Services/Auth/PasskeyService.swift` | no Android counterpart | Product clarification: Android supports Google sign-in only; passkeys are intentionally excluded. | **Accepted N/A** |
| `Services/Auth/AuthService.swift` | `services/auth/AuthService.kt` | Product clarification: Apple credential linking, reauthentication and Apple-only account paths are intentionally excluded from Android. | **Accepted N/A** |

### Models: direct audit results so far

| iOS Swift | Kotlin | Direct source finding | Status |
| --- | --- | --- | --- |
| `Models/User.swift` / `Models/UserBadge.swift` | `models/User.kt` | Android deliberately omits Plus/badge fields and helpers. Product clarification: subscriptions and badges are currently disabled; this is an accepted inactive-feature N/A, not a port target. | **Accepted N/A while subscriptions are off** |
| `Models/Cache/CachedConversation.swift` | `models/cache/CachedConversation.kt` + `services/persistence/LocalPersistenceService.kt` | The data fields are present; Swift's conversion methods are split into Android persistence code. Round-trip equivalence remains to be compared there. | **Structural split; audit pending** |
| `Models/Cache/CachedMessage.swift` | `models/cache/CachedMessage.kt` + `models/ChatModels.kt` | The data fields are present; Android's conversions are split out. Media-URL sanitation, JSON fallback and every message type still require a field-by-field comparison. | **Structural split; audit pending** |
| `Models/Models.swift` → `Moment` | `models/Models.kt` → `Moment` | The persisted Moment fields, Firestore keys (including `imageUrl`), media visibility and preview selection were compared and are aligned in source. iOS-only formatting helpers remain a UI responsibility. | **Aligned for the inspected model** |

### Navigation is the first blocking parity issue

`TabBarView.swift` exposes five working product surfaces: Home, Nova, Creator, Explore and Profile, and also routes notifications, messages, story chains and echoes. Android's `TabBarScreen.kt` only routes Home to `FeedView`. The remaining primary routes are explicit placeholders.

This makes Android functionally incomplete even though it compiles: navigation can present labels for the other tabs, but not the iOS behavior behind them.

### Feed — every named file exists, but not every behavior

The following 47 iOS Feed files have a same-name Kotlin counterpart, so this category is not missing structurally. They are **partial until interaction-level verification is complete**.

High-confidence gaps found by direct source inspection:

| iOS source / behavior | Android source | Status | Evidence |
|---|---|---|---|
| `Core/FeedPresentationModifier.swift` destinations | `core/FeedPresentationModifier.kt` | **Partial** | Routes to Messaging, Profile, Creator, Story, Echoes and other unported UI through `FeedPresentationPlaceholder`. |
| `Video/Reels.swift` | `video/Reels.kt` | **Partial** | `ReelsViewerPlaceholder` / `ReelsFullscreenPlaceholder`; no full immersive reel viewer. |
| `Sharing/StoryShare.swift` | `sharing/StoryShare.kt` | **Partial** | `StoryShareFlowPlaceholder` is explicitly a placeholder. |
| `Sharing/share.swift` | `sharing/Share.kt` | **Partial** | 1,863 Swift lines vs 70 Kotlin lines; the cross-app/share-sheet flows are not equivalent. |
| `Reactions/MomentReactionButton.swift` | `reactions/MomentReactionButton.kt` | **Partial** | 1,077 Swift lines vs 287 Kotlin lines; advanced reaction UX needs behavior review. |
| `Moments/HiddenLayersOverlayView.swift` | `moments/HiddenLayersOverlayView.kt` | **Partial** | 1,128 Swift lines vs 270 Kotlin lines; interactive layer behavior remains unverified. |
| `Video/VideoPlayer.swift` | `video/VideoPlayer.kt` | **Partial** | 1,144 Swift lines vs 229 Kotlin lines; playback controls and lifecycle need equivalence tests. |
| `maps/Maps.swift` and map subviews | `maps/` / `mapsections/` | **Partial** | Android uses a placeholder API key and placeholder canvas/data surfaces; no Maps SDK-backed parity. |
| Feed header layout | `core/FeedView.kt` | **Defect** | The post author overlaps `Siguiendo / Para Ti` in the emulator. |

The rest of the Feed files require a flow-by-flow interaction pass (refresh, pagination, follow, reactions, comments, story opening, media playback, detail routes and deep links). Their existence and successful rendering do not yet prove parity.

### View areas absent from Android

The following rows are missing because no Android View package exists for the corresponding iOS source files. Each individual Swift file is listed in `PORT_FILES.md` and remains unchecked.

| iOS area | Missing files | Consequence |
|---|---:|---|
| Creator | 67 UI files | No native capture, gallery, editor, stickers, audiences, publishing or story creation. |
| Messaging | 77 UI/support files | No conversation list, chat screen, composer, attachment UI, message requests or settings. |
| Profile | 50 | No own/user profile, editor, highlights, themes, saved moments or social connection screens. |
| Settings | 36 | No settings, privacy, activity, export, account or storage screens. |
| Nova | 31 | No assistant UI, memory, tools, conversations or confirmations. |
| Story | 26 | No story deck/viewer, stickers, replies, chains or archived stories. |
| Explore | 9 | No search/discovery UI. |
| Permission and Permissions | 14 | No Android equivalent of permission primers/gates. The manifest also currently lacks camera, microphone and media-read permissions needed for Creator and messaging media. |
| Components | 18 | Missing skeletons, banners, stickers, refresh and shared moment UI. |
| Shared | 5 | Missing detail container, offline banner modifier and video wrappers. |
| Echoes, comments, Misc | 7 | No echo flows, comments UI or What's New screen. |

## Localization and release configuration

- Android has 600 base string definitions and 588 in each translated locale. iOS has roughly 3,925–3,974 localized entries per locale. Counts are not one-to-one because source platforms structure strings differently, but the gap is substantial and every newly ported flow must verify all eight Android locales.
- Android declares network, notification, vibration and location permissions. It does not currently declare camera, microphone or media-read permissions, so Creator and chat-media parity cannot be completed without manifest and runtime-permission work.
- Android uses the Google sample AdMob app ID. This is correct for debugging but must be replaced before release parity.

## Ordered correction backlog

1. **Remove root placeholders:** implement navigation shells for Profile, Explore, Creator, Messaging and Nova, then route them from `TabBarScreen` and Feed presentation paths.
2. **Fix the visible Feed header overlap** and run an interaction matrix on the existing Feed before adding more features.
3. **Port Profile and Explore:** both unblock navigation and reuse existing Firestore/profile services.
4. **Port Creator with permissions/media:** add manifest/runtime permissions, capture/picker/editing, audience selection and background upload integration.
5. **Port Messaging UI and complete live location/session gaps:** services are present, but chat cannot be used without its screens and composers.
6. **Port story viewer, comments, Echoes, settings and Nova in dependency order.**
7. **Close with model serialization fixtures, eight-locale checks, Firebase contract tests and emulator validation of every primary user flow.**

## Audit conclusion

Android is a working Feed/login shell backed by a substantially ported service layer, not yet a mirror of the iOS app. The highest-risk false-positive is treating same-name Kotlin files as completed features: Feed has all 47 names but several are explicit placeholders, while the other primary tabs are currently unreachable product placeholders. The row-level inventory is retained so each missing iOS source remains visible until it is implemented, accepted as platform N/A, and verified.
