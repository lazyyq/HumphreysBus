package kyklab.test.subwaymap.ui

import android.content.res.ColorStateList
import android.graphics.Matrix
import android.graphics.Rect
import android.icu.text.SimpleDateFormat
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.*
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.bold
import androidx.core.text.scale
import com.google.android.material.textview.MaterialTextView
import com.otaliastudios.zoom.ZoomEngine
import kotlinx.android.synthetic.main.activity_bus_details.*
import kyklab.test.subwaymap.R
import kyklab.test.subwaymap.bus.Bus
import kyklab.test.subwaymap.bus.BusUtils
import kyklab.test.subwaymap.calcTimeLeft
import kyklab.test.subwaymap.dpToPx
import java.util.*


class BusDetailsActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "BusViewActivity"
    }

    private var busName: String? = null
    private var stopToHighlightIndex: Int? = null
    private var busToShow: Bus? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bus_details)

        val intent = intent

        busName = intent.extras?.get("busname") as? String
        stopToHighlightIndex = intent.extras?.get("highlightstopindex") as? Int

        for (b in BusUtils.buses) {
            if (b.name == busName) {
                busToShow = b
                break
            }
        }
        if (busToShow == null || busToShow!!.instances.isEmpty()) {
            Toast.makeText(this, "Bus not found", Toast.LENGTH_SHORT).show()
            finish()
        }

        zoomLayoutTimeTable.engine.addListener(object : ZoomEngine.Listener {
            override fun onIdle(engine: ZoomEngine) {

            }

            override fun onUpdate(engine: ZoomEngine, matrix: Matrix) {
                val y: Float = -(zoomLayoutStopName.engine.computeVerticalScrollRange()
                    .toFloat() - zoomLayoutStopName.engine.containerHeight) / 4 // Why 4??
                zoomLayoutStopName.moveTo(engine.zoom, engine.panX, y, false)
            }
        })
        // Block touch for stop names to prevent scrolling
        zoomLayoutStopName.setOnTouchListener { _, _ -> true }
        showBusTimeTable()
    }

    private fun showBusTimeTable() {
        Thread {
            // TODO: Generify
            var tintColor: Int? = null
            when (busName) {
                "Red" -> { // Red
                    tintColor = android.R.color.holo_red_dark
                }
                "Blue" -> {  // Blue
                    tintColor = android.R.color.holo_blue_dark
                }
                "Green" -> { // Green
                    tintColor = android.R.color.holo_green_dark
                }
            }
            tintColor?.let {
                runOnUiThread {
                    ivBus.imageTintList = ColorStateList.valueOf(resources.getColor(it, this.theme))
                    tvBus.text = busName
                    tvBus.setTextColor(resources.getColor(it, this.theme))
                }
            }

            // Get current time
            val sdfWithoutColon = SimpleDateFormat("HHmm")
            val curTime: Int
            MainActivity.etCustomTime!!.text.toString().let {
                curTime = if (it == "") sdfWithoutColon.format(
                    Date()
                ).toInt() else it.toInt()
            }

            var closestBusTimeLeft = 1440
            var closestBusTextView: TextView? = null
            var scrollY = 0
            var scrollYFinal = 0
            var tempPosition: Int = 0
            var y1: Float? = null
            var y2: Float? = null
            for (busInstance in busToShow!!.instances) {
                val textView = MaterialTextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setPadding(
                            dpToPx(this@BusDetailsActivity, 16f),
                            0,
                            dpToPx(this@BusDetailsActivity, 16f),
                            0
                        )
                    }
                    gravity = Gravity.CENTER

                    val strStops = SpannableStringBuilder()
                    for ((i, stop) in busInstance.stops.withIndex()) {
                        val stopTime = stop.stopTime.toInt()
                        val timeLeft = calcTimeLeft(curTime, stopTime)
                        if (i == stopToHighlightIndex) {
                            if (closestBusTimeLeft > timeLeft) {
                                closestBusTimeLeft = timeLeft
                                closestBusTextView = this
                                text = strStops
                                this.measure(0, 0)
                                y1 = this.measuredHeight.toFloat()
                                scrollY = strStops.lines().size
                            }
                            strStops.bold {
                                scale(1.2f) {
                                    append(
                                        "${
                                            BusUtils.getStopWithStopNo(
                                                stop.stopNo
                                            )?.stopName ?: "Unknown"
                                        } (${stop.stopTime})\n($timeLeft mins)\n\n"
                                    )
                                }
                            }
                        } else {
                            strStops.append("${BusUtils.getStopWithStopNo(stop.stopNo)?.stopName ?: "Unknown"} (${stop.stopTime})\n($timeLeft mins)\n\n")
                        }
                    }
                    text = strStops
                    this.measure(0, 0)
                    y2 = this.measuredHeight.toFloat()
                    scrollYFinal = strStops.lines().size
//                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
//                setLineSpacing(1f, 2f)
                }
                val textViewStopName = MaterialTextView(this).apply {
                    textView.measure(0, 0)
                    text = "Text"
                    layoutParams = LinearLayout.LayoutParams(
                        textView.measuredWidth,
                        LinearLayout.LayoutParams.MATCH_PARENT
                    )
                    gravity = Gravity.CENTER
                }
                runOnUiThread {
                    timeTableContainer.addView(textView)
                    stopNameContainer.addView(textViewStopName)
                }
            }
            runOnUiThread {
                progressBar.visibility = View.INVISIBLE
            }
        }.start()
    }

    fun scrollToView(scrollView: HorizontalScrollView, view: TextView) {

        // View needs a focus
        view.requestFocus()

        // Determine if scroll needs to happen
        val scrollBounds = Rect()
        scrollView.getHitRect(scrollBounds)
        if (!view.getLocalVisibleRect(scrollBounds)) {
            scrollView.smoothScrollTo(view.left, 0)
//            Handler().post(Runnable { scrollView.smoothScrollTo(view.getLeft(), 0) })
        }
    }
}