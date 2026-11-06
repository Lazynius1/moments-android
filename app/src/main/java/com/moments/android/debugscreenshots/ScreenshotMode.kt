package com.moments.android.debugscreenshots

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.MainActivity
import com.moments.android.services.cache.UserCacheService

/**
 * Modo capturas para las fichas de las tiendas: UI real con datos de demostración
 * y Firebase aislado. Solo en debug: cada llamada desde la app va precedida de
 * `BuildConfig.DEBUG &&` para que R8 lo elimine en release.
 *
 * Se activa con `--es MomentsScreenshotMode <pantalla>` (y opcionalmente
 * `MomentsScreenshotStoryIndex`). Corre sobre el teléfono real con la sesión
 * iniciada: nunca cierra sesión ni escribe en la cuenta real.
 */
object ScreenshotMode {

    enum class Screen(val raw: String) {
        FEED("feed"),
        AI_LABEL("aiLabel"),
        STORY_VIEWER("storyViewer"),
        HIDDEN_LAYERS("hiddenLayers"),
        CHAT("chat"),
        CHAT_INBOX("chatInbox"),
        ECHO("echo"),
        MAP("map"),
        PRIVACY("privacy"),
        APP_ICONS("appIcons"),
        NOVA("nova");

        companion object {
            fun from(raw: String?): Screen? {
                val value = raw?.trim().orEmpty()
                if (value.isEmpty()) return null
                return entries.firstOrNull { it.raw.equals(value, ignoreCase = true) }
            }
        }
    }

    const val EXTRA_SCREEN = "MomentsScreenshotMode"
    const val EXTRA_STORY_INDEX = "MomentsScreenshotStoryIndex"
    private const val EXTRA_RELAUNCHED = "MomentsScreenshotRelaunched"
    private const val EXTRA_CLEAN_RELAUNCH = "MomentsScreenshotCleanRelaunch"

    /** Fichero propio (no lo usa la sesión real): petición de arranque de un solo uso. */
    private const val BOOT_PREFS = "moments_screenshot_boot"
    private const val BOOT_SCREEN = "screen"
    private const val BOOT_STORY_INDEX = "storyIndex"
    private const val BOOT_AT = "at"
    private const val BOOT_MAX_AGE_MS = 120_000L

    private const val TAG = "ScreenshotMode"

    /** Usuario propio de la demo (no existe en Auth). */
    const val ME_ID = "ss_me"

    @Volatile
    var screen: Screen? = null
        private set

    val isActive: Boolean get() = screen != null

    /** Historia que abre el visor (por defecto la segunda). */
    @Volatile
    var storyIndex: Int = 1
        private set

    @Volatile
    private var context: Context? = null

    @Volatile
    private var hasPresentedActivity = false

    @Volatile
    private var didBootstrap = false

    val appContext: Context
        get() = context ?: error("ScreenshotMode sin contexto")

    /** uid de la sesión real del teléfono (las vistas que leen Auth directamente lo usan). */
    val authUid: String?
        get() = FirebaseAuth.getInstance().currentUser?.uid

    // MARK: - Activación

    /**
     * Llamar al principio de `Application.onCreate`, antes de tocar Firebase.
     * Lee la petición de un solo uso o el intent base de la tarea que se está lanzando.
     */
    fun detectAtProcessStart(app: Application): Boolean {
        context = app.applicationContext
        val request = consumeBootRequest(app) ?: requestFromLaunchTask(app) ?: return false
        screen = request.first
        storyIndex = request.second
        Log.w(TAG, "Modo capturas activo: ${request.first.raw}")
        return true
    }

    fun screenFromIntent(intent: Intent?): Screen? =
        Screen.from(intent?.extras?.let { readString(it, EXTRA_SCREEN) })

    fun storyIndexFromIntent(intent: Intent?): Int {
        val extras = intent?.extras ?: return 1
        return readString(extras, EXTRA_STORY_INDEX)?.trim()?.toIntOrNull() ?: 1
    }

    @Suppress("DEPRECATION")
    private fun readString(extras: Bundle, key: String): String? =
        when (val value = extras.get(key)) {
            null -> null
            is String -> value
            is Number -> value.toString()
            else -> value.toString()
        }

