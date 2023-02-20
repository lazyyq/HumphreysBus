package kyklab.humphreysbus.ui

import android.animation.Animator
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.*
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import kyklab.humphreysbus.Const
import kyklab.humphreysbus.R
import kyklab.humphreysbus.bus.Bus
import kyklab.humphreysbus.bus.BusUtils
import kyklab.humphreysbus.data.BusStop
import kyklab.humphreysbus.databinding.ActivityBusTrackBinding
import kyklab.humphreysbus.utils.*
import kyklab.humphreysbus.utils.MinDateTime.Companion.compare
import kyklab.humphreysbus.utils.MinDateTime.Companion.getCurDateTime
import kyklab.humphreysbus.utils.MinDateTime.Companion.isBetween
import kyklab.humphreysbus.utils.MinDateTime.Companion.timeInMillis
import kyklab.humphreysbus.utils.MinDateTime.Companion.timeInSecs
import java.text.SimpleDateFormat
import java.util.*

class BusTrackActivity : AppCompatActivity() {
    companion object {
        private val TAG = BusTrackActivity::class.java.simpleName

        private const val STATE_BUS_NAME = "state_bus_name"
        private const val STATE_HIGHLIGHT_INDEX = "state_highlight_index"
    }

    private lateinit var binding: ActivityBusTrackBinding

