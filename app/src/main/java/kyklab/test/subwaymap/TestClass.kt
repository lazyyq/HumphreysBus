package kyklab.test.subwaymap

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class TestClass : AppCompatActivity() {
    fun main() {
        Toast.makeText(this@TestClass, "", Toast.LENGTH_SHORT).show()

        var nullString: String? = null
        for (i in 1..3) {
            nullString = nullString ?: "default"
            nullString = "sdf"
        }
    }
}