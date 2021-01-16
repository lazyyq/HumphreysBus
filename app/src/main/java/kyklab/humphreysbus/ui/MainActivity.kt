package kyklab.humphreysbus.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PointF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import kotlinx.android.synthetic.main.activity_main.*
import kyklab.humphreysbus.R
import kyklab.humphreysbus.bus.BusDBHelper
import kyklab.humphreysbus.bus.BusUtils
import kyklab.humphreysbus.ui.allbusstops.AllBusAndStopActivity
import kyklab.humphreysbus.ui.stopinfodialog.StopInfoDialog
import kyklab.humphreysbus.utils.AppUpdateChecker
import kyklab.humphreysbus.utils.Prefs
import kyklab.humphreysbus.utils.gMapCoordToLocalMapCoord
import kyklab.humphreysbus.utils.toast

class MainActivity : AppCompatActivity() {
    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }
    private val locationCallback by lazy {
        object : LocationCallback() {
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
                    if (ivMap.isReady) {
                        val point = PointF(
                            it[0].toFloat(),
                            it[1].toFloat()
                        )
//                        imageView.setPin(point)
                        setStopSelectionPin(point)
                        val animationBuilder =
                            if (ivMap.scale < 1.0f)
                                ivMap.animateScaleAndCenter(1f, point)
                            else
                                ivMap.animateCenter(point)
                        animationBuilder?.withDuration(250)?.start()
                    }
                } ?: run { toast("Couldn't find location") }

                hideLocationProgressBar()
                isLoadingLocation = false
            }
        }
    }
    private var selectionPin: Int? = null // Pin for current selection on bus map
    private var fabElevation: Float? = null
    private var isLoadingLocation = false

    private var currentStopInfoDialog: StopInfoDialog? = null

    private val appUpdateChecker by lazy { AppUpdateChecker(this) }

    companion object {
        private const val TAG = "MainActivity"
        const val REQ_CODE_SELECT_STOP = 1
        const val RESULT_STOP_SELECTED = 100
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayShowCustomEnabled(true)
            setDisplayShowTitleEnabled(false)
        }

        BusUtils.loadData()
        checkAppUpdate()

        ivAllBuses.setOnClickListener {
            startActivityForResult(
                Intent(this, AllBusAndStopActivity::class.java),
                REQ_CODE_SELECT_STOP
            )
        }
        ivSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
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
            if (isLoadingLocation) return@setOnClickListener
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
                isLoadingLocation = true
                showLocationProgressBar()
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
                )
            }

        }

        fabElevation = fabLocation.compatElevation

        ivMap.setImage(ImageSource.asset("subway.webp"))
        ivMap.setScaleAndCenter(1f, PointF(2000f, 2000f))

        val gestureDetector = GestureDetector(this, object :
            GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
                if (ivMap.isReady) {
                    val sCoord = ivMap.viewToSourceCoord(e!!.x, e.y)
                    val xCor = sCoord!!.x
                    val yCor = sCoord.y
                    Log.e(TAG, "x: $xCor, y: $yCor")

                    val station = BusUtils.getStopFromCoord(xCor, yCor)

                    if (station != null) {
                        val pinCoord = PointF(station.xCenter.toFloat(), station.yCenter.toFloat())
//                    imageView.setPin(pinCoord)
                        setStopSelectionPin(pinCoord)
                        val listener = object : SubsamplingScaleImageView.OnAnimationEventListener {
                            override fun onComplete() {
                                showStopInfoDialog(station.id)
                            }

                            override fun onInterruptedByUser() {
                            }

                            override fun onInterruptedByNewAnim() {
                            }

                        }
                        val animationBuilder =
                            if (ivMap.scale < 1.0f)
                                ivMap.animateScaleAndCenter(1f, pinCoord)
                            else
                                ivMap.animateCenter(pinCoord)
                        animationBuilder?.withOnAnimationEventListener(listener)?.withDuration(250)
                            ?.start()

                        /*val times = StringBuilder(station.name)
                        station.times.forEach { t -> times.append('\n').append(t) }
                        toast(this@MainActivity, times.toString())*/
                    } else {
                        resetStopSelectionPin()
                    }
                }
                return super.onSingleTapConfirmed(e)
            }
        })
        ivMap.setOnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)
            false
        }
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
        if (isLoadingLocation) {
            isLoadingLocation = false
            hideLocationProgressBar()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQ_CODE_SELECT_STOP) {
            if (resultCode == RESULT_STOP_SELECTED) {
                data?.let {
                    val coord = PointF(
                        data.getFloatExtra("xCor", -1f),
                        data.getFloatExtra("yCor", -1f)
                    )
                    val id = data.getIntExtra("stopId", -1)
                    val animationBuilder =
                        if (ivMap.scale < 1.0f)
                            ivMap.animateScaleAndCenter(1f, coord)
                        else
                            ivMap.animateCenter(coord)
                    animationBuilder?.withDuration(500)?.start()
                    setStopSelectionPin(coord)
                    // Workaround for IllegalStateException
                    Handler(Looper.getMainLooper()).post {
                        showStopInfoDialog(id)
                    }
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onDestroy() {
        BusDBHelper.close()
        appUpdateChecker.unregisterDownloadReceiver()
        super.onDestroy()
    }

    private fun setStopSelectionPin(coord: PointF) {
        resetStopSelectionPin()
        selectionPin =
            ivMap.addPin(MultiplePinView.Pin(coord, resources, R.drawable.pushpin_blue))
    }

    private fun resetStopSelectionPin() {
        selectionPin?.let { ivMap.removePin(it) }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun showLocationProgressBar() {
        fabElevation = fabLocation.compatElevation
        fabLocation.compatElevation = 0f
        Log.e(TAG, "killed elevation")
        pbLocation.visibility = View.VISIBLE
    }

    private fun hideLocationProgressBar() {
        fabElevation?.let { fabLocation.compatElevation = it }
        Log.e(TAG, "restored elevation")
        pbLocation.visibility = View.GONE
    }

    private fun showStopInfoDialog(stopId: Int) {
        currentStopInfoDialog?.dismiss()
        currentStopInfoDialog = StopInfoDialog { currentStopInfoDialog = null }.apply {
            val bundle = Bundle()
            bundle.putInt(StopInfoDialog.ARGUMENT_STOP_ID, stopId)
            arguments = bundle
            show(supportFragmentManager, this.tag)
        }
    }

    private fun checkAppUpdate() {
        if (Prefs.autoCheckUpdateOnStartup) {
            appUpdateChecker.checkAppUpdate()
        }
    }
}