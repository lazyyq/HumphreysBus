package kyklab.humphreysbus.bus

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.*
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kyklab.humphreysbus.App
import kyklab.humphreysbus.R
import kyklab.humphreysbus.data.BusStop
import kyklab.humphreysbus.data.Spot
import kyklab.humphreysbus.ui.MultiplePinView
import kyklab.humphreysbus.utils.dpToPx


class BusMap(
    private val activity: Activity,
    private val scope: CoroutineScope,
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

    private var selectionPin: MultiplePinView.Pin? = null // Pin for current selection on bus map
    private val busRouteListHashMap = HashMap<Bus, List<MultiplePinView.Pin>>()
    private val busRouteJobHashMap = HashMap<Bus, Job>()

    @SuppressLint("ClickableViewAccessibility")
    fun init() {
        mapView.setImage(ImageSource.asset(MAP_ASSET_FILENAME))
        mapView.setScaleAndCenter(1f, PointF(2000f, 2000f))

        val gestureDetector = GestureDetector(
            activity,
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
        mapView.simpleScaleThreshold = 0.5f

        // Add stop pins to map
        scope.launch(Dispatchers.Default) {
            BusUtils.onLoadDone {
                BusUtils.stops.forEach { stop ->
                    val pin = MultiplePinView.Pin(
                        pinCoord = PointF(stop.xCenter.toFloat(), stop.yCenter.toFloat()),
                        bitmap = createBusBitmap(stop),
                        bitmapSimple = createBusBitmapSimple(stop)
                    ) { coord, pinWidth, pinHeight ->
                        val x = coord.x - pinWidth / 2
                        val y = coord.y - pinHeight / 2
                        PointF(x, y)
                    }
                    mapView.addPin(pin)
                }
            }
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

    fun showBusRoute(bus: Bus, onFinished: (() -> Unit)? = null) {
        if (bus.busRouteImageCoords.size != bus.busRouteImageFilenames.size) return

        val list = ArrayList<MultiplePinView.Pin>(bus.busRouteImageCoords.size)
        busRouteListHashMap[bus] = list
        val assetMgr = App.context.assets
        val job = scope.launch(Dispatchers.Default) {
            for (i in bus.busRouteImageCoords.indices) {
                var bitmap: Bitmap
                assetMgr.open(bus.busRouteImageFilenames[i]).use {
                    bitmap = BitmapFactory.decodeStream(it)
                }
                val pin = MultiplePinView.Pin(
                    pinCoord = bus.busRouteImageCoords[i],
                    name = getBusRoutePinName(bus),
                    bitmap = bitmap,
                    autoScale = true,
                    priority = 0
                )

                list.add(pin)
                launch(Dispatchers.Main) {
                    mapView.addPin(pin)
                }
            }

            if (onFinished != null) {
                onFinished()
            }
        }
        busRouteJobHashMap[bus] = job
    }

    fun hideBusRoute(bus: Bus, onFinished: (() -> Unit)? = null) {
        val job = busRouteJobHashMap[bus]
        job?.cancel()

        val list = busRouteListHashMap[bus]
        list?.forEach { mapView.removePin(it) }
        busRouteListHashMap.remove(bus)
        if (onFinished != null) {
            onFinished()
        }
    }

    private fun getBusRoutePinName(bus: Bus) = bus.name + "_route"

    private fun setStopSelectionPin(coord: PointF) {
        resetStopSelectionPin()
        selectionPin = MultiplePinView.Pin(
            context = activity, pinCoord = coord, resId = R.drawable.pushpin_blue,
        ) { c, pinWidth, pinHeight ->
            val x = c.x - pinWidth / 2
            val y = c.y - pinHeight
            PointF(x, y)
        }.apply {
            setPinSize(36.dpToPx(), 44.dpToPx())
        }
        mapView.addPin(selectionPin!!)
    }

    private fun resetStopSelectionPin() {
        selectionPin?.let { mapView.removePin(it) }
    }

    private fun createBusBitmap(stop: BusStop): Bitmap {
        val resId = R.drawable.ic_bus
        val text = stop.name

        val innerImageWidth = 32.dpToPx()
        val innerImageHeight = 32.dpToPx()
        val innerImage = AppCompatResources.getDrawable(this.activity, resId)!!
            .apply { setTint(Color.DKGRAY) }
            .toBitmap()
            .scale(innerImageWidth, innerImageHeight)

        val innerTextSize = 10.dpToPx().toFloat()
        val textBorderSize = 4.dpToPx().toFloat()
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(61, 61, 61)
            textSize = innerTextSize
            typeface = Typeface.DEFAULT_BOLD
        }
        val textStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = innerTextSize
            strokeWidth = textBorderSize
            style = Paint.Style.STROKE
            typeface = Typeface.DEFAULT_BOLD
        }

        val textBounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, textBounds)

        val textWidth = textBounds.width()
        val textHeight = textBounds.height()

        val innerImageMargin = 2.dpToPx()
        val resultImageMargin = 2.dpToPx()

        val resultImageWidth = Math.max(
            textBounds.width(),
            innerImageWidth + innerImageMargin * 2
        ) + resultImageMargin * 2
        val resultImageHeight =
            innerImageHeight + (innerImageMargin + textHeight + resultImageMargin) * 2


        val backgroundBitmap =
            Bitmap.createBitmap(resultImageWidth, resultImageHeight, Bitmap.Config.ARGB_8888)

        val resultBitmap = Bitmap.createBitmap(
            backgroundBitmap.width, backgroundBitmap.height,
            backgroundBitmap.config
        )
        val c = Canvas(resultBitmap)
        c.drawBitmap(backgroundBitmap, Matrix(), null)

        val innerImageLeft = (resultImageWidth - innerImageWidth) / 2f
        val innerImageTop = (resultImageHeight - innerImageHeight) / 2f
        c.drawBitmap(innerImage, innerImageLeft, innerImageTop, Paint())

        val textLeft = (resultImageWidth - textWidth) / 2f
        val textBottom = (resultImageHeight - resultImageMargin).toFloat()
        c.drawText(text, textLeft, textBottom, textStrokePaint)
        c.drawText(text, textLeft, textBottom, textPaint)

        return resultBitmap
    }

    private fun createBusBitmapSimple(stop: BusStop): Bitmap {
        val resId = R.drawable.ic_bus

        val width = 32.dpToPx()
        val height = 32.dpToPx()

        return AppCompatResources.getDrawable(activity, resId)!!
            .apply { setTint(Color.DKGRAY) }
            .toBitmap()
            .scale(width, height)
    }

    private fun Number.dpToPx(): Int {
        return dpToPx(activity, this.toFloat())
    }
}