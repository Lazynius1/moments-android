package com.moments.android.services.firestore

import com.google.firebase.firestore.FieldValue
import com.moments.android.models.CustomAudienceList
import kotlinx.coroutines.tasks.await

/** Port de FirestoreAudienceRepository.swift. */
suspend fun FirestoreService.saveCustomAudienceForContent(
    contentType: String,
    contentId: String? = null,
    authorId: String,
    allowedUsers: List<String>,
) {
    val documentId = if (!contentId.isNullOrEmpty()) "${contentType}_$contentId" else "default_$contentType"
    val data = mapOf(
        "contentType" to contentType,
        "allowedUsers" to allowedUsers,
        "createdAt" to FieldValue.serverTimestamp(),
        "lastUpdated" to FieldValue.serverTimestamp(),
    )
    db.collection("users").document(authorId)
        .collection("customAudiences").document(documentId)
        .set(data, com.google.firebase.firestore.SetOptions.merge())
        .await()
}

suspend fun FirestoreService.getCustomAudience(contentType: String, authorId: String): List<String> {
    val snap = db.collection("users").document(authorId)
        .collection("customAudiences").document("default_$contentType")
        .get()
        .await()
    @Suppress("UNCHECKED_CAST")
    return (snap.data?.get("allowedUsers") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
}

suspend fun FirestoreService.canUserViewContentWithCustomList(
    contentId: String,
    contentType: String,
    authorId: String,
    viewerId: String,
): Boolean {
    val collection = if (contentType == "moment") "moments" else "stories"
    val snap = db.collection("users").document(authorId)
        .collection(collection).document(contentId)
        .get()
        .await()
    val customListId = snap.data?.get("customListId") as? String ?: return false
    return isUserInCustomList(viewerId, customListId, authorId)
}

suspend fun FirestoreService.fetchCustomLists(userId: String): List<CustomAudienceList> {
    val snap = db.collection("users").document(userId)
        .collection("customAudienceLists")
        .orderBy("updatedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
        .get()
        .await()
    return snap.documents.mapNotNull { doc ->
        @Suppress("UNCHECKED_CAST")
        CustomAudienceList.from(doc.id, doc.data as Map<String, Any?>)
    }
}

/** Port de `CreateListViewModel.createList` — `users/{uid}/customAudienceLists`. */
suspend fun FirestoreService.createCustomAudienceList(
    userId: String,
    name: String,
    description: String,
    members: List<String>,
    color: String,
    icon: String,
): String {
    val ref = db.collection("users").document(userId).collection("customAudienceLists").document()
    val data = mutableMapOf<String, Any>(
        "name" to name,
        "members" to members,
        "color" to color,
        "icon" to icon,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )
    if (description.isNotBlank()) data["description"] = description
    ref.set(data).await()
    return ref.id
}

/** Port de `EditListViewModel.updateList`. */
suspend fun FirestoreService.updateCustomAudienceList(
    userId: String,
    listId: String,
    name: String,
    description: String,
    members: List<String>,
    color: String,
    icon: String,
) {
    val update = mutableMapOf<String, Any>(
        "name" to name,
        "members" to members,
        "color" to color,
        "icon" to icon,
        "updatedAt" to FieldValue.serverTimestamp(),
    )
    // iOS: description vacía → FieldValue.delete()
    if (description.isBlank()) {
        update["description"] = FieldValue.delete()
    } else {
        update["description"] = description
    }
    db.collection("users").document(userId)
        .collection("customAudienceLists").document(listId)
        .update(update)
        .await()
}

suspend fun FirestoreService.fetchCustomListDetails(listId: String, ownerId: String): CustomAudienceList {
    val snap = db.collection("users").document(ownerId)
        .collection("customAudienceLists").document(listId)
        .get()
        .await()
    @Suppress("UNCHECKED_CAST")
    val data = snap.data as? Map<String, Any?> ?: error("Lista no encontrada")
    return CustomAudienceList.from(snap.id, data) ?: error("Lista no encontrada")
}

suspend fun FirestoreService.addMembersToCustomList(listId: String, ownerId: String, memberIds: List<String>) {
    db.collection("users").document(ownerId)
        .collection("customAudienceLists").document(listId)
        .update(
            mapOf(
                "members" to FieldValue.arrayUnion(*memberIds.toTypedArray()),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
}

suspend fun FirestoreService.removeMembersFromCustomList(listId: String, ownerId: String, memberIds: List<String>) {
    db.collection("users").document(ownerId)
        .collection("customAudienceLists").document(listId)
        .update(
            mapOf(
                "members" to FieldValue.arrayRemove(*memberIds.toTypedArray()),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
}

private suspend fun FirestoreService.isUserInCustomList(
    userId: String,
    listId: String,
    listOwnerId: String,
): Boolean {
    val snap = db.collection("users").document(listOwnerId)
        .collection("customAudienceLists").document(listId)
        .get()
        .await()
    @Suppress("UNCHECKED_CAST")
    val members = (snap.data?.get("members") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
    return members.contains(userId)
}
