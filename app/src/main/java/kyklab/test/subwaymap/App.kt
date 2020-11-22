package kyklab.test.subwaymap

import android.app.Application
import android.content.Context

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        application = this
    }

    companion object {
        private var application: Application? = null
        val context: Context
            get() = application!!.applicationContext
    }
}