    private fun consumeBootRequest(app: Application): Pair<Screen, Int>? {
        val prefs = app.getSharedPreferences(BOOT_PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(BOOT_SCREEN, null) ?: return null
        val index = prefs.getInt(BOOT_STORY_INDEX, 1)
        val at = prefs.getLong(BOOT_AT, 0L)
        prefs.edit().clear().commit()
        if (System.currentTimeMillis() - at > BOOT_MAX_AGE_MS) return null
        val parsed = Screen.from(raw) ?: return null
        return parsed to index
    }

    private fun requestFromLaunchTask(app: Application): Pair<Screen, Int>? {
        val manager = app.getSystemService(ActivityManager::class.java) ?: return null
        val tasks = runCatching { manager.appTasks }.getOrNull().orEmpty()
        val intents = tasks.mapNotNull { task -> runCatching { task.taskInfo?.baseIntent }.getOrNull() }
        for (intent in intents) {
            if (intent.getBooleanExtra(EXTRA_CLEAN_RELAUNCH, false)) continue
            val parsed = screenFromIntent(intent) ?: continue
            return parsed to storyIndexFromIntent(intent)
        }
        return null
    }

    /**
     * Llamar en `MainActivity.onCreate` antes de `setContent`. Devuelve true si la
     * actividad está reiniciando el proceso y no debe seguir.
     * - Lanzamiento nuevo con la pantalla pero el proceso arrancó normal → reinicia en modo capturas.
     * - Proceso en modo capturas restaurado desde recientes o sin la pantalla → reinicia limpio.
     */
    fun handleActivityCreate(activity: Activity, savedInstanceState: Bundle?): Boolean {
        val intent = activity.intent
        val requested = screenFromIntent(intent)
        val fromHistory = intent != null &&
            (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
        val freshLaunch = savedInstanceState == null && !fromHistory

        if (isActive) {
            // Recreación (giro, tema) dentro de la misma sesión de capturas.
            if (hasPresentedActivity) return false
            if (freshLaunch && requested != null) {
                hasPresentedActivity = true
                return false
            }
            if (intent?.getBooleanExtra(EXTRA_CLEAN_RELAUNCH, false) == true) {
                Log.e(TAG, "No se pudo salir del modo capturas; se mantiene la demo")
                hasPresentedActivity = true
                return false
            }
            relaunch(activity, Intent(activity, MainActivity::class.java).putExtra(EXTRA_CLEAN_RELAUNCH, true))
            return true
        }

        if (requested == null || !freshLaunch) return false
        if (intent?.getBooleanExtra(EXTRA_RELAUNCHED, false) == true) {
            Log.e(TAG, "El proceso no arrancó en modo capturas tras reiniciar; se abre la app normal")
            return false
        }
        // El proceso ya arrancó con Firebase real: se reinicia para aislarlo desde el principio.
        activity.getSharedPreferences(BOOT_PREFS, Context.MODE_PRIVATE).edit()
            .putString(BOOT_SCREEN, requested.raw)
            .putInt(BOOT_STORY_INDEX, storyIndexFromIntent(intent))
            .putLong(BOOT_AT, System.currentTimeMillis())
            .commit()
        val relaunchIntent = Intent(activity, MainActivity::class.java)
            .putExtra(EXTRA_SCREEN, requested.raw)
            .putExtra(EXTRA_STORY_INDEX, storyIndexFromIntent(intent).toString())
            .putExtra(EXTRA_RELAUNCHED, true)
        relaunch(activity, relaunchIntent)
        return true
    }

    private fun relaunch(activity: Activity, intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity.startActivity(intent)
        activity.finish()
        Runtime.getRuntime().exit(0)
    }

    // MARK: - Preparación

    /** Siembra Firestore en memoria, cachés y preferencias de demo. Tras `ScreenshotSandbox.configure()`. */
    fun bootstrap(app: Context) {
        if (!isActive || didBootstrap) return
        didBootstrap = true
        ScreenshotFirestoreSeeder.seed()
        ScreenshotFixtures.allUsers.forEach(UserCacheService::cacheUser)
        prepareDefaults(app)
    }

    /** Solo claves de ids de demo: nunca se borra ni cambia nada de la sesión real. */
    private fun prepareDefaults(app: Context) {
        // Capas ocultas: dos reveladas y una pendiente.
        val seen = app.getSharedPreferences("moments_hidden_layers_seen", Context.MODE_PRIVATE)
        val viewer = authUid ?: "anonymous"
        val moment = ScreenshotFixtures.hiddenLayersMoment
        val momentId = moment.id.orEmpty()
        val editor = seen.edit()
        ScreenshotFixtures.hiddenLayers(momentId).forEachIndexed { index, layer ->
            val key = "hiddenLayerSeen:$viewer:$momentId:${layer.id}"
            if (index < 2) editor.putBoolean(key, true) else editor.remove(key)
        }
        editor.apply()

        // La historia con rasca debe aparecer sin revelar.
        val stickers = app.getSharedPreferences("moments_story_stickers", Context.MODE_PRIVATE).edit()
        ScreenshotFixtures.storyIds.forEach { stickers.remove("reveal_revealed_$it") }
        // La polaroid se revela agitando el móvil: en la captura sale ya revelada.
        ScreenshotFixtures.storyIds.forEach { stickers.putBoolean("frame_revealed_$it.ss_frame", true) }
        stickers.apply()
    }
}
