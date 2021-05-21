package kyklab.humphreysbus.utils

import android.content.Context
import android.util.Log
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import kotlin.math.abs

class OnSwipeTouchListener(context: Context, private val onSwipeCallback: OnSwipeCallback) :
    OnTouchListener {

    private companion object {
        private const val SWIPE_THRESHOLD = 100
        private const val SWIPE_VELOCITY_THRESHOLD = 100
    }

    private val gestureDetector = GestureDetector(context, GestureListener())

    // Coordinates for the start point of fling action.
    // Used to determine where the drag started when GestureListener is not properly attached.
    private var origX = 0f
    private var origY = 0f
    // Whether GestureListener is properly attached to view.
    private var registered = false
    // Used to identify if this touch event is the first ACTION_DOWN event among those with the same action.
    private var startOfFling = true

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        if (!registered) {
            val mask = event?.actionMasked
            if (startOfFling && mask == MotionEvent.ACTION_DOWN) {
                // origEvent = event // Won't work. Maybe they use the same event object for every onTouch() call?
                origX = event.x
                origY = event.y
//                Log.e("onTouch()", event?.actionMasked.toString() + "x:${event?.x},y:${event?.y}")
//                Log.e("onTouch()", "recorded")
                startOfFling = false
            } else if (mask == MotionEvent.ACTION_UP) {
                /*Log.e(
                    "onTouch()",
                    event?.actionMasked.toString() + "x:${event?.x},y:${event?.y}"
                )
                Log.e("onTouch()", "recorded")*/
                startOfFling = true
            }
        }

        return gestureDetector.onTouchEvent(event)
    }

    private inner class GestureListener : SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent?): Boolean {
            //Log.e("GestureDetector", "onDown()")
            //savedEvent = e
            return false
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent?,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e2 == null) return false
            if (e1 != null) {
                // For some reason this listener doesn't seem to get attached to
                // some views immediately, including RecyclerView.
                // Consider this listener as registered if
                // start coordinates are successfully received.
                // In the meanwhile, use origX and origY instead of e1.
                Log.e("SwipeDetector", "registered")
                registered = true
            }

            var result = false
            try {
                val diffY = e2.y - (e1?.y ?: origY)
                val diffX = e2.x - (e1?.x ?: origX)
//                Log.e("SwipeDetector", "diffX: ${abs(diffX)}, diffY: ${abs(diffY)}, velocityX: $velocityX, velocityY: $velocityY")
                if (abs(diffX) > abs(diffY)) {
                    if (abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            onSwipeCallback.onSwipeRight()
                        } else {
                            onSwipeCallback.onSwipeLeft()
                        }
                    }
                    result = true
                }/* else if (abs(diffY) > SWIPE_THRESHOLD && abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffY > 0) {
                        onSwipeCallback.onSwipeBottom()
                    } else {
                        onSwipeCallback.onSwipeTop()
                    }
                }*/
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return result
        }
    }

    interface OnSwipeCallback {
        fun onSwipeLeft()
        fun onSwipeRight()
    }
}