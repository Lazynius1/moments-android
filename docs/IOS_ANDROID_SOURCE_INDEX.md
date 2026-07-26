# Índice de código iOS → Android

Este repositorio conserva a iOS como fuente de verdad. La ruta de una pieza Kotlin
se obtiene trasladando la ruta de Swift a minúsculas bajo
`app/src/main/java/com/moments/android/` y cambiando `.swift` por `.kt`.
No se crean capas genéricas que oculten el origen funcional: si Android necesita
un adaptador de plataforma, queda junto al archivo equivalente y se anota aquí.

| iOS | Android | Regla |
| --- | --- | --- |
| `Moments/Services/<Area>/Thing.swift` | `services/<area>/Thing.kt` | Misma área y mismo nombre de responsabilidad. |
| `Moments/Views/<Area>/Thing.swift` | `views/<area>/Thing.kt` | Misma jerarquía visual; Compose sustituye SwiftUI. |
| `Moments/Models/<Area>/Thing.swift` | `models/<area>/Thing.kt` | Modelo y contrato de Firestore equivalentes. |
| `Moments/Notifications/<Area>/Thing.swift` | `notifications/<area>/Thing.kt` | Mismo flujo de notificación; Android conserva sus entry points del sistema. |
| `Moments/Shared/Thing.swift` | `shared/Thing.kt` | Preferencias o lógica compartida sin UI. |
| `Moments/Utilities/Thing.swift` | `utilities/Thing.kt` | Utilidad equivalente. |

## Chat E2E: mapa mantenido

| Swift (fuente) | Kotlin (espejo) | Nota |
| --- | --- | --- |
| `Services/Messaging/EncryptionService.swift` | `services/messaging/EncryptionService.kt` | Identidad X25519, recovery, claves envueltas y cifrado. |
| `Models/ChatSecurityModels.swift` | `models/ChatSecurityModels.kt` | Shapes de Firestore idénticos. |
| `Views/Messaging/Services/ChatService.swift` | `views/messaging/services/ChatService.kt` | Envío, estados, recibos y privacidad. |
| `Views/Messaging/Services/ChatService.swift` | `views/messaging/services/ChatService.kt` | Core messaging service. |
| `MomentsNotificationService/NotificationService.swift` | `notifications/services/MomentsFirebaseMessagingService.kt` | Entry point de push Android y preview E2E local. |
| `Shared/ChatPreviewPrivacy.swift` | `shared/ChatPreviewPrivacy.kt` | Preferencia por conversación y bloqueo de vanish. |

El contrato de identidad está dentro de `EncryptionService.kt`, no es una
funcionalidad adicional: evita que Android vuelva a escribir en
`chatIdentities`, una colección que iOS ni las reglas de Firestore reconocen.

## Privacidad: división de archivo documentada

| Swift (fuente) | Kotlin (espejo) | Nota |
| --- | --- | --- |
| `Services/Privacy/PrivacyService.swift` | `services/privacy/PrivacyService.kt` + `services/privacy/PrivacyServiceExtension.kt` | Android separa las extensiones de audiencia, listas, visibilidad de Moment/Story/Explore y filtros; en Swift la mayor parte vive dentro de `PrivacyService.swift`. |
| `Services/Privacy/PrivacyServiceExtension.swift` | `services/privacy/PrivacyServiceExtension.kt` | `filterVisibleContent` y `canViewMoment`; el mismo Kotlin también alberga la parte estructuralmente extraída de Swift. |

## Diferencias de nombre ya localizadas

Estas rutas tienen el mismo cometido pero no coinciden en mayúsculas/minúsculas;
se mantienen explícitas para que una búsqueda desde Swift llegue al archivo
Kotlin correcto:

| Swift | Kotlin |
| --- | --- |
| `Views/Feed/Controls/feedchange.swift` | `views/feed/controls/FeedChange.kt` |
| `Views/Feed/Reactions/reacciones.swift` | `views/feed/reactions/Reacciones.kt` |
| `Views/Feed/Sharing/share.swift` | `views/feed/sharing/Share.kt` |

## Regla para todo archivo nuevo o movido

1. Primero se localiza el archivo Swift responsable y se conserva su área y
   nombre en Kotlin.
2. Si un archivo Kotlin cubre varias piezas Swift por necesidades de Android,
   se mantiene en la carpeta más próxima y se añade una fila al índice.
3. Si una pieza iOS aún no existe en Android, su carpeta destino se crea con la
   primera implementación; no se suplanta con un placeholder en otra área.
4. `PORT_FILES.md` es el inventario exhaustivo de los 574 archivos Swift; este
   índice es la guía estable para navegar y para las excepciones de plataforma.
