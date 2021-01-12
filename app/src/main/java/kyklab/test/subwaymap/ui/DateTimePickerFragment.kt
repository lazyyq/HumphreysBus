package kyklab.test.subwaymap.ui.stopinfodialog

import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.icu.util.Calendar
import android.os.Bundle
import androidx.fragment.app.DialogFragment

class DatePickerFragment(
    private val calendar: Calendar? = Calendar.getInstance(),
    private val listener: DatePickerDialog.OnDateSetListener
) :
    DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val c = calendar ?: Calendar.getInstance()
        val year = c.get(Calendar.YEAR)
        val month = c.get(Calendar.MONTH)
        val day = c.get(Calendar.DAY_OF_MONTH)

        return DatePickerDialog(requireContext(), listener, year, month, day)
    }
}

class TimePickerFragment(
    private val calendar: Calendar? = Calendar.getInstance(),
    private val listener: TimePickerDialog.OnTimeSetListener
) :
    DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val c = calendar ?: Calendar.getInstance()
        val hour = c.get(Calendar.HOUR_OF_DAY)
        val minute = c.get(Calendar.MINUTE)

        return TimePickerDialog(requireContext(), listener, hour, minute, true)
    }
}