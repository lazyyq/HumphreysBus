package kyklab.humphreysbus.ui

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.res.Configuration
import android.icu.util.Calendar
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.widget.DatePicker
import android.widget.TimePicker
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import kyklab.humphreysbus.R

class DateTimePickerFragment(
    private val calendar: Calendar = Calendar.getInstance(),
    private val listener: OnDateTimeSetListener
) : DialogFragment() {
    private lateinit var pContext: Context

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        try {
            pContext = requireContext()
        } catch (e: IllegalStateException) {
            dismiss()
        }
        return DateTimePickerDialog(pContext, calendar, listener)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        dismiss()
//        dialog?.setContentView(R.layout.dialog_datetime_picker)
        show(parentFragmentManager, tag)
    }

    private class DateTimePickerDialog(
        context: Context,
        private val calendar: Calendar = Calendar.getInstance(),
        private val listener: OnDateTimeSetListener? = null
    ) : AlertDialog(context, resolveDialogTheme(context)), DialogInterface.OnClickListener {
        private val datePicker: DatePicker
        private val timePicker: TimePicker

        companion object {
            private fun resolveDialogTheme(context: Context): Int {
                val outValue = TypedValue()
                context.theme.resolveAttribute(R.attr.materialTimePickerTheme, outValue, true)
                return outValue.resourceId
            }
        }

        init {
            val inflater = LayoutInflater.from(context)
            val view = inflater.inflate(R.layout.dialog_datetime_picker, null)
            setView(view)

            datePicker = view.findViewById(R.id.datePicker)
            timePicker = view.findViewById(R.id.timePicker)

            setButton(BUTTON_POSITIVE, context.getString(android.R.string.ok), this)
            setButton(BUTTON_NEGATIVE, context.getString(android.R.string.cancel), this)
            setButton(BUTTON_NEUTRAL, "Current", this)

            setTime(calendar)

            setOnShowListener {
                getButton(BUTTON_POSITIVE)?.setOnClickListener {
                    // Clearing focus forces the dialog to commit any pending
                    // changes, e.g. typed text in a NumberPicker.
                    datePicker.clearFocus()
                    timePicker.clearFocus()

                    if (listener != null) {
                        val year = datePicker.year
                        val month = datePicker.month
                        val dayOfMonth = datePicker.dayOfMonth
                        val hourOfDay = timePicker.hour
                        val minute = timePicker.minute
                        listener.onDateTimeSet(year, month, dayOfMonth, hourOfDay, minute)
                    }

                    dismiss()
                }
                getButton(BUTTON_NEUTRAL)?.setOnClickListener {
                    setTime()
                }
            }
        }

        override fun onClick(dialog: DialogInterface?, which: Int) {}

        private fun setTime(cal: Calendar = Calendar.getInstance()) {
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH)
            val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
            val hourOfDay = cal.get(Calendar.HOUR_OF_DAY)
            val minute = cal.get(Calendar.MINUTE)

            datePicker.updateDate(year, month, dayOfMonth)
            timePicker.hour = hourOfDay
            timePicker.minute = minute
        }
    }

    fun interface OnDateTimeSetListener {
        /**
         * @param year       the selected year
         * @param month      the selected month (0-11 for compatibility with {@link Calendar#MONTH})
         * @param dayOfMonth the selected day of the month (1-31, depending on month)
         * @param hourOfDay  the hour that was set
         * @param minute     the minute that was set
         */
        fun onDateTimeSet(year: Int, month: Int, dayOfMonth: Int, hourOfDay: Int, minute: Int)
    }
}
