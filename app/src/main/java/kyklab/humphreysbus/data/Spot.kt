package kyklab.humphreysbus.data

open class Spot(
    open val name: String,
    open val xCenter: Int,
    open val yCenter: Int
) {
    companion object {
        protected const val TOUCH_RECOGNITION_DISTANCE = 150 * 150

        protected fun Int.square() = this * this
        protected fun Float.square() = this * this
    }

    fun checkDistance(x: Float, y: Float): Float? {
        val distance = (x - xCenter).square() + (y - yCenter).square()
        return if (distance <= TOUCH_RECOGNITION_DISTANCE) distance else null
    }
}
