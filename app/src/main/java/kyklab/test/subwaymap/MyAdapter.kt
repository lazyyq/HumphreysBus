package kyklab.test.subwaymap

import android.app.Activity
import android.content.Context
import android.icu.text.SimpleDateFormat
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.text.bold
import androidx.core.text.scale
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.stop_timetable_item.view.*
import java.util.*
import kotlin.collections.ArrayList

class MyAdapter(private val context: Context, private val items: List<Item>) :
    RecyclerView.Adapter<MyAdapter.ViewHolder>() {
    private var curTime: Int? = null
    private lateinit var tmpParent: ViewGroup

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        MainActivity.etCustomTime!!.text.toString().let {
            curTime = if (it == "") SimpleDateFormat("HHmm").format(Date()).toInt() else it.toInt()
        }
        tmpParent = parent
        return ViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.stop_timetable_container,
                parent,
                false
            )
        )
    }


    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val bus = items[position].bus

        val textColor = when (bus.name) {
            "Red" -> android.R.color.holo_red_dark
            "Blue" -> android.R.color.holo_blue_dark
            "Green" -> android.R.color.holo_green_dark
            else -> null
        }

        val curStopNo = items[position].stopNo
        var curStopIndexes = ArrayList<Int>(4)
        if (bus.instances.isNotEmpty()) {
            for ((i, stop) in bus.instances[0].stops.withIndex()) {
                if (stop.stopNo == curStopNo) curStopIndexes.add(i)
            }

            for (i in curStopIndexes.indices) {
                val curStopIndex = curStopIndexes[i]
                val timeTableItem = LayoutInflater.from(context)
                    .inflate(R.layout.stop_timetable_item, null)
                timeTableItem.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT, 1f
                )
                timeTableItem.findViewById<ScrollView>(R.id.scrollView).setOnClickListener{ v->
                    timeTableItem.performClick()}
                val stopBefore =
                    if (curStopIndex > 0) bus.instances[0].stops[curStopIndex - 1] else null
                val stopCurrent = bus.instances[0].stops[curStopIndex]
                val stopAfter =
                    if (curStopIndex < bus.instances[0].stops.size - 1) bus.instances[0].stops[curStopIndex + 1] else null
                val stopNameBefore =
                    stopBefore?.let { MapManager.getStationWithMapNo(it.stopNo)?.name.toString() }
                val stopNameCurrent =
                    stopCurrent.let { MapManager.getStationWithMapNo(it.stopNo)?.name.toString() }
                val stopNameAfter =
                    stopAfter?.let { MapManager.getStationWithMapNo(it.stopNo)?.name.toString() }
                textColor?.let { color ->
                    timeTableItem.tvStopsInfo.setTextColor(
                        context.resources.getColor(
                            color,
                            context.theme
                        )
                    )
                    timeTableItem.tvTimeTable.setTextColor(
                        context.resources.getColor(
                            color,
                            context.theme
                        )
                    )
                }
                timeTableItem.tvStopsInfo.text = SpannableStringBuilder().apply {
                    appendLine(stopNameBefore ?: "")
                    bold { scale(1.2f) { appendLine(stopNameCurrent) } }
                    append(stopNameAfter ?: "")
                }
                timeTableItem.tvTimeTable.text = SpannableStringBuilder().apply {
                    var first = true
                    val times = ArrayList<Int>()
                    for (instance in bus.instances) {
                        times.add(instance.stops[curStopIndex].time.toInt())
                    }
                    for (i in times.indices) {
                        if (first && times[if (i == 0) times.size - 1 else i - 1] < curTime!! && curTime!! <= times[i]) {
                            bold { scale(1.2f) { appendLine(times[i].toString()) } }
                            first = false
                        } else {
                            appendLine(times[i].toString())
                        }
                    }
                }
                timeTableItem.isClickable = true
                timeTableItem.setOnClickListener { v ->
                    StationInfoBottomSheetDialog.showBusSchedules(
                        context as Activity,
                        position,
                        curStopNo
                    )
                }

                holder.container.addView(timeTableItem)
                if (i != curStopIndexes.size - 1)
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
    }

    override fun getItemCount() = items.size

    class ViewHolder(val itemView: View) : RecyclerView.ViewHolder(itemView) {
        val container = itemView.findViewById<LinearLayout>(R.id.items_container)
    }

    data class Item(val bus: Buses.Bus, val stopNo: String)
}