package kyklab.humphreysbus.utils

import android.animation.Animator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.graphics.*
import android.provider.Settings
import android.text.style.ReplacementSpan
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.*
import android.widget.AdapterView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.core.graphics.ColorUtils
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import kyklab.humphreysbus.App
import java.util.*
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

fun Context.toast(text: String? = null) {
    Toast.makeText(this, text ?: "", Toast.LENGTH_SHORT).show()
}

val Context.lbm: LocalBroadcastManager
    get() = LocalBroadcastManager.getInstance(this)

fun LocalBroadcastManager.sendBroadcast(vararg intents: Intent) {
    intents.forEach { sendBroadcast(it) }
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
            windowManager.defaultDisplay.getRealMetrics(metrics)
        }
        return metrics.widthPixels
    }

val Activity.screenHeight: Int
    get() {
        val metrics = DisplayMetrics()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            display?.getRealMetrics(metrics)
        } else {
            windowManager.defaultDisplay.getRealMetrics(metrics)
        }
        return metrics.heightPixels
    }

val Activity.statusBarHeight: Int
    get() {
        var result = -1
        val resId = resources.getIdentifier("status_bar_height", "dimen", "android");
        if (resId > 0) {
            result = resources.getDimensionPixelSize(resId);
        }
        return result
    }

val Activity.navigationBarHeight: Int
    get() {
        var result = -1
        val resId = resources.getIdentifier("navigation_bar_height", "dimen", "android");
        if (resId > 0) {
            result = resources.getDimensionPixelSize(resId);
        }
        return result
    }

fun Activity.getDimension(@DimenRes id: Int) = resources.getDimension(id)

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

@SuppressLint("ClickableViewAccessibility")
fun <T : Activity, S : View> T.moveViewOnDrag(
    clicked: S,
    target: S = clicked,
    allowOffBounds: Boolean = false
) {
    var dX = 0f
    var dY = 0f
    target.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
    val width = target.measuredWidth
    val height = target.measuredHeight
    val statusBarHeight = statusBarHeight
    // val navigationBarHeight = if (hasNavigationBar) navigationBarHeight else 0
    val navigationBarHeight = 0 // TODO: Add a reliable way to handle navigation bar height
    val topBorder = statusBarHeight
    val bottomBorder = screenHeight - navigationBarHeight
    clicked.setOnTouchListener { v, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dX = target.x - event.rawX
                dY = target.y - event.rawY
            }
            MotionEvent.ACTION_MOVE -> {
                var newX = event.rawX + dX
                var newY = event.rawY + dY
                if (!allowOffBounds) {
                    val left = newX
                    val right = newX + width
                    val top = newY
                    val bottom = newY + height

                    if (left < 0) newX = 0f
                    else if (right > screenWidth) newX = screenWidth.toFloat() - width
                    if (top < topBorder) newY = topBorder.toFloat()
                    else if (bottom > bottomBorder) newY = bottomBorder.toFloat() - height
                }
                target.animate()
                    .x(newX)
                    .y(newY)
                    .setDuration(0)
                    .start()
            }
            else -> return@setOnTouchListener false
        }
        v.performClick()
        true
    }
}

@SuppressLint("ClickableViewAccessibility")
fun <T : Activity, S : View> T.attachViewOnLeft(
    target: S,
    attached: S = target,
    allowOffBounds: Boolean = false
) {
    attached.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)

    val updateLocation = {
        val arr = IntArray(2)
        target.getLocationOnScreen(arr)
        val targetX = arr[0]
        val targetY = arr[1]
        val margin = dpToPx(this@attachViewOnLeft, 4f)
        var attachedX = targetX - attached.measuredWidth - margin
        if (!allowOffBounds) attachedX = max(0, attachedX)
        val attachedY = targetY
        attached.animate()
            .setDuration(0)
            .x(attachedX.toFloat())
            .y(attachedY.toFloat())
            .start()
    }

    updateLocation()

    target.animate().setListener(object : Animator.AnimatorListener {
        override fun onAnimationStart(animation: Animator) {}

        override fun onAnimationEnd(animation: Animator) {
            updateLocation()
        }

        override fun onAnimationCancel(animation: Animator) {}

        override fun onAnimationRepeat(animation: Animator) {}
    })
}

/*
val currentTimeHHmm: String
    @SuppressLint("SimpleDateFormat")
    get() = SimpleDateFormat("HHmm").format(Date())

val currentTimeHHmmss: String
    @SuppressLint("SimpleDateFormat")
    get() = SimpleDateFormat("HHmmss").format(Date())

val currentTimemmss: String
    @SuppressLint("SimpleDateFormat")
    get() = SimpleDateFormat("mmss").format(Date())

/**
 * Calculate minutes in HHmm format between `from` to `to`
 */
fun calcMinsLeft(from_HHmm: Int, to_HHmm: Int): Int {
    var fromH = from_HHmm / 100
    var fromM = from_HHmm % 100
    var toH = to_HHmm / 100
    var toM = to_HHmm % 100

    if (from_HHmm > to_HHmm) toH += 24

    val fromMins = fromH * 60 + fromM
    val toMins = toH * 60 + toM

    return toMins - fromMins
}

/**
 * Calculate seconds in HHmmss format between `from` to `to`
 */
fun calcSecsLeft(from_HHmmss: Int, to_HHmmss: Int): Int {
    var fromH = from_HHmmss / 10000
    var fromM = from_HHmmss / 100 % 100
    var fromS = from_HHmmss % 100
    var toH = to_HHmmss / 10000
    var toM = to_HHmmss / 100 % 100
    var toS = to_HHmmss % 100

    val fromSecs = fromH * 3600 + fromM * 60 + fromS
    val toSecs = toH * 3600 + toM * 60 + toS

    return toSecs - fromSecs
}

fun minToHH_mm(totalMins: Int) =
    "${(totalMins / 60 % 24).format("%02d")}:${(totalMins % 60).format("%02d")}"

@SuppressLint("SimpleDateFormat")
fun minToHH_mm(totalMins: Int): String {
    val cal = Calendar.getInstance()
    cal.set(Calendar.MINUTE, totalMins)
    return SimpleDateFormat("HH:mm").format(cal)
}
*/

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

/**
 * Get animation duration independent of user's animation scale preference in dev settings
 */
fun getRealAnimDuration(orig: Long): Long {
    val userAnimScale = Settings.Global.getFloat(
        App.context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
    )
    return (orig / userAnimScale).roundToLong()
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

@ColorInt
fun @receiver:ColorInt Int.darken(ratio: Float = 0.2f) =
    ColorUtils.blendARGB(this, Color.BLACK, ratio)

inline val @receiver:ColorInt Int.isBright: Boolean
    get() = ColorUtils.calculateLuminance(this) > 0.5

@ColorInt
fun Context.getLegibleColorOnBackground(
    @ColorInt background: Int,
    @ColorRes onBrightBackground: Int,
    @ColorRes onDarkBackground: Int
) = resources.getColor(
    if (background.isBright) onBrightBackground else onDarkBackground, theme
)

val TYPEFACE_SANS_SERIF_CONDENSED: Typeface =
    Typeface.create("sans-serif-condensed", Typeface.NORMAL)

// Set text color for spinner which uses android.R.layout.simple_spinner_dropdown_item.. probably.
fun Spinner.setTextColor(@ColorInt color: Int) {
    val origListener = this.onItemSelectedListener
    onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            origListener?.onItemSelected(parent, view, position, id)
            (parent?.getChildAt(0) as TextView)?.setTextColor(color)
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
            origListener?.onNothingSelected(parent)
        }
    }
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
