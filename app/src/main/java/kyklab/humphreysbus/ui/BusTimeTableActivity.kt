package kyklab.humphreysbus.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.bold
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textview.MaterialTextView
import com.otaliastudios.zoom.ZoomEngine
import kotlinx.android.synthetic.main.activity_bus_timetable.*
import kotlinx.android.synthetic.main.activity_bus_timetable.cbHoliday
import kotlinx.android.synthetic.main.activity_bus_timetable.progressBar
import kotlinx.android.synthetic.main.activity_bus_timetable.tvCurrentTime
import kotlinx.android.synthetic.main.activity_bus_timetable_whole_mode_column.view.*
import kotlinx.android.synthetic.main.fragment_stop_info_dialog.*
import kotlinx.coroutines.*
import kyklab.humphreysbus.*
import kyklab.humphreysbus.bus.Bus
import kyklab.humphreysbus.bus.BusUtils
import kyklab.humphreysbus.utils.*
import kyklab.humphreysbus.utils.MinDateTime.Companion.getNextClosestTimeIndex
import kyklab.humphreysbus.utils.MinDateTime.Companion.setCalendar
import java.util.*


class BusTimeTableActivity : AppCompatActivity() {
    companion object {
        private val TAG = BusTimeTableActivity::class.java.simpleName

        private const val VIEW_MODE_SIMPLE = 0
        private const val VIEW_MODE_WHOLE = 1
    }

    private var busName: String? = null
    private var stopToHighlightIndex: Int? = null
    private lateinit var busToShow: Bus

    private val calendar = Calendar.getInstance()
    private var currentTime = MinDateTime.getCurDateTime().apply { s = "00" }
    private val sdf by lazy { SimpleDateFormat("HHmm") }
    private var isHoliday = isHoliday()

    private var loadScheduleJob: Job? = null

    private var viewMode = VIEW_MODE_SIMPLE

    private lateinit var simpleAdapter: SimpleViewAdapter

    private var scrolled = false

