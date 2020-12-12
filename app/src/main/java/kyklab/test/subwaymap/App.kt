package kyklab.test.subwaymap

import android.app.Application
import android.content.Context
import java.io.File
import java.util.*

class App : Application() {
    private var defaultUncaughtExceptionHandler: Thread.UncaughtExceptionHandler? = null

    override fun onCreate() {
        super.onCreate()

        application = this
        defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(ExceptionHandler())
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

