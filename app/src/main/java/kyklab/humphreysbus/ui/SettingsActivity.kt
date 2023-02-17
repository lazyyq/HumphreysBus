package kyklab.humphreysbus.ui

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kyklab.humphreysbus.BuildConfig
import kyklab.humphreysbus.R
import kyklab.humphreysbus.databinding.ActivitySettingsBinding
import kyklab.humphreysbus.utils.Prefs
import kyklab.humphreysbus.utils.toast

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        setSupportActionBar(binding.toolbar)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }

        binding.tvVersion.apply {
            visibility = View.VISIBLE
            text =
                "${getString(R.string.app_name)} ${BuildConfig.BUILD_TYPE} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        }
    }

    class SettingsFragment : PreferenceFragmentCompat(),
        SharedPreferences.OnSharedPreferenceChangeListener {
        companion object {
            private const val FEEDBACK_URL = "https://forms.gle/ZsFS66dLMHkhdUow9"
        }

        val prefEnableStatistics by lazy {
            findPreference<Preference>(Prefs.Key.ENABLE_STATISTICS)
        }
        val prefShowAd by lazy {
            findPreference<Preference>(Prefs.Key.SHOW_AD)
        }
        val prefBeta by lazy {
            findPreference<Preference>(Prefs.Key.BETA)
        }
        val prefSpecialThanks by lazy {
            findPreference<Preference>(Prefs.Key.SPECIAL_THANKS)
        }
        val prefSendFeedback by lazy {
            findPreference<Preference>(Prefs.Key.SEND_FEEDBACK)
        }
        val prefOssLicense by lazy {
            findPreference<Preference>(Prefs.Key.OSS_LICENSE)
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            prefEnableStatistics?.setOnPreferenceChangeListener { preference, newValue ->
                if (newValue is Boolean) {
                    if (BuildConfig.DEBUG) {
                        Toast.makeText(
                            requireContext(),
                            "This preference is ignored in debug mode",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        FirebaseAnalytics.getInstance(requireContext())
                            .setAnalyticsCollectionEnabled(newValue)
                        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(newValue)
                    }
                }
                true
            }
            prefShowAd?.setOnPreferenceChangeListener { preference, newValue ->
                if (newValue is Boolean && newValue) {
                    context?.toast("♡")
                }
                true
            }
            prefBeta?.setOnPreferenceClickListener {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse("https://apps.kykint.com/humphybus")
                startActivity(intent)
                true
            }
            prefSpecialThanks?.setOnPreferenceClickListener {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse("https://github.com/OXcarXierra")
                startActivity(intent)
                true
            }
            prefSendFeedback?.setOnPreferenceClickListener {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse(FEEDBACK_URL)
                startActivity(intent)
                true
            }
            prefOssLicense?.setOnPreferenceClickListener {
                context?.startActivity(Intent(context, OssLicensesMenuActivity::class.java))
                true
            }

            // Debug
            if (BuildConfig.DEBUG) {
                val crash = Preference(requireContext()).apply {
                    title = "Crash!"
                    isIconSpaceReserved = false
                    setOnPreferenceClickListener {
                        throw RuntimeException("CRASH TEST")
                    }
                }
                preferenceScreen.addPreference(crash)
            }
        }

        override fun onResume() {
            super.onResume()
            Prefs.registerPrefChangeListener(this)
        }

        override fun onPause() {
            super.onPause()
            Prefs.unregisterPrefChangeListener(this)
        }

        override fun onDestroy() {
            super.onDestroy()
        }

        override fun onSharedPreferenceChanged(
            sharedPreferences: SharedPreferences?,
            key: String?
        ) {
        }
    }
}