package kyklab.test.subwaymap

import android.content.Context
import android.util.TypedValue
import android.widget.Toast


fun toast(context: Context? = null, text: String? = null) {
    Toast.makeText(context ?: App.context, text ?: "", Toast.LENGTH_SHORT).show()
}

fun dpToPx(context: Context, dp: Float): Int {
    val dm = context.resources.displayMetrics
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, dm).toInt()
}

/**
 * Calculate minutes in hhmm format between `from` to `to`
 */
fun calcTimeLeft(from: Int, to: Int): Int {
    var fromH = from / 100
    var fromM = from % 100
    var toH = to / 100
    var toM = to % 100

    if (from > to) toH += 24

    val fromMins = fromH * 60 + fromM
    val toMins = toH * 60 + toM

    return toMins - fromMins
}

private const val xBase = 126.974512
private const val yBase = 36.945053

private const val xRatio = 1749 / 0.028094
private const val yRatio = 2115 / 0.026039

fun gMapCoordToLocalMapCoord(x: Double, y: Double): Array<Double>? {
    val xCalculated = (x - xBase) * xRatio
    val yCalculated = 3600 - (y - yBase) * yRatio
    return if ((0 <= xCalculated) && (xCalculated <= 4800) &&
        (0 <= yCalculated) && (yCalculated <= 3600)
    ) {
        arrayOf(xCalculated, yCalculated)
    } else null
}