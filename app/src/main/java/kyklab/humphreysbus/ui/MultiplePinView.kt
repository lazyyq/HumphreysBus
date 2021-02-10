package kyklab.humphreysbus.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView

class MultiplePinView @JvmOverloads constructor(context: Context?, attr: AttributeSet? = null) :
    SubsamplingScaleImageView(context, attr) {
    companion object {
        private const val TAG = "MultiplePinView"
    }

    var simpleScaleThreshold = 0f

    private val paint by lazy {
        Paint().apply { isAntiAlias = true }
    }

    private val pins: MutableList<Pin> by lazy {
        ArrayList()
    }

    fun addPin(pin: Pin): Int {
        if (pin.sPin.x < 0 || pin.sPin.y < 0) return -1
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
        return pins.removeIf { p -> point == p.sPin }
    }

    fun removePin(pin: Pin): Boolean {
        return pins.remove(pin)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Don't draw pin before image is ready so it doesn't move around during setup.
        if (!isReady) {
            return
        }

        pins.forEach { pin ->
            sourceToViewCoord(pin.sPin)?.let {
                val bitmap =
                    if (pin.pinSimple != null && simpleScaleThreshold > 0 && scale <= simpleScaleThreshold)
                        pin.pinSimple!!
                    else
                        pin.pin
                val coord = pin.imageCoord(it, bitmap.width.toFloat(), bitmap.height.toFloat())
                val x = coord.x
                val y = coord.y
                canvas.drawBitmap(bitmap, x, y, paint)
            }
        }
    }

    class Pin {
        companion object {
            val DEFAULT_PRIORITY = 10
        }
        var name: String?
        var sPin: PointF
        var pin: Bitmap
        var pinSimple: Bitmap?
        var priority: Int
        var imageCoord: (coord: PointF, pinWidth: Float, pinHeight: Float) -> PointF

        constructor(
            context: Context,
            name: String?,
            sPin: PointF,
            @DrawableRes resId: Int,
            pinSimple: Bitmap?,
            priority: Int?,
            imageCoord: ((coord: PointF, pinWidth: Float, pinHeight: Float) -> PointF)?
        ) {
            this.name = name
            this.sPin = sPin
            this.pin = AppCompatResources.getDrawable(context, resId)!!.toBitmap()
            this.pinSimple = pinSimple
            this.priority = priority ?: DEFAULT_PRIORITY
            this.imageCoord = imageCoord ?: { coord, _, _ -> coord }
        }

        constructor(
            name: String?,
            sPin: PointF, bitmap: Bitmap,
            pinSimple: Bitmap?,
            priority: Int?,
            imageCoord: ((coord: PointF, pinWidth: Float, pinHeight: Float) -> PointF)?
        ) {
            this.name = name
            this.sPin = sPin
            this.pin = bitmap
            this.pinSimple = pinSimple
            this.priority = priority ?: DEFAULT_PRIORITY
            this.imageCoord = imageCoord ?: { coord, _, _ -> coord }
        }

        fun setPinSize(width: Int, height: Int) {
            pin = pin.scale(width, height)
        }

        fun setSimplePinSize(width: Int, height: Int) {
            if (pinSimple == null) {
                pinSimple = pin.copy(pin.config, true)
            }
            pinSimple = pinSimple!!.scale(width, height)
        }

        override fun equals(other: Any?): Boolean {
            return if (other is Pin) {
                this.name == other.name && this.sPin == other.sPin
            } else {
                super.equals(other)
            }
        }

        override fun hashCode(): Int {
            var result = name?.hashCode() ?: 0
            result = 31 * result + sPin.hashCode()
            result = 31 * result + pin.hashCode()
            result = 31 * result + (pinSimple?.hashCode() ?: 0)
            result = 31 * result + imageCoord.hashCode()
            return result
        }
    }
}