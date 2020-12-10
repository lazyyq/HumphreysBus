package kyklab.test.subwaymap.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.android.synthetic.main.fragment_stop_info.view.*
import kyklab.test.subwaymap.R
import kyklab.test.subwaymap.bus.BusUtils
import kyklab.test.subwaymap.bus.Buses
import java.util.*

class StopInfoDialog : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_stop_info, container, false)
        arguments ?: dismiss()

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        showBuses()
    }

    private fun showBuses() {
        val v = requireView()
        val activity = requireActivity()

        val stopId = requireArguments().getInt(ARGUMENT_STOP_ID, -1)
        if (stopId == -1) return

        val stop = BusUtils.getStopWithId(stopId)
        v.tvStopInfo.text = stop.stopName

        Thread {
            val items = ArrayList<StopInfoDialogAdapter.AdapterItem>()
            for (bus in Buses.buses) {
                if (bus.instances.isEmpty()) continue
                for (s in bus.instances[0].stops) {
                    if (s.stopNo == stop.stopNo) {
                        items.add(StopInfoDialogAdapter.AdapterItem(bus, stop.stopNo))
                        break
                    }
                }
            }

            val adapter = StopInfoDialogAdapter(activity, items)
            activity.runOnUiThread {
                v.vpTimeTable.adapter = adapter
                TabLayoutMediator(v.busTabLayout, v.vpTimeTable) { tab, position ->
                    tab.text = adapter.items[position].bus.name
                }.attach()

                v.progressBar.visibility = View.GONE

                /*val pageMarginPx = resources.getDimensionPixelOffset(R.dimen.pageMargin)
            val pagerWidth = resources.getDimensionPixelOffset(R.dimen.pagerWidth)
            val screenWidth = resources.displayMetrics.widthPixels
            val offsetPx = screenWidth - pageMarginPx - pagerWidth*/

                /*v.vpTimeTable.setPageTransformer { page, position ->
                page.translationX = position * -dpToPx(activity, 96f)
            }*/
            }
        }.start()
    }

    companion object {
        const val ARGUMENT_STOP_ID = "argument_id"

        fun showBusSchedules(activity: Activity, busName: String, stopIndex: Int) {
            val intent = Intent(activity, BusDetailsActivity::class.java).apply {
                putExtra("busname", busName)
                putExtra("highlightstopindex", stopIndex)
            }
            activity.startActivity(intent)
        }
    }
}