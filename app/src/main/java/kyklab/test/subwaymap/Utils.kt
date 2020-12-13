package kyklab.test.subwaymap

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import android.text.style.ReplacementSpan
import android.util.TypedValue
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import java.util.*
import kotlin.math.roundToInt


fun Context.toast(text: String? = null) {
    Toast.makeText(this, text ?: "", Toast.LENGTH_SHORT).show()
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

@SuppressLint("SimpleDateFormat")
fun minToHH_mm(totalMins: Int): String {
    val cal = Calendar.getInstance()
    cal.set(Calendar.MINUTE, totalMins)
    return SimpleDateFormat("HH:mm").format(cal)
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

fun Int.format(format: String): String = String.format(format, this)

fun <T> List<T>.getWithWrappedIndex(index: Int): T? {
    return if (isEmpty()) null
    else {
        var i = index
        while (i < 0) i += size
        return get(i % size)
    }
}

fun String.insert(position: Int, str: CharSequence): String =
    StringBuffer().apply {
        append(this@insert.substring(0, position))
        append(str)
        append(this@insert.substring(position, this@insert.length))
    }.toString()

// https://al-e-shevelev.medium.com/how-to-reduce-scroll-sensitivity-of-viewpager2-widget-87797ad02414
fun ViewPager2.reduceDragSensitivity() {
    val rvField = ViewPager2::class.java.getDeclaredField("mRecyclerView")
    rvField.isAccessible = true
    val rv = rvField.get(this) as RecyclerView

    val touchSlopField = RecyclerView::class.java.getDeclaredField("mTouchSlop")
    touchSlopField.isAccessible = true
    val touchSlop = touchSlopField.get(rv) as Int
    touchSlopField.set(rv, touchSlop * 2)
}

fun Context.getResId(attrResId: Int): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(android.R.attr.listDivider, typedValue, true)
    return typedValue.resourceId
}

class RoundedBackgroundSpan(
    private val context: Context,
    @ColorInt private val backgroundColor: Int,
    @ColorInt private val textColor: Int
) : ReplacementSpan() {
    companion object {
        private const val CORNER_RADIUS = 8f
        private const val SIDE_MARGIN = 8f
        private const val TOP_MARGIN = 8f
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val sideMargin = dpToPx(context, SIDE_MARGIN)
        val topMargin = dpToPx(context, TOP_MARGIN)
        val rect =
            RectF(
                x - sideMargin, top.toFloat() - topMargin,
                x + measureText(paint, text, start, end) + sideMargin, bottom.toFloat()
            )
        paint.color = backgroundColor
        canvas.drawRoundRect(rect, CORNER_RADIUS, CORNER_RADIUS, paint)
        paint.color = textColor
        canvas.drawText(text, start, end, x, y.toFloat(), paint)
    }

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        return paint.measureText(text, start, end).roundToInt()
    }

    private fun measureText(paint: Paint, text: CharSequence, start: Int, end: Int): Float {
        return paint.measureText(text, start, end)
    }
}
