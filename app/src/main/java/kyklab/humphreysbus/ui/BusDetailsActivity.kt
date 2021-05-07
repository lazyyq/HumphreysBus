package kyklab.humphreysbus.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Matrix
import android.graphics.Typeface
import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.bold
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textview.MaterialTextView
import com.otaliastudios.zoom.ZoomEngine
import kotlinx.android.synthetic.main.activity_bus_details.*
import kotlinx.android.synthetic.main.activity_bus_details.cbHoliday
import kotlinx.android.synthetic.main.activity_bus_details.progressBar
import kotlinx.android.synthetic.main.activity_bus_details.tvCurrentTime
import kotlinx.android.synthetic.main.activity_bus_details_column.view.*
import kotlinx.android.synthetic.main.fragment_stop_info_dialog.*
import kotlinx.coroutines.*
import kyklab.humphreysbus.*
import kyklab.humphreysbus.bus.Bus
import kyklab.humphreysbus.bus.BusUtils
import kyklab.humphreysbus.utils.*
import kyklab.humphreysbus.utils.MinDateTime.Companion.getNextClosestTimeIndex
import kyklab.humphreysbus.utils.MinDateTime.Companion.setCalendar
import java.util.*


class BusDetailsActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "BusViewActivity"
    }

    private var busName: String? = null
    private var stopToHighlightIndex: Int? = null
    private lateinit var busToShow: Bus

    private val calendar = Calendar.getInstance()
    private var currentTime = MinDateTime.getCurDateTime().apply {s="00"}
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

        btnTimeline.setOnClickListener {
            val timelineIntent = Intent(this, BusDetailsActivity::class.java).apply {
                putExtra("busname", busToShow.name)
            }
            startActivity(timelineIntent)
        }

        zoomLayoutTimeTable.engine.addListener(object : ZoomEngine.Listener {
            override fun onIdle(engine: ZoomEngine) {

            }

            override fun onUpdate(engine: ZoomEngine, matrix: Matrix) {
                zoomLayoutStopName.moveTo(engine.zoom, engine.panX, 0f, false)
            }
        })
        // Automatically resize header ZoomLayout height based on actual displayed layout height
        zoomLayoutStopName.engine.addListener(object : ZoomEngine.Listener {
            override fun onIdle(engine: ZoomEngine) {

            }

            override fun onUpdate(engine: ZoomEngine, matrix: Matrix) {
                val lp = zoomLayoutStopName.layoutParams
                lp.height = (stopNameContainer.height * engine.zoom).toInt()
                zoomLayoutStopName.layoutParams = lp
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

                // Line to auto scroll
                var autoScrollTotalLines = 1
                var autoScrollLine = 0
                val autoScrollOffset = 5

                val instances = busToShow.instances.filter { i -> i.isHoliday == isHoliday }

                // Get next closest bus time in highlighted column
                var nextClosestTimeIndex = -1
                stopToHighlightIndex?.let { index ->
                    val tmpList = instances.map { it.stopTimes[index] }
                    nextClosestTimeIndex = currentTime.getNextClosestTimeIndex(tmpList, true)
                }

                // Columns, which are LinearLayout, that contain list of stop time and time left
                val stopColumns = Array(busToShow.stopPoints.size) { column ->
                    LayoutInflater.from(this@BusDetailsActivity).inflate(
                        R.layout.activity_bus_details_column, timeTableContainer, false
                    ).apply {
                        val timeTextBuilder = SpannableStringBuilder()
                        val timeLeftTextBuilder = SpannableStringBuilder()

                        autoScrollTotalLines = instances.size + 1
                        for (i in instances.indices) {
                            val time = instances[i].stopTimes[column]

                            // If this is the stop to highlight, auto scroll to next closest time line
                            if (column == stopToHighlightIndex && i == nextClosestTimeIndex) {
                                autoScrollLine = i

                                var text = time.h_m + '\n'
                                timeTextBuilder.bold {
                                    append(
                                        SpannableStringBuilder(text).apply {
                                            setSpan(
                                                ForegroundColorSpan(getColor(R.color.details_column_highlighted_text)),
                                                0,
                                                text.length,
                                                SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE
                                            )
                                        })
                                }
                                text = (time - currentTime).h_m + " left" + '\n'
                                timeLeftTextBuilder.bold {
                                    append(
                                        SpannableStringBuilder(text).apply {
                                            setSpan(
                                                ForegroundColorSpan(getColor(R.color.details_column_highlighted_text)),
                                                0,
                                                text.length,
                                                SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE
                                            )
                                        })
                                }
                            } else {
                                timeTextBuilder.append(time.h_m + '\n')
                                timeLeftTextBuilder.append(
                                    (time - currentTime).h_m + " left" + '\n'
                                )
                            }
                        }
                        tvStopTimeColumn.text = timeTextBuilder
                        tvTimeLeftColumn.text = timeLeftTextBuilder

                        setOnClickListener {
                            val stop = busToShow.stopPoints[column]
                            val intent = Intent().apply {
                                putExtra("xCor", stop.xCenter.toFloat())
                                putExtra("yCor", stop.yCenter.toFloat())
                                putExtra("stopId", stop.id)
                            }
                            setResult(MainActivity.RESULT_STOP_SELECTED, intent)
                            finish()
                        }
                    }
                }
                val bgColors =
                    listOf(android.R.color.white, R.color.details_column_lighter_gray)
                launch(Dispatchers.Main) {
                    for ((i, stopColumn) in stopColumns.withIndex()) {
                        timeTableContainer.addView(stopColumn)

                        val bg =
                            if (i == stopToHighlightIndex) R.color.details_column_highlighted_bg
                            else bgColors[i % bgColors.size]
                        stopColumn.setBackgroundResource(bg)
                        stopNameContainerColumnItems[i].setBackgroundResource(bg)
                        stopColumn.onViewReady {
                            // Match column header width with column width
                            stopNameContainerColumnItems[i].layoutParams.width =
                                stopColumn.width
                            stopNameContainer.addView(stopNameContainerColumnItems[i])

                            // Auto scroll to highlighted stop
                            if (i == stopToHighlightIndex) {
                                val relativeLeft =
                                    stopColumn.parentRelativeCoordinates.left
                                val marginLeft = (screenWidth - stopColumn.width) / 2
                                val scrollX = relativeLeft - marginLeft
                                val scrollY =
                                    (stopColumn.height) *
                                            (autoScrollLine - autoScrollOffset) / autoScrollTotalLines

                                zoomLayoutTimeTable.moveTo(
                                    zoomLayoutTimeTable.zoom,
                                    -scrollX.toFloat(), -scrollY.toFloat(), false
                                )
                                stopNameContainerColumnItems[i].onViewReady {
                                    zoomLayoutStopName.moveTo(
                                        zoomLayoutStopName.zoom,
                                        -scrollX.toFloat(), 0f, false
                                    )
                                }
                            }
                        }
                    }
                    progressBar.visibility = View.INVISIBLE
                }
            }
        }
    }

    private fun showCurrentTime() {
        updateDateTime()

        cbHoliday.setOnClickListener {
            isHoliday = !isHoliday

            updateDateTime()
            updateBusTimeTable()
        }

        tvCurrentTime.setOnClickListener {
            DateTimePickerFragment(calendar) { year, month, dayOfMonth, hourOfDay, minute ->
                calendar.set(year, month, dayOfMonth, hourOfDay, minute)
                currentTime.setCalendar(calendar)
                isHoliday = calendar.time.isHoliday()
                updateDateTime()
                updateBusTimeTable()
            }.show(supportFragmentManager, "dateTimePicker")
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
//        pickerDialog?.configChanged()
    }

    private fun updateDateTime() {
        cbHoliday.isChecked = isHoliday
        tvCurrentTime.text = currentTime.h_m
    }
}