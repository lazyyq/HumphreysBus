package kyklab.test.subwaymap

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
import kyklab.test.subwaymap.BusMapManager.getBusStop
import java.util.*

class MyAdapter(private val context: Context, private val items: List<AdapterItem>) :
    RecyclerView.Adapter<MyAdapter.ViewHolder>() {
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
            val bus = items[position].bus

            if (bus.instances.isEmpty()) return@Thread

            val textColor = when (bus.name) {
                "Red" -> android.R.color.holo_red_dark
                "Blue" -> android.R.color.holo_blue_dark
                "Green" -> android.R.color.holo_green_dark
                else -> null
            }

            val curStopNo = items[position].stopNo
            val stopAndTimes = bus.getAllStopTimes(curStopNo)

            val tables = LinkedList<View>()

            for (stopIndex in stopAndTimes.keys) {
                var prevStop: BusMapManager.BusStop?
                var currStop: BusMapManager.BusStop?
                var nextStop: BusMapManager.BusStop?
                // Get names of previous, current, next stops
                bus.instances[0].let {
                    prevStop =
                        if (stopIndex > 0) it.stops[stopIndex - 1].stopNo.getBusStop() else null
                    currStop =
                        it.stops[stopIndex].stopNo.getBusStop()
                    nextStop =
                        if (stopIndex < it.stops.size - 1) it.stops[stopIndex + 1].stopNo.getBusStop() else null
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
                            var secondItem = false
                            stopAndTimes[stopIndex]?.let {
                                for (i in it.indices) {
                                    if (!closestFound && it[if (i == 0) it.size - 1 else i - 1] < curTime!! && curTime!! <= it[i]) {
                                        bold { scale(1.2f) { append(it[i].format("%04d")) } }
                                        closestFound = true
                                    } else {
                                        append(it[i].format("%04d"))
                                    }
                                    append(if (secondItem) "\n" else "    ")
                                    secondItem = !secondItem
                                }
                            }
                        }

                        isClickable = true
                        setOnClickListener { v ->
                            StopInfoDialog.showBusSchedules(
                                context as Activity,
                                position,
                                curStopNo
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
                                LinearLayout.LayoutParams(1, LinearLayout.LayoutParams.MATCH_PARENT)
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

    data class AdapterItem(val bus: Buses.Bus, val stopNo: String)
}