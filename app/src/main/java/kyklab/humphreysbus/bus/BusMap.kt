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
import kyklab.humphreysbus.utils.TYPEFACE_SANS_SERIF_CONDENSED
import kyklab.humphreysbus.utils.dpToPx
import kyklab.humphreysbus.utils.getDimension
import kotlin.math.max


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

    private val assetMgr = App.context.assets
    private val assetRootList = assetMgr.list("")
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
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
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
                val pins = BusUtils.stops.map { stop ->
                    MultiplePinView.Pin(
                        pinCoord = PointF(stop.xCenter.toFloat(), stop.yCenter.toFloat()),
                        bitmap = createBusBitmap(stop),
                        bitmapSimple = createBusBitmapSimple(stop)
                    ) { coord, pinWidth, pinHeight ->
                        val x = coord.x - pinWidth / 2
                        val y = coord.y - pinHeight / 2
                        PointF(x, y)
                    }
                }
                mapView.addPins(pins)
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
        if (assetRootList == null) return

        val list = ArrayList<MultiplePinView.Pin>(bus.busRouteImageCoords.size)
        busRouteListHashMap[bus] = list
        val job = scope.launch(Dispatchers.Default) {
            for (i in bus.busRouteImageCoords.indices) {
                val routeImageName = bus.busRouteImageFilenames[i]
                if (!assetRootList.contains(routeImageName)) {
                    continue
                }

                var bitmap: Bitmap
                assetMgr.open(routeImageName).use {
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
            }
            launch(Dispatchers.Main) {
                mapView.addPins(list)
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
        list?.let { mapView.removePins(it) }
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
            setPinSize(dpToPx(activity, 36f), dpToPx(activity, 44f))
        }
        mapView.addPin(selectionPin!!)
    }

    private fun resetStopSelectionPin() {
        selectionPin?.let { mapView.removePin(it) }
    }

    private fun createBusBitmap(stop: BusStop): Bitmap {
        val iconRes = R.drawable.bus_map_stop_icon
        val text = stop.name
        val accent = activity.getColor(R.color.map_spot_accent)

        val iconWidth = activity.getDimension(R.dimen.map_bus_stop_icon_size)
        val iconHeight = activity.getDimension(R.dimen.map_bus_stop_icon_size)
        val icon = AppCompatResources.getDrawable(activity, iconRes)!!
            .toBitmap().scale(iconWidth.toInt(), iconHeight.toInt())

        val textSize = activity.getDimension(R.dimen.map_bus_stop_text_size)
        val textBorderSize = activity.getDimension(R.dimen.map_bus_stop_text_border_size)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            this.textSize = textSize
            typeface = Typeface.create(TYPEFACE_SANS_SERIF_CONDENSED, Typeface.BOLD)
        }
        val textStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
            strokeWidth = textBorderSize
            style = Paint.Style.STROKE
            typeface = Typeface.create(TYPEFACE_SANS_SERIF_CONDENSED, Typeface.BOLD)
        }

        val textBounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, textBounds)

        val textWidth = textBounds.width()
        val textHeight = textBounds.height()

        val iconMargin = activity.getDimension(R.dimen.map_bus_stop_icon_margin)
        val resultBitmapMargin = activity.getDimension(R.dimen.map_bus_stop_icon_result_margin)

        // Get size of result image
        val resultBitmapWidth = max(
            textBounds.width().toFloat(), iconWidth + iconMargin * 2
        ) + resultBitmapMargin * 2
        val resultBitmapHeight =
            iconHeight + (iconMargin + textHeight + resultBitmapMargin) * 2

        val backgroundBitmap =
            Bitmap.createBitmap(
                resultBitmapWidth.toInt(),
                resultBitmapHeight.toInt(),
                Bitmap.Config.ARGB_8888
            )

        // Create blank bitmap with the size of result image first
        val resultBitmap = Bitmap.createBitmap(
            backgroundBitmap.width, backgroundBitmap.height,
            backgroundBitmap.config
        )
        val c = Canvas(resultBitmap)
        c.drawBitmap(backgroundBitmap, Matrix(), null)

        // Draw icon
        val iconLeft = (resultBitmapWidth - iconWidth) / 2f
        val iconTop = (resultBitmapHeight - iconHeight) / 2f
        c.drawBitmap(icon, iconLeft, iconTop, Paint())

        // Draw stop name text
        val textLeft = (resultBitmapWidth - textWidth) / 2f
        val textBottom = (resultBitmapHeight - resultBitmapMargin).toFloat()
        c.drawText(text, textLeft, textBottom, textStrokePaint)
        c.drawText(text, textLeft, textBottom, textPaint)

        return resultBitmap
    }

    private fun createBusBitmapSimple(stop: BusStop): Bitmap {
        val iconRes = R.drawable.bus_map_stop_icon

        val width = activity.getDimension(R.dimen.map_bus_stop_icon_size)
        val height = activity.getDimension(R.dimen.map_bus_stop_icon_size)

        return AppCompatResources.getDrawable(activity, iconRes)!!
            .toBitmap().scale(width.toInt(), height.toInt())
    }
}