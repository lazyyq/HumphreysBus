package kyklab.test.subwaymap.ui

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.android.synthetic.main.fragment_stop_info_dialog.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kyklab.test.subwaymap.R
import kyklab.test.subwaymap.bus.BusUtils
import java.util.*

class StopInfoDialog : BottomSheetDialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_stop_info_dialog, container, false)
        arguments ?: dismiss()

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        showBuses()
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

    private fun showBuses() {
        val v = requireView()
        val activity = requireActivity()

        val stopId = requireArguments().getInt(ARGUMENT_STOP_ID, -1)
        if (stopId == -1) return

        val stop = BusUtils.getStopWithId(stopId)
        v.tvStopInfo.text = stop.stopName

        lifecycleScope.launch(Dispatchers.Default) {
            val items = ArrayList<StopInfoDialogAdapter.AdapterItem>()
            for (bus in BusUtils.buses) {
                if (bus.instances.isEmpty()) continue
                for (s in bus.stopPoints) {
                    if (s.stopNo == stop.stopNo) {
                        items.add(StopInfoDialogAdapter.AdapterItem(bus, stop.stopNo))
                        break
                    }
                }

                bus.stopPoints.find { s -> s.stopNo == stop.stopNo }?.let {
                    items.add(StopInfoDialogAdapter.AdapterItem(bus, stop.stopNo))
                }
            }

            val adapter = StopInfoDialogAdapter(activity, lifecycleScope, items)
            launch(Dispatchers.Main) {
                v.vpTimeTable.adapter = adapter
                TabLayoutMediator(v.busTabLayout, v.vpTimeTable) { tab, position ->
                    tab.text = adapter.items[position].bus.name
                }.attach()

                v.progressBar.visibility = View.GONE

                // Adjust peek (default expanded) height to match that of its contents size
                v.bottomSheetContents.viewTreeObserver.addOnGlobalLayoutListener(
                    object : ViewTreeObserver.OnGlobalLayoutListener {
                        override fun onGlobalLayout() {
                            val bottomSheetDialog =
                                dialog?.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                            bottomSheetDialog?.let {
                                val behavior = BottomSheetBehavior.from(it)
                                behavior.peekHeight = v.bottomSheetContents.measuredHeight
                            }
                            v.bottomSheetContents.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        }
                    }
                )

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