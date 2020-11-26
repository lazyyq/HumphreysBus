package kyklab.test.subwaymap

import android.content.res.ColorStateList
import android.graphics.Rect
import android.graphics.Typeface
import android.icu.text.SimpleDateFormat
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.text.bold
import androidx.core.text.scale
import com.evrencoskun.tableview.adapter.AbstractTableAdapter
import com.evrencoskun.tableview.adapter.recyclerview.holder.AbstractViewHolder
import com.google.android.material.textview.MaterialTextView
import kotlinx.android.synthetic.main.activity_bus_view.*
import kyklab.test.subwaymap.BusMapManager.getBusStop
import java.util.*


class BusTimeTableViewActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "BusViewActivity"
    }

    var busName: String? = null
    private var stopToHighlightIndex: Int? = null
    private var busToShow: Buses.Bus? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bus_view)

        val intent = intent

        busName = intent.extras?.getString("busname")
        stopToHighlightIndex = intent.extras?.getInt("highlightstopindex")

        for (b in Buses.buses) {
            if (b.name == busName) {
                busToShow = b
                break
            }
        }
        if (busToShow == null || busToShow!!.instances.isEmpty()) {
            Toast.makeText(this, "Bus not found", Toast.LENGTH_SHORT).show()
            finish()
        }

        showBusTimeTable()
    }

    private fun showBusTimeTable() {
        Thread {
            // TODO: Generify
            var tintColor: Int? = null
            when (busName) {
                "Red" -> { // Red
                    tintColor = android.R.color.holo_red_dark
                }
                "Blue" -> {  // Blue
                    tintColor = android.R.color.holo_blue_dark
                }
                "Green" -> { // Green
                    tintColor = android.R.color.holo_green_dark
                }
            }
            tintColor?.let {
                runOnUiThread {
                    ivBus.imageTintList = ColorStateList.valueOf(resources.getColor(it, this.theme))
                    tvBus.text = busName
                    tvBus.setTextColor(resources.getColor(it, this.theme))
                }
            }

            // Get current time
            val sdfWithoutColon = SimpleDateFormat("HHmm")
            val curTime: Int
            MainActivity.etCustomTime!!.text.toString().let {
                curTime = if (it == "") sdfWithoutColon.format(
                    Date()
                ).toInt() else it.toInt()
            }

            busToShow?.let { bus ->
                val instances = bus.instances
                val refInstance = instances[0]

                val tableRowHeaderList = ArrayList<String>(refInstance.stops.size)
                // Table rows
                refInstance.stops.forEach { stop ->
                    tableRowHeaderList.add(stop.stopNo.getBusStop()?.stopName ?: "")
                }
                val tableColumnHeaderList = ArrayList<String>(instances.size)
                val tableCellList =
                    ArrayList<ArrayList<EntireTimeTableAdapter.TimeData>>(instances[0].stops.size)
                // Table columns
                for (j in instances.indices) {
                    tableColumnHeaderList.add("String")
                }
                // Table cells
                for (i in instances[0].stops.indices) {
                    val tableCellColumnList =
                        ArrayList<EntireTimeTableAdapter.TimeData>(instances.size)
                    for (j in instances.indices) {
                        val time = instances[j].stops[i].stopTime
                        val timeLeft = calcTimeLeft(curTime, time.toInt())
                        tableCellColumnList.add(
                            EntireTimeTableAdapter.TimeData(
                                SpannableStringBuilder().apply {
                                    if (i == stopToHighlightIndex)
                                        bold { append(time) }
                                    else
                                        append(time)
                                },
                                SpannableStringBuilder().apply {
                                    if (i == stopToHighlightIndex)
                                        bold { append("($timeLeft mins)") }
                                    else
                                        append("($timeLeft mins)")
                                })
                        )
                    }
                    tableCellList.add(tableCellColumnList)
                }

                val adapter = EntireTimeTableAdapter()
                runOnUiThread {
                    entireTimeTable.setAdapter(adapter)
                    adapter.setAllItems(
                        tableColumnHeaderList, tableRowHeaderList,
                        tableCellList as List<MutableList<EntireTimeTableAdapter.TimeData>>?
                    )
                    progressBar.visibility = View.INVISIBLE
                }
            }



            /*
            var closestBusTimeLeft = 1440
            var closestBusTextView: TextView? = null
            var scrollY = 0
            var scrollYFinal = 0
            var tempPosition: Int = 0
            var y1: Float? = null
            var y2: Float? = null
            for (busInstance in busToShow!!.instances) {
                val textView = MaterialTextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setPadding(
                            dpToPx(this@BusTimeTableViewActivity, 16f),
                            0,
                            dpToPx(this@BusTimeTableViewActivity, 16f),
                            0
                        )
                    }
                    gravity = Gravity.CENTER

                    val strStops = SpannableStringBuilder()
                    for ((i, stop) in busInstance.stops.withIndex()) {
                        val stopTime = stop.stopTime.toInt()
                        val timeLeft = calcTimeLeft(curTime, stopTime)
                        if (i == stopToHighlightIndex) {
                            if (closestBusTimeLeft > timeLeft) {
                                closestBusTimeLeft = timeLeft
                                closestBusTextView = this
                                text = strStops
                                this.measure(0, 0)
                                y1 = this.measuredHeight.toFloat()
                                scrollY = strStops.lines().size
                            }
                            strStops.bold {
                                scale(1.5f) {
                                    append(
                                        "${
                                            BusMapManager.getStopWithStopNo(
                                                stop.stopNo
                                            )?.stopName ?: "Unknown"
                                        } (${stop.stopTime})\n($timeLeft mins)\n\n"
                                    )
                                }
                            }
                        } else {
                            strStops.append("${BusMapManager.getStopWithStopNo(stop.stopNo)?.stopName ?: "Unknown"} (${stop.stopTime})\n($timeLeft mins)\n\n")
                        }
                    }
                    text = strStops
                    this.measure(0, 0)
                    y2 = this.measuredHeight.toFloat()
                    scrollYFinal = strStops.lines().size
//                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
//                setLineSpacing(1f, 2f)
                }
                runOnUiThread {
                    progressBar.visibility = View.INVISIBLE
                    timeTableContainer.addView(textView)
                }
            }

            // Scroll to instance with closest bus for selected stop
//        scrollToView(scrollView, closestBusTextView!!)
//        Handler().postDelayed({ scrollView.scrollTo(closestBusTextView!!.left, 0) }, 500)
            horizontalScrollView.viewTreeObserver.addOnGlobalLayoutListener(object :
                ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    val scrollX =
                        (closestBusTextView!!.left + closestBusTextView!!.right - horizontalScrollView.width) / 2
//                val scrollX = closestBusTextView!!.left
                    val baseHeight =
                        closestBusTextView!!.bottom - closestBusTextView!!.top*//*-verticalScrollView.height*//*
                    val scrollYnew = *//*closestBusTextView!!.top+*//*
                        (if (baseHeight < 0) 0 else baseHeight) * (y1!! / y2!!)*//*closestBusTextView!!.lineCount*//*
//                scrollView.scrollTo(scrollX, 0)
//                Handler().postDelayed (
//                    {
                    horizontalScrollView.scrollTo(scrollX, 0)
//                        timeTableContainer.scrollTo(0, scrollYnew.toInt())
                    verticalScrollView.scrollTo(
                        0,
                        y1!!.toInt() - verticalScrollView.marginTop - (verticalScrollView.height * 0.4).toInt()
                    )
//                    }, 1000)
                    horizontalScrollView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            })*/
        }.start()
    }

    fun scrollToView(scrollView: HorizontalScrollView, view: TextView) {

        // View needs a focus
        view.requestFocus()

        // Determine if scroll needs to happen
        val scrollBounds = Rect()
        scrollView.getHitRect(scrollBounds)
        if (!view.getLocalVisibleRect(scrollBounds)) {
            scrollView.smoothScrollTo(view.getLeft(), 0)
//            Handler().post(Runnable { scrollView.smoothScrollTo(view.getLeft(), 0) })
        }
    }

    private open class TableCell(val data: Any)

    /*private class TableColumnHeader(data: Any): TableCell(data)
    private class TableRowHeader(data: Any): TableCell(data)*/

    class EntireTimeTableAdapter: AbstractTableAdapter<String, String, EntireTimeTableAdapter.TimeData>() {
        class TimeData(val time: SpannableStringBuilder, val timeLeft: SpannableStringBuilder)

        class TableCellViewHolder(itemView: View): AbstractViewHolder(itemView) {
            val cellContainer: ConstraintLayout = itemView.findViewById(R.id.cellContainer)
            val tvScheduledTime: TextView = itemView.findViewById(R.id.tvScheduledTime)
            val tvTimeLeft: TextView = itemView.findViewById(R.id.tvTimeLeft)
        }

        override fun onCreateCellViewHolder(parent: ViewGroup, viewType: Int): AbstractViewHolder {
            val layout = LayoutInflater.from(parent.context)
                .inflate(R.layout.entire_timetable_cell, parent, false)
            return TableCellViewHolder(layout)
        }

        override fun onBindCellViewHolder(
            holder: AbstractViewHolder,
            cellItemModel: TimeData?,
            columnPosition: Int,
            rowPosition: Int
        ) {
            if (holder is TableCellViewHolder) {
                holder.tvScheduledTime.text = cellItemModel?.time ?: ""
                cellItemModel?.let {
                    holder.tvScheduledTime.text = it.time
                    holder.tvTimeLeft.text = it.timeLeft
                    /*holder.tvTimeLeft.text = "(${it.timeLeft} mins)"
                    holder.tvScheduledTime.setTypeface(holder.tvScheduledTime.typeface, it.typeface)
                    holder.tvTimeLeft.setTypeface(holder.tvTimeLeft.typeface, it.typeface)*/
                }
//                val typeface = if (cellItemModel?.highlight == true) Typeface.BOLD else Typeface.NORMAL
//                holder.tvScheduledTime.setTypeface(holder.tvScheduledTime.typeface, typeface)
//                holder.tvTimeLeft.setTypeface(holder.tvTimeLeft.typeface, .typeface)

                // Remeasure
                holder.cellContainer.layoutParams.width =
                    ConstraintLayout.LayoutParams.WRAP_CONTENT
                holder.tvScheduledTime.requestLayout()
                holder.tvTimeLeft.requestLayout()
            }
        }

        class TableColumnHeaderViewHolder(itemView: View): AbstractViewHolder(itemView) {
            val columnHeaderContainer: ConstraintLayout = itemView.findViewById(R.id.columnHeaderContainer)
            val tvColumnHeader: TextView = itemView.findViewById(R.id.tvColumnHeader)
        }

        override fun onCreateColumnHeaderViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): AbstractViewHolder {
            val layout = LayoutInflater.from(parent.context)
                .inflate(R.layout.entire_timetable_column_header, parent, false)
            return TableColumnHeaderViewHolder(layout)
        }

        override fun onBindColumnHeaderViewHolder(
            holder: AbstractViewHolder,
            columnHeaderItemModel: String?,
            columnPosition: Int
        ) {
            if (holder is TableColumnHeaderViewHolder) {
                holder.columnHeaderContainer.layoutParams.width =
                    ConstraintLayout.LayoutParams.WRAP_CONTENT
                holder.tvColumnHeader.requestLayout()
            }
        }

        class TableRowHeaderViewHolder(itemView: View): AbstractViewHolder(itemView) {
            val rowHeaderContainer: ConstraintLayout = itemView.findViewById(R.id.rowHeaderContainer)
            val tvRowHeader: TextView = itemView.findViewById(R.id.tvRowHeader)
        }

        override fun onCreateRowHeaderViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): AbstractViewHolder {
            val layout = LayoutInflater.from(parent.context)
                .inflate(R.layout.entire_timetable_row_header, parent, false)
            return TableRowHeaderViewHolder(layout)
        }

        override fun onBindRowHeaderViewHolder(
            holder: AbstractViewHolder,
            rowHeaderItemModel: String?,
            rowPosition: Int
        ) {
            if (holder is TableRowHeaderViewHolder) {
                holder.tvRowHeader.isSelected = true // for marquee effect
                holder.tvRowHeader.text = rowHeaderItemModel
            }
        }

        override fun onCreateCornerView(parent: ViewGroup): View {
            return LayoutInflater.from(parent.context)
                .inflate(R.layout.entire_timetable_corner, parent, false)
        }

        override fun getColumnHeaderItemViewType(position: Int): Int {
            return 0
        }

        override fun getRowHeaderItemViewType(position: Int): Int {
            return 0
        }

        override fun getCellItemViewType(position: Int): Int {
            return 0
        }
    }
}