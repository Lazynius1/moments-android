package com.moments.android.services.auth

import android.content.Context
import android.graphics.Bitmap
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Source
import com.moments.android.models.AppUser
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.messaging.MessageCatchUpService
import com.moments.android.views.messaging.services.ChatAccessCoordinator
import com.moments.android.views.messaging.services.ChatSessionEngine
import com.moments.android.views.messaging.services.LiveLocationSharingService
import com.moments.android.services.messaging.MessageIngestService
import com.moments.android.notifications.services.FCMTokenService
import com.moments.android.notifications.services.InAppNotificationService
import com.moments.android.notifications.services.NotificationBadgeService
import com.moments.android.notifications.services.NotificationService
import com.moments.android.services.network.NetworkMonitor
import com.moments.android.services.persistence.LocalPersistenceService
import com.moments.android.services.storage.StorageService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date
import com.moments.android.services.firestore.fetchUser
import com.moments.android.services.firestore.createUser
import com.moments.android.services.firestore.fetchAvailableInterests
import com.moments.android.services.firestore.verifyUserCreation
import com.moments.android.services.firestore.changeUsername

/**
 * Port de AuthService.swift — email + Google únicamente.
 *
 * N/A en Android (comentado en código donde aplica):
 * - Sign in with Apple (startAppleSignIn, signInWithApple, linkWithApple, unlinkFromApple, …)
 * - Passkeys (signInWithPasskeyToken)
 * - Widget sync (syncProfileDataToWidget)
 * - Apple private relay / backup email para Apple ID
 */
object AuthService {

    object RegistrationRecoveryCode {
        const val ACCOUNT_ALREADY_COMPLETE = 9_001
    }

    class AuthServiceException(message: String, val code: Int = -1, cause: Throwable? = null) :
        Exception(message, cause)

    enum class BackupEmailStatus { MISSING, APPLE_RELAY, USABLE }

    enum class RegistrationState { IDLE, REGISTERING, COMPLETING }

    enum class CachedAccountDecision { ALLOWED, DEACTIVATED, SUSPENDED }

    sealed class AccountDeletionConfirmation {
        data class Password(val password: String) : AccountDeletionConfirmation()
        // iOS: .appleVerified — N/A en Android (sin Sign in with Apple)
    }

    data class CachedAccountStatus(
        val userId: String,
        val decision: CachedAccountDecision,
        val reason: String? = null,
        val expiresAt: Date? = null,
        val verifiedAt: Date = Date(),
    ) {
        val isExpiredSuspension: Boolean
            get() = decision == CachedAccountDecision.SUSPENDED &&
                expiresAt != null && Date().after(expiresAt)
    }

    sealed class AuthState {
        data object Loading : AuthState()
        data object VerifyingAccount : AuthState()
        data object Authenticated : AuthState()
        data object Deactivated : AuthState()
        data class Suspended(val reason: String?, val expiresAt: Date?) : AuthState()
        data object Unauthenticated : AuthState()
    }

    private sealed class ServerProfileCheck {
        data object Missing : ServerProfileCheck()
        data class Exists(val user: AppUser?) : ServerProfileCheck()
        data object Unreachable : ServerProfileCheck()
    }

    private sealed class RegistrationSessionResolution {
        data object Suspended : RegistrationSessionResolution()
        data class AccountComplete(val user: AppUser) : RegistrationSessionResolution()
        data class Deactivated(val user: AppUser) : RegistrationSessionResolution()
        data object IncompleteProfile : RegistrationSessionResolution()
    }

    private data class AccountCheck(val isActive: Boolean, val user: AppUser?, val isSuspended: Boolean)

    private const val FUNCTIONS_REGION = "europe-southwest1"
    private const val DELETE_ACCOUNT_FUNCTION = "deleteMyAccount"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val authMutex = Mutex()
    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()
    private val firestoreService = FirestoreService()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _currentUser = MutableStateFlow<AppUser?>(null)
    val currentUser: StateFlow<AppUser?> = _currentUser.asStateFlow()

    private val _currentFirebaseUser = MutableStateFlow<FirebaseUser?>(null)
    val currentFirebaseUser: StateFlow<FirebaseUser?> = _currentFirebaseUser.asStateFlow()

    private val _isAccountDeactivated = MutableStateFlow(false)
    val isAccountDeactivated: StateFlow<Boolean> = _isAccountDeactivated.asStateFlow()

    private val _deactivatedUserData = MutableStateFlow<AppUser?>(null)
    val deactivatedUserData: StateFlow<AppUser?> = _deactivatedUserData.asStateFlow()

    private val _isVerifyingAccount = MutableStateFlow(false)
    val isVerifyingAccount: StateFlow<Boolean> = _isVerifyingAccount.asStateFlow()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _isRegistering = MutableStateFlow(false)
    val isRegistering: StateFlow<Boolean> = _isRegistering.asStateFlow()

    private val _isResumingOnboarding = MutableStateFlow(false)
    val isResumingOnboarding: StateFlow<Boolean> = _isResumingOnboarding.asStateFlow()

    private val _resumingOnboardingContext = MutableStateFlow(OnboardingDraftContext.EMAIL)
    val resumingOnboardingContext: StateFlow<OnboardingDraftContext> = _resumingOnboardingContext.asStateFlow()

    @Volatile private var registrationState: RegistrationState = RegistrationState.IDLE
    @Volatile private var authProcessingEnabled: Boolean = true
    @Volatile private var transitionLock: Boolean = false
    @Volatile private var suspensionRegistration: ListenerRegistration? = null
    @Volatile private var authListenerAttached = false
    @Volatile private var lastChatScopedUserId: String? = null
    @Volatile private var appContext: Context? = null

    val isInRegistrationProcess: Boolean get() = registrationState != RegistrationState.IDLE
    val isTransitionLocked: Boolean get() = transitionLock

    private val linkedProviderIds: List<String>
        get() = (auth.currentUser ?: _currentFirebaseUser.value)
            ?.providerData?.map { it.providerId }.orEmpty()

    val isPasswordLinked: Boolean get() = linkedProviderIds.contains("password")
    val isGoogleLinked: Boolean get() = linkedProviderIds.contains("google.com")

    val backupEmailStatus: BackupEmailStatus
        get() {
            val email = _currentFirebaseUser.value?.email?.trim().orEmpty()
            if (email.isEmpty()) return BackupEmailStatus.MISSING
            // N/A: Apple private relay — solo aplica a Sign in with Apple en iOS
            return BackupEmailStatus.USABLE
        }

    val requiresBackupEmailSetup: Boolean
        get() = backupEmailStatus != BackupEmailStatus.USABLE

    fun isValidEmail(email: String): Boolean {
        val trimmed = email.trim()
        return Regex("^[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,64}$").matches(trimmed)
    }

    fun initialize(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        cleanupExpiredOnboardingDraft()
        attachAuthListener()
        scope.launch { bootstrapIncompleteOnboardingIfNeeded() }
    }

