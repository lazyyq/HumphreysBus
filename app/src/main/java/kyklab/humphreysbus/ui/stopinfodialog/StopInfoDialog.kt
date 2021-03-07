package kyklab.humphreysbus.ui.stopinfodialog

import android.app.Activity
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import android.os.Bundle
import android.view.*
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.android.synthetic.main.fragment_stop_info_dialog.*
import kotlinx.android.synthetic.main.fragment_stop_info_dialog.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kyklab.humphreysbus.R
import kyklab.humphreysbus.bus.Bus
import kyklab.humphreysbus.bus.BusUtils
import kyklab.humphreysbus.data.BusStop
import kyklab.humphreysbus.ui.BusDetailsActivity
import kyklab.humphreysbus.ui.DateTimePickerFragment
import kyklab.humphreysbus.ui.MainActivity
import kyklab.humphreysbus.utils.*
import java.util.*

class StopInfoDialog(private val onDismiss: (() -> Unit)? = null) : BottomSheetDialogFragment() {
    private lateinit var activity: Activity
    private val calendar = Calendar.getInstance()
    private var currentTime = currentTimeHHmm
    private val sdf by lazy { SimpleDateFormat("HHmm") }
    private var isHoliday = isHoliday()
    private var stopId = -1
    private lateinit var stop: BusStop

    private var loadBusJob: Job? = null
    private lateinit var rvAdapter: NewAdapter
    private val rvAdapterItems: MutableList<NewAdapter.NewAdapterItem> = ArrayList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        try {
            activity = requireActivity()
        } catch (e: IllegalStateException) {
            dismiss()
        }
        val view = inflater.inflate(R.layout.fragment_stop_info_dialog, container, false)
        arguments ?: dismiss()

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        showCurrentTime()
        showBuses()

