package kyklab.test.subwaymap.ui

import android.app.Activity
import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.text.bold
import androidx.core.text.italic
import androidx.core.text.scale
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.fragment_stop_info_timetable_column_item.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kyklab.test.subwaymap.*
import kyklab.test.subwaymap.bus.Bus
import kyklab.test.subwaymap.bus.BusUtils
import java.util.*

class StopInfoDialogAdapter(
    private val context: Context,
    private val scope: CoroutineScope,
//    adapterItems: List<AdapterItem>
    private val stopId: Int
) : RecyclerView.Adapter<StopInfoDialogAdapter.ViewHolder>() {

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

    private val curTime by lazy {
        when (val customTime = MainActivity.etCustomTime!!.text.toString()) {
            "" -> currentTimeHHmm.toInt()
            else -> customTime.toInt()
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
                val stopTimes = Array(bus.instances.size) { bus.instances[it].stopTimes[stopIndex] }
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
                        tvTimeTable.text = SpannableStringBuilder().apply {
                            var closestFound = false
                            var itemNum = 0 // To change line on `timeTextsPerLine`th text
                            stopTimes.let {
                                for (i in it.indices) {
                                    ++itemNum
                                    append("    ")
                                    val str = it[i].format("%04d").insert(2, ":");
                                    if (!closestFound) {
                                        val isBetween = isBetween(
                                            curTime,
                                            it.getWithWrappedIndex(i - 1)!!.toInt(0),
                                            it[i].toInt()
                                        )
                                        if (isBetween) {
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
                                        } else {
                                            append(str)
                                        }
                                    } else {
                                        append(str)
                                    }
                                    append("    ")
                                    if (itemNum == timeTextsPerLine) append("\n")
                                    itemNum %= timeTextsPerLine
                                }
                            }
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