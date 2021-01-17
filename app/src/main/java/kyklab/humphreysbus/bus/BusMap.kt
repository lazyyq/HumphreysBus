package kyklab.humphreysbus.bus

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.PointF
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import kyklab.humphreysbus.R
import kyklab.humphreysbus.data.Spot
import kyklab.humphreysbus.ui.MultiplePinView

class BusMap(
    private val activity: Activity,
    private val mapView: MultiplePinView,
    private val onSpotSelected: (Spot) -> Unit

) {
    companion object {
        private val TAG = BusMap::class.simpleName
        private const val MAP_ASSET_FILENAME = "subway.webp"

        private const val xBase = 126.974512
        private const val yBase = 36.945053

        private const val xRatio = 1749 / 0.028094
        private const val yRatio = 2115 / 0.026039

        fun gMapCoordToLocalMapCoord(x: Double, y: Double): PointF? {
            val xCalculated = (x - xBase) * xRatio
            val yCalculated = 3600 - (y - yBase) * yRatio
            return if ((0 <= xCalculated) && (xCalculated <= 4800) &&
                (0 <= yCalculated) && (yCalculated <= 3600)
            ) {
                PointF(xCalculated.toFloat(), yCalculated.toFloat())
            } else null
        }
    }

    private var selectionPin: Int? = null // Pin for current selection on bus map

    @SuppressLint("ClickableViewAccessibility")
    fun init() {
        mapView.setImage(ImageSource.asset(MAP_ASSET_FILENAME))
        mapView.setScaleAndCenter(1f, PointF(2000f, 2000f))

        val gestureDetector = GestureDetector(activity,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
                    if (e != null && mapView.isReady) {
                        val sCoord = mapView.viewToSourceCoord(e.x, e.y)
                        val xCor = sCoord!!.x
                        val yCor = sCoord.y
                        Log.e(TAG, "x: $xCor, y: $yCor")

                        val stop = BusUtils.getStopFromCoord(xCor, yCor)
                        if (stop != null) {
                            onSpotSelected(stop)
                        } else {
                            resetStopSelectionPin()
                        }
                    }
                    return super.onSingleTapConfirmed(e)
                }
            })
        mapView.setOnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)
            false
        }
    }

    fun highlight(
        x: Float, y: Float, minScale: Float = 1f,
        animateDuration: Long? = null,
        animationListener: SubsamplingScaleImageView.OnAnimationEventListener? = null
    ) {
        val coord = PointF(x, y)
        highlight(coord, minScale, animateDuration, animationListener)
    }

    fun highlight(
        coord: PointF, minScale: Float = 1f,
        animateDuration: Long? = null,
        animationListener: SubsamplingScaleImageView.OnAnimationEventListener? = null
    ) {
        if (!mapView.isReady) return

        setStopSelectionPin(coord)
        if (animateDuration != null) {
            val builder = mapView.animateScaleAndCenter(
                mapView.scale.coerceAtLeast(minScale), coord
            )?.withDuration(animateDuration)
            animationListener?.let { builder?.withOnAnimationEventListener(it) }
            builder?.start()
        } else {
            mapView.setScaleAndCenter(mapView.scale.coerceAtLeast(minScale), coord)
        }
    }


    private fun setStopSelectionPin(coord: PointF) {
        resetStopSelectionPin()
        selectionPin =
            mapView.addPin(
                MultiplePinView.Pin(coord, activity.resources, R.drawable.pushpin_blue)
            )
    }

    private fun resetStopSelectionPin() {
        selectionPin?.let { mapView.removePin(it) }
    }
}