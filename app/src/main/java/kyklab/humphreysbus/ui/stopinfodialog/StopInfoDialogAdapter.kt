package kyklab.humphreysbus.ui.stopinfodialog

import android.app.Activity
import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.view.*
import android.widget.LinearLayout
import androidx.core.text.bold
import androidx.core.text.italic
import androidx.core.text.scale
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.fragment_stop_info_timetable_column_item.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kyklab.humphreysbus.*
import kyklab.humphreysbus.bus.Bus
import kyklab.humphreysbus.bus.BusUtils
import java.util.*

class StopInfoDialogAdapter(
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

                var prevStop: BusUtils.BusStop?
                var currStop: BusUtils.BusStop?
                var nextStop: BusUtils.BusStop?
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
                            tvPrevCurrNextStop.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                        } else if (prevStop == null && nextStop != null) {
                            tvPrevCurrNextStop.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
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
                                LinearLayout.LayoutParams(1, LinearLayout.LayoutParams.MATCH_PARENT)
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
}