package kyklab.humphreysbus.ui.stopinfodialog

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.CompoundButton
import android.widget.FrameLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.android.synthetic.main.fragment_stop_info_dialog.*
import kotlinx.android.synthetic.main.fragment_stop_info_dialog.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kyklab.humphreysbus.R
import kyklab.humphreysbus.bus.BusUtils
import kyklab.humphreysbus.ui.BusDetailsActivity
import kyklab.humphreysbus.utils.currentTimeHHmm
import kyklab.humphreysbus.utils.insert
import kyklab.humphreysbus.utils.isHoliday

class StopInfoDialog : BottomSheetDialogFragment() {
    private val calendar = Calendar.getInstance()
    private var currentTime = currentTimeHHmm
    private val sdf by lazy { SimpleDateFormat("HHmm") }
    private var isHoliday = isHoliday()
    private var stopId = -1

    private var loadBusJob: Job? = null

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
        showCurrentTime()
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
        val view = requireView()

        stopId = requireArguments().getInt(ARGUMENT_STOP_ID, -1)
        val stop = BusUtils.getBusStop(stopId) ?: return
        view.tvStopInfo.text = stop.name

        updateBuses()
    }

    private fun updateBuses() {
        val view = requireView()
        val activity = requireActivity()

        view.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.Default) {
            loadBusJob?.let { if (it.isActive) it.cancelAndJoin() }
            loadBusJob = launch {
                val adapter =
                    StopInfoDialogAdapter(activity, lifecycleScope, stopId, currentTime, isHoliday)
                launch(Dispatchers.Main) {
                    view.vpTimeTable.adapter = adapter
                    TabLayoutMediator(view.busTabLayout, view.vpTimeTable) { tab, position ->
                        tab.text = adapter.adapterItems[position].bus.name
                    }.attach()

                    view.progressBar.visibility = View.GONE

                    // Adjust peek (default expanded) height to match that of its contents size
                    view.bottomSheetContents.viewTreeObserver.addOnGlobalLayoutListener(
                        object : ViewTreeObserver.OnGlobalLayoutListener {
                            override fun onGlobalLayout() {
                                val bottomSheetDialog =
                                    dialog?.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                                bottomSheetDialog?.let {
                                    val behavior = BottomSheetBehavior.from(it)
                                    behavior.peekHeight = view.bottomSheetContents.measuredHeight
                                }
                                view.bottomSheetContents.viewTreeObserver
                                    .removeOnGlobalLayoutListener(this)
                            }
                        }
                    )
                }
            }
        }
    }

    private fun showCurrentTime() {
        updateDateTime()

        switchHoliday.setOnClickListener {
            if (it is CompoundButton) {
                isHoliday = it.isChecked
            }
            updateDateTime()
            updateBuses()
        }

        tvCurrentTime.setOnClickListener {
            DatePickerFragment(calendar) { d, year, month, dayOfMonth ->
                TimePickerFragment(calendar) { t, hourOfDay, minute ->
                    calendar.set(year, month, dayOfMonth, hourOfDay, minute)
                    currentTime = sdf.format(calendar.time)
                    isHoliday = calendar.time.isHoliday()
                    updateDateTime()
                    updateBuses()
                }.show(parentFragmentManager, "timePicker")
            }.show(parentFragmentManager, "datePicker")
        }
    }

    private fun updateDateTime() {
        switchHoliday.isChecked = isHoliday // TODO: Check if this triggers listener
        tvCurrentTime.text = "As of ${currentTime.insert(2, ":")}"
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