    private fun prefs() =
        (appContext ?: error("AuthService.initialize required"))
            .getSharedPreferences("moments_auth", Context.MODE_PRIVATE)

    // MARK: - Auth listener

    private fun attachAuthListener() {
        if (authListenerAttached) return
        authListenerAttached = true
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            scope.launch { handleAuthStateChange(user) }
        }
    }

    private suspend fun handleAuthStateChange(user: FirebaseUser?) {
        invalidateChatScopedStateIfNeeded(user?.uid)

        if (transitionLock) {
            if (user != null) _currentFirebaseUser.value = user
            return
        }

        if (registrationState == RegistrationState.REGISTERING || !authProcessingEnabled) {
            _currentFirebaseUser.value = user
            return
        }

        if (registrationState == RegistrationState.COMPLETING) {
            handleRegistrationCompletion(user)
            return
        }

        if (user != null) {
            val creationAge = user.metadata?.creationTimestamp?.let { System.currentTimeMillis() - it } ?: Long.MAX_VALUE
            if (creationAge < 5_000) {
                _authState.value = AuthState.VerifyingAccount
                _isVerifyingAccount.value = true
                _currentFirebaseUser.value = user
                val check = retryUserFetchForNewUser(user.uid)
                _isVerifyingAccount.value = false
                if (check.isSuspended) return
                if (check.isActive && check.user != null) {
                    saveCachedAccountStatus(user.uid, CachedAccountDecision.ALLOWED)
                    hydrateAuthenticatedSession(user, check.user)
                } else {
                    handleMissingFirestoreProfile(user.uid)
                }
                return
            }
        }

        if (user != null && _isRegistering.value) {
            _currentFirebaseUser.value = user
            return
        }

        if (_authState.value is AuthState.Loading && user == null) {
            _authState.value = AuthState.Unauthenticated
            return
        }

        if (user == null) {
            if (_authState.value is AuthState.Suspended) return
            stopSuspensionListener()
            _isLoggedIn.value = false
            _currentUser.value = null
            _currentFirebaseUser.value = null
            _isAccountDeactivated.value = false
            _deactivatedUserData.value = null
            _isVerifyingAccount.value = false
            _isRegistering.value = false
            _authState.value = AuthState.Unauthenticated
            registrationState = RegistrationState.IDLE
            authProcessingEnabled = true
            return
        }

        if (_isLoggedIn.value && _currentFirebaseUser.value?.uid == user.uid) {
            _currentFirebaseUser.value = user
            return
        }

        val cachedUser = LocalPersistenceService.loadUser(user.uid)
        val cachedStatus = loadCachedAccountStatus(user.uid)
        if (cachedUser != null) {
            if (cachedStatus?.decision == CachedAccountDecision.SUSPENDED &&
                cachedStatus.isExpiredSuspension.not()
            ) {
                applySuspended(user, cachedStatus.reason, cachedStatus.expiresAt)
                return
            }
            if (!cachedUser.isActive || cachedStatus?.decision == CachedAccountDecision.DEACTIVATED) {
                applyDeactivatedSession(user, cachedUser)
                return
            }
            hydrateAuthenticatedSession(user, cachedUser)
            if (!NetworkMonitor.isConnected) return
            val check = checkAccountStatus(user.uid)
            if (check.isSuspended) {
                _isLoggedIn.value = false
                _currentUser.value = null
                return
            }
            if (check.isActive) {
                val resolved = check.user ?: cachedUser
                saveCachedAccountStatus(user.uid, CachedAccountDecision.ALLOWED)
                hydrateAuthenticatedSession(user, resolved)
            } else if (check.user != null) {
                saveCachedAccountStatus(user.uid, CachedAccountDecision.DEACTIVATED)
                applyDeactivatedSession(user, check.user)
            } else {
                hydrateAuthenticatedSession(user, cachedUser)
            }
            return
        }

        _authState.value = AuthState.VerifyingAccount
        _isVerifyingAccount.value = true
        _isLoggedIn.value = false
        _isAccountDeactivated.value = false
        _deactivatedUserData.value = null
        _currentFirebaseUser.value = user
        _currentUser.value = null

        val check = checkAccountStatus(user.uid)
        _isVerifyingAccount.value = false
        if (check.isSuspended) return
        if (check.isActive) {
            OnboardingDraftStore.clear()
            check.user?.let {
                saveCachedAccountStatus(user.uid, CachedAccountDecision.ALLOWED)
                LocalPersistenceService.saveCurrentUser(it)
            }
            hydrateAuthenticatedSession(user, check.user)
        } else if (check.user != null) {
            saveCachedAccountStatus(user.uid, CachedAccountDecision.DEACTIVATED)
            applyDeactivatedSession(user, check.user)
        } else if (!_isRegistering.value && !isInRegistrationProcess) {
            handleMissingFirestoreProfile(user.uid)
        }
    }

    private fun invalidateChatScopedStateIfNeeded(userId: String?) {
        if (lastChatScopedUserId == userId) return
        lastChatScopedUserId = userId
        ChatAccessCoordinator.invalidateAll()
        ChatSessionEngine.resetOnSignOut()
        MessageIngestService.resetOnSignOut()
        MessageCatchUpService.resetOnSignOut()
    }

    // MARK: - Login

    suspend fun login(identifier: String, password: String) {
        require(identifier.isNotEmpty() && password.isNotEmpty()) { "Empty fields" }
        val emailRegex = Regex("^[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,64}$")
        val user: FirebaseUser = if (emailRegex.matches(identifier)) {
            auth.signInWithEmailAndPassword(identifier, password).await().user
                ?: error("Unknown login error")
        } else {
            val username = identifier.lowercase()
            val cachedEmail = prefs().getString("cachedEmail_$username", null)
            val email = cachedEmail ?: resolveEmailForUsername(username)
            auth.signInWithEmailAndPassword(email, password).await().user
                ?: error("Unknown login error")
        }
        finishCredentialLogin(user)
    }

    private suspend fun resolveEmailForUsername(username: String): String {
        val doc = db.collection("usernames").document(username).get().await()
        val data = doc.data ?: error("Username not found")
        val email = data["email"] as? String
        if (!email.isNullOrEmpty()) {
            prefs().edit().putString("cachedEmail_$username", email).apply()
            return email
        }
        val userId = data["userId"] as? String ?: error("Username not found")
        val userDoc = db.collection("users").document(userId).get().await()
        val userEmail = userDoc.getString("email") ?: error("Username not found")
        db.collection("usernames").document(username).set(
            mapOf("email" to userEmail, "updatedAt" to FieldValue.serverTimestamp()),
            com.google.firebase.firestore.SetOptions.merge(),
        )
        prefs().edit().putString("cachedEmail_$username", userEmail).apply()
        return userEmail
    }

    private suspend fun finishCredentialLogin(user: FirebaseUser) {
        authMutex.withLock {
            transitionLock = true
            registrationState = RegistrationState.IDLE
            authProcessingEnabled = true
        }
        _currentFirebaseUser.value = user
        _isLoggedIn.value = false
        _currentUser.value = null
        _isAccountDeactivated.value = false
        _deactivatedUserData.value = null
        _isVerifyingAccount.value = true
        _isRegistering.value = false
        _authState.value = AuthState.VerifyingAccount

        val check = checkAccountStatus(user.uid, allowMissingProfile = true)
        _isVerifyingAccount.value = false

        if (check.isSuspended) {
            authMutex.withLock { transitionLock = false }
            LoginActivityService.recordSuccessfulLogin(user.uid, "email")
            return
        }

        if (check.isActive) {
            if (check.user != null) {
                hydrateAuthenticatedSession(user, check.user)
            } else {
                _isLoggedIn.value = true
                _currentFirebaseUser.value = user
                _authState.value = AuthState.Authenticated
                startSuspensionListener()
            }
            LoginActivityService.recordSuccessfulLogin(user.uid, "email")
            authMutex.withLock { transitionLock = false }
            return
        }

        if (check.user != null) {
            applyDeactivatedSession(user, check.user)
            authMutex.withLock { transitionLock = false }
            return
        }

        _isVerifyingAccount.value = true
        _authState.value = AuthState.VerifyingAccount
        when (val profileCheck = confirmProfileMissingOnServer(user.uid)) {
            ServerProfileCheck.Missing -> {
                _isVerifyingAccount.value = false
                authMutex.withLock { transitionLock = false }
                beginOnboardingResume(user)
            }
            is ServerProfileCheck.Exists -> {
                _isVerifyingAccount.value = false
                if (profileCheck.user != null) {
                    if (profileCheck.user.isActive) {
                        hydrateAuthenticatedSession(user, profileCheck.user)
                    } else {
                        saveCachedAccountStatus(user.uid, CachedAccountDecision.DEACTIVATED)
                        applyDeactivatedSession(user, profileCheck.user)
                    }
                } else {
                    runCatching {
                        val userData = firestoreService.fetchUser(user.uid)
                        if (userData.isActive) hydrateAuthenticatedSession(user, userData)
                        else {
                            saveCachedAccountStatus(user.uid, CachedAccountDecision.DEACTIVATED)
                            applyDeactivatedSession(user, userData)
                        }
                    }.onFailure { failCredentialLogin(it) }
                }
                authMutex.withLock { transitionLock = false }
            }
            ServerProfileCheck.Unreachable -> {
                _isVerifyingAccount.value = false
                authMutex.withLock { transitionLock = false }
                failCredentialLogin(Exception("Network error"))
            }
        }
    }

    private suspend fun failCredentialLogin(error: Throwable) {
        runCatching { auth.signOut() }
        _currentFirebaseUser.value = null
        _isLoggedIn.value = false
        _currentUser.value = null
        _isVerifyingAccount.value = false
        _authState.value = AuthState.Unauthenticated
        throw mapAuthError(error)
    }

    // MARK: - Google Sign-In (equivalente al flujo social de iOS, sin Apple)

    /**
     * @return true si el usuario ya tenía perfil completo (login); false si requiere onboarding.
     */
    suspend fun signInWithGoogle(idToken: String): Boolean {
        authMutex.withLock { transitionLock = true }
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val user = auth.signInWithCredential(credential).await().user
            ?: run {
                authMutex.withLock { transitionLock = false }
                error("Auth failed")
            }

        val check = checkAccountStatus(user.uid, allowMissingProfile = true)
        if (check.isSuspended) {
            _isLoggedIn.value = false
            _currentUser.value = null
            _currentFirebaseUser.value = user
            _isAccountDeactivated.value = false
            _deactivatedUserData.value = null
            _isRegistering.value = false
            _isVerifyingAccount.value = false
            authMutex.withLock { transitionLock = false }
            return false
        }

        if (check.isActive && check.user != null) {
            saveCachedAccountStatus(user.uid, CachedAccountDecision.ALLOWED)
            LocalPersistenceService.saveCurrentUser(check.user)
            hydrateAuthenticatedSession(user, check.user)
            LoginActivityService.recordSuccessfulLogin(user.uid, "google")
            authMutex.withLock { transitionLock = false }
            return true
        }

        if (check.user != null) {
            saveCachedAccountStatus(user.uid, CachedAccountDecision.DEACTIVATED)
            applyDeactivatedSession(user, check.user)
            authMutex.withLock { transitionLock = false }
            return false
        }

        authMutex.withLock {
            registrationState = RegistrationState.REGISTERING
            authProcessingEnabled = false
            transitionLock = false
        }
        _isRegistering.value = true
        _resumingOnboardingContext.value = OnboardingDraftContext.GOOGLE
        _currentFirebaseUser.value = user
        OnboardingDraftStore.markStarted(
            context = OnboardingDraftContext.GOOGLE,
            firebaseUID = user.uid,
        )
        OnboardingDraftStore.update(email = user.email.orEmpty())
        return false
    }

    suspend fun linkWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val linked = auth.currentUser?.linkWithCredential(credential)?.await()?.user
        _currentFirebaseUser.value = linked ?: auth.currentUser
    }

    suspend fun completeGoogleRegistration(
        username: String,
        interests: List<String>,
        profileImage: Bitmap?,
    ) = completeSocialRegistration(username, interests, profileImage)

    /** Equivalente a completeSocialRegistration de iOS (allí orientado a Apple). */
    suspend fun completeSocialRegistration(
        username: String,
        interests: List<String>,
        profileImage: Bitmap?,
        fallbackEmail: String? = null,
    ) {
        val firebaseUser = auth.currentUser ?: error("No Firebase user")
        val email = firebaseUser.email?.takeIf { it.isNotEmpty() }
            ?: fallbackEmail?.takeIf { it.isNotEmpty() }
            ?: error("Email required to finish social registration")
        val profilePath = profileImage?.let {
            runCatching { StorageService.uploadProfileImage(firebaseUser.uid, it) }.getOrNull()
        }
        firestoreService.createUser(firebaseUser.uid, username, email, interests, profilePath)
    }

    // MARK: - Register

    suspend fun register(
        username: String,
        email: String,
        password: String,
        interests: List<String>,
        privacyPolicyAccepted: Boolean,
        profileImage: Bitmap?,
    ) {
        require(privacyPolicyAccepted) { "Privacy policy required" }
        authMutex.withLock {
            registrationState = RegistrationState.REGISTERING
            authProcessingEnabled = false
        }
        _isRegistering.value = true
        OnboardingDraftStore.markStarted(OnboardingDraftContext.EMAIL)
        OnboardingDraftStore.update(
            step = 3,
            username = username,
            email = email,
            selectedInterests = interests,
            privacyPolicyAccepted = privacyPolicyAccepted,
        )

        val usernameLower = username.lowercase()
        val usernameDoc = db.collection("usernames").document(usernameLower).get().await()
        if (usernameDoc.exists()) {
            clearRegistrationState()
            error("Username unavailable")
        }

        val existingUser = auth.currentUser
        val isResumingExistingAuth = existingUser?.email?.equals(email.trim(), ignoreCase = true) == true

        val finalizeRegistration: suspend (FirebaseUser, String) -> Unit = { user, userId ->
            OnboardingDraftStore.updateUID(userId)
            val profilePath = profileImage?.let {
                runCatching { StorageService.uploadProfileImage(userId, it) }.getOrNull()
            }
            try {
                firestoreService.createUser(userId, username, email.trim(), interests, profilePath)
            } catch (e: Exception) {
                if (!isResumingExistingAuth) {
                    runCatching { user.delete().await() }
                    OnboardingDraftStore.update(firebaseUID = null)
                }
                clearRegistrationState()
                throw e
            }
        }

        if (existingUser != null && isResumingExistingAuth) {
            when (val resolution = resolveAuthenticatedUserForRegistration(existingUser)) {
                RegistrationSessionResolution.Suspended -> {
                    clearRegistrationState()
                    error("User disabled")
                }
                is RegistrationSessionResolution.AccountComplete -> {
                    hydrateAuthenticatedSession(existingUser, resolution.user)
                    clearRegistrationState()
                    throw AuthServiceException(
                        "Account already complete",
                        RegistrationRecoveryCode.ACCOUNT_ALREADY_COMPLETE,
                    )
                }
                is RegistrationSessionResolution.Deactivated -> {
                    clearRegistrationState()
                    applyDeactivatedSession(existingUser, resolution.user)
                    error("User disabled")
                }
                RegistrationSessionResolution.IncompleteProfile -> {
                    OnboardingDraftStore.updateUID(existingUser.uid)
                    _currentFirebaseUser.value = existingUser
                    finalizeRegistration(existingUser, existingUser.uid)
                }
            }
            return
        }

        try {
            val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
            val user = result.user ?: run {
                clearRegistrationState()
                error("User ID not found")
            }
            runCatching { user.sendEmailVerification().await() }
            finalizeRegistration(user, user.uid)
        } catch (e: FirebaseAuthException) {
            if (e.errorCode == "ERROR_EMAIL_ALREADY_IN_USE") {
                attemptResumeEmailRegistrationAfterConflict(
                    email = email.trim(),
                    password = password,
                    finalizeRegistration = finalizeRegistration,
                )
            } else {
                clearRegistrationState()
                throw mapAuthError(e)
            }
        }
    }

    private suspend fun attemptResumeEmailRegistrationAfterConflict(
        email: String,
        password: String,
        finalizeRegistration: suspend (FirebaseUser, String) -> Unit,
    ) {
        authMutex.withLock {
            registrationState = RegistrationState.REGISTERING
            authProcessingEnabled = false
        }
        _isRegistering.value = true
        _isResumingOnboarding.value = true
        _resumingOnboardingContext.value = OnboardingDraftContext.EMAIL

        try {
            val user = auth.signInWithEmailAndPassword(email, password).await().user
                ?: run {
                    clearRegistrationState()
                    error("User ID not found")
                }
            when (val resolution = resolveAuthenticatedUserForRegistration(user)) {
                RegistrationSessionResolution.Suspended -> {
                    clearRegistrationState()
                    error("User disabled")
                }
                is RegistrationSessionResolution.AccountComplete -> {
                    hydrateAuthenticatedSession(user, resolution.user)
                    clearRegistrationState()
                    throw AuthServiceException(
                        "Account already complete",
                        RegistrationRecoveryCode.ACCOUNT_ALREADY_COMPLETE,
                    )
                }
                is RegistrationSessionResolution.Deactivated -> {
                    clearRegistrationState()
                    applyDeactivatedSession(user, resolution.user)
                    error("User disabled")
                }
                RegistrationSessionResolution.IncompleteProfile -> {
                    OnboardingDraftStore.updateUID(user.uid)
                    _currentFirebaseUser.value = user
                    finalizeRegistration(user, user.uid)
                }
            }
        } catch (e: FirebaseAuthException) {
            clearRegistrationState()
            _isResumingOnboarding.value = false
            val message = when (e.errorCode) {
                "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL", "ERROR_INVALID_LOGIN_CREDENTIALS" ->
                    "Wrong password for incomplete registration"
                else -> mapAuthError(e).message ?: e.errorCode
            }
            throw Exception(message, e)
        }
    }

    private suspend fun resolveAuthenticatedUserForRegistration(user: FirebaseUser): RegistrationSessionResolution {
        val check = checkAccountStatus(user.uid, allowMissingProfile = true)
        if (check.isSuspended) return RegistrationSessionResolution.Suspended
        if (check.user != null) {
            return if (check.user.isActive) {
                RegistrationSessionResolution.AccountComplete(check.user)
            } else {
                RegistrationSessionResolution.Deactivated(check.user)
            }
        }
        return RegistrationSessionResolution.IncompleteProfile
    }

    fun completeRegistration() {
        scope.launch {
            authMutex.withLock {
                registrationState = RegistrationState.COMPLETING
                authProcessingEnabled = true
                transitionLock = true
            }
            val user = auth.currentUser ?: _currentFirebaseUser.value
            if (user != null) {
                handleRegistrationCompletion(user)
            } else {
                clearRegistrationState()
                authMutex.withLock { transitionLock = false }
            }
        }
    }

    private suspend fun handleRegistrationCompletion(user: FirebaseUser?) {
        if (user == null) return
        _authState.value = AuthState.VerifyingAccount
        _isVerifyingAccount.value = true
        _currentFirebaseUser.value = user

        delay(1_000)

        val check = checkAccountStatus(user.uid)
        _isVerifyingAccount.value = false

        if (check.isSuspended) return

        if (check.isActive && check.user != null) {
            OnboardingDraftStore.clear()
            hydrateAuthenticatedSession(user, check.user)
            scope.launch {
                delay(2_000)
                _isRegistering.value = false
                authMutex.withLock {
                    registrationState = RegistrationState.IDLE
                    authProcessingEnabled = true
                }
                delay(1_000)
                authMutex.withLock { transitionLock = false }
            }
            return
        }

        val retry = retryUserFetchForNewUser(user.uid)
        if (retry.isActive && retry.user != null) {
            OnboardingDraftStore.clear()
            hydrateAuthenticatedSession(user, retry.user)
            scope.launch {
                delay(2_000)
                _isRegistering.value = false
                authMutex.withLock {
                    registrationState = RegistrationState.IDLE
                    authProcessingEnabled = true
                    transitionLock = false
                }
            }
        } else {
            _authState.value = AuthState.Deactivated
            _isAccountDeactivated.value = true
            _deactivatedUserData.value = check.user
            _isRegistering.value = false
            authMutex.withLock {
                registrationState = RegistrationState.IDLE
                authProcessingEnabled = true
                transitionLock = false
            }
        }
    }

    suspend fun cancelOnboardingRegistration(
        deleteIncompleteAccount: Boolean = false,
        signOut: Boolean = true,
    ) {
        authMutex.withLock {
            registrationState = RegistrationState.IDLE
            authProcessingEnabled = true
            transitionLock = false
        }
        _isRegistering.value = false
        _isResumingOnboarding.value = false
        _isLoggedIn.value = false
        _currentUser.value = null
        _isAccountDeactivated.value = false
        _deactivatedUserData.value = null
        _isVerifyingAccount.value = false
        _authState.value = AuthState.Unauthenticated
        OnboardingDraftStore.clear()

        val user = auth.currentUser
        if (deleteIncompleteAccount && user != null) {
            val snap = db.collection("users").document(user.uid).get().await()
            if (!snap.exists()) {
                runCatching { user.delete().await() }
            }
        }
        if (signOut) runCatching { auth.signOut() }
        _currentFirebaseUser.value = null
    }

    // MARK: - Account status

    private suspend fun checkAccountStatus(
        userId: String,
        allowMissingProfile: Boolean = false,
    ): AccountCheck {
        if (registrationState == RegistrationState.REGISTERING || _isRegistering.value) {
            return retryUserFetch(userId, maxRetries = 5)
        }

        if (!NetworkMonitor.isConnected) {
            val cachedUser = LocalPersistenceService.loadUser(userId)
            val cachedStatus = loadCachedAccountStatus(userId)
            return when {
                cachedStatus?.decision == CachedAccountDecision.SUSPENDED &&
                    cachedStatus.isExpiredSuspension.not() ->
                    AccountCheck(false, cachedUser, true)
                cachedStatus?.decision == CachedAccountDecision.DEACTIVATED ||
                    cachedUser?.isActive == false ->
                    AccountCheck(false, cachedUser, false)
                else -> AccountCheck(cachedUser != null, cachedUser, false)
            }
        }

        val result = withTimeoutOrNull(8_000) {
            performAccountStatusCheck(userId, allowMissingProfile)
        }

        if (result != null) return result

        val cachedUser = LocalPersistenceService.loadUser(userId)
        val cachedStatus = loadCachedAccountStatus(userId)
        return when {
            cachedStatus?.decision == CachedAccountDecision.SUSPENDED &&
                cachedStatus.isExpiredSuspension.not() ->
                AccountCheck(false, cachedUser, true)
            cachedStatus?.decision == CachedAccountDecision.DEACTIVATED ||
                cachedUser?.isActive == false ->
                AccountCheck(false, cachedUser, false)
            cachedUser != null -> {
                if (_currentFirebaseUser.value?.uid == userId) {
                    _isLoggedIn.value = true
                    _currentUser.value = cachedUser
                    _isVerifyingAccount.value = false
                    _authState.value = AuthState.Authenticated
                }
                AccountCheck(true, cachedUser, false)
            }
            else -> {
                if (_currentFirebaseUser.value?.uid == userId) {
                    _isLoggedIn.value = false
                    _currentUser.value = null
                    _isVerifyingAccount.value = false
                    _authState.value = AuthState.Unauthenticated
                }
                AccountCheck(false, null, false)
            }
        }
    }

    private suspend fun performAccountStatusCheck(
        userId: String,
        allowMissingProfile: Boolean,
    ): AccountCheck {
        val (isSuspended, reason, expiresAt) = checkUserSuspension(userId)
        if (isSuspended) {
            saveCachedAccountStatus(userId, CachedAccountDecision.SUSPENDED, reason, expiresAt)
            _authState.value = AuthState.Suspended(reason, expiresAt)
            scope.launch {
                delay(100)
                runCatching { auth.signOut() }
            }
            return AccountCheck(false, null, true)
        }

        return try {
            val appUser = firestoreService.fetchUser(userId)
            saveCachedAccountStatus(
                userId,
                if (appUser.isActive) CachedAccountDecision.ALLOWED else CachedAccountDecision.DEACTIVATED,
            )
            AccountCheck(appUser.isActive, appUser, false)
        } catch (e: Exception) {
            when {
                allowMissingProfile && isMissingUserProfileError(e) ->
                    AccountCheck(false, null, false)
                registrationState == RegistrationState.REGISTERING || _isRegistering.value ->
                    AccountCheck(false, null, false)
                isMissingUserProfileError(e) -> {
                    handleMissingFirestoreProfile(userId)
                    AccountCheck(false, null, false)
                }
                else -> {
                    val cachedUser = LocalPersistenceService.loadUser(userId)
                    val cachedStatus = loadCachedAccountStatus(userId)
                    when {
                        cachedStatus?.decision == CachedAccountDecision.SUSPENDED &&
                            cachedStatus.isExpiredSuspension.not() ->
                            AccountCheck(false, cachedUser, true)
                        cachedStatus?.decision == CachedAccountDecision.DEACTIVATED ||
                            cachedUser?.isActive == false ->
                            AccountCheck(false, cachedUser, false)
                        cachedUser != null -> AccountCheck(true, cachedUser, false)
                        else -> {
                            forceLogout(signOut = true)
                            AccountCheck(false, null, false)
                        }
                    }
                }
            }
        }
    }

    private suspend fun confirmProfileMissingOnServer(userId: String): ServerProfileCheck {
        return try {
            val snap = db.collection("users").document(userId).get(Source.SERVER).await()
            if (!snap.exists()) ServerProfileCheck.Missing
            else {
                @Suppress("UNCHECKED_CAST")
                val data = snap.data as? Map<String, Any?>
                val user = data?.let { runCatching { AppUser.from(snap.id, it) }.getOrNull() }
                ServerProfileCheck.Exists(user)
            }
        } catch (_: Exception) {
            ServerProfileCheck.Unreachable
        }
    }

    private suspend fun handleMissingFirestoreProfile(userId: String) {
        val user = auth.currentUser?.takeIf { it.uid == userId } ?: run {
            forceLogout(signOut = true)
            return
        }
        when (val result = confirmProfileMissingOnServer(userId)) {
            ServerProfileCheck.Missing -> beginOnboardingResume(user)
            is ServerProfileCheck.Exists -> {
                val appUser = result.user
                if (appUser != null) {
                    if (appUser.isActive) hydrateAuthenticatedSession(user, appUser)
                    else {
                        saveCachedAccountStatus(userId, CachedAccountDecision.DEACTIVATED)
                        applyDeactivatedSession(user, appUser)
                    }
                } else {
                    runCatching {
                        val userData = firestoreService.fetchUser(userId)
                        if (userData.isActive) hydrateAuthenticatedSession(user, userData)
                        else {
                            saveCachedAccountStatus(userId, CachedAccountDecision.DEACTIVATED)
                            applyDeactivatedSession(user, userData)
                        }
                    }.onFailure { forceLogout(signOut = true) }
                }
            }
            ServerProfileCheck.Unreachable -> {
                val draft = OnboardingDraftStore.load()
                if (draft?.firebaseUID == userId && !OnboardingDraftStore.isExpired(draft)) {
                    beginOnboardingResume(user)
                } else {
                    forceLogout(signOut = true)
                }
            }
        }
    }

    private suspend fun retryUserFetchForNewUser(userId: String): AccountCheck =
        retryUserFetchWithCustomParams(userId, maxRetries = 8, baseDelayMs = 500, maxDelayMs = 1500)

    private suspend fun retryUserFetch(
        userId: String,
        maxRetries: Int,
        retryCount: Int = 0,
    ): AccountCheck {
        val delayMs = (retryCount * 500L).coerceAtMost(2_000L)
        if (delayMs > 0) delay(delayMs)

        val (isSuspended, reason, expiresAt) = checkUserSuspension(userId)
        if (isSuspended) {
            _authState.value = AuthState.Suspended(reason, expiresAt)
            return AccountCheck(false, null, true)
        }

        return try {
            val appUser = firestoreService.fetchUser(userId)
            saveCachedAccountStatus(
                userId,
                if (appUser.isActive) CachedAccountDecision.ALLOWED else CachedAccountDecision.DEACTIVATED,
            )
            AccountCheck(appUser.isActive, appUser, false)
        } catch (_: Exception) {
            if (retryCount < maxRetries - 1) {
                retryUserFetch(userId, maxRetries, retryCount + 1)
            } else if (registrationState == RegistrationState.REGISTERING || _isRegistering.value) {
                AccountCheck(true, null, false)
            } else {
                handleMissingFirestoreProfile(userId)
                AccountCheck(false, null, false)
            }
        }
    }

    private suspend fun retryUserFetchWithCustomParams(
        userId: String,
        maxRetries: Int,
        baseDelayMs: Long,
        maxDelayMs: Long,
        retryCount: Int = 0,
    ): AccountCheck {
        val delayMs = (retryCount * baseDelayMs).coerceAtMost(maxDelayMs)
        if (delayMs > 0) delay(delayMs)

        val (isSuspended, _, _) = checkUserSuspension(userId)
        if (isSuspended) return AccountCheck(false, null, true)

        return try {
            val appUser = firestoreService.fetchUser(userId)
            saveCachedAccountStatus(
                userId,
                if (appUser.isActive) CachedAccountDecision.ALLOWED else CachedAccountDecision.DEACTIVATED,
            )
            AccountCheck(appUser.isActive, appUser, false)
        } catch (_: Exception) {
            if (retryCount < maxRetries - 1) {
                retryUserFetchWithCustomParams(userId, maxRetries, baseDelayMs, maxDelayMs, retryCount + 1)
            } else {
                handleMissingFirestoreProfile(userId)
                AccountCheck(false, null, false)
            }
        }
    }

    suspend fun checkUserSuspension(userId: String): Triple<Boolean, String?, Date?> {
        if (!NetworkMonitor.isConnected) return Triple(false, null, null)
        val snap = db.collection("users").document(userId).get().await()
        val data = snap.data ?: return Triple(false, null, null)
        val isSuspended = data["isSuspended"] as? Boolean ?: false
        if (!isSuspended) return Triple(false, null, null)

        val suspendedUntil = data["suspendedUntil"] as? Timestamp
        if (suspendedUntil != null) {
            val expiration = suspendedUntil.toDate()
            if (Date().after(expiration)) {
                db.collection("users").document(userId).update(
                    mapOf(
                        "isSuspended" to false,
                        "suspendedUntil" to FieldValue.delete(),
                        "suspensionReason" to FieldValue.delete(),
                    ),
                ).await()
                return Triple(false, null, null)
            }
            return Triple(true, data["suspensionReason"] as? String, expiration)
        }
        return Triple(true, data["suspensionReason"] as? String, null)
    }

    // MARK: - Session hydration

    private fun hydrateAuthenticatedSession(user: FirebaseUser, userData: AppUser?) {
        OnboardingDraftStore.clear()
        if (userData != null) {
            saveCachedAccountStatus(user.uid, CachedAccountDecision.ALLOWED)
            LocalPersistenceService.saveCurrentUser(userData)
        }
        _isLoggedIn.value = true
        _currentUser.value = userData
        _currentFirebaseUser.value = user
        _isAccountDeactivated.value = false
        _deactivatedUserData.value = null
        _isResumingOnboarding.value = false
        _authState.value = AuthState.Authenticated
        _isVerifyingAccount.value = false
        startSuspensionListener()
        // N/A: syncProfileDataToWidget — widgets iOS
    }

    private fun applyDeactivatedSession(user: FirebaseUser, userData: AppUser) {
        saveCachedAccountStatus(user.uid, CachedAccountDecision.DEACTIVATED)
        _isLoggedIn.value = false
        _currentUser.value = null
        _currentFirebaseUser.value = user
        _isAccountDeactivated.value = true
        _deactivatedUserData.value = userData
        _authState.value = AuthState.Deactivated
        _isVerifyingAccount.value = false
    }

    private fun applySuspended(user: FirebaseUser, reason: String?, expiresAt: Date?) {
        _isLoggedIn.value = false
        _currentUser.value = null
        _currentFirebaseUser.value = user
        _isAccountDeactivated.value = false
        _deactivatedUserData.value = null
        _authState.value = AuthState.Suspended(reason, expiresAt)
        _isVerifyingAccount.value = false
    }

    private fun startSuspensionListener() {
        val userId = _currentUser.value?.id ?: auth.currentUser?.uid ?: return
        suspensionRegistration?.remove()
        suspensionRegistration = db.collection("users").document(userId)
            .addSnapshotListener { snap, _ ->
                if (snap == null || !snap.exists()) return@addSnapshotListener
                val suspended = snap.getBoolean("isSuspended") == true
                if (!suspended) return@addSnapshotListener
                val suspendedUntil = snap.getTimestamp("suspendedUntil")?.toDate()
                if (suspendedUntil != null && Date().after(suspendedUntil)) return@addSnapshotListener
                scope.launch { logout() }
            }
    }

    private fun stopSuspensionListener() {
        suspensionRegistration?.remove()
        suspensionRegistration = null
    }

    private fun clearRegistrationState() {
        registrationState = RegistrationState.IDLE
        authProcessingEnabled = true
        _isRegistering.value = false
        _isResumingOnboarding.value = false
    }

    private fun inferredOnboardingContext(user: FirebaseUser, draft: OnboardingDraft?): OnboardingDraftContext {
        if (draft != null && (draft.firebaseUID == user.uid || draft.firebaseUID == null)) {
            return draft.context
        }
        if (user.providerData.any { it.providerId == "google.com" }) return OnboardingDraftContext.GOOGLE
        return OnboardingDraftContext.EMAIL
    }

    private fun beginOnboardingResume(user: FirebaseUser) {
        val draft = OnboardingDraftStore.load()
        val context = inferredOnboardingContext(user, draft)

        if (draft == null || draft.firebaseUID != user.uid) {
            OnboardingDraftStore.markStarted(context = context, firebaseUID = user.uid)
            OnboardingDraftStore.update(email = user.email.orEmpty())
        } else {
            OnboardingDraftStore.updateUID(user.uid)
        }

        registrationState = RegistrationState.REGISTERING
        authProcessingEnabled = false

        _resumingOnboardingContext.value = context
        _isResumingOnboarding.value = true
        _isRegistering.value = true
        _isVerifyingAccount.value = false
        _isAccountDeactivated.value = false
        _deactivatedUserData.value = null
        _isLoggedIn.value = false
        _currentUser.value = null
        _currentFirebaseUser.value = user
        _authState.value = AuthState.Unauthenticated
    }

    private fun beginOnboardingResumeWithoutAuthenticatedUser(draft: OnboardingDraft) {
        registrationState = RegistrationState.REGISTERING
        authProcessingEnabled = false
        _resumingOnboardingContext.value = draft.context
        _isResumingOnboarding.value = true
        _isRegistering.value = true
        _isVerifyingAccount.value = false
        _isAccountDeactivated.value = false
        _deactivatedUserData.value = null
        _isLoggedIn.value = false
        _currentUser.value = null
        _authState.value = AuthState.Unauthenticated
    }

    private suspend fun bootstrapIncompleteOnboardingIfNeeded() {
        if (auth.currentUser != null) return
        val draft = OnboardingDraftStore.load() ?: return
        if (OnboardingDraftStore.isExpired(draft)) return
        beginOnboardingResumeWithoutAuthenticatedUser(draft)
    }

    private fun cleanupExpiredOnboardingDraft() {
        val draft = OnboardingDraftStore.load() ?: return
        if (OnboardingDraftStore.isExpired(draft)) OnboardingDraftStore.clear()
    }

    private fun forceLogout(signOut: Boolean = false) {
        _isLoggedIn.value = false
        _currentUser.value = null
        _isAccountDeactivated.value = false
        _deactivatedUserData.value = null
        _isVerifyingAccount.value = false
        _isRegistering.value = false
        _isResumingOnboarding.value = false
        _authState.value = AuthState.Unauthenticated
        if (signOut) {
            runCatching { auth.signOut() }
            _currentFirebaseUser.value = null
        }
        registrationState = RegistrationState.IDLE
        authProcessingEnabled = true
        transitionLock = false
    }

    // MARK: - Account management (AccountManagementService.swift)

    suspend fun reactivateAccount() {
        val userId = _currentFirebaseUser.value?.uid ?: error("Usuario no autenticado")
        _isVerifyingAccount.value = true
        _authState.value = AuthState.VerifyingAccount
        try {
            db.collection("users").document(userId).update(
                mapOf(
                    "isActive" to true,
                    "reactivatedAt" to FieldValue.serverTimestamp(),
                    "deactivatedAt" to FieldValue.delete(),
                    "deactivatedBy" to FieldValue.delete(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            val check = checkAccountStatus(userId)
            _isVerifyingAccount.value = false
            if (check.isActive && check.user != null) {
                hydrateAuthenticatedSession(_currentFirebaseUser.value!!, check.user)
            } else {
                _authState.value = AuthState.Deactivated
            }
        } catch (e: Exception) {
            _isVerifyingAccount.value = false
            _authState.value = AuthState.Deactivated
            throw e
        }
    }

    suspend fun deactivateAccount() {
        val userId = _currentFirebaseUser.value?.uid ?: error("Usuario no autenticado")
        db.collection("users").document(userId).update(
            mapOf(
                "isActive" to false,
                "deactivatedAt" to FieldValue.serverTimestamp(),
                "deactivatedBy" to "user",
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        val userData = _currentUser.value ?: firestoreService.fetchUser(userId)
        saveCachedAccountStatus(userId, CachedAccountDecision.DEACTIVATED)
        applyDeactivatedSession(_currentFirebaseUser.value!!, userData)
    }

    suspend fun deleteAccount(confirmation: AccountDeletionConfirmation) {
        val user = auth.currentUser ?: error("Usuario no autenticado")
        when (confirmation) {
            is AccountDeletionConfirmation.Password -> {
                val email = user.email ?: error("No email")
                val credential = EmailAuthProvider.getCredential(email, confirmation.password)
                user.reauthenticate(credential).await()
            }
        }
        requestBackendAccountDeletion(user)
        LocalPersistenceService.clearCurrentUser()
        OnboardingDraftStore.clear()
        forceLogout(signOut = false)
        _currentFirebaseUser.value = null
    }

    private suspend fun requestBackendAccountDeletion(user: FirebaseUser) = withContext(Dispatchers.IO) {
        val projectId = FirebaseApp.getInstance().options.projectId
            ?: error("Could not delete account")
        val url = URL("https://$FUNCTIONS_REGION-$projectId.cloudfunctions.net/$DELETE_ACCOUNT_FUNCTION")
        val token = user.getIdToken(true).await().token ?: error("User not found")

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 35_000
            readTimeout = 35_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
        }
        connection.outputStream.use { it.write("""{"source":"settings"}""".toByteArray()) }

        val code = connection.responseCode
        if (code !in 200..299) {
            val body = runCatching {
                connection.errorStream?.bufferedReader()?.readText()
            }.getOrNull().orEmpty()
            val message = runCatching {
                JSONObject(body).optString("error").takeIf { it.isNotEmpty() }
            }.getOrNull() ?: "Could not delete account"
            error(message)
        }
        auth.signOut()
    }

    // MARK: - Email / password linking

    suspend fun sendEmailVerification() {
        auth.currentUser?.sendEmailVerification()?.await()
            ?: error("No authenticated user")
    }

    suspend fun linkPassword(email: String, password: String) {
        val user = auth.currentUser ?: error("User not found")
        require(!isPasswordLinked) { "Password already set" }
        val normalized = email.trim().lowercase()
        require(isValidEmail(normalized)) { "Invalid email" }
        val credential = EmailAuthProvider.getCredential(normalized, password)
        val linked = user.linkWithCredential(credential).await().user
        _currentFirebaseUser.value = linked ?: auth.currentUser
    }

    suspend fun updateAccountEmail(email: String) {
        val user = auth.currentUser ?: error("User not found")
        val normalized = email.trim().lowercase()
        require(isValidEmail(normalized)) { "Invalid email" }
        user.updateEmail(normalized).await()
        _currentFirebaseUser.value = auth.currentUser
        runCatching { user.sendEmailVerification().await() }
        updateUserField("email", normalized)
    }

    fun refreshLinkedProviders() {
        val user = auth.currentUser ?: run {
            _currentFirebaseUser.value = null
            return
        }
        scope.launch {
            runCatching { user.reload().await() }
            _currentFirebaseUser.value = auth.currentUser
        }
    }

    // MARK: - Public helpers

    fun logout() {
        scope.launch {
            stopSuspensionListener()
            runCatching { LiveLocationSharingService.endActiveSessionForSignOut() }
            FCMTokenService.clearFCMToken()
            NotificationService.resetOnSignOut()
            InAppNotificationService.stopListening()
            NotificationBadgeService.cleanup()
            MessageIngestService.resetOnSignOut()
            MessageCatchUpService.resetOnSignOut()
            ChatSessionEngine.resetOnSignOut()
            runCatching { auth.signOut() }
            LocalPersistenceService.clearCurrentUser()
            OnboardingDraftStore.clear()
            _isLoggedIn.value = false
            _currentUser.value = null
            _currentFirebaseUser.value = null
            _isAccountDeactivated.value = false
            _deactivatedUserData.value = null
            _isVerifyingAccount.value = false
            _isRegistering.value = false
            _isResumingOnboarding.value = false
            _authState.value = AuthState.Unauthenticated
            authMutex.withLock {
                registrationState = RegistrationState.IDLE
                authProcessingEnabled = true
                transitionLock = false
            }
        }
    }

    suspend fun resetPassword(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    suspend fun fetchAvailableInterests(): List<String> =
        firestoreService.fetchAvailableInterests()

    suspend fun checkUsernameAvailability(
        username: String,
        interests: List<String> = emptyList(),
    ): Pair<Boolean, List<String>?> {
        val clean = username.lowercase().trim()
        if (clean.isEmpty()) return true to null
        val doc = db.collection("usernames").document(clean).get().await()
        if (!doc.exists()) return true to null
        val suggestions = mutableListOf<String>()
        if (interests.isNotEmpty()) {
            val interest = interests.random().lowercase().replace(" ", "")
            suggestions += "${clean}_$interest"
        }
        suggestions += "$clean${(100..999).random()}"
        suggestions += "${clean}_${(10..99).random()}"
        suggestions += "$clean.moments"
        return false to suggestions.take(4)
    }

    fun refreshCurrentUser() {
        val userId = _currentFirebaseUser.value?.uid ?: return
        scope.launch {
            runCatching {
                val user = firestoreService.fetchUser(userId)
                _currentUser.value = user
                LocalPersistenceService.saveCurrentUser(user)
            }
        }
    }

    suspend fun updateUserField(field: String, value: Any) {
        val userId = _currentUser.value?.id ?: error("No current user")
        db.collection("users").document(userId).update(
            mapOf(field to value, "updatedAt" to FieldValue.serverTimestamp()),
        ).await()
        refreshCurrentUser()
    }

    fun persistOnboardingDraft(
        context: OnboardingDraftContext,
        step: Int,
        username: String,
        email: String,
        selectedInterests: List<String>,
        privacyPolicyAccepted: Boolean,
        profileImage: Bitmap? = null,
        firebaseUID: String? = null,
    ) {
        if (OnboardingDraftStore.load() == null) {
            OnboardingDraftStore.markStarted(context, firebaseUID = firebaseUID)
        }
        OnboardingDraftStore.update(
            step = step,
            username = username,
            email = email,
            selectedInterests = selectedInterests,
            privacyPolicyAccepted = privacyPolicyAccepted,
            profileImage = profileImage,
            firebaseUID = firebaseUID ?: auth.currentUser?.uid,
        )
    }

    private fun isMissingUserProfileError(error: Throwable): Boolean {
        val message = error.message.orEmpty()
        return message.contains("not found", ignoreCase = true) ||
            message.contains("Document not found", ignoreCase = true) ||
            message.contains("User not found", ignoreCase = true)
    }

    private fun cachedAccountStatusKey(userId: String) = "accountStatus_$userId"

    private fun loadCachedAccountStatus(userId: String): CachedAccountStatus? {
        val raw = prefs().getString(cachedAccountStatusKey(userId), null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            CachedAccountStatus(
                userId = json.getString("userId"),
                decision = CachedAccountDecision.valueOf(json.getString("decision")),
                reason = json.optString("reason").takeIf { it.isNotEmpty() && !json.isNull("reason") },
                expiresAt = if (json.has("expiresAt") && !json.isNull("expiresAt")) Date(json.getLong("expiresAt")) else null,
                verifiedAt = Date(json.optLong("verifiedAt", System.currentTimeMillis())),
            )
        }.getOrNull()
    }

    private fun saveCachedAccountStatus(
        userId: String,
        decision: CachedAccountDecision,
        reason: String? = null,
        expiresAt: Date? = null,
    ) {
        val json = JSONObject().apply {
            put("userId", userId)
            put("decision", decision.name)
            put("reason", reason)
            put("expiresAt", expiresAt?.time)
            put("verifiedAt", System.currentTimeMillis())
        }
        prefs().edit().putString(cachedAccountStatusKey(userId), json.toString()).apply()
    }

    fun mapAuthError(error: Throwable): Throwable {
        if (error is FirebaseAuthException) {
            val message = when (error.errorCode) {
                "ERROR_EMAIL_ALREADY_IN_USE" -> "Email already registered"
                "ERROR_INVALID_EMAIL" -> "Invalid email"
                "ERROR_WRONG_PASSWORD" -> "Wrong password"
                "ERROR_USER_NOT_FOUND" -> "User not found"
                "ERROR_WEAK_PASSWORD" -> "Weak password"
                "ERROR_NETWORK_REQUEST_FAILED" -> "Network error"
                "ERROR_TOO_MANY_REQUESTS" -> "Too many requests"
                "ERROR_INVALID_CREDENTIAL", "ERROR_INVALID_LOGIN_CREDENTIALS" -> "Invalid credentials"
                "ERROR_USER_DISABLED" -> "User disabled"
                "ERROR_REQUIRES_RECENT_LOGIN" -> "Recent login required"
                else -> error.message ?: error.errorCode
            }
            return Exception(message, error)
        }
        return error
    }

    // MARK: - N/A Apple / Passkey (stubs documentados para paridad de API)

    // fun startAppleSignIn(): String — N/A
    // fun signInWithApple(...) — N/A
    // fun linkWithApple(...) — N/A
    // fun unlinkFromApple(...) — N/A
    // fun reauthenticateWithApple(...) — N/A
    // fun signInWithPasskeyToken(...) — N/A
    // static isApplePrivateRelayEmail — N/A
    // var isAppleLinked / canUnlinkApple / isAppleOnlyAccess — N/A
    // var currentNonce / pendingAppleRegistrationEmail — N/A
}
