package kyklab.humphreysbus

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import android.text.style.ReplacementSpan
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import kotlinx.android.synthetic.main.activity_bus_details.*
import java.util.*
import kotlin.math.roundToInt


fun Context.toast(text: String? = null) {
    Toast.makeText(this, text ?: "", Toast.LENGTH_SHORT).show()
}

fun dpToPx(context: Context, dp: Float): Int {
    val dm = context.resources.displayMetrics
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, dm).toInt()
}

val Activity.screenWidth: Int
    get() {
        val metrics = DisplayMetrics()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            display?.getRealMetrics(metrics)
        } else {
            windowManager.defaultDisplay.getMetrics(metrics)
        }
        return metrics.widthPixels
    }

val <T : View> T.parentRelativeCoordinates: Rect
    get() {
        val bounds = Rect()
        val p = parent
        getDrawingRect(bounds)
        if (p is ViewGroup) {
            p.offsetDescendantRectToMyCoords(this, bounds)
        }
        return bounds
    }

fun <T : View> T.onViewReady(block: T.() -> Unit) {
    viewTreeObserver.addOnGlobalLayoutListener(
        object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                viewTreeObserver.removeOnGlobalLayoutListener(this)
                this@onViewReady.block()
            }
        }
    )
}

val currentTimeHHmm: String
    @SuppressLint("SimpleDateFormat")
    get() = SimpleDateFormat("HHmm").format(Date())

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

fun minToHH_mm(totalMins: Int) =
    "${(totalMins / 60 % 24).format("%02d")}:${(totalMins % 60).format("%02d")}"

/*
@SuppressLint("SimpleDateFormat")
fun minToHH_mm(totalMins: Int): String {
    val cal = Calendar.getInstance()
    cal.set(Calendar.MINUTE, totalMins)
    return SimpleDateFormat("HH:mm").format(cal)
}
*/

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

fun <T> Array<T>.getWithWrappedIndex(index: Int): T? =
    if (isEmpty()) {
        null
    } else {
        var i = index
        while (i < 0) i += size
        get(i % size)
    }

fun <T> List<T>.getWithWrappedIndex(index: Int): T? =
    if (isEmpty()) {
        null
    } else {
        var i = index
        while (i < 0) i += size
        get(i % size)
    }

fun String.insert(position: Int, str: CharSequence): String =
    StringBuffer().apply {
        append(this@insert.substring(0, position))
        append(str)
        append(this@insert.substring(position, this@insert.length))
    }.toString()

inline fun <T : Cursor> T.forEachCursor(block: (T) -> Unit): T {
    this.use {
        if (moveToFirst()) {
            do {
                block(this)
            } while (moveToNext())
        }
    }
    return this
}

fun isHoliday() = Date().isHoliday()

fun Date.isHoliday(): Boolean {
    val cal = Calendar.getInstance()
    cal.time = this
    return ((cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY)
            || (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY))
}

@JvmOverloads
fun SQLiteDatabase.kQuery(
    table: String, columns: Array<String>? = null, selection: String? = null,
    selectionArgs: Array<String>? = null, groupBy: String? = null, having: String? = null,
    orderBy: String? = null, limit: String? = null
): Cursor = query(
    table, columns, selection, selectionArgs, groupBy, having, orderBy, limit
)

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
    theme.resolveAttribute(attrResId, typedValue, true)
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
