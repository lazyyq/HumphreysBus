package kyklab.humphreysbus.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        Log.e("!!!!!!!!!!SplashActivity!!!!!!!!!!!!!!", "onDestroy() called")
        super.onDestroy()
    }
}