    private var itemheight = 0
    private lateinit var curTime: MinDateTime
    private lateinit var busStatusUpdater: BusStatusUpdater
    private lateinit var recyclerView: RecyclerView
    private lateinit var bus: Bus
    private var stopToHighlightIndex: Int? = null
    private val rvOnScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)
            binding.sv.scrollBy(dx, dy)
        }
    }

    private val intentFilter = IntentFilter(Const.Intent.ACTION_BACK_TO_MAP)
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Const.Intent.ACTION_BACK_TO_MAP) {
                finish()
            }
        }
    }

    // https://stackoverflow.com/a/58384788
    private val busIconBitmap by lazy {
        val bitmapOrig = BitmapFactory.decodeResource(resources, R.drawable.bus_track_bus_icon)
        val bitmapCopy = Bitmap.createBitmap(
            bitmapOrig.width,
            bitmapOrig.height,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmapCopy)
        val paint = Paint()
        val mode = PorterDuff.Mode.LIGHTEN
        paint.colorFilter = PorterDuffColorFilter(bus.colorInt.darken(0.25f), mode)

        val maskPaint = Paint()
        maskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_ATOP)

        canvas.drawBitmap(bitmapOrig, 0f, 0f, paint)
        canvas.drawBitmap(bitmapOrig, 0f, 0f, maskPaint)
        bitmapCopy
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityBusTrackBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        setSupportActionBar(binding.toolbar)

        val busName: String?
        if (savedInstanceState != null) {
            busName = savedInstanceState.getString(STATE_BUS_NAME, null)
            stopToHighlightIndex = savedInstanceState.getInt(STATE_HIGHLIGHT_INDEX, -1)
        } else {
            busName = intent.extras?.getString("busname", null)
            stopToHighlightIndex = intent.extras?.getInt("highlightstopindex", -1)
        }
        if (stopToHighlightIndex == -1) {
            stopToHighlightIndex = null
        }

        when (val found = BusUtils.buses.find { b -> b.name == busName }) {
            null -> {
                toast("Bus not found")
                finish()
            }
            else -> bus = found
        }
        if (bus.instances.isEmpty()) {
            toast("No schedule for bus $busName available")
            finish()
        }

        setupToolbar()

        recyclerView = binding.rv

        busStatusUpdater = BusStatusUpdater()
        busStatusUpdater.init()

        lbm.registerReceiver(receiver, intentFilter)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putString(STATE_BUS_NAME, bus.name)
        stopToHighlightIndex?.let { outState.putInt(STATE_HIGHLIGHT_INDEX, it) }
    }

    override fun onResume() {
        // Log.e(TAG, "onResume() called")
        super.onResume()
        busStatusUpdater.start()
    }

    override fun onPause() {
        // Log.e(TAG, "onPause() called")
        super.onPause()
        busStatusUpdater.stop()
    }

    override fun onDestroy() {
        lbm.unregisterReceiver(receiver)
        super.onDestroy()
    }

    private fun setupToolbar() {
        supportActionBar?.apply {
            title = bus.name ?: return
        }
        // Get color for toolbar background and items on it
        val toolbarColor = bus.colorInt.darken(0.25f)
        val toolbarItemColor =
            getLegibleColorOnBackground(
                toolbarColor,
                getResId(R.attr.colorOnSurface),
                getResId(R.attr.colorSurface)
            )

        // Set toolbar background and status bar color
        binding.toolbar.setBackgroundColor(toolbarColor)
        window.statusBarColor = toolbarColor
        // Light icons for status bar if needed
        if (toolbarColor.isBright) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        // Apply colors to items in toolbar
        toolbarItemColor.let {
            binding.toolbar.setTitleTextColor(it)

            binding.ivTimeTable.imageTintList = ColorStateList.valueOf(it)
        }

        // Setup click listeners for items in toolbar
        binding.ivTimeTable.setOnClickListener {
            val intent = Intent(this, BusTimeTableActivity::class.java)
            intent.putExtra("busname", bus.name)
            startActivity(intent)
        }
    }

    val STAYINGTIME = 15

    private inner class BusStatusUpdater {
        private var timer: Timer? = null
        private var topmargin = 0
        private lateinit var instances: List<Bus.BusInstance>
        private val runningInstances = LinkedList<InstanceItem>()
        private lateinit var adapterItems: List<MyAdapter.MyAdapterItem>
        private lateinit var adapter: MyAdapter
        private var lastBusIndex: Int? = null
        private var lastScannedLeftTime: MinDateTime? = null
        private var closestFirstBusIndex = -1
        private var scrolled = false

        fun init() {
            itemheight = dpToPx(this@BusTrackActivity, 72f)
            topmargin = itemheight / 2 - dpToPx(this@BusTrackActivity, 32f) / 2

            // Sync recyclerview scroll with scrollview
            binding.rv.addOnScrollListener(rvOnScrollListener)

            adapterItems = bus.stopPoints.map { MyAdapter.MyAdapterItem(it) }
            adapter = MyAdapter(this@BusTrackActivity, bus, adapterItems)
            binding.rv.adapter = adapter

            val rvheight = itemheight * adapter.itemCount
            binding.container.layoutParams.height = rvheight
        }

        fun addInitialBuses() {
            instances.withIndex().forEach { instanceTmp ->
                val instance = instanceTmp.value

                for (i in 0..instance.stopTimes.size - 2) {
                    val prevTime = instance.stopTimes[i]
                    val nextTime = instance.stopTimes[i + 1]
                    if (curTime.isBetween(prevTime, nextTime, true, false)) {
                        lastBusIndex = instanceTmp.index
                        val busIcon = getBusIconView(i)
                        val item = InstanceItem(busIcon, instance, i)
                        runningInstances.add(item)
                        binding.container.addView(busIcon)
                        // Log.e(TAG, "container.addView() initial called")
                        continue
                    }
                }

                if (lastBusIndex == null) {
                    if (lastScannedLeftTime == null) {
                        lastScannedLeftTime = instance.stopTimes[0] - curTime
                        closestFirstBusIndex = instanceTmp.index
                    } else {
                        if (lastScannedLeftTime!!.compare(
                                instance.stopTimes[0] - curTime,
                                false
                            ) > 0
                        ) {
                            closestFirstBusIndex = instanceTmp.index
                        }
                    }
                }

                // TODO: REMOVEME after debugging is done
                if (closestFirstBusIndex == -1) {
                    val map = mapOf(
                        "lastBusIndex" to lastBusIndex,
                        "lastScannedLeftTime" to lastScannedLeftTime.toString(),
                        "instance.stopTimes[0]" to instance.stopTimes[0].toString(),
                        "curTime" to curTime.toString(),
                        "instanceTmp.index" to instanceTmp.index,
                    )
                    logEvent(
                        "addInitialBuses()",
                        Bundle().apply { putString("params", map.encode()) })
                }
            }
        }

        fun scheduleNextBus() {
            val nextIndex = if (lastBusIndex != null) {
                (lastBusIndex!! + 1) % instances.size
            } else {
                closestFirstBusIndex
            }
            if (nextIndex == -1) {
                val map = mapOf(
                    "lastBusIndex" to lastBusIndex,
                    "instances.size" to instances.size,
                    "closestFirstBusIndex" to closestFirstBusIndex,
                )
                logEvent(
                    "scheduleNextBus()",
                    Bundle().apply { putString("params", map.encode()) })
                return
            }
            val nextBus = instances[nextIndex]
            val icon = getBusIconView(0)/*.apply { visibility = View.INVISIBLE}*/
            val nextBusItem =
                InstanceItem(icon, nextBus, 0).apply { isReady = false }
            runningInstances.add(nextBusItem)
//                container.addView(icon)
        }

        fun getBusIconView(index: Int): ImageView {
            return ImageView(this@BusTrackActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    dpToPx(this@BusTrackActivity, 32f),
                    dpToPx(this@BusTrackActivity, 32f)
                ).apply {
                    topMargin = index * itemheight + topmargin
                }
                setImageBitmap(busIconBitmap)
            }
        }

        fun start() {
            // Add bus icons
            cleanup()
            curTime = getCurDateTime()
            instances = bus.instances.filter { it.day == BusUtils.getDay() }

            // Scroll to highlighted stop
            if (!scrolled && stopToHighlightIndex != null) {
                val scrollOffsetCount =
                    when (resources.configuration.orientation) {
                        Configuration.ORIENTATION_LANDSCAPE -> 1
                        else -> 3 // Portrait, undefined
                    }
                val scrollOffsetPx = (stopToHighlightIndex!! - scrollOffsetCount) * itemheight
                recyclerView.onViewReady {
                    clearOnScrollListeners()
                    scrollBy(0, scrollOffsetPx)
                    addOnScrollListener(rvOnScrollListener)
                }
                binding.sv.onViewReady { scrollBy(0, scrollOffsetPx) }
                /*
                val lm = recyclerView.layoutManager
                if (lm is LinearLayoutManager) {
                    val visibleItemsOnScreen =
                        lm.findLastVisibleItemPosition() - lm.findFirstVisibleItemPosition()
                    val scrollPosition = stopToHighlightIndex!! - visibleItemsOnScreen / 2 + 1
                    recyclerView.scrollToPosition(scrollPosition)
                }
                */
                adapterItems[stopToHighlightIndex!!].isHighlighted = true
                scrolled = true
            }

            if (instances.isEmpty()) {
                return // No buses available today, do not show bus icons
            }

            addInitialBuses()
            scheduleNextBus()

            val secUntilNextMin = (60 - SimpleDateFormat("ss").format(Date()).toInt()) % 60
            // Log.e("ANIMATION", "secUntilNextMin: $secUntilNextMin")
            // Log.e("ANIMATION", "launched init anim")
            updateInstanceStatus()

            timer = Timer()
            timer!!.schedule(object : TimerTask() {
                override fun run() {
                    runOnUiThread {
                        // Log.e("ANIMATION", "launching scheduled anim")
//                        instances.removeIf { it.isDone }
                        updateInstanceStatus()
                    }
                }
            }, secUntilNextMin * 1000L, 60000)
        }

        fun cleanup() {
            runningInstances.clear()
            binding.container.removeAllViews()
            // Log.e(TAG, "container.removeAllViews() called")
            lastBusIndex = null
            lastScannedLeftTime = null
            closestFirstBusIndex = -1
        }

        fun updateInstanceStatus() {
            curTime = getCurDateTime()

            if (runningInstances.first.isDone) {
                runningInstances.removeAt(0)
                binding.container.removeViewAt(0)
            }
            // Check if it is time for the scheduled bus to run
            runningInstances.last.apply {
                if (!isReady) {
                    if (busInstance.stopTimes[0]
                            .compare(MinDateTime.getCurDateTime(), false) == 0
                    ) {
                        isReady = true
                        binding.container.addView(icon)
                        // Log.e(TAG, "container.addView() for last called")
//                        icon.visibility = View.VISIBLE
                        scheduleNextBus()
                    }
                }
            }
            runningInstances.forEach { it.depart() }
            updateEtas()
        }

        fun updateEtas() {
            adapterItems.forEach { it.eta = null }
            runningInstances.reversed().forEach {
                for (i in it.indexHeadingTo until it.busInstance.stopTimes.size) {
                    adapterItems[i].eta = it.busInstance.stopTimes[i]
                }
            }
            adapter.notifyDataSetChanged()
        }

        fun stop() {
            timer?.cancel()
            runningInstances.forEach { it.stop() }
        }

        fun restart() {
            stop()
            cleanup()
            start()
        }

        private inner class InstanceItem(
            val icon: ImageView,
            val busInstance: Bus.BusInstance,
            var initialIndex: Int
        ) {
            /**
             *  Whether the bus has departed already, or is scheduled for departure
             */
            var isReady = true
            var isDone = false
            var debugEta = MinDateTime()
            var indexHeadingTo = 0

            private val animator = icon.animate().apply { interpolator = null }
            private var firstAnim = true
            private var isRunning = false

            private val animListener = object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {
                    isRunning = true

                    // DEBUG
                    val bus = BusUtils.buses.find { it.instances.contains(busInstance) }
                    val past = bus!!.stopPoints[indexHeadingTo - 1].name
                    val next = bus.stopPoints[indexHeadingTo].name
                    debugEta = busInstance.stopTimes[indexHeadingTo]
                    //icon.setImageBitmap(createBmp(busInstance.stopTimes[indexInStopTimes - 1].m + "~" + busInstance.stopTimes[indexInStopTimes].m + "\nSTART"))
                    // Log.e("ANIMATION", "Just started past $past, heading $next eta $debugEta")
                    // DEBUG
                }

                override fun onAnimationEnd(animation: Animator) {
                    isRunning = false
                    // Check if bus reached the end
                    isDone = busInstance.stopTimes.size - 1 <= indexHeadingTo

                    // DEBUG
                    val bus = BusUtils.buses.find { it.instances.contains(busInstance) }
                    val arrived = bus!!.stopPoints[indexHeadingTo].name
                    // Log.e("ANIMATION", "Just arrived $arrived")

                    if (indexHeadingTo >= bus.stopPoints.size - 1) {
                        // Log.e("ANIMATION", "Just arrived END OF BUS")
                    }
                    // DEBUG
                    /*icon.setImageBitmap(
                        createBmp(
                            busInstance.stopTimes[indexInStopTimes - 1].m + "~" +
                                    if (indexInStopTimes >= bus!!.stopPoints.size) "ENDOFBUS" else busInstance.stopTimes[indexInStopTimes].m
                                            + "\nEND"
                        )
                    )*/
                }

                override fun onAnimationCancel(animation: Animator) {
                    animation?.removeAllListeners()
                }

                override fun onAnimationRepeat(animation: Animator) {}
            }

            fun depart() {
                if (isDone || isRunning || !isReady) return

                if (firstAnim) {
                    indexHeadingTo = initialIndex + 1
                } else {
                    ++indexHeadingTo
                }

                // Check if bus reached the end
                if (busInstance.stopTimes.size - 1 < indexHeadingTo) {
                    return
                }

                val prevTime = busInstance.stopTimes[indexHeadingTo - 1]
                val nextTime = busInstance.stopTimes[indexHeadingTo]
                if (firstAnim) {
                    if ((nextTime - curTime).timeInSecs > STAYINGTIME) {
                        val animTimeTotalMillis = (nextTime - prevTime - STAYINGTIME).timeInMillis
                        val animTimeLeftMillis = (nextTime - curTime - STAYINGTIME).timeInMillis
                        val initialOffset =
                            itemheight * (animTimeTotalMillis - animTimeLeftMillis).toFloat() / animTimeTotalMillis
                        /* Log.e(
                            "sdf",
                            "next: $nextTime, prev: $prevTime, cur: $curTime, initialOffset: $initialOffset"
                        ) */
                        animator.translationYBy(initialOffset).setDuration(0).setListener(null)
                            .start()

                        animator.translationYBy(itemheight - initialOffset)
                            .setDuration(getRealAnimDuration(animTimeLeftMillis.toLong()))
                            .setListener(animListener).start()
                        /* Log.e(
                            "ICONITEM",
                            "first anim, eta $debugEta, prevTimeHHmmss ${prevTime.hms} nextTimeHHmmss ${nextTime.hms} duration ${animTimeLeftMillis / 1000}"
                        ) */
                    } else {
                        // Bus has already arrived, so this has to share some of the codes with
                        // animation done listener!!
                        animator.translationYBy(itemheight.toFloat())
                            .setDuration(0).setListener(null).start()
                        isDone = busInstance.stopTimes.size - 1 <= indexHeadingTo
                    }
                    firstAnim = false
                } else {
                    val animTimeTotalMillis = (nextTime - prevTime - STAYINGTIME).timeInMillis
                    animator.translationYBy(itemheight.toFloat())
                        .setDuration(getRealAnimDuration(animTimeTotalMillis.toLong()))
                        .setListener(animListener).start()
                    /* Log.e(
                        "ICONITEM",
                        "eta $debugEta, prevTimeHHmmss ${prevTime.hms} nextTimeHHmmss ${nextTime.hms} animation duration ${animTimeTotalMillis / 1000}"
                    ) */
                }
            }

            fun stop() {
                animator.cancel()
            }
        }
    }

    private class MyAdapter(
        val activity: Activity,
        val bus: Bus,
        val items: List<MyAdapterItem>,
    ) : RecyclerView.Adapter<MyAdapter.MyViewHolder>() {

        private val selectableItemBackground =
            activity.getResId(R.attr.selectableItemBackground)

        class MyAdapterItem(
            val stop: BusStop,
            var eta: MinDateTime? = null,
            var isHighlighted: Boolean = false
        )

        inner class MyViewHolder(
            activity: Activity,
            itemView: View,
            busName: String,
            tintColor: Int
        ) :
            RecyclerView.ViewHolder(itemView) {
            val waypoint: ImageView = itemView.findViewById(R.id.waypoint)
            val itemBackground: ViewGroup = itemView.findViewById(R.id.itemBackground)
            val stopname: TextView = itemView.findViewById(R.id.stopname)
            val arrivetime: TextView = itemView.findViewById(R.id.arrivetime)

            init {
                waypoint.imageTintList = ColorStateList.valueOf(tintColor)

                itemBackground.setOnClickListener {
                    val stop = bus.stopPoints[adapterPosition]
                    val intents = arrayOf(
                        Intent(Const.Intent.ACTION_SHOW_ON_MAP).apply {
                            putExtra(Const.Intent.EXTRA_STOP_ID, stop.id)
                            putExtra(Const.Intent.EXTRA_X_COR, stop.xCenter)
                            putExtra(Const.Intent.EXTRA_Y_COR, stop.yCenter)
                        },
                        Intent(Const.Intent.ACTION_BACK_TO_MAP)
                    )
                    activity.lbm.sendBroadcast(*intents)
                    activity.finish()
                }
                itemView.findViewById<ImageView>(R.id.ivShowOnTimeTable).setOnClickListener {
                    val intent = Intent(activity, BusTimeTableActivity::class.java)
                    intent.putExtra("busname", busName)
                    intent.putExtra("highlightstopindex", adapterPosition)
                    activity.startActivity(intent)
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
            return MyViewHolder(
                activity,
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.activity_bus_track_stop_item, parent, false),
                bus.name,
                bus.colorInt
            )
        }

        override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
            val item = items[position]

            holder.stopname.text = item.stop.name
            holder.arrivetime.text =
                item.eta?.let { "Arriving at ${it.h_m}" } ?: "No bus available today"
            holder.waypoint.setImageResource(
                when (position) {
                    0 -> R.drawable.waypoint_start
                    itemCount - 1 -> R.drawable.waypoint_end
                    else -> R.drawable.waypoint_mid
                }
            )

            if (item.isHighlighted) {
                holder.itemBackground.setBackgroundColor(
                    activity.resources.getColor(R.color.lighter_gray, activity.theme)
                )
                holder.stopname.setTypeface(null, Typeface.BOLD)
                holder.arrivetime.setTypeface(null, Typeface.BOLD)
            } else {
                holder.itemBackground.setBackgroundResource(selectableItemBackground)
                holder.stopname.setTypeface(null, Typeface.NORMAL)
                holder.arrivetime.setTypeface(null, Typeface.NORMAL)
            }
            // Not updating properly
            // holder.stopname.setTypeface(holder.stopname.typeface,
            //     if (item.isHighlighted) Typeface.BOLD else Typeface.NORMAL)
        }

        override fun getItemCount(): Int {
            return items.size
        }
    }

    /**
     * Decide if we're between previous and next stop time
     */
    private fun isBetween(_curTime: Int, _prevTime: Int, _nextTime: Int): Boolean {
        val currTime: Int
        val nextTime: Int
        if (_nextTime < _prevTime) {
            nextTime = _nextTime + 2400
            currTime =
                if (_curTime < _prevTime) _curTime + 2400
                else _curTime
        } else {
            nextTime = _nextTime
            currTime = _curTime
        }
        return currTime in _prevTime until nextTime
    }

    /**
     * @param curTime   Current time in mmss
     * @param until     Destination time in HHmm
     */
    private fun getSecondsLeft(curTime: Int, until: Int): Int {
        val curMin = curTime / 100
        val curSec = curTime % 100
        val nextMin = until % 100

        val curSecTotal = curMin * 60 + curSec
        val nextSecTotal = nextMin * 60

        return nextSecTotal - curSecTotal
    }
}