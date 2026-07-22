package com.moments.android.services.auth

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class OnboardingDraftContext(val raw: String) {
    /** Legacy iOS; en Android no se usa Sign in with Apple. */
    APPLE("apple"),
    EMAIL("email"),
    GOOGLE("google");

    companion object {
        fun from(raw: String?) = entries.firstOrNull { it.raw == raw } ?: EMAIL
    }
}

data class OnboardingDraft(
    var context: OnboardingDraftContext,
    var firebaseUID: String? = null,
    var step: Int = 1,
    var username: String = "",
    var email: String = "",
    var selectedInterests: List<String> = emptyList(),
    var privacyPolicyAccepted: Boolean = false,
    var profileImageFilename: String? = null,
    var pendingAppleEmail: String? = null,
    var startedAt: Date = Date(),
    var updatedAt: Date = Date(),
)

/** Port de OnboardingDraftStore.swift — SharedPreferences + ficheros de imagen. */
object OnboardingDraftStore {
    private const val PREFS = "moments_onboarding_draft"
    private const val STORAGE_KEY = "pendingOnboardingDraft"
    private val TTL_MS = TimeUnit.DAYS.toMillis(30)
    private const val DRAFTS_DIR = "OnboardingDrafts"

    @Volatile private var appContext: Context? = null

    fun initialize(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    private fun prefs() = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun requireContext(): Context =
        appContext ?: error("OnboardingDraftStore.initialize(Context) required")

    fun load(): OnboardingDraft? {
        val raw = prefs().getString(STORAGE_KEY, null) ?: return null
        return runCatching { decode(JSONObject(raw)) }.getOrNull()
    }

    fun save(draft: OnboardingDraft) {
        prefs().edit().putString(STORAGE_KEY, encode(draft).toString()).apply()
    }

    fun clear() {
        load()?.let { removeProfileImage(it.profileImageFilename) }
        prefs().edit().remove(STORAGE_KEY).apply()
        runCatching { draftsDirectory().deleteRecursively() }
    }

    fun clearPersistedData() = clear()

    fun isExpired(draft: OnboardingDraft): Boolean =
        System.currentTimeMillis() - draft.updatedAt.time > TTL_MS

    fun markStarted(
        context: OnboardingDraftContext,
        firebaseUID: String? = null,
        pendingAppleEmail: String? = null,
    ) {
        val draft = load() ?: OnboardingDraft(context = context, firebaseUID = firebaseUID)
        draft.context = context
        if (firebaseUID != null) draft.firebaseUID = firebaseUID
        if (pendingAppleEmail != null) draft.pendingAppleEmail = pendingAppleEmail
        draft.updatedAt = Date()
        save(draft)
    }

    fun updateUID(uid: String) {
        val draft = load() ?: return
        draft.firebaseUID = uid
        draft.updatedAt = Date()
        save(draft)
    }

    fun update(
        step: Int? = null,
        username: String? = null,
        email: String? = null,
        selectedInterests: List<String>? = null,
        privacyPolicyAccepted: Boolean? = null,
        profileImage: Bitmap? = null,
        firebaseUID: String? = null,
        pendingAppleEmail: String? = null,
    ) {
        val draft = load() ?: return
        if (step != null) draft.step = step.coerceIn(1, 3)
        if (username != null) draft.username = username
        if (email != null) draft.email = email
        if (selectedInterests != null) draft.selectedInterests = selectedInterests
        if (privacyPolicyAccepted != null) draft.privacyPolicyAccepted = privacyPolicyAccepted
        if (firebaseUID != null) draft.firebaseUID = firebaseUID
        if (pendingAppleEmail != null) draft.pendingAppleEmail = pendingAppleEmail
        if (profileImage != null) {
            draft.profileImageFilename?.let { removeProfileImage(it) }
            draft.profileImageFilename = saveProfileImage(profileImage)
        }
        draft.updatedAt = Date()
        save(draft)
    }

    fun profileImage(from: OnboardingDraft): Bitmap? {
        val filename = from.profileImageFilename ?: return null
        val file = File(draftsDirectory(), filename)
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    private fun draftsDirectory(): File {
        val dir = File(requireContext().filesDir, DRAFTS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun saveProfileImage(image: Bitmap): String? {
        val filename = "profile-${UUID.randomUUID()}.jpg"
        val file = File(draftsDirectory(), filename)
        return runCatching {
            file.outputStream().use { out ->
                if (!image.compress(Bitmap.CompressFormat.JPEG, 82, out)) return null
            }
            filename
        }.getOrNull()
    }

    private fun removeProfileImage(filename: String?) {
        if (filename == null) return
        File(draftsDirectory(), filename).delete()
    }

    private fun encode(d: OnboardingDraft): JSONObject = JSONObject().apply {
        put("context", d.context.raw)
        put("firebaseUID", d.firebaseUID)
        put("step", d.step)
        put("username", d.username)
        put("email", d.email)
        put("selectedInterests", JSONArray(d.selectedInterests))
        put("privacyPolicyAccepted", d.privacyPolicyAccepted)
        put("profileImageFilename", d.profileImageFilename)
        put("pendingAppleEmail", d.pendingAppleEmail)
        put("startedAt", d.startedAt.time)
        put("updatedAt", d.updatedAt.time)
    }

    private fun decode(json: JSONObject): OnboardingDraft {
        val interests = mutableListOf<String>()
        json.optJSONArray("selectedInterests")?.let { arr ->
            for (i in 0 until arr.length()) interests += arr.getString(i)
        }
        return OnboardingDraft(
            context = OnboardingDraftContext.from(json.optString("context")),
            firebaseUID = json.optString("firebaseUID").takeIf { it.isNotEmpty() && json.has("firebaseUID") && !json.isNull("firebaseUID") },
            step = json.optInt("step", 1),
            username = json.optString("username"),
            email = json.optString("email"),
            selectedInterests = interests,
            privacyPolicyAccepted = json.optBoolean("privacyPolicyAccepted"),
            profileImageFilename = json.optString("profileImageFilename").takeIf { it.isNotEmpty() && !json.isNull("profileImageFilename") },
            pendingAppleEmail = json.optString("pendingAppleEmail").takeIf { it.isNotEmpty() && !json.isNull("pendingAppleEmail") },
            startedAt = Date(json.optLong("startedAt", System.currentTimeMillis())),
            updatedAt = Date(json.optLong("updatedAt", System.currentTimeMillis())),
        )
    }
}