        // Adjust peek (default expanded) height to match that of its contents size
        view.bottomSheetContents.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    val bottomSheetDialog =
                        dialog?.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                    bottomSheetDialog?.let {
                        val behavior = BottomSheetBehavior.from(it)
                        behavior.peekHeight = view.bottomSheetContents.measuredHeight
                    }
                    view.bottomSheetContents.viewTreeObserver
                        .removeOnGlobalLayoutListener(this)
                }
            }
        )
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            setOnShowListener {
                // Set bottom sheet dialog background to rounded corner one
                findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                    ?.setBackgroundResource(R.drawable.bottom_sheet_dialog_rounded_corner)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        view ?: return
        val view = requireView()
        /*val lp = LinearLayout.LayoutParams(view.rvClosestBuses.layoutParams)
        lp.height =  resources.getDimension(R.dimen.stop_info_dialog_bus_list_height).toInt()
        view.rvClosestBuses.layoutParams = lp*/
        dismiss()
        show(parentFragmentManager, tag)
    }

    private fun showBuses() {
        lateinit var view: View
        try {
            view = requireView()
        } catch (e: IllegalStateException) {
            dismiss()
        }

        stopId = requireArguments().getInt(ARGUMENT_STOP_ID, -1)
        stop = BusUtils.getBusStop(stopId) ?: return
        view.tvStopInfo.text = stop.name

//        updateBuses()
        initBusList()
        newUpdateBuses()
    }

    private fun initBusList() {
        rvAdapter = NewAdapter(activity, lifecycleScope, rvAdapterItems, currentTime)
        rvClosestBuses.adapter = rvAdapter
        val layoutManager: RecyclerView.LayoutManager

        val rotation = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            activity.display?.rotation ?: 0
        } else {
            activity.windowManager.defaultDisplay.rotation
        }
        layoutManager = when (rotation) {
            Surface.ROTATION_0, Surface.ROTATION_180 -> LinearLayoutManager(activity)
            else -> GridLayoutManager(activity, 2)
        }
        rvClosestBuses.layoutManager = layoutManager
    }

    private fun newUpdateBuses() {
        progressBar.visibility = View.VISIBLE
        rvAdapterItems.clear()

        BusUtils.buses.forEach { bus ->
            val stopIndexes =
                bus.stopPoints.withIndex().filter { it.value.id == stopId }.map { it.index }
            stopIndexes.forEach { index ->
                val times = bus.instances.filter { it.isHoliday == isHoliday }
                    .map { it.stopTimes[index].toInt() }
                // Find closest bus time
                var closest: Int = -1
                for (i in times.indices) {
                    if (isBetween(
                            currentTime.toInt(),
                            times.getWithWrappedIndex(i - 1)!!, times[i]
                        )
                    ) {
                        closest = i
                        break
                    }
                }
                val newAdapterItem = NewAdapter.NewAdapterItem(
                    bus, stopId, index, times[closest], times.getWithWrappedIndex(closest + 1)!!
                )
                rvAdapterItems.add(newAdapterItem)
            }
        }

        rvAdapterItems.sortBy { it.closestBusTime }
        rvAdapter.notifyDataSetChanged()

        progressBar.visibility = View.GONE
    }

    /*private fun updateBuses() {
        val view = requireView()
        val activity = requireActivity()

        view.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.Default) {
            loadBusJob?.let { if (it.isActive) it.cancelAndJoin() }
            loadBusJob = launch {
                val adapter =
                    StopInfoDialogAdapter(activity, lifecycleScope, stopId, currentTime, isHoliday)
                launch(Dispatchers.Main) {
//                    view.vpTimeTable.adapter = adapter
//                    TabLayoutMediator(view.busTabLayout, view.vpTimeTable) { tab, position ->
//                        tab.text = adapter.adapterItems[position].bus.name
//                    }.attach()

                    view.progressBar.visibility = View.GONE

                    // Adjust peek (default expanded) height to match that of its contents size
                    view.bottomSheetContents.viewTreeObserver.addOnGlobalLayoutListener(
                        object : ViewTreeObserver.OnGlobalLayoutListener {
                            override fun onGlobalLayout() {
                                val bottomSheetDialog =
                                    dialog?.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                                bottomSheetDialog?.let {
                                    val behavior = BottomSheetBehavior.from(it)
                                    behavior.peekHeight = view.bottomSheetContents.measuredHeight
                                }
                                view.bottomSheetContents.viewTreeObserver
                                    .removeOnGlobalLayoutListener(this)
                            }
                        }
                    )
                }
            }
        }
    }*/

    private fun showCurrentTime() {
        updateDateTime()

        cbHoliday.setOnClickListener {
            if (it is CompoundButton) {
                isHoliday = it.isChecked
            }
            updateDateTime()
            newUpdateBuses()
        }


        tvCurrentTime.setOnClickListener {
            DateTimePickerFragment(calendar) { year, month, dayOfMonth, hourOfDay, minute ->
                calendar.set(year, month, dayOfMonth, hourOfDay, minute)
                currentTime = sdf.format(calendar.time)
                rvAdapter.currentTime = currentTime
                isHoliday = calendar.time.isHoliday()
                updateDateTime()
                newUpdateBuses()
            }.show(parentFragmentManager, "dateTimePicker")
            /*
            DateTimePickerDialog(
               activity,
                calendar
            ) { year, month, dayOfMonth, hourOfDay, minute ->
                calendar.set(year, month, dayOfMonth, hourOfDay, minute)
                currentTime = sdf.format(calendar.time)
                rvAdapter.currentTime = currentTime
                isHoliday = calendar.time.isHoliday()
                updateDateTime()
                newUpdateBuses()
            }.show()
            */

        }
        /*tvCurrentTime.setOnClickListener {
            DatePickerFragment(calendar) { d, year, month, dayOfMonth ->
                TimePickerFragment(calendar)
                { t, hourOfDay, minute ->
                    calendar.set(year, month, dayOfMonth, hourOfDay, minute)
                    currentTime = sdf.format(calendar.time)
                    rvAdapter.currentTime = currentTime
                    isHoliday = calendar.time.isHoliday()
                    updateDateTime()
                    newUpdateBuses()
                }.show(parentFragmentManager, "timePicker")
            }.show(parentFragmentManager, "datePicker")
        }*/
    }

    private fun updateDateTime() {
        cbHoliday.isChecked = isHoliday // TODO: Check if this triggers listener
        tvCurrentTime.text = currentTime.insert(2, ":")
    }

    override fun onDismiss(dialog: DialogInterface) {
        onDismiss?.invoke()
        super.onDismiss(dialog)
    }

    companion object {
        const val ARGUMENT_STOP_ID = "argument_id"

        fun showBusSchedules(activity: Activity, busName: String, stopIndex: Int) {
            val intent = Intent(activity, BusDetailsActivity::class.java).apply {
                putExtra("busname", busName)
                putExtra("highlightstopindex", stopIndex)
            }
            activity.startActivityForResult(intent, MainActivity.REQ_CODE_SELECT_STOP)
        }

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
    }

    class NewAdapter(
        private val activity: Activity,
        private val scope: CoroutineScope,
        private val items: List<NewAdapterItem>,
        var currentTime: String,
    ) : RecyclerView.Adapter<NewAdapter.NewViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NewViewHolder {
            return NewViewHolder(
                LayoutInflater.from(parent.context).inflate(
                    R.layout.fragment_stop_info_bus_item,
                    parent,
                    false
                )
            )
        }

        override fun onBindViewHolder(holder: NewViewHolder, position: Int) {
            val item = items[position]

            holder.ivBus.imageTintList = ColorStateList.valueOf(item.bus.colorInt)
            holder.tvBusName.text = item.bus.name
            holder.tvBusName.setTextColor(item.bus.colorInt)
            if (item.stopPointIndex + 1 < item.bus.stopPoints.size) {
                holder.tvTowards.text =
                    "Heading " + item.bus.stopPoints[item.stopPointIndex + 1].name
            } else {
                holder.tvTowards.text = "(End of bus)"
            }

            val closestLeft = calcTimeLeft(currentTime.toInt(), item.closestBusTime)
            val nextClosestLeft = calcTimeLeft(currentTime.toInt(), item.nextClosestBusTime)
            holder.tvNextBus.text =
                "${item.closestBusTime.format("%04d").insert(2, ":")} ($closestLeft mins)"
            holder.tvNextNextBus.text =
                "${item.nextClosestBusTime.format("%04d").insert(2, ":")} ($nextClosestLeft mins)"
        }

        override fun getItemCount() = items.size

        inner class NewViewHolder(val itemView: View) : RecyclerView.ViewHolder(itemView) {
            val ivBus = itemView.findViewById<ImageView>(R.id.ivBus)
            val tvBusName = itemView.findViewById<TextView>(R.id.tvBusName)
            val tvTowards = itemView.findViewById<TextView>(R.id.tvTowards)
            val tvNextBus = itemView.findViewById<TextView>(R.id.tvNextBus)
            val tvNextNextBus = itemView.findViewById<TextView>(R.id.tvNextNextBus)

            init {
                itemView.setOnClickListener {
                    val item = items[adapterPosition]
                    showBusSchedules(activity, item.bus.name, item.stopPointIndex)
                }
            }
        }

        data class NewAdapterItem(
            val bus: Bus,
            val stopId: Int,
            val stopPointIndex: Int,
            val closestBusTime: Int,
            val nextClosestBusTime: Int
        )
    }

    /*private class StopInfoDialogAdapter(
        private val context: Context,
        private val scope: CoroutineScope,
        private val stopId: Int,
        private val currentTime: String,
        private val isHoliday: Boolean
    ) : RecyclerView.Adapter<StopInfoDialogAdapter.ViewHolder>() {

        companion object {
            private val TAG = StopInfoDialogAdapter::class.simpleName
        }

        val adapterItems: List<AdapterItem>

        //val bus: Buses.Bus, val stopIndex: Int, val stopTimes: List<Int>
        init {
            adapterItems = LinkedList()
            BusUtils.buses.forEach { bus ->
                bus.stopPoints.withIndex().filter { it.value.id == stopId }.map { it.index }.let {
                    if (it.isNotEmpty()) adapterItems.add(AdapterItem(bus, it))
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(
                LayoutInflater.from(parent.context).inflate(
                    R.layout.fragment_stop_info_timetable_container,
                    parent,
                    false
                )
            )
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            scope.launch(Dispatchers.Default) {
                val item = adapterItems[position]
                val bus = item.bus

                if (bus.instances.isEmpty()) return@launch

                val tables = LinkedList<View>()
                var largestPrevNextStopViewHeight = 0 // To evenly set each table item's view height
                for (stopIndex in item.stopIndexes) {
                    val timeTextsPerLine: Int = 4 / item.stopIndexes.size

                    var prevStop: BusStop?
                    var currStop: BusStop?
                    var nextStop: BusStop?
                    bus.stopPoints.let {
                        prevStop = it.getOrNull(stopIndex - 1)
                        currStop = it.getOrNull(stopIndex)
                        nextStop = it.getOrNull(stopIndex + 1)
                    }

                    // Prepare new timetable item
                    val timeTableColumnItem = LayoutInflater.from(context)
                        .inflate(R.layout.fragment_stop_info_timetable_column_item, null).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.MATCH_PARENT, 1f
                            )
                            bus.colorInt.let {
                                tvPrevCurrNextStop.setTextColor(it)
                                tvTimeTable.setTextColor(it)
                            }

                            // Show previous, current, next stop name
                            tvPrevCurrNextStop.text = SpannableStringBuilder().apply {
                                prevStop?.let { appendLine(it.name) }
                                    ?: italic { appendLine("(NONE)") }
                                currStop?.let { bold { scale(1.2f) { append(it.name) } } }
                                    ?: append("Unknown")
                                nextStop?.let { append('\n' + it.name) }
                                    ?: italic { append("\n(NONE)") }
                            }
                            tvPrevCurrNextStop.measure(0, 0)
                            largestPrevNextStopViewHeight = Math.max(
                                largestPrevNextStopViewHeight,
                                tvPrevCurrNextStop.measuredHeight
                            )
                            if (prevStop != null && nextStop == null) {
                                tvPrevCurrNextStop.gravity =
                                    Gravity.TOP or Gravity.CENTER_HORIZONTAL
                            } else if (prevStop == null && nextStop != null) {
                                tvPrevCurrNextStop.gravity =
                                    Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                            }

                            // Show times for current stop, with the closest one in bold

                            // Save lines count for auto scrolling to closest stop time
                            var autoScrollTotalLines: Int
                            var autoScrollHighlightLine = 0
                            val autoScrollOffset = 4

                            tvTimeTable.text = SpannableStringBuilder().apply {
                                var closestFound = false
                                var itemNum = 0 // To change line on `timeTextsPerLine`th text
                                val stopTimes = bus.instances
                                    .filter { it.isHoliday == isHoliday }
                                    .map { it.stopTimes[stopIndex] }
                                stopTimes.let {
                                    autoScrollTotalLines = 1
                                    for (i in it.indices) {
                                        ++itemNum
                                        append("    ")
                                        val str = it[i].format("%04d").insert(2, ":");
                                        if (!closestFound) {
                                            val isBetween = isBetween(
                                                currentTime.toInt(),
                                                it.getWithWrappedIndex(i - 1)!!.toInt(),
                                                it[i].toInt()
                                            )
                                            if (isBetween) {
                                                // Found the closest stop time
                                                append(SpannableString(str).apply {
                                                    setSpan(
                                                        RoundedBackgroundSpan(
                                                            context, bus.colorInt,
                                                            resources.getColor(
                                                                R.color.white, context.theme
                                                            )
                                                        ),
                                                        0, str.length,
                                                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                                                    )
                                                })
                                                closestFound = true
                                                autoScrollHighlightLine = autoScrollTotalLines
                                            } else {
                                                append(str)
                                            }
                                        } else {
                                            append(str)
                                        }
                                        append("    ")
                                        if (itemNum == timeTextsPerLine) {
                                            append("\n")
                                            ++autoScrollTotalLines
                                        }
                                        itemNum %= timeTextsPerLine
                                    }
                                }
                            }
                            // Auto scroll to closest stop time
                            scope.launch(Dispatchers.Main) {
                                tvTimeTable.addOnLayoutChangeListener(object :
                                    View.OnLayoutChangeListener {
                                    override fun onLayoutChange(
                                        v: View?, left: Int, top: Int, right: Int, bottom: Int,
                                        oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
                                    ) {
                                        val scroll = tvTimeTable.height *
                                                (autoScrollHighlightLine - autoScrollOffset) / autoScrollTotalLines
                                        scrollView.scrollTo(0, scroll)
                                        tvTimeTable.removeOnLayoutChangeListener(this)
                                    }
                                })
                            }

                            val showBusScheduledOnClick = { v: View ->
                                StopInfoDialog.showBusSchedules(
                                    context as Activity,
                                    bus.name,
                                    stopIndex
                                )
                            }
                            tvTimeTable.setOnClickListener(showBusScheduledOnClick)
                            tvPrevCurrNextStop.setOnClickListener(showBusScheduledOnClick)
                        }
                    tables.add(timeTableColumnItem)
                }
                for (table in tables) {
                    table.findViewById<View>(R.id.tvPrevCurrNextStop).layoutParams.height =
                        largestPrevNextStopViewHeight
                }
                launch(Dispatchers.Main) {
                    for ((index, view) in tables.withIndex()) {
                        holder.container.addView(view)
                        // Add divider
                        if (index < tables.size - 1)
                            holder.container.addView(View(context).apply {
                                layoutParams =
                                    LinearLayout.LayoutParams(
                                        1,
                                        LinearLayout.LayoutParams.MATCH_PARENT
                                    )
                                        .apply {
                                            setMargins(0, dpToPx(context, 8f), 0, 0)
                                        }
                                setBackgroundColor(
                                    context.resources.getColor(
                                        android.R.color.darker_gray,
                                        context.theme
                                    )
                                )
                            })
                    }
                }
            }.start()
        }

        override fun getItemCount() = adapterItems.size

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

        class ViewHolder(val itemView: View) : RecyclerView.ViewHolder(itemView) {
            val container = itemView.findViewById<LinearLayout>(R.id.items_container)
        }

        data class AdapterItem(
            val bus: Bus,
            val stopIndexes: List<Int> // Indexes in the bus' stop points list for the given stop id
        )

//    data class InternalItem(val bus: Bus, val stopIndexAndTimes: Map<Int, List<Int>>)
    }*/
}