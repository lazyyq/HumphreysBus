package kyklab.humphreysbus.ui

import android.animation.Animator
import android.graphics.*
import android.os.Bundle
import android.os.Handler
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
import kyklab.humphreysbus.utils.MinDateTime.Companion.timeMillis
import java.text.SimpleDateFormat
import java.util.*

class BusTestActivity : AppCompatActivity() {

    var itemheight = 0
    private val timer = Timer()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)

        val bus = BusUtils.buses[0] //TODO: replace with intent

        itemheight = dpToPx(this, 84f)


        // Sync recyclerview scroll with scrollview
        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                sv.scrollBy(dx, dy)
            }
        })

        val adapter = MyAdapter(bus.stopPoints)
        rv.adapter = adapter


        val topmargin = itemheight / 2 - dpToPx(this@BusTestActivity, 48f) / 2
        val curtime = MinDateTime.getCurDateTime()

        val rvheight = itemheight * adapter.itemCount
        container.layoutParams.height = rvheight


        val tmplist = LinkedList<IconItem>()


        // Add bus icons
        bus.instances.filter{it.isHoliday== isHoliday()}.withIndex().forEach { instanceTmp ->

            val instance = instanceTmp.value

            for (i in 0..instance.stopTimes.size - 2) {
                val prevTime = instance.stopTimes[i]
                val nextTime = instance.stopTimes[i + 1]
//                if (prevTime.toInt() <= curtime.toInt() && curtime.toInt() < nextTime.toInt()) {
//                if (isBetween(curtime.toInt(), prevTime.toInt(), nextTime.toInt())) {
                if (curtime.isBetween(prevTime, nextTime, true, false)) {
                    val busicon = ImageView(this).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            dpToPx(this@BusTestActivity, 48f),
                            dpToPx(this@BusTestActivity, 48f)
                        ).apply {
                            topMargin = i * itemheight + topmargin
                        }
                        setImageResource(R.drawable.ic_bus)
                        imageTintList = getColorStateList(android.R.color.black)
                    }
                    tmplist.add(IconItem(busicon, instance, i + 1))


                    // Add animation start
                    /*
                    val totalTime = (nextTime.toInt()-prevTime.toInt())*60-stayingTime
                    val animExecTime = max(getSecondsLeft(currentTimemmss.toInt(), nextTime.toInt())-stayingTime,0)
                    val anim = ObjectAnimator.ofFloat(busicon, "translationY",
                        (animExecTime/totalTime.toFloat())*itemheight)
                    anim.setDuration(animExecTime.toLong()*1000)
                    anim.repeatCount = instance.stopTimes.size - 1 - i
                    anim.addListener(object: Animator.AnimatorListener{
                        override fun onAnimationStart(animation: Animator?) {
                        }

                        override fun onAnimationEnd(animation: Animator?) {
                        }

                        override fun onAnimationCancel(animation: Animator?) {
                        }

                        override fun onAnimationRepeat(animation: Animator?) {
                            if (animation != null) {
                                animation.pause()
                                Handler(mainLooper).postDelayed(animation::resume,
                                    stayingTime.toLong()
                                )
                            }
                        }
                    })
                    anim.start()
                    Log.e("tag", "found $i in ${instanceTmp.index}")
                    */
                    // Add animation done

                    container.addView(busicon)


                    continue
                }
            }
        }

        val secUntilNextMin = (60 - SimpleDateFormat("ss").format(Date()).toInt()) % 60
        Log.e("ANIMATION", "secUntilNextMin: $secUntilNextMin")
        Log.e("ANIMATION", "launched init anim")
        tmplist.forEach { it.startAnimation() }
        timer.schedule(object : TimerTask() {
            override fun run() {
                runOnUiThread {
                    Log.e("ANIMATION", "launching scheduled anim")
                    tmplist.forEach {
                        it.startAnimation()
                    }
                }
            }
        }, secUntilNextMin * 1000L, 60000)
    }

    override fun onDestroy() {
        super.onDestroy()
        timer.cancel()
    }

    val STAYINGTIME = 3

    /**
     * @param indexInStopTimes  index the bus instance is currently heading to
     */
    private inner class IconItem(
        val icon: ImageView,
        val busInstance: Bus.BusInstance,
        var indexInStopTimes: Int
    ) {
        var debugEta = MinDateTime()

        private val animListener = object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator?) {
                isRunning = true
                val bus = BusUtils.buses.find { it.instances.contains(busInstance) }
                val past = bus!!.stopPoints[indexInStopTimes-1].name
                debugEta = busInstance.stopTimes[indexInStopTimes]
                icon.setImageBitmap(createBmp(busInstance.stopTimes[indexInStopTimes-1].m + "~"+busInstance.stopTimes[indexInStopTimes].m))
                Log.e("ANIMATION", "Just started past $past, eta $debugEta")
            }

            override fun onAnimationEnd(animation: Animator?) {
                isRunning = false
                val bus = BusUtils.buses.find { it.instances.contains(busInstance) }
                if (indexInStopTimes >= bus!!.stopPoints.size) {
                    Log.e("ANIMATION", "Just arrived END OF BUS")
                } else {
                    val arrived = bus!!.stopPoints[indexInStopTimes].name
                    Log.e("ANIMATION", "Just arrived $arrived")
                }
            }

            override fun onAnimationCancel(animation: Animator?) {}
            override fun onAnimationRepeat(animation: Animator?) {}
        }

        private val animator = icon.animate().apply { interpolator = null }
        private var firstAnim = true
        private var isRunning = false
        private var isDone = false

        fun startAnimation() {
            if (isDone) return

            if (indexInStopTimes <= 0) {
                Log.e(javaClass.simpleName, "index must be larger than 0")
                return
            }

            // Check if bus icon reached the end
            if (busInstance.stopTimes.size <= indexInStopTimes) {
                isDone = true
                return
            }

            if (isRunning) return

            if (firstAnim) {
                val sec = Calendar.getInstance().get(Calendar.SECOND)
                if (sec < 60 - STAYINGTIME) {
                    val prevTime = busInstance.stopTimes[indexInStopTimes - 1]
                    val nextTime = busInstance.stopTimes[indexInStopTimes]
                    val currTime = MinDateTime.getCurDateTime()
                    val animTimeTotalMillis = (nextTime - prevTime - STAYINGTIME).timeMillis()
                    val animTimeLeftMillis = (nextTime - currTime - STAYINGTIME).timeMillis()
                    val startingPos =
                        itemheight * (animTimeLeftMillis.toFloat() / animTimeTotalMillis)
                    animator.translationYBy(startingPos).setDuration(0).setListener(null).start()
                    /*icon.layoutParams.apply {
                        if (this is LinearLayout.LayoutParams)
                            topMargin += startingPos.toInt()
                    }*/

                        animator.translationYBy(itemheight - startingPos)
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
                val prevTime = busInstance.stopTimes[indexInStopTimes - 1]
                val nextTime = busInstance.stopTimes[indexInStopTimes]
                val animTimeTotalMillis = (nextTime - prevTime - STAYINGTIME).timeMillis()
                animator.translationYBy(itemheight.toFloat())
                    .setDuration(getRealAnimDuration(animTimeTotalMillis.toLong()))
                    .setListener(animListener).start()
                Log.e(
                    "ICONITEM",
                    "eta $debugEta, prevTimeHHmmss ${prevTime.hms} nextTimeHHmmss ${nextTime.hms} animation duration ${animTimeTotalMillis / 1000}"
                )
            }
            ++indexInStopTimes
        }
    }

    private class MyAdapter(val items: List<BusStop>) :
        RecyclerView.Adapter<MyAdapter.MyViewHolder>() {

        class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val stopname: TextView = itemView.findViewById(R.id.stopname)
            val arrivetime: TextView = itemView.findViewById(R.id.arrivetime)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
            return MyViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.acitivity_test_bus_stop_item, parent, false)
            )
        }

        override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
            holder.stopname.text = items[position].name
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