    private val intentFilter = IntentFilter(Const.Intent.ACTION_BACK_TO_MAP)
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Const.Intent.ACTION_BACK_TO_MAP) {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bus_timetable)

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

        lbm.registerReceiver(receiver, intentFilter)

        showCurrentTime()
        initBusTimeTable()
    }

    override fun onDestroy() {
        lbm.unregisterReceiver(receiver)
        super.onDestroy()
    }

    private fun initSimpleModeView() {
        val instances = busToShow.instances.filter { it.isHoliday == isHoliday }
        val closestIndexes = ArrayList<Int>(busToShow.stopPoints.size)
        for (i in busToShow.stopPoints.indices) {
            val list = instances.map { it.stopTimes[i] }
            val index = currentTime.getNextClosestTimeIndex(list)
            closestIndexes.add(index)
        }
        simpleAdapter = SimpleViewAdapter(
            this,
            busToShow,
            instances,
            closestIndexes,
            stopToHighlightIndex ?: 0,
            currentTime
        )
        rvSimpleTimeTable.adapter = simpleAdapter
        val scrollPosition = closestIndexes[stopToHighlightIndex ?: 0] - 3
        rvSimpleTimeTable.scrollToPosition(scrollPosition)
        btnSimpleBefore.setOnClickListener {
            if (simpleAdapter.stopIndex > 0) {
                --simpleAdapter.stopIndex
                simpleAdapter.notifyDataSetChanged()
            }
        }
        btnSimpleNext.setOnClickListener {
            if (simpleAdapter.stopIndex < busToShow.stopPoints.size - 1) {
                ++simpleAdapter.stopIndex
                simpleAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun initWholeModeView() {
        zoomLayoutTimeTable.engine.addListener(object : ZoomEngine.Listener {
            override fun onIdle(engine: ZoomEngine) {}
            override fun onUpdate(engine: ZoomEngine, matrix: Matrix) {
                zoomLayoutStopName.moveTo(engine.zoom, engine.panX, 0f, false)
            }
        })
        // Automatically resize header ZoomLayout height based on actual displayed layout height
        zoomLayoutStopName.engine.addListener(object : ZoomEngine.Listener {
            override fun onIdle(engine: ZoomEngine) {}
            override fun onUpdate(engine: ZoomEngine, matrix: Matrix) {
                val lp = zoomLayoutStopName.layoutParams
                lp.height = (wholeStopNameContainer.height * engine.zoom).toInt()
                zoomLayoutStopName.layoutParams = lp
            }
        })
        // Block touch for stop names to prevent scrolling
        zoomLayoutStopName.setOnTouchListener { _, _ -> true }
    }

    private fun initBusTimeTable() {
        busToShow.colorInt.let {
            ivBus.imageTintList = ColorStateList.valueOf(it)
            tvBus.text = busName
            tvBus.setTextColor(it)
        }

        ivSwitchView.setOnClickListener { switchViewMode() }

        initSimpleModeView()
        initWholeModeView()

        updateSelectedTimeTable()
    }

    private fun switchViewMode() {
        viewMode = when (viewMode) {
            VIEW_MODE_SIMPLE -> VIEW_MODE_WHOLE
            VIEW_MODE_WHOLE -> VIEW_MODE_SIMPLE
            else -> VIEW_MODE_SIMPLE
        }
        updateSelectedTimeTable()
    }

    private fun updateSelectedTimeTable() {
        if (viewMode == VIEW_MODE_SIMPLE) {
            simpleViewContainer.visibility = View.VISIBLE
            wholeViewContainer.visibility = View.GONE
            updateSimpleBusTimeTable()
        } else if (viewMode == VIEW_MODE_WHOLE) {
            simpleViewContainer.visibility = View.GONE
            wholeViewContainer.visibility = View.VISIBLE
            updateWholeBusTimeTable()
        }
    }

    private fun updateSimpleBusTimeTable() {
        val instances = busToShow.instances.filter { it.isHoliday == isHoliday }
        val closestIndexes = ArrayList<Int>(busToShow.stopPoints.size)
        for (i in busToShow.stopPoints.indices) {
            val list = instances.map { it.stopTimes[i] }
            val index = currentTime.getNextClosestTimeIndex(list)
            closestIndexes.add(index)
        }
        simpleAdapter.instances = instances
        simpleAdapter.currentTime = currentTime
        simpleAdapter.closestIndexes = closestIndexes
        simpleAdapter.notifyDataSetChanged()
    }

    private class SimpleViewAdapter(
        val context: Context,
        val bus: Bus,
        var instances: List<Bus.BusInstance>,
        var closestIndexes: List<Int>, // Index of closest bus instance for each stop
        var stopIndex: Int,
        var currentTime: MinDateTime
    ) : RecyclerView.Adapter<SimpleViewAdapter.SimpleViewHolder>() {
        private var highlightedIndex = -1
        private var previousHighlightedIndex = 0
        private val selectableItemBackground =
            context.getResId(R.attr.selectableItemBackground)

        private inner class SimpleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val itemBackground = itemView.findViewById<ViewGroup>(R.id.itemBackground)
            val tvPrev = itemView.findViewById<TextView>(R.id.tvPrev)
            val tvPrevLeft = itemView.findViewById<TextView>(R.id.tvPrevLeft)
            val tvCurrent = itemView.findViewById<TextView>(R.id.tvCurrent)
            val tvCurrentLeft = itemView.findViewById<TextView>(R.id.tvCurrentLeft)
            val tvNext = itemView.findViewById<TextView>(R.id.tvNext)
            val tvNextLeft = itemView.findViewById<TextView>(R.id.tvNextLeft)

            init {
                itemBackground.setOnClickListener {
                    if (adapterPosition == highlightedIndex) {
                        highlightedIndex = -1
                    } else {
                        highlightedIndex = adapterPosition
                    }
                    notifyItemChanged(previousHighlightedIndex)
                    notifyItemChanged(adapterPosition)
                    previousHighlightedIndex = adapterPosition
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SimpleViewHolder {
            return SimpleViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.activity_bus_timetable_simple_mode_item, parent, false)
            )
        }

        override fun onBindViewHolder(holder: SimpleViewHolder, position: Int) {
            // Set background color if selected
            if (position == highlightedIndex) {
                holder.itemBackground.setBackgroundColor(
                    context.resources.getColor(R.color.lighter_gray, context.theme)
                )
            } else {
                holder.itemBackground.setBackgroundResource(selectableItemBackground)
            }

            // Prev texts
            if (stopIndex > 0) {
                holder.tvPrev.text = instances[position].stopTimes[stopIndex - 1].h_m
                if (position == closestIndexes[stopIndex - 1]) {
                    holder.tvPrev.setTypeface(null, Typeface.BOLD)
                    holder.tvPrevLeft.text =
                        (instances[position].stopTimes[stopIndex - 1] - currentTime).h_m + " left"
                } else {
                    holder.tvPrev.setTypeface(null, Typeface.NORMAL)
                    holder.tvPrevLeft.text = ""
                }
            } else {
                holder.tvPrev.text = ""
                holder.tvPrevLeft.text = ""
            }

            // Current texts
            holder.tvCurrent.text = instances[position].stopTimes[stopIndex].h_m
            if (position == closestIndexes[stopIndex]) {
                holder.tvCurrent.setTypeface(null, Typeface.BOLD)
                holder.tvCurrentLeft.text =
                    (instances[position].stopTimes[stopIndex] - currentTime).h_m + " left"
            } else {
                holder.tvCurrent.setTypeface(null, Typeface.NORMAL)
                holder.tvCurrentLeft.text = ""
            }

            // Next texts
            if (stopIndex < bus.stopPoints.size - 1) {
                holder.tvNext.text = instances[position].stopTimes[stopIndex + 1].h_m
                if (position == closestIndexes[stopIndex + 1]) {
                    holder.tvNext.setTypeface(null, Typeface.BOLD)
                    holder.tvNextLeft.text =
                        (instances[position].stopTimes[stopIndex + 1] - currentTime).h_m + " left"
                } else {
                    holder.tvNext.setTypeface(null, Typeface.NORMAL)
                    holder.tvNextLeft.text = ""
                }
            } else {
                holder.tvNext.text = ""
                holder.tvNextLeft.text = ""
            }
        }

        override fun getItemCount(): Int {
            return instances.size
        }
    }

    private fun updateWholeBusTimeTable() {
        lifecycleScope.launch(Dispatchers.Default) {
            loadScheduleJob?.let { if (it.isActive) it.cancelAndJoin() }
            loadScheduleJob = launch {
                launch(Dispatchers.Main) {
                    progressBar.visibility = View.VISIBLE
                    wholeStopNameContainer.removeAllViews()
                    wholeTimeTableContainer.removeAllViews()
                }
                // TextViews in header that display stop names
                val stopNameContainerColumnItems =
                    Array<TextView>(busToShow.stopPoints.size) { i ->
                        MaterialTextView(this@BusTimeTableActivity).apply {
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
                    LayoutInflater.from(this@BusTimeTableActivity).inflate(
                        R.layout.activity_bus_timetable_whole_mode_column,
                        wholeTimeTableContainer,
                        false
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
                            val intents = arrayOf(
                                Intent(Const.Intent.ACTION_SHOW_ON_MAP).apply {
                                    putExtra(Const.Intent.EXTRA_STOP_ID, stop.id)
                                    putExtra(Const.Intent.EXTRA_X_COR, stop.xCenter)
                                    putExtra(Const.Intent.EXTRA_Y_COR, stop.yCenter.toFloat())
                                },
                                Intent(Const.Intent.ACTION_BACK_TO_MAP)
                            )
                            lbm.sendBroadcast(*intents)
                            finish()
                        }
                    }
                }
                val bgColors =
                    listOf(android.R.color.white, R.color.details_column_lighter_gray)
                launch(Dispatchers.Main) {
                    for ((i, stopColumn) in stopColumns.withIndex()) {
                        wholeTimeTableContainer.addView(stopColumn)

                        val bg =
                            if (i == stopToHighlightIndex) R.color.details_column_highlighted_bg
                            else bgColors[i % bgColors.size]
                        stopColumn.setBackgroundResource(bg)
                        stopNameContainerColumnItems[i].setBackgroundResource(bg)
                        stopColumn.onViewReady {
                            // Match column header width with column width
                            stopNameContainerColumnItems[i].layoutParams.width =
                                stopColumn.width
                            wholeStopNameContainer.addView(stopNameContainerColumnItems[i])

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
            updateSelectedTimeTable()
        }

        tvCurrentTime.setOnClickListener {
            DateTimePickerFragment(calendar) { year, month, dayOfMonth, hourOfDay, minute ->
                calendar.set(year, month, dayOfMonth, hourOfDay, minute)
                calendar.set(Calendar.SECOND, 0)
                currentTime.setCalendar(calendar)
                isHoliday = calendar.time.isHoliday()
                updateDateTime()
                updateSelectedTimeTable()
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