package kyklab.humphreysbus.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Matrix
import android.graphics.Typeface
import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.bold
import androidx.core.view.updatePadding
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textview.MaterialTextView
import com.otaliastudios.zoom.ZoomEngine
import kotlinx.android.synthetic.main.activity_all_bus_stop_view.*
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
import kotlin.math.abs


class BusTimeTableActivity : AppCompatActivity() {
    companion object {
        private val TAG = BusTimeTableActivity::class.java.simpleName

        private const val STATE_BUS_NAME = "state_bus_name"
        private const val STATE_HIGHLIGHT_INDEX = "state_highlight_index"

        private const val VIEW_MODE_SIMPLE = 0
        private const val VIEW_MODE_WHOLE = 1
    }

    private var stopToHighlightIndex: Int? = null
    private lateinit var bus: Bus

    private val calendar = Calendar.getInstance()
    private var currentTime = MinDateTime.getCurDateTime().apply { s = "00" }
    private val sdf by lazy { SimpleDateFormat("HHmm") }
    private var isHoliday = BusUtils.isHoliday()

    private var loadScheduleJob: Job? = null

    private var viewMode = VIEW_MODE_SIMPLE

    private lateinit var tabAdapter: SimpleViewTabAdapter
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
        setSupportActionBar(toolbar)

        val busName: String
        if (savedInstanceState != null) {
            busName = savedInstanceState[STATE_BUS_NAME] as String
            stopToHighlightIndex = savedInstanceState[STATE_HIGHLIGHT_INDEX] as Int
        } else {
            busName = intent.extras?.get("busname") as String
            stopToHighlightIndex = intent.extras?.get("highlightstopindex") as? Int
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

        lbm.registerReceiver(receiver, intentFilter)

        showCurrentTime()
        initBusTimeTable()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putString(STATE_BUS_NAME, bus.name)
        stopToHighlightIndex?.let { outState.putInt(STATE_HIGHLIGHT_INDEX, it) }
    }

    override fun onDestroy() {
        lbm.unregisterReceiver(receiver)
        super.onDestroy()
    }

