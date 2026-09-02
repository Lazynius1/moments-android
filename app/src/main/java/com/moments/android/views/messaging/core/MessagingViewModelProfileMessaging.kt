package com.moments.android.views.messaging.core

import com.moments.android.models.AppUser

/** ≡ iOS `MessagingViewModel.ProfileMessagePresentation`. */
data class ProfileMessagePresentation(
    val destination: Destination,
) {
    sealed class Destination {
        data class OpenConversation(val conversation: Conversation) : Destination()
        data class PendingChat(val context: PendingChatContext) : Destination()
    }
}

/**
 * Resuelve la navegación de chat desde perfiles tras `startConversation`.
 * El flujo v2 publica el destino en `presentationRoute` y deja `requiresMessageRequest` en `false`.
 *
 * Port de `MessagingViewModel+ProfileMessaging.swift`.
 */
suspend fun MessagingViewModel.consumeProfileMessagePresentation(
    conversation: Conversation?,
    user: AppUser,
    currentUserId: String,
    followersCountOverride: Int? = null,
    momentsCountOverride: Int? = null,
): ProfileMessagePresentation? {
    if (conversation != null) {
        presentationRoute = null
        return ProfileMessagePresentation(
            ProfileMessagePresentation.Destination.OpenConversation(conversation),
        )
    }

    when (val route = presentationRoute) {
        is MessagingPresentationRoute.Conversation -> {
            presentationRoute = null
            return ProfileMessagePresentation(
                ProfileMessagePresentation.Destination.OpenConversation(route.conversation),
            )
        }
        is MessagingPresentationRoute.PendingChat -> {
            presentationRoute = null
            return ProfileMessagePresentation(
                ProfileMessagePresentation.Destination.PendingChat(route.context),
            )
        }
        null -> Unit
    }

    if (!errorMessage.isNullOrEmpty()) {
        return null
    }

    val context = PendingChatContextFactory.outgoing(
        user = user,
        currentUserId = currentUserId,
        followersCountOverride = followersCountOverride,
        momentsCountOverride = momentsCountOverride,
    )
    return ProfileMessagePresentation(
        ProfileMessagePresentation.Destination.PendingChat(context),
    )
}
