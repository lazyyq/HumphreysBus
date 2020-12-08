package kyklab.test.subwaymap.ui

import android.app.Activity
import android.content.Context
import android.icu.text.SimpleDateFormat
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.text.bold
import androidx.core.text.scale
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.stop_timetable_item.view.*
import kyklab.test.subwaymap.*
import kyklab.test.subwaymap.bus.Bus
import kyklab.test.subwaymap.bus.BusMapManager
import kyklab.test.subwaymap.bus.BusMapManager.getBusStop
import java.util.*

class MyAdapter(private val context: Context, adapterItems: List<AdapterItem>) :
    RecyclerView.Adapter<MyAdapter.ViewHolder>() {
    val items: List<InternalItem>
    //val bus: Buses.Bus, val stopIndex: Int, val stopTimes: List<Int>
    init {
        items = LinkedList()
        for (adapterItem in adapterItems) {
            val m = adapterItem.bus.getAllStopTimes(adapterItem.curStopNo)
            items.add(InternalItem(adapterItem.bus, m))
        }
    }

    private var curTime: Int? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        MainActivity.etCustomTime!!.text.toString().let {
            curTime = if (it == "") SimpleDateFormat("HHmm").format(Date()).toInt() else it.toInt()
        }
        return ViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.stop_timetable_container,
                parent,
                false
            )
        )
    }


    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        Thread {
            val item = items[position]
            val bus = item.bus

            if (bus.instances.isEmpty()) return@Thread

            val textColor = when (bus.name) {
                "Red" -> android.R.color.holo_red_dark
                "Blue" -> android.R.color.holo_blue_dark
                "Green" -> android.R.color.holo_green_dark
                else -> null
            }

            val tables = LinkedList<View>()
            for (stopIndex in item.stopIndexAndTimes.keys)
            {
                val stopTimes = item.stopIndexAndTimes[stopIndex]
                val timeTextsPerLine: Int = 4 / item.stopIndexAndTimes.keys.size

                var prevStop: BusMapManager.BusStop?
                var currStop: BusMapManager.BusStop?
                var nextStop: BusMapManager.BusStop?
                // Get names of previous, current, next stops
                bus.instances[0].let {
                    prevStop = getBusStop(it.stops.getOrNull(stopIndex - 1)?.stopNo)
                    currStop = getBusStop(it.stops.getOrNull(stopIndex)?.stopNo)
                    nextStop = getBusStop(it.stops.getOrNull(stopIndex + 1)?.stopNo)
                }

                // Prepare new timetable item
                val timeTableItem = LayoutInflater.from(context)
                    .inflate(R.layout.stop_timetable_item, null).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.MATCH_PARENT, 1f
                        )
                        textColor?.let { color ->
                            tvStopsInfo.setTextColor(
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
                        tvStopsInfo.text = SpannableStringBuilder().apply {
                            prevStop?.let { appendLine(it.stopName) }
                            currStop?.let { bold { scale(1.2f) { append(it.stopName) } } }
                            nextStop?.let { append('\n' + it.stopName) }
                        }

                        // Show times for current stop, with the closest one in bold
                        tvTimeTable.text = SpannableStringBuilder().apply {
                            var closestFound = false
                            var itemNum = 0 // To change line on `timeTextsPerLine`th text
                            stopTimes?.let {
                                for (i in it.indices) {
                                    ++itemNum
                                    if (!closestFound && it.getWithWrappedIndex(i - 1)!! < curTime!! && curTime!! <= it[i]) {
                                        bold { scale(1f) { append(it[i].format("%04d")) } }
                                        closestFound = true
                                    } else {
                                        append(it[i].format("%04d"))
                                    }
                                    append(if (itemNum == timeTextsPerLine) "\n" else "        ")
                                    itemNum %= timeTextsPerLine
                                }
                            }
                        }

                        tvStopsInfo.setOnClickListener { v: View ->
                            StopInfoDialog.showBusSchedules(
                                context as Activity,
                                bus.name,
                                stopIndex
                            )
                        }
                    }
                tables.add(timeTableItem)
            }
            (context as Activity).runOnUiThread {
                for ((index, view) in tables.withIndex()) {
                    holder.container.addView(view)
                    // Add divider
                    if (index < tables.size - 1)
                        holder.container.addView(View(context).apply {
                            layoutParams =
                                LinearLayout.LayoutParams(1, LinearLayout.LayoutParams.MATCH_PARENT).apply{
                                    setMargins(0, dpToPx(context, 8f),0,0)
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

    class ViewHolder(val itemView: View) : RecyclerView.ViewHolder(itemView) {
        val container = itemView.findViewById<LinearLayout>(R.id.items_container)
    }

    data class AdapterItem(val bus: Bus, val curStopNo: String)

    data class InternalItem(val bus: Bus, val stopIndexAndTimes: Map<Int, List<Int>>)
}