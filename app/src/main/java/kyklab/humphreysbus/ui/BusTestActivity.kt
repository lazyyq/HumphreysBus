package kyklab.humphreysbus.ui

import android.animation.Animator
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.*
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.activity_bus_details.*
import kotlinx.android.synthetic.main.activity_test.*
import kotlinx.android.synthetic.main.activity_test.ivBus
import kotlinx.android.synthetic.main.activity_test.tvBus
import kotlinx.android.synthetic.main.activity_test.view.*
import kyklab.humphreysbus.R
import kyklab.humphreysbus.bus.Bus
import kyklab.humphreysbus.bus.BusUtils
import kyklab.humphreysbus.data.BusStop
import kyklab.humphreysbus.utils.*
import kyklab.humphreysbus.utils.MinDateTime.Companion.compare
import kyklab.humphreysbus.utils.MinDateTime.Companion.getCurDateTime
import kyklab.humphreysbus.utils.MinDateTime.Companion.isBetween
import kyklab.humphreysbus.utils.MinDateTime.Companion.timeInMillis
import kyklab.humphreysbus.utils.MinDateTime.Companion.timeInSecs
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

class BusTestActivity : AppCompatActivity() {
    private var itemheight = 0
    private var curTime = MinDateTime.getCurDateTime()
    private lateinit var busStatusUpdater: BusStatusUpdater
    private lateinit var recyclerView: RecyclerView
    private lateinit var bus: Bus
    private var stopToHighlightIndex: Int? = null
    private val rvOnScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)
            sv.scrollBy(dx, dy)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)

        val busName = intent.extras?.get("busname") as? String
        stopToHighlightIndex = intent.extras?.get("highlightstopindex") as? Int
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

        ivBus.imageTintList = ColorStateList.valueOf(bus.colorInt)
        tvBus.text = busName
        tvBus.setTextColor(bus.colorInt)

        ivTimeTable.imageTintList = ColorStateList.valueOf(bus.colorInt)
        ivTimeTable.setOnClickListener {
            val intent = Intent(this, BusDetailsActivity::class.java)
            intent.putExtra("busname", busName)
            startActivity(intent)
        }

        recyclerView = rv

        busStatusUpdater = BusStatusUpdater()
        busStatusUpdater.init()
    }

    override fun onResume() {
        super.onResume()
        busStatusUpdater.start()
    }

    override fun onPause() {
        super.onPause()
        busStatusUpdater.stop()
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
            itemheight = dpToPx(this@BusTestActivity, 72f)
            topmargin = itemheight / 2 - dpToPx(this@BusTestActivity, 36f) / 2

            // Sync recyclerview scroll with scrollview
            rv.addOnScrollListener(rvOnScrollListener)

            val emptyTime = MinDateTime()
            adapterItems = bus.stopPoints.map { MyAdapter.MyAdapterItem(it, emptyTime) }
            adapter = MyAdapter(this@BusTestActivity, bus, adapterItems)
            rv.adapter = adapter

            val rvheight = itemheight * adapter.itemCount
            container.layoutParams.height = rvheight
        }

        fun addInitialBuses() {
            instances.withIndex().forEach { instanceTmp ->
                val instance = instanceTmp.value

                for (i in 0..instance.stopTimes.size - 2) {
                    val prevTime = instance.stopTimes[i]
                    val nextTime = instance.stopTimes[i + 1]
                    if (curTime.isBetween(prevTime, nextTime, true, false)) {
                        lastBusIndex = instanceTmp.index
                        val busIcon = getBusIcon(i)
                        val item = InstanceItem(busIcon, instance, i)
                        runningInstances.add(item)
                        container.addView(busIcon)
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
            }
        }

        fun scheduleNextBus() {
            val nextIndex = if (lastBusIndex != null) {
                (lastBusIndex!! + 1) % instances.size
            } else {
                closestFirstBusIndex
            }
            val nextBus = instances[nextIndex]
            val icon = getBusIcon(0)/*.apply { visibility = View.INVISIBLE}*/
            val nextBusItem =
                InstanceItem(icon, nextBus, 0).apply { isReady = false }
            runningInstances.add(nextBusItem)
//                container.addView(icon)
        }

        fun getBusIcon(index: Int): ImageView {
            return ImageView(this@BusTestActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    dpToPx(this@BusTestActivity, 36f),
                    dpToPx(this@BusTestActivity, 36f)
                ).apply {
                    topMargin = index * itemheight + topmargin
                }
                setImageResource(R.drawable.ic_bus)
                imageTintList = getColorStateList(android.R.color.black)
            }
        }

        fun start() {
            // Add bus icons
            instances = bus.instances.filter { it.isHoliday == isHoliday() }
            cleanup()
            addInitialBuses()
            scheduleNextBus()

            val secUntilNextMin = (60 - SimpleDateFormat("ss").format(Date()).toInt()) % 60
            Log.e("ANIMATION", "secUntilNextMin: $secUntilNextMin")
            Log.e("ANIMATION", "launched init anim")
            updateInstanceStatus()

            // Scroll to highlighted stop
            if (!scrolled && stopToHighlightIndex != null) {
                val scrollOffset = (stopToHighlightIndex!! - 3) * itemheight
                recyclerView.onViewReady {
                    clearOnScrollListeners()
                    scrollBy(0, scrollOffset)
                    addOnScrollListener(rvOnScrollListener)
                }
                sv.onViewReady { scrollBy(0, scrollOffset) }
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

            timer = Timer()
            timer!!.schedule(object : TimerTask() {
                override fun run() {
                    runOnUiThread {
                        Log.e("ANIMATION", "launching scheduled anim")
//                        instances.removeIf { it.isDone }
                        updateInstanceStatus()
                    }
                }
            }, secUntilNextMin * 1000L, 60000)
        }

        fun cleanup() {
            runningInstances.clear()
            container.removeAllViews()
            lastBusIndex = null
            lastScannedLeftTime = null
            closestFirstBusIndex = -1
        }

        fun updateInstanceStatus() {
            curTime = getCurDateTime()

            if (runningInstances.first.isDone) {
                runningInstances.removeAt(0)
                container.removeViewAt(0)
            }
            // Check if it is time for the scheduled bus to run
            runningInstances.last.apply {
                if (!isReady) {
                    if (busInstance.stopTimes[0]
                            .compare(MinDateTime.getCurDateTime(), false) == 0
                    ) {
                        isReady = true
                        container.addView(icon)
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
                override fun onAnimationStart(animation: Animator?) {
                    isRunning = true

                    // DEBUG
                    val bus = BusUtils.buses.find { it.instances.contains(busInstance) }
                    val past = bus!!.stopPoints[indexHeadingTo - 1].name
                    val next = bus!!.stopPoints[indexHeadingTo].name
                    debugEta = busInstance.stopTimes[indexHeadingTo]
                    //icon.setImageBitmap(createBmp(busInstance.stopTimes[indexInStopTimes - 1].m + "~" + busInstance.stopTimes[indexInStopTimes].m + "\nSTART"))
                    Log.e("ANIMATION", "Just started past $past, heading $next eta $debugEta")
                    // DEBUG
                }

                override fun onAnimationEnd(animation: Animator?) {
                    isRunning = false
                    // Check if bus reached the end
                    isDone = busInstance.stopTimes.size - 1 < indexHeadingTo

                    // DEBUG
                    val bus = BusUtils.buses.find { it.instances.contains(busInstance) }
                    if (indexHeadingTo > bus!!.stopPoints.size - 1) {
                        Log.e("ANIMATION", "Just arrived END OF BUS")
                    } else {
                        val arrived = bus!!.stopPoints[indexHeadingTo].name
                        Log.e("ANIMATION", "Just arrived $arrived")
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

                override fun onAnimationCancel(animation: Animator?) {}
                override fun onAnimationRepeat(animation: Animator?) {}
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
                        Log.e(
                            "sdf",
                            "next: $nextTime, prev: $prevTime, cur: $curTime, initialOffset: $initialOffset"
                        )
                        animator.translationYBy(initialOffset).setDuration(0).setListener(null)
                            .start()

                        animator.translationYBy(itemheight - initialOffset)
                            .setDuration(getRealAnimDuration(animTimeLeftMillis.toLong()))
                            .setListener(animListener).start()
                        Log.e(
                            "ICONITEM",
                            "first anim, eta $debugEta, prevTimeHHmmss ${prevTime.hms} nextTimeHHmmss ${nextTime.hms} duration ${animTimeLeftMillis / 1000}"
                        )
                    } else {
                        animator.translationYBy(itemheight.toFloat())
                            .setDuration(0).setListener(null).start()
                    }
                    firstAnim = false
                } else {
                    val animTimeTotalMillis = (nextTime - prevTime - STAYINGTIME).timeInMillis
                    animator.translationYBy(itemheight.toFloat())
                        .setDuration(getRealAnimDuration(animTimeTotalMillis.toLong()))
                        .setListener(animListener).start()
                    Log.e(
                        "ICONITEM",
                        "eta $debugEta, prevTimeHHmmss ${prevTime.hms} nextTimeHHmmss ${nextTime.hms} animation duration ${animTimeTotalMillis / 1000}"
                    )
                }
            }
        }
    }

    private class MyAdapter(
        val context: Context,
        val bus: Bus,
        val items: List<MyAdapterItem>,
    ) :
        RecyclerView.Adapter<MyAdapter.MyViewHolder>() {

        class MyAdapterItem(
            val stop: BusStop,
            var eta: MinDateTime?,
            var isHighlighted: Boolean = false
        )

        class MyViewHolder(context: Context, itemView: View, busName: String, tintColor: Int) :
            RecyclerView.ViewHolder(itemView) {
            val waypoint: ImageView = itemView.findViewById(R.id.waypoint)
            val itemBackground: ViewGroup = itemView.findViewById(R.id.itemBackground)
            val stopname: TextView = itemView.findViewById(R.id.stopname)
            val arrivetime: TextView = itemView.findViewById(R.id.arrivetime)

            init {
                waypoint.imageTintList = ColorStateList.valueOf(tintColor)

//                itemView.findViewById<ImageView>(R.id.ivShowOnMap).setOnClickListener { }
                itemView.findViewById<ImageView>(R.id.ivShowOnTimeTable).setOnClickListener {
                    val intent = Intent(context, BusDetailsActivity::class.java)
                    intent.putExtra("busname", busName)
                    intent.putExtra("highlightstopindex", adapterPosition)
                    context.startActivity(intent)
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
            return MyViewHolder(
                context,
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.acitivity_test_bus_stop_item, parent, false),
                bus.name,
                bus.colorInt
            )
        }

        override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
            val item = items[position]

            holder.stopname.text = item.stop.name
            holder.arrivetime.text = item.eta?.let { "Arriving at ${it.h_m}" } ?: ""
            holder.waypoint.setImageResource(
                when (position) {
                    0 -> R.drawable.waypoint_start
                    itemCount - 1 -> R.drawable.waypoint_end
                    else -> R.drawable.waypoint_mid
                }
            )

            if (item.isHighlighted) {
                holder.itemBackground.setBackgroundColor(
                    context.resources.getColor(R.color.lighter_gray, context.theme)
                )
                holder.stopname.setTypeface(null, Typeface.BOLD)
                holder.arrivetime.setTypeface(null, Typeface.BOLD)
            } else {
                holder.itemBackground.setBackgroundColor(context.getResId(R.attr.backgroundColor))
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

    private fun Number.dpToPx(): Int {
        return dpToPx(this@BusTestActivity, this.toFloat())
    }

    private fun createBmp(text: String): Bitmap {
        val resId = R.drawable.ic_bus

        val innerImageWidth = 32.dpToPx()
        val innerImageHeight = 32.dpToPx()
        val innerImage = AppCompatResources.getDrawable(this, resId)!!
            .apply { setTint(Color.DKGRAY) }
            .toBitmap()
            .scale(innerImageWidth, innerImageHeight)

        val innerTextSize = 16.dpToPx().toFloat()
        val textBorderSize = 4.dpToPx().toFloat()
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(61, 61, 61)
            textSize = innerTextSize
            typeface = Typeface.DEFAULT_BOLD
        }
        val textStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = innerTextSize
            strokeWidth = textBorderSize
            style = Paint.Style.STROKE
            typeface = Typeface.DEFAULT_BOLD
        }

        val textBounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, textBounds)

        val textWidth = textBounds.width()
        val textHeight = textBounds.height()

        val innerImageMargin = 2.dpToPx()
        val resultImageMargin = 2.dpToPx()

        val resultImageWidth = max(
            textBounds.width(),
            innerImageWidth + innerImageMargin * 2
        ) + resultImageMargin * 2
        val resultImageHeight =
            innerImageHeight + (innerImageMargin + textHeight + resultImageMargin) * 2


        val backgroundBitmap =
            Bitmap.createBitmap(resultImageWidth, resultImageHeight, Bitmap.Config.ARGB_8888)

        val resultBitmap = Bitmap.createBitmap(
            backgroundBitmap.width, backgroundBitmap.height,
            backgroundBitmap.config
        )
        val c = Canvas(resultBitmap)
        c.drawBitmap(backgroundBitmap, Matrix(), null)

        val innerImageLeft = (resultImageWidth - innerImageWidth) / 2f
        val innerImageTop = (resultImageHeight - innerImageHeight) / 2f
        c.drawBitmap(innerImage, innerImageLeft, innerImageTop, Paint())

        val textLeft = (resultImageWidth - textWidth) / 2f
        val textBottom = (resultImageHeight - resultImageMargin).toFloat()
//        c.drawText(text, textLeft, textBottom, textStrokePaint)
        c.drawText(text, textLeft, textBottom, textPaint)

        return resultBitmap
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