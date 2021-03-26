package kyklab.humphreysbus.ui

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.activity_bus_details.*
import kotlinx.android.synthetic.main.activity_bus_details_column_item.view.*
import kotlinx.coroutines.Job
import kyklab.humphreysbus.R
import kyklab.humphreysbus.StartSnapHelper
import kyklab.humphreysbus.bus.Bus
import kyklab.humphreysbus.bus.BusUtils
import kyklab.humphreysbus.utils.*
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

        showCurrentTime()
        showBusTimeTable()

        val adapter = ColumnAdapter(this, busToShow, stopToHighlightIndex ?: -1,
            currentTime, isHoliday, busToShow.instances.filter { it.isHoliday == isHoliday })
        rvTimeTables.adapter = adapter
        rvTimeTables.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        LinearSnapHelper().attachToRecyclerView(rvTimeTables)

        rvTimeTables.setHasFixedSize(true)

        progressBar.hide()
    }

    private fun showBusTimeTable() {
        busToShow.colorInt.let {
            ivBus.imageTintList = ColorStateList.valueOf(it)
            tvBus.text = busName
            tvBus.setTextColor(it)
        }
        newUpdateBusTimeTable()
//        updateBusTimeTable()
    }

    private fun newUpdateBusTimeTable() {

    }

    class NotifyingLinearLayoutManager(
        context: Context,
        var listener: OnLayoutCompleteListener? = null
    ) :
        LinearLayoutManager(context) {

        override fun onLayoutCompleted(state: RecyclerView.State?) {
            super.onLayoutCompleted(state)
            listener?.onLayoutComplete()
        }

        fun interface OnLayoutCompleteListener {
            fun onLayoutComplete()
        }
    }

    private class ColumnAdapter(
        private val context: Context,
        private val bus: Bus,
        private val stopIndex: Int,
        private val currentTime: String,
        private val isHoliday: Boolean,
        private val items: List<Bus.BusInstance>
    ) :
        RecyclerView.Adapter<ColumnAdapter.ColumnViewHolder>() {

        var touchedRvTag = -1

        var rvPos = 0

        var rvY = 0

        var array = Array<RecyclerView?>(itemCount) { null }

        val scrollListener = object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    if (recyclerView.tag as? Int == touchedRvTag) {
                        (recyclerView.layoutManager as? LinearLayoutManager)?.apply {
                            rvPos = findFirstVisibleItemPosition()
                        }
                    }
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (recyclerView.tag as? Int == touchedRvTag) {
                    array.withIndex().filter { it.index != touchedRvTag }
                        .forEach { it.value?.scrollBy(0, dy) }
                    rvY += dy
                }
            }
        }

        private inner class ColumnViewHolder(itemView: View) :
            RecyclerView.ViewHolder(itemView) {
            val rvTimeTable: RecyclerView = itemView.findViewById(R.id.rvTimeTable)
            val tv: TextView = itemView.findViewById(R.id.tv)
            val snapHelper = StartSnapHelper()

            init {
                rvTimeTable.setHasFixedSize(true)
//                setIsRecyclable(false) //TODO: test or remove
            }

            fun attachHelper() {
                snapHelper.attachToRecyclerView(rvTimeTable)
            }

            fun detachHelper() {
                snapHelper.attachToRecyclerView(null)
            }

            
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColumnViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(
                R.layout.activity_bus_details_column_item,
                parent, false
            )
            val adapter = RecyclerViewAdapter(
                bus.instances.filter { it.isHoliday == isHoliday },
                ArrayList<RecyclerViewAdapter.RecyclerViewItem>(bus.stopPoints.size).apply {
                    repeat(bus.stopPoints.size) { add(RecyclerViewAdapter.RecyclerViewItem()) }
                }, this@ColumnAdapter.itemCount, -1, this.currentTime
            )

            view.rvTimeTable.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    touchedRvTag = rv.tag as Int
                    return false
                }

                override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}

                override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
            })


            view.rvTimeTable.adapter = adapter
            view.rvTimeTable.addItemDecoration(DividerItemDecoration(context, LinearLayoutManager.VERTICAL))
            view.rvTimeTable.layoutManager = LinearLayoutManager(context)
            return ColumnViewHolder(view)
        }

        override fun onBindViewHolder(holder: ColumnViewHolder, position: Int) {
            val e = Log.e("onBindViewHolder()", "binding $position")
            holder.rvTimeTable.apply {
                (adapter as? RecyclerViewAdapter)?.apply {
                    indexInViewPager = position
                    notifyDataSetChanged()
                }
                tag = position
                scrollToPosition(rvPos)
                array[position] = this
                addOnScrollListener(scrollListener)
            }
            holder.attachHelper()

            holder.tv.text = position.toString()
        }



        override fun onViewAttachedToWindow(holder: ColumnViewHolder) {
//            Log.e("onViewAttachedToWindow()", "attached ${holder.adapterPosition}")
            super.onViewAttachedToWindow(holder)
//            holder.rvTimeTable.clearOnScrollListeners()
//            holder.rvTimeTable.scrollToPosition(rvPos)

//            array[holder.adapterPosition] = holder.rvTimeTable
//            holder.rvTimeTable.addOnScrollListener(scrollListener)
        }

        override fun onViewDetachedFromWindow(holder: ColumnViewHolder) {
//            Log.e("onViewDetachedFromWindow()", "Detaching ${holder.adapterPosition}")
//            array[holder.adapterPosition] = null
//            holder.rvTimeTable.clearOnScrollListeners()
            super.onViewDetachedFromWindow(holder)
        }

        override fun onViewRecycled(holder: ColumnViewHolder) {
            val position = holder.adapterPosition
            Log.e("onViewRecycled()", "Recycled ${position}")
            array[position] = null
            holder.rvTimeTable.clearOnScrollListeners()
            holder.rvTimeTable.scrollToPosition(rvPos)
            holder.detachHelper()
            super.onViewRecycled(holder)
        }



        override fun onFailedToRecycleView(holder: ColumnViewHolder): Boolean {
            val position = holder.adapterPosition
            Log.e("onFailedToRecycleView()", "Failed to recycle ${position}")
            array[position] = null
            holder.rvTimeTable.clearOnScrollListeners()
            holder.rvTimeTable.scrollToPosition(rvPos)
            holder.detachHelper()
            return super.onFailedToRecycleView(holder)
        }

        override fun getItemCount() = bus.stopPoints.size

        /*private class RecyclerViewSynchronizer {
            val recyclerViews = LinkedList<RecyclerView>()
            var y = 0
            val onScrollChanged = object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                }

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    y += dy
                    Log.e("SYNC", "y: $y")
                    recyclerViews.forEach {
                        if (it != recyclerView) {
                            Log.e(TAG, "scroll called in listener: $y")
                            it.post {
                                it.removeOnScrollListener(this)
                                it.scrollBy(0, y)
                                it.addOnScrollListener(this)
                            }
                        }
                    }
                }
            }

            fun addRecyclerView(view: RecyclerView) {
                Log.e(TAG, "scroll called in add: $y")
                view.removeOnScrollListener(onScrollChanged)
                view.scrollBy(0,y)
                view.addOnScrollListener(onScrollChanged)
                recyclerViews.add(view)
                Log.e(TAG, "added, size ${recyclerViews.size}")
            }

            fun removeRecyclerView(view: RecyclerView) {
                recyclerViews.remove(view)
                Log.e(TAG, "removed, size ${recyclerViews.size}")
            }
        }*/
    }

    private class RecyclerViewAdapter(
        private val busInstances: List<Bus.BusInstance>,
        var items: List<RecyclerViewItem>,
        var viewPagerSize: Int,
        var indexInViewPager: Int,
        private val currentTime: String
    ) : RecyclerView.Adapter<RecyclerViewAdapter.RecyclerViewViewHolder>() {

        private class RecyclerViewViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvTime: TextView = itemView.findViewById(R.id.tvTime)
            val tvTimeLeft: TextView = itemView.findViewById(R.id.tvTimeLeft)
        }

        class RecyclerViewItem(
//            val stopIndex: Int,
            val closestTimeInListIndex: Int = 0,
        )


        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerViewViewHolder {
            return RecyclerViewViewHolder(
                LayoutInflater.from(parent.context).inflate(
                    R.layout.activity_bus_details_time_item,
                    parent, false
                )
            )
        }

        override fun onBindViewHolder(holder: RecyclerViewViewHolder, position: Int) {
//            Log.e("RecyclerViewAdapter", "onBindViewHolder ${holder.hashCode()}")

            // DEBUG
            holder.tvTime.text = position.toString()
//            holder.tvTime.text = busInstances[position].stopTimes[indexInViewPager].insert(2, ":")
            if (position == items[indexInViewPager].closestTimeInListIndex) {
                // TODO: Make tvTime bold here
                holder.tvTimeLeft.visibility = View.VISIBLE
                val timeLeft =
                    calcTimeLeft(
                        currentTime.toInt(),
                        busInstances[position].stopTimes[indexInViewPager].toInt()
                    )
                holder.tvTimeLeft.text = "(${timeLeft}mins left)"
            } else {
                holder.tvTimeLeft.visibility = View.GONE
            }
        }

        override fun getItemCount() = busInstances.size
    }

    /*private fun updateBusTimeTable() {
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

                // Columns, which are LinearLayout, that contain list of stop time and time left
                val stopColumns = Array(busToShow.stopPoints.size) { column ->
                    var closestTimeFound = false
                    LayoutInflater.from(this@BusDetailsActivity).inflate(
                        R.layout.activity_bus_details_column, timeTableContainer, false
                    ).apply {
                        val timeTextBuilder = SpannableStringBuilder()
                        val timeLeftTextBuilder = SpannableStringBuilder()
                        val instances = busToShow.instances.filter { i -> i.isHoliday == isHoliday }
                        autoScrollTotalLines = instances.size + 1
                        for (i in instances.indices) {
                            val time = instances[i].stopTimes[column]

                            // If this is the stop to highlight, find line to auto scroll to
                            if (!closestTimeFound && column == stopToHighlightIndex &&
                                isBetween(
                                    currentTime.toInt(),
                                    instances.getWithWrappedIndex(i - 1)!!.stopTimes[column].toInt(),
                                    time.toInt()
                                )
                            ) {
                                autoScrollLine = i
                                closestTimeFound = true

                                var text = time.insert(2, ":") + '\n'
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
                                text = minToHH_mm(
                                    calcTimeLeft(currentTimeHHmm.toInt(), time.toInt())
                                ) + " left" + '\n'
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
                                timeTextBuilder.append(time.insert(2, ":") + '\n')
                                timeLeftTextBuilder.append(
                                    minToHH_mm(
                                        calcTimeLeft(currentTimeHHmm.toInt(), time.toInt())
                                    ) + " left" + '\n'
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
    }*/

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
        return currTime in (_prevTime + 1)..nextTime
    }

    private fun showCurrentTime() {
        updateDateTime()

        cbHoliday.setOnClickListener {
            isHoliday = !isHoliday

            updateDateTime()
            newUpdateBusTimeTable()
//            updateBusTimeTable()
        }

        tvCurrentTime.setOnClickListener {
            DateTimePickerFragment(calendar) { year, month, dayOfMonth, hourOfDay, minute ->
                calendar.set(year, month, dayOfMonth, hourOfDay, minute)
                currentTime = sdf.format(calendar.time)
                isHoliday = calendar.time.isHoliday()
                updateDateTime()
                newUpdateBusTimeTable()
//                updateBusTimeTable()
            }.show(supportFragmentManager, "dateTimePicker")
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
//        pickerDialog?.configChanged()
    }

    private fun updateDateTime() {
        cbHoliday.isChecked = isHoliday
        tvCurrentTime.text = currentTime.insert(2, ":")
    }
}