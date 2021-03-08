package kyklab.humphreysbus.ui

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.android.synthetic.main.activity_bus_details.*
import kotlinx.android.synthetic.main.activity_bus_details_column_item.view.*
import kotlinx.coroutines.Job
import kyklab.humphreysbus.R
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

        /*zoomLayoutTimeTable.engine.addListener(object : ZoomEngine.Listener {
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
        zoomLayoutStopName.setOnTouchListener { _, _ -> true }*/

        showCurrentTime()
        showBusTimeTable()


        val adapter = ViewPagerAdapter(busToShow, stopToHighlightIndex ?: -1,
            currentTime, isHoliday, busToShow.instances.filter { it.isHoliday == isHoliday })
        vpTimeTables.adapter = adapter
        TabLayoutMediator(tabLayout, vpTimeTables) { tab, position ->
            tab.text = busToShow.stopPoints[position].name
        }.attach()
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

    private class ViewPagerAdapter(
        private val bus: Bus,
        private val stopIndex: Int,
        private val currentTime: String,
        private val isHoliday: Boolean,
        private val items: List<Bus.BusInstance>
    ) :
        RecyclerView.Adapter<ViewPagerAdapter.ViewPagerViewHolder>() {
        private class ViewPagerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val rvTimeTable: RecyclerView = itemView.findViewById(R.id.rvTimeTable)
        }

        class ViewPagerItem(
            val instance: Bus.BusInstance
        )

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewPagerViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(
                R.layout.activity_bus_details_column_item,
                parent, false
            )
            val adapter = RecyclerViewAdapter(
                bus.instances.filter { it.isHoliday == isHoliday },
                ArrayList<RecyclerViewAdapter.RecyclerViewItem>(bus.stopPoints.size).apply {
                    fill(
                        RecyclerViewAdapter.RecyclerViewItem()
                    )
                },
                -1, currentTime
            )

            view.rvTimeTable.adapter = adapter
            return ViewPagerViewHolder(
                view
            )
        }

        override fun onBindViewHolder(holder: ViewPagerViewHolder, position: Int) {
            /*holder.itemView.rvTimeTable.adapter.let {
                if (it is RecyclerViewAdapter) {
                    it.indexInViewPager = position
                    it.notifyDataSetChanged()
                }
            }*/

            (holder.itemView.rvTimeTable.adapter as? RecyclerViewAdapter)?.apply {
                indexInViewPager = position
                notifyDataSetChanged()
            }
        }

        override fun getItemCount() = bus.stopPoints.size
    }

    private class RecyclerViewAdapter(
        private val busInstances: List<Bus.BusInstance>,
        var items: List<RecyclerViewItem>,
        var indexInViewPager: Int,
        private val currentTime: String
    ) :
        RecyclerView.Adapter<RecyclerViewAdapter.RecyclerViewViewHolder>() {
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
            holder.tvTime.text = busInstances[indexInViewPager].stopTimes[position]
            if (position == items[indexInViewPager].closestTimeInListIndex) {
                // TODO: Make tvTime bold here
                holder.tvTimeLeft.visibility = View.VISIBLE
                val timeLeft =
                    calcTimeLeft(
                        currentTime.toInt(),
                        busInstances[indexInViewPager].stopTimes[position].toInt()
                    )
                holder.tvTimeLeft.text = timeLeft.toString()
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