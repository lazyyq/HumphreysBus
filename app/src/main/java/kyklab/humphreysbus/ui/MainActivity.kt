package kyklab.humphreysbus.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kyklab.humphreysbus.BuildConfig
import kyklab.humphreysbus.Const
import kyklab.humphreysbus.R
import kyklab.humphreysbus.bus.Bus
import kyklab.humphreysbus.bus.BusMap
import kyklab.humphreysbus.bus.BusMap.Companion.gMapCoordToLocalMapCoord
import kyklab.humphreysbus.bus.BusUtils
import kyklab.humphreysbus.data.BusStop
import kyklab.humphreysbus.databinding.ActivityMainBinding
import kyklab.humphreysbus.databinding.BusDirectionsChooserItemBinding
import kyklab.humphreysbus.ui.allbusstops.AllBusAndStopActivity
import kyklab.humphreysbus.utils.*

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }
    private val locationCallback by lazy {
        object : LocationCallback() {
            override fun onLocationResult(p0: LocationResult) {
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

    private var scheduledIntent: Intent? = null
    private val intentFilter = IntentFilter(Const.Intent.ACTION_SHOW_ON_MAP)
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Const.Intent.ACTION_SHOW_ON_MAP) {
                scheduledIntent = intent
            }
        }
    }

    private var isLoadingLocation = false

    private lateinit var busMap: BusMap
    private var currentStopInfoDialog: StopInfoDialog? = null

    private var adView: AdView? = null

    companion object {
        private const val TAG = "MainActivity"
        const val REQ_CODE_SELECT_STOP = 1
        const val RESULT_STOP_SELECTED = 100
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        initBusMap()
        if (!BusUtils.isLoadDone) {
            BusUtils.loadData(this)
        }

        setupViews()

        val locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = 10000
            fastestInterval = 5000
        }

        val requestPermissionLauncher =
            registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { result ->

            }

        binding.quickCard.btnLocation.setOnClickListener { v ->
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
        lbm.registerReceiver(receiver, intentFilter)

        if (Prefs.showAd) {
            loadAd()
        }
    }

    override fun onResume() {
        super.onResume()
        if (scheduledIntent != null) {
            val intent = scheduledIntent!!
            scheduledIntent = null
            val x = intent.extras?.get(Const.Intent.EXTRA_X_COR)
            val y = intent.extras?.get(Const.Intent.EXTRA_Y_COR)
            if (x == null || y == null || x !is Number || y !is Number) return

            Log.e("RECEIVER", "received x:$x, y:$y")

            busMap.highlight(x.toFloat(), y.toFloat(), animateDuration = 500L)

            val stopId = intent.extras?.getInt(Const.Intent.EXTRA_STOP_ID)
            stopId?.let { Handler(mainLooper).post { showStopInfoDialog(it) } }
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

    override fun onDestroy() {
        if (!BusUtils.isLoadDone) {
            BusUtils.cancelLoad(this)
        }
        lbm.unregisterReceiver(receiver)
        super.onDestroy()
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun showLocationProgressBar() {
        binding.quickCard.btnLocation.visibility = View.GONE
        binding.quickCard.pbLocation.visibility = View.VISIBLE
    }

    private fun hideLocationProgressBar() {
        binding.quickCard.btnLocation.visibility = View.VISIBLE
        binding.quickCard.pbLocation.visibility = View.GONE
    }

    private fun initBusMap() {
        busMap = BusMap(this, lifecycleScope, binding.ivMap) { spot ->
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

    private val busDirectionsChooserItems by lazy {
        BusUtils.buses.map {
            BusDirectionChooserAdapter.AdapterItem(it, false)
        }
    }

    private val busDirectionsChooserAdapter by lazy {
        BusDirectionChooserAdapter(busDirectionsChooserItems) { bus, checked, item ->
            if (checked) {
                busMap.showBusRoute(bus)
            } else {
                busMap.hideBusRoute(bus)
            }
        }
    }

    private fun setupViews() {
        // Show contents under translucent status bar
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }

        binding.quickCard.btnAllBuses.setOnClickListener {
            startActivityForResult(
                Intent(this, AllBusAndStopActivity::class.java),
                REQ_CODE_SELECT_STOP
            )
        }
        binding.quickCard.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Add status bar height to quick button card top margin
        binding.quickCard.cardLayout.apply {
            val lp = layoutParams
            if (lp is ViewGroup.MarginLayoutParams) {
                lp.topMargin += statusBarHeight
            }
            layoutParams = lp
        }

        moveViewOnDrag(binding.quickCard.btnHandle, binding.quickCard.cardLayout)
        // TODO: Uncomment and fix this once direction chooser is back
        /*
        lifecycleScope.launch(Dispatchers.Default) {
            BusUtils.onLoadDone {
                launch(Dispatchers.Main) {
                    binding.quickCard.rvBusDirectionChooser.adapter = busDirectionsChooserAdapter
                    binding.quickCard.rvBusDirectionChooser.layoutManager = LinearLayoutManager(this@MainActivity)
                }
            }
        }

        btnBusDirections.setOnClickListener {
            busDirectionsChooserLayout.apply {
                if (visibility == View.VISIBLE) {
                    visibility = View.GONE
                } else {
                    visibility = View.VISIBLE
                }
            }
        }

        // Collapse on click outside of directions chooser
        busDirectionsChooserLayout.setOnClickListener {
            busDirectionsChooserLayout.visibility = View.GONE
        }

        binding.btnCloseDirectionChooser.setOnClickListener {
            binding.busDirectionsChooserLayout.visibility = View.GONE
        }

        binding.busDirectionsChooserLayout.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    binding.busDirectionsChooserLayout.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    binding.attachViewOnLeft(quickCard, cardBusDirectionChooser)
                }
            })
        */
    }

    private fun loadAd() {
        lifecycleScope.launch(Dispatchers.Default) {
            adView = AdView(this@MainActivity).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = getString(
                    if (BuildConfig.DEBUG) R.string.banner_ad_unit_debug_id
                    else R.string.banner_ad_unit_id
                )
            }
            MobileAds.initialize(this@MainActivity)
            val adRequest = AdRequest.Builder().build()
            launch(Dispatchers.Main) {
                binding.adContainer.addView(adView)
                binding.adContainer.visibility = View.VISIBLE
                adView!!.loadAd(adRequest)
            }
        }
    }

    private fun hideAd() {
        binding.adContainer.visibility = View.GONE
    }

    // TODO: Test ViewHolder migration once bus direction chooser is back
    private class BusDirectionChooserAdapter(
        val items: List<AdapterItem>,
        val onBusChosen: (Bus, Boolean, AdapterItem) -> Unit
    ) :
        RecyclerView.Adapter<BusDirectionChooserAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = BusDirectionsChooserItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.bind(item)
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(private val itemBinding: BusDirectionsChooserItemBinding) :
            RecyclerView.ViewHolder(itemBinding.root) {
            init {
                itemBinding.cbBus.setOnClickListener {
                    val checked = itemBinding.cbBus.isChecked
                    val item = items[adapterPosition]
                    item.checked = checked
                    onBusChosen(item.bus, checked, item)
                }
            }

            fun bind(item: BusDirectionChooserAdapter.AdapterItem) {
                itemBinding.cbBus.apply {
                    isChecked = item.checked
                    TextViewCompat.setCompoundDrawableTintList(
                        this, ColorStateList.valueOf(item.bus.colorInt)
                    )
                    text = item.bus.name
                    setTextColor(item.bus.colorInt)
                }
            }
        }

        class AdapterItem(val bus: Bus, var checked: Boolean, var map: MultiplePinView? = null)
    }
}