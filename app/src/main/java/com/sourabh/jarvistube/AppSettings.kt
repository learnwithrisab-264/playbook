package com.sourabh.jarvistube

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sourabh.jarvistube.data.Repo
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

/** User settings that shape what YouTube returns and how videos start. */
object AppSettings {
    data class Region(val code: String, val name: String)

    val regions = listOf(
        Region("IN", "India"),
        Region("US", "United States"),
        Region("GB", "United Kingdom"),
        Region("CA", "Canada"),
        Region("AU", "Australia"),
        Region("AE", "United Arab Emirates"),
        Region("SG", "Singapore"),
        Region("NP", "Nepal"),
        Region("BD", "Bangladesh"),
        Region("PK", "Pakistan"),
        Region("LK", "Sri Lanka"),
        Region("DE", "Germany"),
        Region("FR", "France"),
        Region("JP", "Japan"),
        Region("BR", "Brazil"),
        Region("ID", "Indonesia")
    )

    /** 0 = Auto. Higher ones only take effect when the video and this phone support them. */
    val qualities = listOf(0, 2160, 1440, 1080, 720, 480, 360, 240, 144)

    fun qualityLabel(height: Int): String = when {
        height <= 0 -> "Auto"
        height >= 2160 -> "${height}p 4K"
        height >= 1080 -> "${height}p HD"
        else -> "${height}p"
    }

    // ---- Captions: drawn by our own overlay (ui/CaptionOverlay.kt), so these apply instantly ----
    val captionFonts = listOf(
        "default" to "Default", "serif" to "Serif", "mono" to "Monospace",
        "casual" to "Casual", "cursive" to "Cursive", "condensed" to "Condensed"
    )
    val captionSizes = listOf(0.75f to "Small", 1f to "Normal", 1.25f to "Large", 1.5f to "Larger", 2f to "Huge")
    val captionColors = listOf(
        0xFFFFFFFF to "White", 0xFFFFEB3B to "Yellow", 0xFF69F0AE to "Green", 0xFF18FFFF to "Cyan",
        0xFF82B1FF to "Blue", 0xFFFF80AB to "Pink", 0xFFFF5252 to "Red"
    )

