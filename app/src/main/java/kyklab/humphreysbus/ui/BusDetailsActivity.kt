package kyklab.humphreysbus.ui

import android.content.res.ColorStateList
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.Typeface
import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
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
import kyklab.humphreysbus.*
import kyklab.humphreysbus.bus.Bus
import kyklab.humphreysbus.bus.BusUtils
import kyklab.humphreysbus.ui.stopinfodialog.DatePickerFragment
import kyklab.humphreysbus.ui.stopinfodialog.TimePickerFragment
import java.util.*


class BusDetailsActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "BusViewActivity"
    }

    private var busName: String? = null
    private var stopToHighlightIndex: Int? = null
    private lateinit var busToShow: Bus

    private val calendar = Calendar.getInstance()
    private var currentTime = currentTimeHHmm
    private val sdf by lazy { SimpleDateFormat("HHmm") }
    private var isHoliday = isHoliday()

    private var loadScheduleJob: Job? = null

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

        showCurrentTime()
        showBusTimeTable()
    }

    private fun showBusTimeTable() {
        busToShow.colorInt.let {
            ivBus.imageTintList = ColorStateList.valueOf(it)
            tvBus.text = busName
            tvBus.setTextColor(it)
        }
        updateBusTimeTable()
    }

    private fun updateBusTimeTable() {
        lifecycleScope.launch(Dispatchers.Default) {
            loadScheduleJob?.let { if (it.isActive) it.cancelAndJoin() }
            loadScheduleJob = launch {
                launch(Dispatchers.Main) {
                    progressBar.visibility = View.VISIBLE
                    stopNameContainer.removeAllViews()
                    timeTableContainer.removeAllViews()
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
                        val instances = busToShow.instances.filter { i -> i.isHoliday == isHoliday }
                        for (i in instances.indices) {
                            val time = instances[i].stopTimes[stopIndex]
                            timeText.append(time.insert(2, ":") + '\n')
                            timeLeftText.append(
                                minToHH_mm(
                                    calcTimeLeft(currentTimeHHmm.toInt(), time.toInt())
                                ) + " left" + '\n'
                            )
                        }
                        tvStopTimeColumn.text = timeText
                        tvTimeLeftColumn.text = timeLeftText
                    }
                }
                launch(Dispatchers.Main) {
                    val bg = listOf(android.R.color.white, R.color.details_column_lighter_gray)
                    for (i in stopColumns.indices) {
                        timeTableContainer.addView(stopColumns[i])
                        stopColumns[i].setBackgroundResource(bg[i % bg.size])
                        stopNameContainerColumnItems[i].setBackgroundResource(bg[i % bg.size])
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
                    progressBar.visibility = View.INVISIBLE
                }
            }
        }
    }

    private fun showCurrentTime() {
        updateDateTime()

        tvHoliday.setOnClickListener {
            isHoliday = !isHoliday

            updateDateTime()
            updateBusTimeTable()
        }

        tvCurrentTime.setOnClickListener {
            DatePickerFragment(calendar) { d, year, month, dayOfMonth ->
                TimePickerFragment(calendar) { t, hourOfDay, minute ->
                    calendar.set(year, month, dayOfMonth, hourOfDay, minute)
                    currentTime = sdf.format(calendar.time)
                    isHoliday = calendar.time.isHoliday()
                    updateDateTime()
                    updateBusTimeTable()
                }.show(supportFragmentManager, "timePicker")
            }.show(supportFragmentManager, "datePicker")
        }
    }

    private fun updateDateTime() {
        tvHoliday.isSelected = isHoliday
        tvCurrentTime.text = currentTime.insert(2, ":")
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