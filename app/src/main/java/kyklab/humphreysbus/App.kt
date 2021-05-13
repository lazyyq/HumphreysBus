package kyklab.humphreysbus

import android.app.Application
import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kyklab.humphreysbus.utils.Prefs
import java.io.File
import java.util.*

class App : Application() {
    private var defaultUncaughtExceptionHandler: Thread.UncaughtExceptionHandler? = null

    override fun onCreate() {
        super.onCreate()

        application = this
        defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(ExceptionHandler())

        val isStatisticsEnabled = Prefs.enableStatistics
        FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(isStatisticsEnabled)
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(isStatisticsEnabled)
    }

    companion object {
        private lateinit var application: Application
        val context: Context
            get() = application.applicationContext
    }

    private inner class ExceptionHandler : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(t: Thread, e: Throwable) {
            val output: String =
                getExternalFilesDir(null).toString() + File.separator + Date() + ".txt"
            File(output).printWriter().use { e.printStackTrace(it) }

            defaultUncaughtExceptionHandler?.uncaughtException(t, e)
        }
    }
}

