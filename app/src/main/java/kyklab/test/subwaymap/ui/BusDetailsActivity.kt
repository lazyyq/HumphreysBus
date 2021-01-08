package kyklab.test.subwaymap.ui

import android.content.res.ColorStateList
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewTreeObserver
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textview.MaterialTextView
import com.otaliastudios.zoom.ZoomEngine
import kotlinx.android.synthetic.main.activity_bus_details.*
import kotlinx.android.synthetic.main.activity_bus_details_column.view.*
import kotlinx.coroutines.*
import kyklab.test.subwaymap.*
import kyklab.test.subwaymap.bus.Bus
import kyklab.test.subwaymap.bus.BusUtils
import java.util.*


class BusDetailsActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "BusViewActivity"
    }

    private var busName: String? = null
    private var stopToHighlightIndex: Int? = null
    private lateinit var busToShow: Bus

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bus_details)

        val intent = intent

        busName = intent.extras?.get("busname") as? String
        stopToHighlightIndex = intent.extras?.get("highlightstopindex") as? Int
        when (val found = BusUtils.buses.find { b -> b.name == busName }) {
            null -> {
                toast("Bus not found")
                finish()
            }
            else -> busToShow = found
        }
        if (busToShow.instances.isEmpty()) {
            toast("No schedule for bus $busName available")
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
        lifecycleScope.launch(Dispatchers.Default) {
            busToShow.colorInt.let {
                launch(Dispatchers.Main) {
                    ivBus.imageTintList = ColorStateList.valueOf(it)
                    tvBus.text = busName
                    tvBus.setTextColor(resources.getColor(it, this@BusDetailsActivity.theme))
                }
            }

            val curTime: Int = when(val customTime = MainActivity.etCustomTime!!.text.toString()) {
                "" -> currentTimeHHmm.toInt()
                else -> customTime.toInt()
            }

            // TextViews in header that display stop names
            val stopNameContainerColumnItems =
                Array<TextView>(busToShow.stopPoints.size) { i ->
                    MaterialTextView(this@BusDetailsActivity).apply {
                        text = busToShow.stopPoints[i].name
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.MATCH_PARENT
                        )
                        gravity = Gravity.CENTER
                        setTypeface(typeface, Typeface.BOLD)
                    }
                }
            // Columns, which are LinearLayout, that contain list of stop time and time left
            val stopColumns = Array(busToShow.stopPoints.size) { stopIndex ->
                LayoutInflater.from(this@BusDetailsActivity).inflate(
                    R.layout.activity_bus_details_column, timeTableContainer, false
                ).apply {
                    val timeText = StringBuilder()
                    val timeLeftText = StringBuilder()
                    for (i in busToShow.instances.indices) {
                        val time = busToShow.instances[i].stopTimes[stopIndex]
                        timeText.append(time.insert(2, ":") + '\n')
                        timeLeftText.append(
                            minToHH_mm(
                                calcTimeLeft(curTime, time.toInt())
                            ) + " left" + '\n'
                        )
                    }
                    tvStopTimeColumn.text = timeText
                    tvTimeLeftColumn.text = timeLeftText
                }
            }
            launch(Dispatchers.Main) {
                for (i in stopColumns.indices) {
                    timeTableContainer.addView(stopColumns[i])
                    stopColumns[i].viewTreeObserver.addOnGlobalLayoutListener(object :
                        ViewTreeObserver.OnGlobalLayoutListener {
                        override fun onGlobalLayout() {
                            // Match column header width with column width
                            stopNameContainerColumnItems[i].layoutParams.width =
                                stopColumns[i].width
                            stopNameContainer.addView(stopNameContainerColumnItems[i])
                            stopColumns[i].viewTreeObserver.removeOnGlobalLayoutListener(this)
                        }
                    })
                }
            }
            /*
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
            */
            launch(Dispatchers.Main) {
                progressBar.visibility = View.INVISIBLE
            }
        }
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