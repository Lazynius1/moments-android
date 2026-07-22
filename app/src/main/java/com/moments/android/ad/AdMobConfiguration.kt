package com.moments.android.ad

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import com.moments.android.BuildConfig
import com.moments.android.models.AppUser
import com.moments.android.services.auth.AuthService
import com.moments.android.utilities.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Port de `AdMob Configuration.swift` — configuración AdMob, UMP, managers nativos y Plus.
 *
 * Plus: descartado en modelos Android; [PlusAdManager] mantiene `shouldShowAds = true` por defecto.
 */
object AdMobConfiguration {

    // iOS real IDs (reference only — Android app not registered in AdMob/Play yet):
    // appId = "ca-app-pub-7805678909278568~7091658934"
    // nativeAdUnitId = "ca-app-pub-7805678909278568/9925436334"

    // REPLACE_WHEN_YOU_HAVE_GOOGLE_KEY: Android AdMob App ID (Play Console / AdMob Android app)
    const val APP_ID = "REPLACE_WHEN_YOU_HAVE_GOOGLE_KEY"

    // REPLACE_WHEN_YOU_HAVE_GOOGLE_KEY: Android native ad unit
    const val NATIVE_AD_UNIT_ID = "REPLACE_WHEN_YOU_HAVE_GOOGLE_KEY"

    /** Google official test native ad unit (debug / diagnostic). */
    const val TEST_NATIVE_AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"

    /** Google sample app ID — also used in AndroidManifest for safe startup. */
    const val TEST_APP_ID = "ca-app-pub-3940256099942544~3347511713"

    /** Diagnostic mode: force Google test IDs on real devices. */
    const val IS_DIAGNOSTIC_MODE = false

    private const val PREFS_NAME = "admob_config"
    private const val KEY_HAS_SEEN_PRIVACY_CONSENT = "hasSeenPrivacyConsent"
    private const val KEY_ATT_STATUS = "attAuthorizationStatus"
    private const val KEY_ATT_TIMESTAMP = "attDecisionTimestamp"
    private const val KEY_HAS_SEEN_ATT_PRE_ALERT = "hasSeenATTPreAlert"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var prefs: SharedPreferences? = null

    private var preloadedNativeAd: NativeAd? = null

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    @Volatile
    private var hasPresentedConsentFlow = false

    fun initialize(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        prefs = appContext!!.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (!canSafelyInitializeAds()) {
            AppLog.debug { "AdMob: APP_ID placeholder — skipping SDK init (ads disabled until Android key is set)" }
            return
        }

        MobileAds.initialize(appContext!!) {
            _isInitialized.value = true
            AppLog.debug { "AdMob: SDK initialized" }
        }
    }

    fun isPlaceholder(value: String): Boolean =
        value.contains("REPLACE_WHEN_YOU_HAVE_GOOGLE_KEY", ignoreCase = true)

    fun canSafelyInitializeAds(): Boolean =
        BuildConfig.DEBUG || IS_DIAGNOSTIC_MODE || !isPlaceholder(APP_ID)

    fun getNativeAdUnitId(): String {
        val raw = when {
            IS_DIAGNOSTIC_MODE || BuildConfig.DEBUG -> TEST_NATIVE_AD_UNIT_ID
            isPlaceholder(NATIVE_AD_UNIT_ID) -> TEST_NATIVE_AD_UNIT_ID
            else -> NATIVE_AD_UNIT_ID
        }
        return raw.trim()
    }

    // MARK: - Consent (UMP; ATT → no-op on Android)

    val shouldShowConsentFlow: Boolean
        get() {
            if (hasPresentedConsentFlow) return false
            if (prefs?.getBoolean(KEY_HAS_SEEN_PRIVACY_CONSENT, false) == true) return false

            val ctx = appContext ?: return false
            if (!canSafelyInitializeAds()) return false

            val umpStatus = UserMessagingPlatform.getConsentInformation(ctx).consentStatus
            return umpStatus == ConsentInformation.ConsentStatus.UNKNOWN ||
                umpStatus == ConsentInformation.ConsentStatus.REQUIRED
        }

