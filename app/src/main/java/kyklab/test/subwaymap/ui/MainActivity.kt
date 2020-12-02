package kyklab.test.subwaymap.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.PointF
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.google.android.gms.location.*
import kotlinx.android.synthetic.main.activity_main.*
import kyklab.test.subwaymap.bus.BusMapManager
import kyklab.test.subwaymap.R
import kyklab.test.subwaymap.gMapCoordToLocalMapCoord

class MainActivity : AppCompatActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    companion object {
        private const val TAG = "MainActivity"

        fun toast(context: Context, text: String) {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }

        var etCustomTime: EditText? = null
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(p0: LocationResult?) {
                p0 ?: return
                for (location in p0.locations) {
                    Log.e(
                        "LocationCallback",
                        "longitude(x) ${location.longitude} latitude(y) ${location.latitude}"
                    )
                }

                stopLocationUpdates()

                val localMapCoords =
                    gMapCoordToLocalMapCoord(
                        p0.locations.last().longitude,
                        p0.locations.last().latitude
                    )
                localMapCoords?.let {
                    if (imageView.isReady) {
                        val point = PointF(
                            it[0].toFloat(),
                            it[1].toFloat()
                        )
                        imageView.setPin(point)
                        imageView.setScaleAndCenter(2f, point)
                    }
                } ?: run { toast(this@MainActivity, "Couldn't find location") }

            }
        }
        val locationRequest = LocationRequest.create()?.apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = 10000
            fastestInterval = 5000
        }

        val requestPermissionLauncher =
            registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { result ->

            }

        fabLocation.setOnClickListener { v ->
            // Permission

            /*{ isGranted: Boolean ->
                if (isGranted) {
                    // Permission is granted. Continue the action or workflow in your
                    // app.
                } else {
                    // Explain to the user that the feature is unavailable because the
                    // features requires a permission that the user has denied. At the
                    // same time, respect the user's decision. Don't link to system
                    // settings in an effort to convince the user to change their
                    // decision.
                }
            }*/

            val permissions = arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            var granted = true
            for (p in permissions) {
                if (ContextCompat.checkSelfPermission(
                        this,
                        p
                    ) == PackageManager.PERMISSION_DENIED
                ) {
                    granted = false
                    break
                }
            }
            if (!granted) {
                requestPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            } else {
                // Request location
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
                )
            }

        }


        imageView.setImage(ImageSource.asset("subway.webp"))
        imageView.setScaleAndCenter(1f, PointF(2000f, 2000f))

        val stationManager = BusMapManager
        BusMapManager.loadFromDB()

        //TODO: For Debug
//        val buses = Buses

        etCustomTime = textCustomTime

        val gestureDetector = GestureDetector(this, object :
            GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
                if (imageView.isReady) {
                    val sCoord = imageView.viewToSourceCoord(e!!.x, e.y)
                    val xCor = sCoord!!.x
                    val yCor = sCoord.y
                    Log.e(TAG, "x: $xCor, y: $yCor")

                    val station = BusMapManager.getStopFromCoord(xCor, yCor)
                        ?: return super.onSingleTapConfirmed(e)

                    val pinCoord = PointF(station.xCenter.toFloat(), station.yCenter.toFloat())
                    imageView.setPin(pinCoord)
                    val listener = object: SubsamplingScaleImageView.OnAnimationEventListener {
                        override fun onComplete() {
                            showStopInfoDialog(station.id)
                        }

                        override fun onInterruptedByUser() {
                        }

                        override fun onInterruptedByNewAnim() {
                        }

                    }
                    val animationBuilder =
                        if (imageView.scale < 1.0f)
                            imageView.animateScaleAndCenter(1f, pinCoord)
                        else
                            imageView.animateCenter(pinCoord)
                    animationBuilder?.withOnAnimationEventListener(listener)?.withDuration(250)?.start()

                    /*val times = StringBuilder(station.name)
                    station.times.forEach { t -> times.append('\n').append(t) }
                    toast(this@MainActivity, times.toString())*/
                }
                return super.onSingleTapConfirmed(e)
            }
        })
        imageView.setOnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)
            false
        }
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun showStopInfoDialog(stopId: Int) {
        StopInfoDialog().apply {
            val bundle = Bundle()
            bundle.putInt(StopInfoDialog.ARGUMENT_STOP_ID, stopId)
            arguments = bundle
            show(supportFragmentManager, this.tag)
        }
    }
}