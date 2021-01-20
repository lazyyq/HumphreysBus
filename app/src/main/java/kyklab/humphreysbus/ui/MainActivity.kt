package kyklab.humphreysbus.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import kotlinx.android.synthetic.main.activity_main.*
import kyklab.humphreysbus.R
import kyklab.humphreysbus.bus.BusDBHelper
import kyklab.humphreysbus.bus.BusMap
import kyklab.humphreysbus.bus.BusMap.Companion.gMapCoordToLocalMapCoord
import kyklab.humphreysbus.bus.BusUtils
import kyklab.humphreysbus.data.BusStop
import kyklab.humphreysbus.ui.allbusstops.AllBusAndStopActivity
import kyklab.humphreysbus.ui.stopinfodialog.StopInfoDialog
import kyklab.humphreysbus.utils.AppUpdateChecker
import kyklab.humphreysbus.utils.Prefs
import kyklab.humphreysbus.utils.toast

class MainActivity : AppCompatActivity() {
    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }
    private val locationCallback by lazy {
        object : LocationCallback() {
            override fun onLocationResult(p0: LocationResult?) {
                p0 ?: return
                for (location in p0.locations)
                    Log.e(
                        "LocationCallback",
                        "longitude(x) ${location.longitude} latitude(y) ${location.latitude}"
                    )
                stopLocationUpdates()

                gMapCoordToLocalMapCoord(
                    p0.locations.last().longitude,
                    p0.locations.last().latitude
                )?.let {
                    busMap.highlight(it, animateDuration = 250L)
                } ?: run { toast("Couldn't find location") }

                hideLocationProgressBar()
                isLoadingLocation = false
            }
        }
    }

    private var fabElevation: Float? = null
    private var isLoadingLocation = false

    private lateinit var busMap: BusMap
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

        initBusMap()
        BusUtils.loadData()
        checkAppUpdate()

        setupViews()

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
                    val x = data.getFloatExtra("xCor", -1f)
                    val y = data.getFloatExtra("yCor", -1f)
                    val id = data.getIntExtra("stopId", -1)
                    if (x > 0 && y > 0 && id > 0) {
                        busMap.highlight(x, y, animateDuration = 500L)
                        // Workaround for IllegalStateException
                        Handler(Looper.getMainLooper()).post {
                            showStopInfoDialog(id)
                        }
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

    private fun initBusMap() {
        busMap = BusMap(this, lifecycleScope, ivMap) { spot ->
            if (spot is BusStop) {
                busMap.highlight(
                    spot.xCenter.toFloat(), spot.yCenter.toFloat(),
                    animateDuration = 250L,
                    animationListener = object :
                        SubsamplingScaleImageView.OnAnimationEventListener {
                        override fun onComplete() {
                            showStopInfoDialog(spot.id)
                        }

                        override fun onInterruptedByUser() {}
                        override fun onInterruptedByNewAnim() {}
                    })
            }
        }
        busMap.init()
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

    private fun setupViews() {
        // Show contents under translucent status bar
        window.decorView.systemUiVisibility =
            window.decorView.systemUiVisibility or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        fabAllBuses.setOnClickListener {
            startActivityForResult(
                Intent(this, AllBusAndStopActivity::class.java),
                REQ_CODE_SELECT_STOP
            )
        }
        fabSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }
}