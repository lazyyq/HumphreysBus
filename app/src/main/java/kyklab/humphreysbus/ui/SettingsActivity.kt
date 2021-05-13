package kyklab.humphreysbus.ui

import android.content.SharedPreferences
import android.icu.text.DateFormat
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import kotlinx.android.synthetic.main.activity_settings.*
import kyklab.humphreysbus.BuildConfig
import kyklab.humphreysbus.R
import kyklab.humphreysbus.utils.AppUpdateChecker
import kyklab.humphreysbus.utils.Prefs
import java.util.*

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setSupportActionBar(toolbar)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        supportActionBar?.apply {
            setDisplayShowHomeEnabled(true)
            setLogo(R.drawable.ic_settings)
            setDisplayUseLogoEnabled(true)
        }

        tvVersion.apply {
            visibility = View.VISIBLE
            text =
                "${getString(R.string.app_name)} ${BuildConfig.BUILD_TYPE} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        }
    }

    class SettingsFragment : PreferenceFragmentCompat(),
        SharedPreferences.OnSharedPreferenceChangeListener {
        val appUpdateChecker by lazy { AppUpdateChecker(requireActivity()) }

        val prefAutoCheckUpdateOnStartup by lazy {
            findPreference<SwitchPreferenceCompat>(Prefs.Key.AUTO_CHECK_UPDATE_ON_STARTUP)
        }
        val prefForceCheckUupdate by lazy {
            findPreference<Preference>(Prefs.Key.FORCE_CHECK_UPDATE)
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            prefForceCheckUupdate?.setOnPreferenceClickListener {
                appUpdateChecker.checkAppUpdate(true)
                true
            }

            // Debug
            if (BuildConfig.DEBUG) {
                val crash = Preference(context).apply {
                    title = "Crash!"
                    isIconSpaceReserved = true
                    setOnPreferenceClickListener {
                        throw RuntimeException("CRASH TEST")
                    }
                }
                preferenceScreen.addPreference(crash)
            }
        }

        override fun onResume() {
            super.onResume()
            updateLastUpdateCheckedTimeSummary()
            Prefs.registerPrefChangeListener(this)
        }

        override fun onPause() {
            super.onPause()
            Prefs.unregisterPrefChangeListener(this)
        }

        override fun onDestroy() {
            appUpdateChecker.unregisterDownloadReceiver()
            super.onDestroy()
        }

        override fun onSharedPreferenceChanged(
            sharedPreferences: SharedPreferences?,
            key: String?
        ) {
            if (key == Prefs.Key.LAST_UPDATE_CHECKED) {
                updateLastUpdateCheckedTimeSummary()
            }
        }

        private fun updateLastUpdateCheckedTimeSummary() {
            val cal = Calendar.getInstance()
            cal.time = Date(Prefs.lastUpdateChecked)
            val df = DateFormat.getDateTimeInstance()
            val date = Date(Prefs.lastUpdateChecked)
            val time = df.format(date)
            prefForceCheckUupdate?.summary = "Last checked at $time"
        }
    }
}