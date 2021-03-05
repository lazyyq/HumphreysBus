package kyklab.humphreysbus.ui

import android.content.Context
import android.icu.util.Calendar
import androidx.fragment.app.DialogFragment
import kotlinx.android.synthetic.main.dialog_datetime_picker.*
import java.util.*

class DateTimePickerDialogFragment(
    context: Context,
    private val calendar: Calendar = Calendar.getInstance(),
    private val onNegativeClicked: DateTimePickerDialog.OnDateTimeSetListener? = null,
    private val onPositiveClicked: DateTimePickerDialog.OnDateTimeSetListener? = null
) : DialogFragment() /*{

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.dialog_datetime_picker)

        *//*window?.attributes?.apply {
            flags = flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
            dimAmount = 0.8f
            window?.attributes = this
        }*//*

        setupCalendar()
        setupCallbacks()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

    }

    private fun setupCalendar() {
        reset.setOnClickListener {
            calendar.time = Date()
            setTime()
        }
        setTime()
    }

    private fun setTime() {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        datePicker.updateDate(year, month, day)
        timePicker.hour = hour
        timePicker.minute = minute
    }

    private fun setupCallbacks() {
        ok.setOnClickListener {
            if (onPositiveClicked != null) {
                val year = datePicker.year
                val month = datePicker.month
                val day = datePicker.dayOfMonth
                val hour = timePicker.hour
                val minute = timePicker.minute

                onPositiveClicked.onDateTimeSet(year, month, day, hour, minute)
            }
            dismiss()
        }

        cancel.setOnClickListener {
            if (onNegativeClicked != null) {
                val year = datePicker.year
                val month = datePicker.month
                val day = datePicker.dayOfMonth
                val hour = timePicker.hour
                val minute = timePicker.minute

                onNegativeClicked.onDateTimeSet(year, month, day, hour, minute)
            }
            dismiss()
        }
    }

    fun interface OnDateTimeSetListener {
        */
/**
 * @param year       the selected year
 * @param month      the selected month (0-11 for compatibility with {@link Calendar#MONTH})
 * @param dayOfMonth the selected day of the month (1-31, depending on month)
 * @param hourOfDay  the hour that was set
 * @param minute     the minute that was set
 *//*
        fun onDateTimeSet(year: Int, month: Int, dayOfMonth: Int, hourOfDay: Int, minute: Int)
    }
}*/
