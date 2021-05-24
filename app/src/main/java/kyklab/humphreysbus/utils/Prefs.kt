package kyklab.humphreysbus.utils

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import kyklab.humphreysbus.App
import kyklab.humphreysbus.BuildConfig
import kyklab.humphreysbus.R
import kyklab.humphreysbus.utils.Prefs.Key.ENABLE_STATISTICS_DFEAULT
import kyklab.humphreysbus.utils.Prefs.Key.LAST_KNOWN_APP_VERSION_DEFAULT
import kyklab.humphreysbus.utils.Prefs.Key.SHOW_AD_DEFAULT

object Prefs {
    private val pref = PreferenceManager.getDefaultSharedPreferences(App.context)
    private val editor = pref.edit()

    init {
        editor.apply()
    }

    var lastKnownAppVersion: Int
        get() = pref.getInt(
            Key.LAST_KNOWN_APP_VERSION, LAST_KNOWN_APP_VERSION_DEFAULT
        )
        set(value) = editor.putInt(Key.LAST_KNOWN_APP_VERSION, value).apply()

    var enableStatistics: Boolean
        get() = pref.getBoolean(
            Key.ENABLE_STATISTICS, ENABLE_STATISTICS_DFEAULT
        )
        set(value) = editor.putBoolean(Key.ENABLE_STATISTICS, value).apply()

    var showAd: Boolean
        get() = pref.getBoolean(
            Key.SHOW_AD, SHOW_AD_DEFAULT
        )
        set(value) = editor.putBoolean(Key.SHOW_AD, value).apply()


    fun registerPrefChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        pref.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterPrefChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        pref.unregisterOnSharedPreferenceChangeListener(listener)
    }

    object Key {
        val LAST_KNOWN_APP_VERSION =
            App.context.resources.getString(R.string.pref_last_known_app_version)
        val LAST_KNOWN_APP_VERSION_DEFAULT = BuildConfig.VERSION_CODE

        val ENABLE_STATISTICS =
            App.context.resources.getString(R.string.pref_enable_statistics)
        val ENABLE_STATISTICS_DFEAULT =
            App.context.resources.getBoolean(R.bool.pref_enable_statistics_default)

        val SHOW_AD =
            App.context.resources.getString(R.string.pref_show_ad)
        val SHOW_AD_DEFAULT =
            App.context.resources.getBoolean(R.bool.pref_show_ad_default)

        val SEND_FEEDBACK =
            App.context.resources.getString(R.string.pref_send_feedback)

        val OSS_LICENSE =
            App.context.resources.getString(R.string.pref_oss_license)
    }
}