    fun startConsentFlow(activity: Activity, completion: () -> Unit) {
        hasPresentedConsentFlow = true
        prefs?.edit()?.putBoolean(KEY_HAS_SEEN_PRIVACY_CONSENT, true)?.apply()

        requestGdprConsent(activity) {
            requestATTAuthorization()
            completion()
        }
    }

    fun showPrivacyOptionsForm(activity: Activity) {
        val ctx = appContext ?: return
        val consentInfo = UserMessagingPlatform.getConsentInformation(ctx)
        if (consentInfo.privacyOptionsRequirementStatus !=
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        ) {
            AppLog.debug { "UMP: privacy options form not required" }
            return
        }

        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error: FormError? ->
            if (error != null) {
                AppLog.debug { "UMP privacy options error: ${error.message}" }
            }
        }
    }

    private fun requestGdprConsent(activity: Activity, completion: (Boolean) -> Unit) {
        val ctx = appContext ?: run {
            completion(false)
            return
        }

        val paramsBuilder = ConsentRequestParameters.Builder()
        if (BuildConfig.DEBUG) {
            val debugSettings = com.google.android.ump.ConsentDebugSettings.Builder(ctx)
                .setDebugGeography(com.google.android.ump.ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_DISABLED)
                .build()
            paramsBuilder.setConsentDebugSettings(debugSettings)
        }

        val consentInfo = UserMessagingPlatform.getConsentInformation(ctx)
        consentInfo.requestConsentInfoUpdate(
            activity,
            paramsBuilder.build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError: FormError? ->
                    if (formError != null) {
                        AppLog.debug { "UMP form error: ${formError.message}" }
                    }
                    completion(consentInfo.canRequestAds())
                }
            },
            { formError: FormError ->
                AppLog.debug { "UMP consent info error: ${formError.message}" }
                completion(true)
            },
        )
    }

    /** ATT is iOS-only; record notApplicable_android in SharedPreferences. */
    fun requestATTAuthorization() {
        prefs?.edit()
            ?.putString(KEY_ATT_STATUS, "notApplicable_android")
            ?.putLong(KEY_ATT_TIMESTAMP, System.currentTimeMillis())
            ?.apply()
        AppLog.debug { "AdMob: ATT N/A on Android — recorded notApplicable_android" }
    }

    fun getAdPersonalizationStatus(): Pair<Boolean, String> {
        val ctx = appContext
        if (ctx != null && canSafelyInitializeAds()) {
            val canRequest = UserMessagingPlatform.getConsentInformation(ctx).canRequestAds()
            return if (canRequest) {
                true to "UMP allows ad requests"
            } else {
                false to "UMP consent pending or denied"
            }
        }
        return false to "Ads not initialized (placeholder APP_ID)"
    }

    fun getATTDecisionInfo(): Triple<String, Long?, Boolean> {
        val status = prefs?.getString(KEY_ATT_STATUS, "notApplicable_android") ?: "notApplicable_android"
        val timestamp = prefs?.getLong(KEY_ATT_TIMESTAMP, 0L)?.takeIf { it > 0L }
        return Triple(status, timestamp, status != "notDetermined")
    }

    // MARK: - Preload

    fun preloadNativeAd(activity: Activity?) {
        if (!_isInitialized.value) {
            AppLog.debug { "AdMob: ignoring preload (SDK not initialized)" }
            return
        }
        val act = activity ?: run {
            AppLog.debug { "AdMob: no Activity for preload" }
            return
        }

        val adUnitId = getNativeAdUnitId()
        val options = NativeAdOptions.Builder()
            .setMediaAspectRatio(NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_ANY)
            .build()

        val loader = AdLoader.Builder(act, adUnitId)
            .forNativeAd { ad ->
                preloadedNativeAd?.destroy()
                preloadedNativeAd = ad
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    AppLog.debug { "AdMob preload failed: ${error.message}" }
                    preloadedNativeAd = null
                }
            })
            .withNativeAdOptions(options)
            .build()

        loader.loadAd(createAdRequest())
    }

    fun getPreloadedNativeAd(): NativeAd? = preloadedNativeAd

    fun clearPreloadedNativeAd() {
        preloadedNativeAd?.destroy()
        preloadedNativeAd = null
    }

    fun createAdRequest(): AdRequest = AdRequest.Builder().build()
}

