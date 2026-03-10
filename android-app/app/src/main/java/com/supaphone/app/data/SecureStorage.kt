package com.supaphone.app.data

import android.content.Context
import android.content.SharedPreferences
import android.telephony.TelephonyManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.Locale
import java.util.UUID

/**
 * Hardware-backed secure storage using EncryptedSharedPreferences.
 * All keys are encrypted with a master key stored in the Android Keystore.
 */
object SecureStorage {

    private const val PREFS_FILE = "supaphone_secure_prefs"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_PAIRING_SECRET = "pairing_secret"
    private const val KEY_CLIENT_INSTANCE_ID = "client_instance_id"
    private const val KEY_CLIENT_AUTH_SECRET = "client_auth_secret"
    private const val KEY_THEME = "app_theme" // "dark" or "light"
    private const val KEY_DEFAULT_REGION_CODE = "default_region_code"
    private const val KEY_INITIAL_PERMISSIONS_REQUESTED = "initial_permissions_requested"
    private const val KEY_LAST_APP_OPEN_AD_SHOWN_AT = "last_app_open_ad_shown_at"
    private const val KEY_NORMAL_APP_LAUNCH_COUNT = "normal_app_launch_count"
    private const val KEY_HIDE_NOTIFICATION_CONTENT = "hide_notification_content"
    private const val KEY_ALLOW_UNOFFICIAL_WHATSAPP_PACKAGES = "allow_unofficial_whatsapp_packages"

    private fun getEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // Pairing

    fun savePairing(context: Context, deviceId: String, secret: String) {
        getEncryptedPrefs(context).edit()
            .putString(KEY_DEVICE_ID, deviceId)
            .putString(KEY_PAIRING_SECRET, secret)
            .apply()
    }

    fun getDeviceId(context: Context): String? =
        getEncryptedPrefs(context).getString(KEY_DEVICE_ID, null)

    fun getPairingSecret(context: Context): String? =
        getEncryptedPrefs(context).getString(KEY_PAIRING_SECRET, null)

    fun isPaired(context: Context): Boolean =
        !getDeviceId(context).isNullOrBlank() && !getPairingSecret(context).isNullOrBlank()

    fun clearPairing(context: Context) {
        getEncryptedPrefs(context).edit()
            .remove(KEY_DEVICE_ID)
            .remove(KEY_PAIRING_SECRET)
            .remove(KEY_CLIENT_INSTANCE_ID)
            .remove(KEY_CLIENT_AUTH_SECRET)
            .apply()
    }

    fun getOrCreateClientInstanceId(context: Context): String {
        val prefs = getEncryptedPrefs(context)
        val existing = prefs.getString(KEY_CLIENT_INSTANCE_ID, null)?.trim()
        if (!existing.isNullOrBlank()) {
            return existing
        }

        val created = "android-${UUID.randomUUID()}"
        prefs.edit().putString(KEY_CLIENT_INSTANCE_ID, created).apply()
        return created
    }

    fun getOrCreateClientAuthSecret(context: Context): String {
        val prefs = getEncryptedPrefs(context)
        val existing = prefs.getString(KEY_CLIENT_AUTH_SECRET, null)?.trim()
        if (!existing.isNullOrBlank()) {
            return existing
        }

        val created = "sec-${UUID.randomUUID()}-${UUID.randomUUID()}".replace("_", "-")
        prefs.edit().putString(KEY_CLIENT_AUTH_SECRET, created).apply()
        return created
    }

    // Region

    fun setDefaultRegionCode(context: Context, regionCode: String) {
        val normalized = regionCode.trim().uppercase(Locale.ROOT)
        if (normalized.length != 2) {
            return
        }
        getEncryptedPrefs(context).edit()
            .putString(KEY_DEFAULT_REGION_CODE, normalized)
            .apply()
    }

    fun getDefaultRegionCode(context: Context): String? {
        val value = getEncryptedPrefs(context)
            .getString(KEY_DEFAULT_REGION_CODE, null)
            ?.trim()
            ?.uppercase(Locale.ROOT)
        return if (value.isNullOrBlank() || value.length != 2) null else value
    }