    private fun setupToolbar() {
        supportActionBar?.apply {
            title = bus.name
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
        toolbar.setBackgroundColor(toolbarColor)
        window.statusBarColor = toolbarColor
        // Light icons for status bar if needed
        if (toolbarColor.isBright) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        // Apply colors to items in toolbar
        toolbarItemColor.let {
            toolbar.setTitleTextColor(it)
            cbHoliday.apply {
                buttonTintList = ColorStateList.valueOf(it)
                setTextColor(it)
            }
            tvCurrentTime.apply {
                TextViewCompat.setCompoundDrawableTintList(
                    this, ColorStateList.valueOf(it)
                )
                setTextColor(it)
            }
            ivSwitchView.imageTintList = ColorStateList.valueOf(it)
        }
    }

    private fun initSimpleModeView() {
        val instances = bus.instances.filter { it.isHoliday == isHoliday }

        // Setup tab view
        val stopNames = bus.stopPoints.map { it.name }
        tabAdapter = SimpleViewTabAdapter(
            stopNames,
            stopToHighlightIndex ?: 0
        ) { position ->
            rvSimpleViewTab.smoothScrollToPosition(position) // Without this getAdapterPosition() sometimes returns -1. Why?
            simpleAdapter.stopIndex = position
            simpleAdapter.notifyDataSetChanged()
        }
        val tabScrollOffset = 0
        rvSimpleViewTab.adapter = tabAdapter
        val tabSnapHelper = LinearSnapHelper()
        tabSnapHelper.attachToRecyclerView(rvSimpleViewTab)
        simpleViewContainer.onViewReady {
            // Set horizontal padding to put selected item in center
            val tabHorizontalOffset =
                (simpleViewContainer.measuredWidth -
                        resources.getDimension(R.dimen.timetable_simple_mode_column_width)) / 2
            rvSimpleViewTab.updatePadding(
                left = tabHorizontalOffset.toInt(),
                right = tabHorizontalOffset.toInt()
            )
            // Scroll to initial stop
            val highlight = stopToHighlightIndex ?: 0
            tabAdapter.highlight(highlight)
            rvSimpleViewTab.scrollToPosition(highlight)
        }

        val tabScrollingListener = SnapOnScrollListener(tabSnapHelper,
            SnapOnScrollListener.Behavior.NOTIFY_ON_SCROLL,
            object : SnapOnScrollListener.OnSnapPositionChangeListener {
                override fun onSnapPositionChange(position: Int) {
                    tabAdapter.highlight(position)
                    if (rvSimpleViewTab.scrollState == RecyclerView.SCROLL_STATE_IDLE) {
                        simpleAdapter.stopIndex = position
                        simpleAdapter.notifyDataSetChanged()
                    }
                }
            })
        val tabScrollDoneListener = SnapOnScrollListener(tabSnapHelper,
            SnapOnScrollListener.Behavior.NOTIFY_ON_SCROLL_DONE,
            object : SnapOnScrollListener.OnSnapPositionChangeListener {
                override fun onSnapPositionChange(position: Int) {
                    tabAdapter.highlight(position)
                    simpleAdapter.stopIndex = position
                    simpleAdapter.notifyDataSetChanged()
                }
            })
        rvSimpleViewTab.addOnScrollListener(tabScrollingListener)
        rvSimpleViewTab.addOnScrollListener(tabScrollDoneListener)

        // Setup timetable view
        val closestIndexes = ArrayList<Int>(bus.stopPoints.size)
        for (i in bus.stopPoints.indices) {
            val list = instances.map { it.stopTimes[i] }
            val index = currentTime.getNextClosestTimeIndex(list)
            closestIndexes.add(index)
        }
        simpleAdapter = SimpleViewAdapter(
            this,
            bus,
            instances,
            closestIndexes,
            stopToHighlightIndex ?: 0,
            currentTime
        )
        rvSimpleTimeTable.adapter = simpleAdapter
        val scrollPosition = closestIndexes[stopToHighlightIndex ?: 0] - 3
        // Log.e("ScrollPosition", "$scrollPosition")
        val scrollBy =
            scrollPosition * getDimension(R.dimen.timetable_simple_mode_row_height).toInt()
        // Log.e("ScrollBy", "$scrollBy")
        rvSimpleTimeTable.onViewReady { rvSimpleTimeTable.post { smoothScrollBy(0, scrollBy) } }

        val callPrevPage = {
            if (simpleAdapter.stopIndex > 0) {
                --simpleAdapter.stopIndex
                simpleAdapter.notifyDataSetChanged()
                rvSimpleViewTab.scrollToPosition(simpleAdapter.stopIndex)
                tabAdapter.highlight(simpleAdapter.stopIndex)
                Log.e("SCROLL", simpleAdapter.stopIndex.toString())
            }
        }
        val callNextPage = {
            if (simpleAdapter.stopIndex < bus.stopPoints.size - 1) {
                ++simpleAdapter.stopIndex
                simpleAdapter.notifyDataSetChanged()
                rvSimpleViewTab.scrollToPosition(simpleAdapter.stopIndex)
                tabAdapter.highlight(simpleAdapter.stopIndex)
                Log.e("SCROLL", simpleAdapter.stopIndex.toString())
            }
        }
        // Setup swipe behavior
        /*rvSimpleTimeTable.setOnTouchListener(object: OnSwipeTouchListenerJava(this){
            override fun onSwipeRight() {
                callPrevPage()
            }

            override fun onSwipeLeft() {
                callNextPage()
            }
        })*/
        /*// Add filter
        val newView = object:UntouchableView(this){
            private val gestureDetector = GestureDetector(context, GestureListener())
            override fun onTouchEvent(ev: MotionEvent?): Boolean {
                return gestureDetector.onTouchEvent(ev)
            }


        }.apply {
            val listener = OnSwipeTouchListener(this@BusTimeTableActivity, object:OnSwipeTouchListener.OnSwipeCallback{
                override fun onSwipeLeft() {

                }

                override fun onSwipeRight() {
                }
            })
            setOnTouchListener(listener)
            id = View.generateViewId()
        }
        val set = ConstraintSet()
        set.clone(simpleViewContainer)
        simpleViewContainer.addView(newView)
        set.connect(newView.id, ConstraintSet.TOP, rvSimpleTimeTable.id, ConstraintSet.TOP, 0)
        set.connect(newView.id, ConstraintSet.START, rvSimpleTimeTable.id, ConstraintSet.START, 0)
        set.connect(newView.id, ConstraintSet.BOTTOM, rvSimpleTimeTable.id, ConstraintSet.BOTTOM, 0)
        set.connect(newView.id, ConstraintSet.END, rvSimpleTimeTable.id, ConstraintSet.END, 0)
        set.applyTo(simpleViewContainer)*/
        rvSimpleTimeTable.setOnTouchListener(
            OnSwipeTouchListener(this,
                object : OnSwipeTouchListener.OnSwipeCallback {
                    override fun onSwipeLeft() {
                        callNextPage()
                    }
                    override fun onSwipeRight() {
                        callPrevPage()
                    }
                })
        )

        // Setup before, next button
        btnSimpleBefore.setOnClickListener { callPrevPage() }
        btnSimpleNext.setOnClickListener {
            callNextPage()
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        private var savedEvent: MotionEvent? = null
        init{
            Log.e("GestureDetector", "Registered")
        }
        override fun onDown(e: MotionEvent?): Boolean {
            Log.e("GestureDetector", "onDown()")
            savedEvent = e
            return false
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent?,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e2 == null) return false
            val origEvent = e1 ?: savedEvent ?: return false

            var result = false
            try {
                val diffY = e2.y - origEvent.y
                val diffX = e2.x - origEvent.x
                Log.e("SwipeDetector", "diffX: ${abs(diffX)}, diffY: ${abs(diffY)}, velocityX: $velocityX, velocityY: $velocityY")
                if (abs(diffX) > abs(diffY)) {
                    if (abs(diffX) > 100 && abs(velocityX) > 100) {
                        if (diffX > 0) {
                        } else {
                        }
                    }
                    result = true
                }/* else if (abs(diffY) > SWIPE_THRESHOLD && abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffY > 0) {
                        onSwipeCallback.onSwipeBottom()
                    } else {
                        onSwipeCallback.onSwipeTop()
                    }
                }*/
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return result
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
        val instances = bus.instances.filter { it.isHoliday == isHoliday }
        val closestIndexes = ArrayList<Int>(bus.stopPoints.size)
        for (i in bus.stopPoints.indices) {
            val list = instances.map { it.stopTimes[i] }
            val index = currentTime.getNextClosestTimeIndex(list)
            closestIndexes.add(index)
        }
        simpleAdapter.instances = instances
        simpleAdapter.currentTime = currentTime
        simpleAdapter.closestIndexes = closestIndexes
        simpleAdapter.notifyDataSetChanged()
    }


    private class SimpleViewTabAdapter(
        val items: List<String>,
        var selectedIndex: Int,
        val clickedCallback: (Int) -> Unit
    ) : RecyclerView.Adapter<SimpleViewTabAdapter.SimpleViewTabViewHolder>() {
        private var previousSelectedIndex = selectedIndex

        fun highlight(position: Int) {
            previousSelectedIndex = selectedIndex
            selectedIndex = position
            notifyItemChanged(previousSelectedIndex, false)
            notifyItemChanged(selectedIndex, true)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SimpleViewTabViewHolder {
            return SimpleViewTabViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.activity_bus_timetable_simple_mode_tab, parent, false)
            )
        }

        override fun onBindViewHolder(holder: SimpleViewTabViewHolder, position: Int) {
            holder.tvTitle.text = items[position]
            holder.tvTitle.setTypeface(
                null,
                if (position == selectedIndex) Typeface.BOLD else Typeface.NORMAL
            )
        }

        override fun onBindViewHolder(
            holder: SimpleViewTabViewHolder,
            position: Int,
            payloads: MutableList<Any>
        ) {
            if (payloads.isEmpty()) {
                super.onBindViewHolder(holder, position, payloads)
            } else {
                payloads.forEach {
                    if (it is Boolean) {
                        holder.tvTitle.setTypeface(
                            null,
                            if (it) Typeface.BOLD else Typeface.NORMAL
                        )
                    }
                }
            }
        }

        override fun getItemCount(): Int {
            return items.size
        }

        private inner class SimpleViewTabViewHolder(itemView: View) :
            RecyclerView.ViewHolder(itemView) {
            val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)

            init {
                itemView.setOnClickListener {
                    Log.e("TMP", "$adapterPosition")
                    highlight(adapterPosition)
                    clickedCallback(adapterPosition)
//                    notifyDataSetChanged()
                }
            }
        }
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
                    Array<TextView>(bus.stopPoints.size) { i ->
                        MaterialTextView(this@BusTimeTableActivity).apply {
                            text = bus.stopPoints[i].name
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

                val instances = bus.instances.filter { i -> i.isHoliday == isHoliday }

                // Get next closest bus time in highlighted column
                var nextClosestTimeIndex = -1
                stopToHighlightIndex?.let { index ->
                    val tmpList = instances.map { it.stopTimes[index] }
                    nextClosestTimeIndex = currentTime.getNextClosestTimeIndex(tmpList, true)
                }

                // Columns, which are LinearLayout, that contain list of stop time and time left
                val stopColumns = Array(bus.stopPoints.size) { column ->
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
                            val stop = bus.stopPoints[column]
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
                isHoliday = BusUtils.isHoliday(calendar)
                updateDateTime()
                updateSelectedTimeTable()
            }.show(supportFragmentManager, "dateTimePicker")
        }
    }

    /*
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        simpleViewContainer.onViewReady {
            val tabHorizontalOffset =
                (simpleViewContainer.measuredWidth -
                        resources.getDimension(R.dimen.timetable_simple_mode_tab_width)) / 2
            Log.e("TMP", simpleViewContainer.measuredWidth.toString())
            rvSimpleViewTab.updatePadding(
                left = tabHorizontalOffset.toInt(),
                right = tabHorizontalOffset.toInt()
            )
            rvSimpleViewTab.scrollToPosition(tabAdapter.selectedIndex)
        }
    }
    */

    private fun updateDateTime() {
        cbHoliday.isChecked = isHoliday
        tvCurrentTime.text = currentTime.h_m
    }
}