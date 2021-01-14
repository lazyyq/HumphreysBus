package kyklab.humphreysbus.ui

import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.util.AttributeSet
import androidx.annotation.DrawableRes
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import kyklab.humphreysbus.App

class MultiplePinView @JvmOverloads constructor(context: Context?, attr: AttributeSet? = null) :
    SubsamplingScaleImageView(context, attr) {
    companion object {
        private const val TAG = "MultiplePinView"
    }

    private val paint by lazy {
        Paint().apply { isAntiAlias = true }
    }

    private val pins: MutableList<Pin> by lazy {
        ArrayList()
    }

    class Pin(val sPin: PointF, res: Resources, @DrawableRes resId: Int) {
        companion object {
            val density = App.context.resources.displayMetrics.densityDpi.toFloat()
        }

        val width: Float
        val height: Float
        val pin: Bitmap

        init {
            val tmpPin = BitmapFactory.decodeResource(res, resId)
            width = density / 420f * tmpPin.width
            height = density / 420f * tmpPin.height
            pin = Bitmap.createScaledBitmap(tmpPin, width.toInt(), height.toInt(), true)
        }
    }

    fun addPin(pin: Pin): Int {
        pins.add(pin)
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Don't draw pin before image is ready so it doesn't move around during setup.
        if (!isReady) {
            return
        }

        pins.forEach { pin ->
            sourceToViewCoord(pin.sPin)?.let {
                val vX = it.x - pin.width / 2
                val vY = it.y - pin.height
                canvas.drawBitmap(pin.pin, vX, vY, paint)
            }
        }
    }
}