// MARK: - Native Ad Manager

class NativeAdManager {
    private val loadScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _nativeAd = MutableStateFlow<NativeAd?>(null)
    val nativeAd: StateFlow<NativeAd?> = _nativeAd.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _hasError = MutableStateFlow(false)
    val hasError: StateFlow<Boolean> = _hasError.asStateFlow()

    private var adLoader: AdLoader? = null
    private var loadedAd: NativeAd? = null

    fun loadAd(activity: Activity?) {
        if (_isLoading.value) return

        if (!AdMobConfiguration.isInitialized.value) {
            AppLog.debug { "AdMob: retrying load in 1s (SDK not initialized)" }
            loadScope.launch {
                kotlinx.coroutines.delay(1_000)
                loadAd(activity)
            }
            return
        }

        AdMobConfiguration.getPreloadedNativeAd()?.let { preloaded ->
            swapAd(preloaded)
            AdMobConfiguration.clearPreloadedNativeAd()
            _isLoading.value = false
            _hasError.value = false
            return
        }

        val act = activity ?: run {
            _hasError.value = true
            return
        }

        _isLoading.value = true
        _hasError.value = false
        swapAd(null)

        val adUnitId = AdMobConfiguration.getNativeAdUnitId()
        AppLog.debug { "AdMob: loading native ad unit=$adUnitId" }

        adLoader = AdLoader.Builder(act, adUnitId)
            .forNativeAd { ad ->
                swapAd(ad)
                _isLoading.value = false
                _hasError.value = false
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    AppLog.debug { "AdMob native load failed: ${error.message}" }
                    _isLoading.value = false
                    _hasError.value = true
                }
            })
            .withNativeAdOptions(AdAspectRatioContext.Feed.nativeAdOptions)
            .build()

        adLoader?.loadAd(AdMobConfiguration.createAdRequest())
    }

    fun destroy() {
        adLoader = null
        swapAd(null)
    }

    private fun swapAd(ad: NativeAd?) {
        loadedAd?.destroy()
        loadedAd = ad
        _nativeAd.value = ad
    }
}

// MARK: - Plus Ad Manager (Plus N/A — always show ads unless extended later)

/**
 * Plus subscription was discarded in Android [AppUser]. Defaults to showing ads.
 * Wire [AuthService] when Plus fields are added to the user model.
 */
class PlusAdManager(
    private val authService: AuthService = AuthService,
) {
    private val _shouldShowAds = MutableStateFlow(true)
    val shouldShowAds: StateFlow<Boolean> = _shouldShowAds.asStateFlow()

    private val _isPlus = MutableStateFlow(false)
    val isPlus: StateFlow<Boolean> = _isPlus.asStateFlow()

    init {
        updateAdDisplayStatus()
    }

    fun updateAdDisplayStatus() {
        // Plus N/A on Android — no isPlusSubscriber / hasActivePlusSubscription on AppUser.
        _shouldShowAds.value = PlusStatusHelper.shouldShowAds(authService.currentUser.value)
        _isPlus.value = PlusStatusHelper.isActivePlus(authService.currentUser.value)
    }

    fun refreshAdStatus() = updateAdDisplayStatus()
}

/** Plus helpers — [AppUser] has no Plus fields; ads always shown for now. */
object PlusStatusHelper {
    /** Returns true unless user model gains `shouldHideAds` / active Plus subscription. */
    fun shouldShowAds(user: AppUser?): Boolean {
        user ?: return true
        // Plus discarded in models — extend when Firestore Plus fields are ported.
        return true
    }

    fun isActivePlus(user: AppUser?): Boolean = false
}
