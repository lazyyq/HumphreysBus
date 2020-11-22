package kyklab.test.subwaymap

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.android.synthetic.main.fragment_station_info.view.*
import java.util.*

class StationInfoBottomSheetDialog : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_station_info, container, false)
        if (arguments != null) {
            val stationId = requireArguments().getInt(ARGUMENT_STATION_ID)
            if (stationId != 0) {
                val station = MapManager.getStationWithId(stationId)
                view.tvStationInfo.text = station.name

                val items = ArrayList<MyAdapter.Item>()
                for (i in 0..2) {
                    items.add(MyAdapter.Item(Buses.buses[i], station.mapNo))
                }

                val adapter= MyAdapter(requireActivity(), items)
                view.vpTimeTable.adapter = adapter
                TabLayoutMediator(view.busTabLayout, view.vpTimeTable) {
                    tab, position ->
                    tab.text = items[position].bus.name
                }.attach()

                view.vpTimeTable.offscreenPageLimit = 1
                // https://blog.gangnamunni.com/post/viewpager2/
                /*val pageMarginPx = dpToPx(requireActivity(), 64f)
                val pagerWidth = dpToPx(requireActivity(), 128f)
                val screenWidth = resources.displayMetrics.widthPixels
                val offsetPx = screenWidth - pageMarginPx - pagerWidth

                view.vpTimeTable.setPageTransformer { page, position ->
                    page.translationX = position * -offsetPx
                }*/

                // V3
                /*val tvs = arrayOf(view.tvRedBus, view.tvBlueBus, view.tvGreenBus)

                view.tvRedBus.setOnClickListener { v ->
                    showBusSchedules(0, station.mapNo)
                }

                view.tvBlueBus.setOnClickListener { v ->
                    showBusSchedules(1, station.mapNo)
                }

                view.tvGreenBus.setOnClickListener { v ->
                    showBusSchedules(2, station.mapNo)
                }

                val sdfWithoutColon = SimpleDateFormat("hhmm")
                val sdfWithColon = SimpleDateFormat("hh:mm")




                val curTime =
                    if (MainActivity.etCustomTime != null && !MainActivity.etCustomTime!!.text.equals("")) {
                        try {
                            MainActivity.etCustomTime!!.text.toString().toInt()
                        } catch (e: Exception) {
                            e.printStackTrace()
                            sdfWithoutColon.format(Date()).toInt()
                        }
                    } else {
                        sdfWithoutColon.format(Date()).toInt()
                    }


                for ((i, bus) in Buses.buses.withIndex()) {
                    if (bus.instances.isEmpty()) continue

                    var curStopIndex: Int? = null // Perhaps replaceable with stationId?
                    for ((j, stop) in bus.instances[0].stops.withIndex()) {
                        if (stop.stopNo == station.mapNo) {
                            curStopIndex = j
                            break
                        }
                    }

                    curStopIndex?.let {
                        var first = true
                        val timetableSpannable = SpannableStringBuilder()
                        for ((j, instance) in bus.instances.withIndex()) {
                            val stopTime = instance.stops[it].time
                            if (first && curTime <= stopTime.toInt()) {
                                    timetableSpannable.bold{append("$stopTime\n(${calcTimeLeft(curTime, stopTime.toInt())} mins)\n\n")}
                                    first = false
                            } else {
                                timetableSpannable.append("$stopTime\n(${calcTimeLeft(curTime, stopTime.toInt())} mins)\n\n")
                            }
                        }
                        tvs[i].text = timetableSpannable
                    }
                }*/

                // V2
                /*for (i in Buses.buses.indices) {
                    var comparison = 0
                    // Get index of current stop from first bus instance
                    var currentStopIndex: Int? = null
                    if (Buses.buses[i].instances.isEmpty()) continue
                    for (ii in Buses.buses[i].instances[0].stops.indices) {
                        val stops = Buses.buses[i].instances[0].stops
                        if (stops[ii].stopNo == station.mapNo) { currentStopIndex = ii; break }
                    }
                    if (currentStopIndex == null) continue

                    // Among bus instances, find one which has the closest stopTime in future
                    for1@for (instance in Buses.buses[i].instances) {
                        comparison++
                        if (curTime < instance.stops[currentStopIndex].time.toInt()) { //TODO: 반대의 경우 24시간을 더해서? 다음날 꺼도 인식하게?
                            val stops = instance.stops

                            val prevStop = if (currentStopIndex > 0) stops[currentStopIndex-1] else null
                            val currStop = stops[currentStopIndex]
                            val nextStop = if (currentStopIndex < stops.size - 1) stops[currentStopIndex + 1] else null

                            val textPrev =
                                if (prevStop != null) "${MapManager.getStationWithMapNo(prevStop.stopNo)?.name ?: "Unknown"}(${prevStop.stopNo}) (${prevStop.time})\n↓\n" else ""
                            val textMiddle =
                                "${MapManager.getStationWithMapNo(currStop.stopNo)!!.name}(${currStop.stopNo}) (${currStop.time})"
                            val textAfter =
                                if (nextStop != null) "\n↓\n${MapManager.getStationWithMapNo(nextStop.stopNo)?.name ?:"Unknown"}(${nextStop.stopNo}) (${nextStop.time})" else ""

                            val spannable = SpannableStringBuilder()
                            spannable.append(textPrev).bold{scale(1.5f) { append(textMiddle) } }.append(textAfter)
                            tvs[i].text = spannable

                            break@for1
                            // TODO: 인스턴스 중 다른 지점의 stopTime에서 같은 stopPoint에 도착하는 경우 고려하기
                            //          예: Red 인스턴스1의 1번 stop 출발시간과 Red 인스턴스2의 1번 stop 도착시간이 비슷함
                        }
                    }

                    Log.e("StationInfoBottomSheetDialog", "comparison occurred $comparison times")
                }*/


                // V1
                /*for2@ for (i in Buses.buses.indices) {
                    var comparison = 0

                    val busInstances = Buses.buses[i].instances
                    for1@ for (j in busInstances.indices) {
                        val stops = busInstances[j].stops
                        // For each bus instances
                        val curTime =
                            if (MainActivity.etCustomTime != null && !MainActivity.etCustomTime!!.text.equals("")) {
                                try {
                                    MainActivity.etCustomTime!!.text.toString().toInt()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    sdfWithoutColon.format(Date()).toInt()
                                }
                            } else {
                                sdfWithoutColon.format(Date()).toInt()
                            }
                        for (k in stops.indices) {
                            if (stops[k].stopNo != station.mapNo) continue
                            val nextStopTime = stops[k].time.toInt()
                            comparison++
                            if (nextStopTime < curTime) continue@for1

                            // TODO: Fix here, get actual before/after stops from buses
                            val textBefore =
                                if (k > 0) "${MapManager.getStationWithMapNo(stops[k-1].stopNo)!!.name}(${stops[k-1].stopNo}) (${stops[k-1].time})\n↓\n" else ""
                            val textMiddle =
                                "${MapManager.getStationWithMapNo(stops[k].stopNo)!!.name}(${stops[k].stopNo}) (${stops[k].time})"
                            val textAfter =
                                if (k < stops.size - 1) "\n↓\n${MapManager.getStationWithMapNo(stops[k+1].stopNo)!!.name}(${stops[k+1].stopNo}) (${stops[k + 1].time})" else ""

                            val spannable = SpannableStringBuilder()
                            spannable.append(textBefore).bold{scale(1.5f) { append(textMiddle) } }.append(textAfter)
                            tvs[i].text = spannable
                        }

                    }
                    Log.e("StationInfoBottomSheetDialog", "comparison occurred $comparison times")
                }*/
            }
        }
        return view
    }

    companion object {
        const val ARGUMENT_STATION_ID = "argument_id"
        fun showBusSchedules(activity: Activity, busIndex: Int, mapNo: String) {
            val intent = Intent(activity, BusViewActivity::class.java).apply {
                putExtra("busindex", busIndex)
                putExtra("highlightstopindex", mapNo)
            }
            activity.startActivity(intent)
        }
    }
}