    // ---- Playback speeds offered in the player's speed menus ----
    const val MIN_SPEED = 0.1f
    const val MAX_SPEED = 4f
    val defaultSpeeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 3.5f, 4f)

    /** "Normal" for 1x, else "1.25x" / "2x" (no trailing zeros). */
    fun speedLabel(s: Float): String = if (s == 1f) "Normal" else speedText(s)

    fun speedText(s: Float): String = java.math.BigDecimal(s.toString()).stripTrailingZeros().toPlainString() + "x"

    private lateinit var prefs: SharedPreferences

    var region by mutableStateOf("IN"); private set
    var defaultQuality by mutableIntStateOf(0); private set
    /** The Recommended tab (YouTube home feed / Trending). Never reachable during a session or lock. */
    var showRecommended by mutableStateOf(true); private set
    /** The Shorts tab in the bottom bar. Off only hides the tab; Shorts in search stay. */
    var showShorts by mutableStateOf(true); private set
    /** The Study tab in the bottom bar. Hiding it never stops a session or lock: see [studyTabRequired]. */
    var showStudy by mutableStateOf(true); private set
    /** Theme from the epics (ui/theme/Epic.kt); "agni" is PlayBook's original look. */
    var themeKey by mutableStateOf("agni"); private set
    /** How things move: "leela", "shanti", "vayu" or "off". */
    var motionStyle by mutableStateOf("leela"); private set
    /** A light tap under the finger for choices. */
    var haptics by mutableStateOf(true); private set
    /** The video's colours glow softly behind the player, as YouTube's Ambient mode. */
    var ambient by mutableStateOf(true); private set
    /** Videos open in YouTube's upright full-screen view; "Details" goes to the normal page. */
    var startUpright by mutableStateOf(false); private set
    /** Ambient mode: blank space under a short page moves above the video, where the glow is. */
    var glowAbove by mutableStateOf(true); private set
    /** The home-screen icon takes the theme's colours (ui/theme/AppIcon). */
    var iconFollowsTheme by mutableStateOf(true); private set
    /** "dark" (default), "light" or "system" (follow the phone). */
    var appearance by mutableStateOf("dark"); private set
    val appearances = listOf("dark" to "Dark", "light" to "Light", "system" to "Follow the phone")
    val appearanceName: String get() = appearances.firstOrNull { it.first == appearance }?.second ?: "Dark"
    var captionFont by mutableStateOf("default"); private set
    var captionScale by mutableFloatStateOf(1f); private set
    var captionColor by mutableLongStateOf(0xFFFFFFFF); private set
    /** "en" / "hi" while captions are on; kept so the next video starts with them. */
    var captionLanguage by mutableStateOf<String?>(null); private set
    /** Light up the word being spoken (auto-generated captions carry word timings). */
    var captionHighlight by mutableStateOf(true); private set
    var speeds by mutableStateOf(defaultSpeeds); private set
    /** Corner the floating in-app player sits in: 0 top-left, 1 top-right, 2 bottom-left, 3 bottom-right. */
    var floatCorner by mutableIntStateOf(3); private set
    /** Width (dp) the user resized the floating player to; 0 = default size. */
    var floatWidthDp by mutableFloatStateOf(0f); private set

    val regionName: String get() = regions.firstOrNull { it.code == region }?.name ?: region

    fun init(context: Context) {
        prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        region = prefs.getString("region", "IN") ?: "IN"
        defaultQuality = prefs.getInt("quality", 0)
        showRecommended = prefs.getBoolean("showRecommended", true)
        showShorts = prefs.getBoolean("showShorts", true)
        showStudy = prefs.getBoolean("showStudy", true)
        appearance = prefs.getString("appearance", "dark") ?: "dark"
        themeKey = prefs.getString("theme", "agni") ?: "agni"
        motionStyle = prefs.getString("motion", "leela") ?: "leela"
        haptics = prefs.getBoolean("haptics", true)
        ambient = prefs.getBoolean("ambient", true)
        startUpright = prefs.getBoolean("startUpright", false)
        glowAbove = prefs.getBoolean("glowAbove", true)
        iconFollowsTheme = prefs.getBoolean("iconFollowsTheme", true)
        captionFont = prefs.getString("captionFont", "default") ?: "default"
        captionScale = prefs.getFloat("captionScale", 1f)
        captionColor = prefs.getLong("captionColor", 0xFFFFFFFF)
        captionLanguage = prefs.getString("captionLanguage", null)
        captionHighlight = prefs.getBoolean("captionHighlight", true)
        speeds = prefs.getString("speeds", null)
            ?.split(",")?.mapNotNull { it.toFloatOrNull() }?.let(::cleanSpeeds)
            ?: defaultSpeeds
        floatCorner = prefs.getInt("floatCorner", 3)
        floatWidthDp = prefs.getFloat("floatWidthDp", 0f)
    }

    fun updateFloatWidth(dp: Float) {
        floatWidthDp = dp
        prefs.edit().putFloat("floatWidthDp", dp).apply()
    }

    fun updateFloatCorner(corner: Int) {
        if (corner == floatCorner) return
        floatCorner = corner
        prefs.edit().putInt("floatCorner", corner).apply()
    }

    fun localization() = Localization("en", region)
    fun contentCountry() = ContentCountry(region)

    fun updateRegion(code: String) {
        if (code == region) return
        region = code
        prefs.edit().putString("region", code).apply()
        NewPipe.setupLocalization(localization(), contentCountry())
        Repo.clearCache() // cached stream info was resolved for the old region
    }

    fun updateDefaultQuality(height: Int) {
        defaultQuality = height
        prefs.edit().putInt("quality", height).apply()
    }

    fun updateShowRecommended(on: Boolean) {
        showRecommended = on
        prefs.edit().putBoolean("showRecommended", on).apply()
    }

    fun updateTheme(key: String) { themeKey = key; prefs.edit().putString("theme", key).apply() }
    fun updateMotion(key: String) { motionStyle = key; prefs.edit().putString("motion", key).apply() }
    fun updateHaptics(on: Boolean) { haptics = on; prefs.edit().putBoolean("haptics", on).apply() }
    fun updateAmbient(on: Boolean) { ambient = on; prefs.edit().putBoolean("ambient", on).apply() }
    fun updateIconFollowsTheme(on: Boolean) { iconFollowsTheme = on; prefs.edit().putBoolean("iconFollowsTheme", on).apply() }
    fun updateStartUpright(on: Boolean) { startUpright = on; prefs.edit().putBoolean("startUpright", on).apply() }
    fun updateGlowAbove(on: Boolean) { glowAbove = on; prefs.edit().putBoolean("glowAbove", on).apply() }

    fun updateAppearance(key: String) {
        appearance = key
        prefs.edit().putString("appearance", key).apply()
    }

    /** A session, Lock Study Session, daily-limit lock or filter lock keeps the Study tab in the bar. */
    val studyTabRequired: Boolean
        get() = Graph.study.let { it.active || it.lockActive || it.limitLocked || it.filterLocked }

    val studyTabShown: Boolean get() = showStudy || studyTabRequired

    fun updateShowStudy(on: Boolean) {
        showStudy = on
        prefs.edit().putBoolean("showStudy", on).apply()
    }

    fun updateCaptionLanguage(lang: String?) {
        captionLanguage = lang
        prefs.edit().putString("captionLanguage", lang).apply()
    }

    fun updateCaptionHighlight(on: Boolean) {
        captionHighlight = on
        prefs.edit().putBoolean("captionHighlight", on).apply()
    }

    fun updateShowShorts(on: Boolean) {
        showShorts = on
        prefs.edit().putBoolean("showShorts", on).apply()
    }

    fun updateCaptionFont(key: String) {
        captionFont = key
        prefs.edit().putString("captionFont", key).apply()
    }

    fun updateCaptionScale(scale: Float) {
        captionScale = scale
        prefs.edit().putFloat("captionScale", scale).apply()
    }

    fun updateCaptionColor(argb: Long) {
        captionColor = argb
        prefs.edit().putLong("captionColor", argb).apply()
    }

    /** Saves the speed menu's list (cleaned: 2 decimals, MIN..MAX, sorted, 1x always kept). */
    fun updateSpeeds(list: Collection<Float>) {
        val clean = cleanSpeeds(list)
        speeds = clean
        prefs.edit().putString("speeds", clean.joinToString(",")).apply()
    }

    fun roundSpeed(s: Float): Float = Math.round(s * 100f) / 100f

    private fun cleanSpeeds(list: Collection<Float>): List<Float> =
        (list + 1f).map(::roundSpeed).filter { it in MIN_SPEED..MAX_SPEED }.distinct().sorted()
}
