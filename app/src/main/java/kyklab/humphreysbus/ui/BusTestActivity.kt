package kyklab.humphreysbus.ui

import android.animation.Animator
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
import kotlinx.android.synthetic.main.activity_test.*
import kotlinx.android.synthetic.main.activity_test.view.*
import kyklab.humphreysbus.R
import kyklab.humphreysbus.bus.Bus
import kyklab.humphreysbus.bus.BusUtils
import kyklab.humphreysbus.data.BusStop
import kyklab.humphreysbus.utils.*
import kyklab.humphreysbus.utils.MinDateTime.Companion.isBetween
import kyklab.humphreysbus.utils.MinDateTime.Companion.timeInMillis
import kyklab.humphreysbus.utils.MinDateTime.Companion.timeInSecs
import java.text.SimpleDateFormat
import java.util.*

class BusTestActivity : AppCompatActivity() {
    private var itemheight = 0
    private val curTime = MinDateTime.getCurDateTime()
    private lateinit var busStatusUpdater: BusStatusUpdater
    private lateinit var recyclerView: RecyclerView
    private lateinit var bus: Bus

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)

        val busName = intent.extras?.get("busname") as? String
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
        recyclerView = rv

        busStatusUpdater = BusStatusUpdater()
        busStatusUpdater.init()
    }

    override fun onDestroy() {
        super.onDestroy()
        busStatusUpdater.stop()
    }

    val STAYINGTIME = 15

    private inner class BusStatusUpdater {
        private val animationPlayer = AnimationPlayer()
        private var topmargin = 0
        private val instancesOnTimeline = LinkedList<IconItem>()
        private lateinit var items: List<MyAdapter.MyAdapterItem>
        private lateinit var adapter: MyAdapter

        fun init() {
            itemheight = dpToPx(this@BusTestActivity, 84f)
            topmargin = itemheight / 2 - dpToPx(this@BusTestActivity, 36f) / 2

            // Sync recyclerview scroll with scrollview
            rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    sv.scrollBy(dx, dy)
                }
            })

            val emptyTime = MinDateTime()
            items = bus.stopPoints.map { MyAdapter.MyAdapterItem(it, emptyTime) }
            adapter = MyAdapter(items, bus.colorInt)
            rv.adapter = adapter

            val rvheight = itemheight * adapter.itemCount
            container.layoutParams.height = rvheight

            // Add bus icons
            bus.instances.filter { it.isHoliday == isHoliday() }.withIndex()
                .forEach { instanceTmp ->

                    val instance = instanceTmp.value

                    for (i in 0..instance.stopTimes.size - 2) {
                        val prevTime = instance.stopTimes[i]
                        val nextTime = instance.stopTimes[i + 1]
//                if (prevTime.toInt() <= curtime.toInt() && curtime.toInt() < nextTime.toInt()) {
//                if (isBetween(curtime.toInt(), prevTime.toInt(), nextTime.toInt())) {
                        if (curTime.isBetween(prevTime, nextTime, true, false)) {
                            val busicon = ImageView(this@BusTestActivity).apply {
                                layoutParams = LinearLayout.LayoutParams(
                                    dpToPx(this@BusTestActivity, 36f),
                                    dpToPx(this@BusTestActivity, 36f)
                                ).apply {
                                    topMargin = i * itemheight + topmargin
                                }
                                setImageResource(R.drawable.ic_bus)
                                imageTintList = getColorStateList(android.R.color.black)
                            }
                            val icon = IconItem(busicon, instance, i)
                            animationPlayer.addIcon(icon)
                            instancesOnTimeline.add(icon)

                            container.addView(busicon)
                            continue
                        }
                    }
                }

            animationPlayer.onEachAnimationDone = ::updateEtas
            animationPlayer.startAnimation()
        }

        fun updateEtas() {
            items.forEach { it.eta = null }
            instancesOnTimeline.removeIf {it.isDone}
            instancesOnTimeline.reversed().forEach {
                for (i in it.indexHeadingTo until it.busInstance.stopTimes.size) {
                    items[i].eta = it.busInstance.stopTimes[i]
                }
            }
            adapter.notifyDataSetChanged()
        }

        fun stop() {
            animationPlayer.stop()
        }
    }

    private inner class AnimationPlayer {
        private val timer = Timer()
        val icons: MutableList<IconItem> = LinkedList()
        var onEachAnimationDone: (() -> Unit)? = null

        fun addIcon(icon: IconItem) {
            icons.add(icon)
        }

        fun startAnimation() {
            val secUntilNextMin = (60 - SimpleDateFormat("ss").format(Date()).toInt()) % 60
            Log.e("ANIMATION", "secUntilNextMin: $secUntilNextMin")
            Log.e("ANIMATION", "launched init anim")
            icons.forEach { it.startAnimation() }
            icons.removeIf { icon -> icon.isDone } // Remove done buses
            onEachAnimationDone?.invoke()
            timer.schedule(object : TimerTask() {
                override fun run() {
                    runOnUiThread {
                        Log.e("ANIMATION", "launching scheduled anim")
                        icons.forEach { it.startAnimation() }
                        icons.removeIf { icon -> icon.isDone } // Remove done buses
                        onEachAnimationDone?.invoke()
                    }
                }
            }, secUntilNextMin * 1000L, 60000)
        }

        fun stop() {
            timer.cancel()
        }
    }

    private inner class IconItem(
        val icon: ImageView,
        val busInstance: Bus.BusInstance,
        var initialIndex: Int
    ) {
        var isDone = false
        var debugEta = MinDateTime()
var indexHeadingTo = -1

        private val animator = icon.animate().apply { interpolator = null }
        private var firstAnim = true
        private var isRunning = false

        private val animListener = object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator?) {
                isRunning = true
                val bus = BusUtils.buses.find { it.instances.contains(busInstance) }
                val past = bus!!.stopPoints[indexHeadingTo - 1].name
                val next = bus!!.stopPoints[indexHeadingTo].name
                debugEta = busInstance.stopTimes[indexHeadingTo]
                //icon.setImageBitmap(createBmp(busInstance.stopTimes[indexInStopTimes - 1].m + "~" + busInstance.stopTimes[indexInStopTimes].m + "\nSTART"))
                Log.e("ANIMATION", "Just started past $past, heading $next eta $debugEta")
            }

            override fun onAnimationEnd(animation: Animator?) {
                isRunning = false
                val bus = BusUtils.buses.find { it.instances.contains(busInstance) }
                if (indexHeadingTo > bus!!.stopPoints.size-1) {
                    Log.e("ANIMATION", "Just arrived END OF BUS")
                } else {
                    val arrived = bus!!.stopPoints[indexHeadingTo].name
                    Log.e("ANIMATION", "Just arrived $arrived")
                }

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

        fun startAnimation() {
            if (isDone || isRunning) return

            if (firstAnim) {
                indexHeadingTo = initialIndex + 1
            } else {
                ++indexHeadingTo
            }

            // Check if bus reached the end
            if (busInstance.stopTimes.size -1 < indexHeadingTo) {
                isDone = true
                return
            }

            val prevTime = busInstance.stopTimes[indexHeadingTo - 1]
            val nextTime = busInstance.stopTimes[indexHeadingTo]
            if (firstAnim) {
                val sec = Calendar.getInstance().get(Calendar.SECOND)
                if ((nextTime - curTime).timeInSecs > STAYINGTIME) {
                    val animTimeTotalMillis = (nextTime - prevTime - STAYINGTIME).timeInMillis
                    val animTimeLeftMillis = (nextTime - curTime - STAYINGTIME).timeInMillis
                    val initialOffset =
                        itemheight * (animTimeTotalMillis - animTimeLeftMillis).toFloat() / animTimeTotalMillis
                    animator.translationYBy(initialOffset).setDuration(0).setListener(null)
                        .start()
                    /*icon.layoutParams.apply {
                        if (this is LinearLayout.LayoutParams)
                            topMargin += startingPos.toInt()
                    }*/

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

    private class MyAdapter(val items: List<MyAdapterItem>, val tintColor: Int) :
        RecyclerView.Adapter<MyAdapter.MyViewHolder>() {

        class MyAdapterItem(val stop: BusStop, var eta: MinDateTime?)

        class MyViewHolder(itemView: View, tintColor: Int) : RecyclerView.ViewHolder(itemView) {
            val waypoint: ImageView = itemView.findViewById(R.id.waypoint)
            val stopname: TextView = itemView.findViewById(R.id.stopname)
            val arrivetime: TextView = itemView.findViewById(R.id.arrivetime)

            init {
                waypoint.imageTintList = ColorStateList.valueOf(tintColor)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
            return MyViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.acitivity_test_bus_stop_item, parent, false),
                tintColor
            )
        }

        override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
            holder.stopname.text = items[position].stop.name
            Log.e("BIND", (items[position].eta != null).toString())
            holder.arrivetime.text = items[position].eta?.let {"Arriving at ${it.h_m}"} ?: ""
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

        val resultImageWidth = Math.max(
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