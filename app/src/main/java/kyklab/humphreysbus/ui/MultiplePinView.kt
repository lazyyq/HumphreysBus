package kyklab.humphreysbus.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import java.util.concurrent.CopyOnWriteArrayList

class MultiplePinView @JvmOverloads constructor(context: Context?, attr: AttributeSet? = null) :
    SubsamplingScaleImageView(context, attr) {
    companion object {
        private const val TAG = "MultiplePinView"
    }

    var simpleScaleThreshold = 0f

    private val paint by lazy {
        Paint().apply { isAntiAlias = true }
    }

    val pins: MutableList<Pin> = CopyOnWriteArrayList()

    private val drawRect = Rect(0, 0, 0, 0)

    fun addPin(pin: Pin): Int {
        if (pin.pinCoord.x < 0 || pin.pinCoord.y < 0) return -1
        pins.add(pin)
        pins.sortBy { it.priority }
        invalidate()
        return pins.size - 1
    }

    fun removePin(pinIndex: Int): Boolean {
        return if (pinIndex < 0 || pinIndex > pins.lastIndex) {
            false
        } else {
            pins.removeAt(pinIndex)
            invalidate()
            true
        }
    }

    fun removePin(point: PointF): Boolean {
        val result = pins.removeIf { p -> point == p.pinCoord }
        invalidate()
        return result
    }

    fun removePin(pin: Pin): Boolean {
        val result = pins.remove(pin)
        invalidate()
        return result
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Don't draw pin before image is ready so it doesn't move around during setup.
        if (!isReady) {
            return
        }

        pins.forEach { pin ->
            sourceToViewCoord(pin.pinCoord)?.let {
                val bitmap =
                    if (pin.bitmapSimple != null && simpleScaleThreshold > 0 && scale <= simpleScaleThreshold)
                        pin.bitmapSimple!!
                    else
                        pin.bitmap
                val coord = pin.imageCoord(it, bitmap.width.toFloat(), bitmap.height.toFloat())
                val x = coord.x
                val y = coord.y
                if (pin.autoScale) {
                    drawRect.apply {
                        left = x.toInt()
                        top = y.toInt()
                        right = left + (bitmap.width * scale - 1).toInt()
                        bottom = top + (bitmap.height * scale - 1).toInt()
                    }
                    canvas.drawBitmap(bitmap, null, drawRect, paint)
                } else {
                    canvas.drawBitmap(bitmap, x, y, paint)
                }
            }
        }
    }

    class Pin {
        companion object {
            const val DEFAULT_PRIORITY = 10
        }

        var name: String?
        var pinCoord: PointF // Coordinates of the pin on the map
        var bitmap: Bitmap // Image of the pin
        var bitmapSimple: Bitmap? // Small version of the image, used when e.g. zoomed out
        var autoScale: Boolean // Whether to scale automatically along with background image
        var priority: Int // Priority of the pin, pin with higher priority is placed on top

        // Coordinates of the image of the pin relative to its coordinates on the map
        var imageCoord: (coord: PointF, pinWidth: Float, pinHeight: Float) -> PointF

        constructor(
            context: Context,
            name: String? = null,
            pinCoord: PointF,
            @DrawableRes resId: Int,
            @DrawableRes resIdSimple: Int? = null,
            autoScale: Boolean = false,
            priority: Int = DEFAULT_PRIORITY,
            imageCoord: ((coord: PointF, pinWidth: Float, pinHeight: Float) -> PointF) =
                { coord, _, _ -> coord }
        ) {
            this.name = name
            this.pinCoord = pinCoord
            this.bitmap = AppCompatResources.getDrawable(context, resId)!!.toBitmap()
            this.bitmapSimple =
                resIdSimple?.let { AppCompatResources.getDrawable(context, it)!!.toBitmap() }
            this.autoScale = autoScale
            this.priority = priority
            this.imageCoord = imageCoord
        }

        constructor(
            name: String? = null,
            pinCoord: PointF,
            bitmap: Bitmap,
            bitmapSimple: Bitmap? = null,
            autoScale: Boolean = false,
            priority: Int = DEFAULT_PRIORITY,
            imageCoord: ((coord: PointF, pinWidth: Float, pinHeight: Float) -> PointF) =
                { coord, _, _ -> coord }
        ) {
            this.name = name
            this.pinCoord = pinCoord
            this.bitmap = bitmap
            this.bitmapSimple = bitmapSimple
            this.autoScale = autoScale
            this.priority = priority
            this.imageCoord = imageCoord
        }

        fun setPinSize(width: Int, height: Int) {
            bitmap = bitmap.scale(width, height)
        }

        fun setSimplePinSize(width: Int, height: Int) {
            if (bitmapSimple == null) {
                bitmapSimple = bitmap.copy(bitmap.config, true)
            }
            bitmapSimple = bitmapSimple!!.scale(width, height)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Pin

            if (name != other.name) return false
            if (pinCoord != other.pinCoord) return false
            if (bitmap != other.bitmap) return false

            return true
        }

        override fun hashCode(): Int {
            var result = name?.hashCode() ?: 0
            result = 31 * result + pinCoord.hashCode()
            result = 31 * result + bitmap.hashCode()
            return result
        }
    }
}