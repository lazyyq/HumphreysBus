package kyklab.humphreysbus.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kyklab.humphreysbus.updater.AssetsManager

class SplashActivity : AppCompatActivity() {
    private val TAG = SplashActivity::class.java.simpleName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check for assets updates
        lifecycleScope.launch(Dispatchers.IO) {
            Log.e(TAG, "Starting AssetsUpdater.init()")
            with (AssetsManager()) {
                init()
                Log.e(TAG, "Starting AssetsUpdater.checkAssetsUpdates()")
                if (checkAssetsUpdates()) {
                    Log.e(TAG, "Updates available, download")
                    fetchAssetsUpdates()
                    Log.e(TAG, "Update successfully downloaded, will apply in next restart")
                } else {
                    Log.e(TAG, "Up to date")
                }
            }
            launch(Dispatchers.Main) {
                startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                finish()
            }
        }
    }
}