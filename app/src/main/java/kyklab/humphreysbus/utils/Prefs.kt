package kyklab.humphreysbus.utils

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import kyklab.humphreysbus.App
import kyklab.humphreysbus.R
import kyklab.humphreysbus.utils.Prefs.Key.AUTO_CHECK_UPDATE_ON_STARTUP_DEFAULT
import kyklab.humphreysbus.utils.Prefs.Key.IGNORE_UPDATE_VERSION_CODE_DEFAULT
import kyklab.humphreysbus.utils.Prefs.Key.LAST_UPDATE_CHECKED_DEFAULT

object Prefs {
    private val pref = PreferenceManager.getDefaultSharedPreferences(App.context)
    private val editor = pref.edit()

    init {
        editor.apply()
    }

    var autoCheckUpdateOnStartup: Boolean
        get() = pref.getBoolean(
            Key.AUTO_CHECK_UPDATE_ON_STARTUP, AUTO_CHECK_UPDATE_ON_STARTUP_DEFAULT
        )
        set(value) = editor.putBoolean(Key.AUTO_CHECK_UPDATE_ON_STARTUP, value).apply()

    var lastUpdateChecked: Long
        get() = pref.getLong(
            Key.LAST_UPDATE_CHECKED, LAST_UPDATE_CHECKED_DEFAULT
        )
        set(value) = editor.putLong(Key.LAST_UPDATE_CHECKED, value).apply()

    var ignoreUpdateVersionCode: Int
        get() = pref.getInt(
            Key.IGNORE_UPDATE_VERSION_CODE, IGNORE_UPDATE_VERSION_CODE_DEFAULT
        )
        set(value) = editor.putInt(Key.IGNORE_UPDATE_VERSION_CODE, value).apply()


    fun registerPrefChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        pref.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterPrefChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        pref.unregisterOnSharedPreferenceChangeListener(listener)
    }

    object Key {
        val AUTO_CHECK_UPDATE_ON_STARTUP =
            App.context.resources.getString(R.string.pref_auto_check_update_on_startup)
        val AUTO_CHECK_UPDATE_ON_STARTUP_DEFAULT =
            App.context.resources.getBoolean(R.bool.pref_auto_check_update_on_startup_default)

        val FORCE_CHECK_UPDATE =
            App.context.resources.getString(R.string.pref_force_check_update)

        val LAST_UPDATE_CHECKED =
            App.context.resources.getString(R.string.pref_last_update_checked)
        val LAST_UPDATE_CHECKED_DEFAULT =
            App.context.resources.getInteger(R.integer.pref_last_update_checked_default).toLong()

        val IGNORE_UPDATE_VERSION_CODE =
            App.context.resources.getString(R.string.pref_ignore_update_version_code)
        val IGNORE_UPDATE_VERSION_CODE_DEFAULT =
            App.context.resources.getInteger(R.integer.pref_ignore_update_version_code_default)
    }
}