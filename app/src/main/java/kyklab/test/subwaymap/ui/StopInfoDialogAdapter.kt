package kyklab.test.subwaymap.ui

import android.app.Activity
import android.content.Context
import android.icu.text.SimpleDateFormat
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
import kyklab.test.subwaymap.bus.BusUtils.getBusStop
import java.util.*

class StopInfoDialogAdapter(
    private val context: Context,
    private val scope: CoroutineScope,
    adapterItems: List<AdapterItem>
) :
    RecyclerView.Adapter<StopInfoDialogAdapter.ViewHolder>() {
    val items: List<InternalItem>

    //val bus: Buses.Bus, val stopIndex: Int, val stopTimes: List<Int>
    init {
        items = LinkedList()
        for (adapterItem in adapterItems) {
            val m = adapterItem.bus.getAllStopTimes(adapterItem.curStopNo)
            items.add(InternalItem(adapterItem.bus, m))
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
            val item = items[position]
            val bus = item.bus

            if (bus.instances.isEmpty()) return@launch

            val tables = LinkedList<View>()
            var largestPrevNextStopViewHeight = 0 // To evenly set each table item's view height
            for (stopIndex in item.stopIndexAndTimes.keys) {
                val stopTimes = item.stopIndexAndTimes[stopIndex]
                val timeTextsPerLine: Int = 4 / item.stopIndexAndTimes.keys.size

                var prevStop: BusUtils.BusStop?
                var currStop: BusUtils.BusStop?
                var nextStop: BusUtils.BusStop?
                // Get names of previous, current, next stops
                bus.instances[0].let {
                    prevStop = getBusStop(it.stops.getOrNull(stopIndex - 1)?.stopNo)
                    currStop = getBusStop(it.stops.getOrNull(stopIndex)?.stopNo)
                    nextStop = getBusStop(it.stops.getOrNull(stopIndex + 1)?.stopNo)
                }

                // Prepare new timetable item
                val timeTableColumnItem = LayoutInflater.from(context)
                    .inflate(R.layout.fragment_stop_info_timetable_column_item, null).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.MATCH_PARENT, 1f
                        )
                        textColor?.let { color ->
                            tvPrevCurrNextStop.setTextColor(
                                context.resources.getColor(
                                    color,
                                    context.theme
                                )
                            )
                            tvTimeTable.setTextColor(
                                context.resources.getColor(
                                    color,
                                    context.theme
                                )
                            )
                        }

                        // Show previous, current, next stop name
                        tvPrevCurrNextStop.text = SpannableStringBuilder().apply {
                            prevStop?.let { appendLine(it.stopName) }
                                ?: italic { appendLine("(NONE)") }
                            currStop?.let { bold { scale(1.2f) { append(it.stopName) } } }
                                ?: append("Unknown")
                            nextStop?.let { append('\n' + it.stopName) }
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
                            stopTimes?.let {
                                for (i in it.indices) {
                                    ++itemNum
                                    append("    ")
                                    val str = it[i].format("%04d").insert(2, ":");
                                    if (!closestFound) {
                                        val isBetween = isBetween(
                                            curTime!!,
                                            it.getWithWrappedIndex(i - 1)!!, it[i]
                                        )
                                        if (isBetween) {
                                            append(SpannableString(str).apply {
                                                if (textColor != null) {
                                                    this.setSpan(
                                                        RoundedBackgroundSpan(
                                                            context,
                                                            resources.getColor(
                                                                textColor, context.theme
                                                            ),
                                                            resources.getColor(
                                                                R.color.white, context.theme
                                                            )
                                                        ),
                                                        0, str.length,
                                                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                                                    )
                                                }
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

    override fun getItemCount() = items.size

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

    data class AdapterItem(val bus: Bus, val curStop: BusUtils.BusStop)

//    data class InternalItem(val bus: Bus, val stopIndexAndTimes: Map<Int, List<Int>>)
}