    fun resolveDefaultRegionCode(context: Context): String {
        val saved = getDefaultRegionCode(context)
        if (!saved.isNullOrBlank()) {
            return saved
        }

        val detected = detectDeviceRegionCode(context)
        setDefaultRegionCode(context, detected)
        return detected
    }

    fun detectDeviceRegionCode(context: Context): String {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val simRegion = telephony
            ?.simCountryIso
            ?.trim()
            ?.uppercase(Locale.ROOT)
            .orEmpty()
        if (simRegion.length == 2) {
            return simRegion
        }

        val networkRegion = telephony
            ?.networkCountryIso
            ?.trim()
            ?.uppercase(Locale.ROOT)
            .orEmpty()
        if (networkRegion.length == 2) {
            return networkRegion
        }

        val localeRegion = context.resources.configuration.locales
            .get(0)
            .country
            .trim()
            .uppercase(Locale.ROOT)
        if (localeRegion.length == 2) {
            return localeRegion
        }

        return "US"
    }

    // Theme

    fun saveTheme(context: Context, isDark: Boolean) {
        getEncryptedPrefs(context).edit()
            .putString(KEY_THEME, if (isDark) "dark" else "light")
            .apply()
    }

    fun getTheme(context: Context): Boolean? {
        val value = getEncryptedPrefs(context).getString(KEY_THEME, null)
        return when (value) {
            "dark" -> true
            "light" -> false
            else -> null // follow system
        }
    }

    // -- Permissions --

    fun hasRequestedInitialPermissions(context: Context): Boolean =
        getEncryptedPrefs(context).getBoolean(KEY_INITIAL_PERMISSIONS_REQUESTED, false)

    fun setInitialPermissionsRequested(context: Context, requested: Boolean) {
        getEncryptedPrefs(context).edit()
            .putBoolean(KEY_INITIAL_PERMISSIONS_REQUESTED, requested)
            .apply()
    }

    fun shouldHideNotificationContent(context: Context): Boolean =
        getEncryptedPrefs(context).getBoolean(KEY_HIDE_NOTIFICATION_CONTENT, true)

    fun setHideNotificationContent(context: Context, hidden: Boolean) {
        getEncryptedPrefs(context).edit()
            .putBoolean(KEY_HIDE_NOTIFICATION_CONTENT, hidden)
            .apply()
    }

    fun allowUnofficialWhatsAppPackages(context: Context): Boolean =
        getEncryptedPrefs(context).getBoolean(KEY_ALLOW_UNOFFICIAL_WHATSAPP_PACKAGES, false)

    fun setAllowUnofficialWhatsAppPackages(context: Context, allowed: Boolean) {
        getEncryptedPrefs(context).edit()
            .putBoolean(KEY_ALLOW_UNOFFICIAL_WHATSAPP_PACKAGES, allowed)
            .apply()
    }

    fun incrementNormalAppLaunchCount(context: Context): Int {
        val prefs = getEncryptedPrefs(context)
        val updated = prefs.getInt(KEY_NORMAL_APP_LAUNCH_COUNT, 0) + 1
        prefs.edit().putInt(KEY_NORMAL_APP_LAUNCH_COUNT, updated).apply()
        return updated
    }

    fun getNormalAppLaunchCount(context: Context): Int =
        getEncryptedPrefs(context).getInt(KEY_NORMAL_APP_LAUNCH_COUNT, 0)

    fun getLastAppOpenAdShownAt(context: Context): Long =
        getEncryptedPrefs(context).getLong(KEY_LAST_APP_OPEN_AD_SHOWN_AT, 0L)

    fun setLastAppOpenAdShownAt(context: Context, timestampMs: Long) {
        getEncryptedPrefs(context).edit()
            .putLong(KEY_LAST_APP_OPEN_AD_SHOWN_AT, timestampMs)
            .apply()
    }
}
