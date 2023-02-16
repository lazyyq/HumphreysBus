package kyklab.humphreysbus.bus

import android.graphics.PointF
import androidx.annotation.ColorInt
import kyklab.humphreysbus.data.BusStop
import kyklab.humphreysbus.utils.MinDateTime

data class Bus(
    val name: String, @ColorInt val colorInt: Int,
    val stopPoints: List<BusStop>,
    val instances: List<BusInstance>,
    val busRouteImageCoords: List<PointF>,
    val busRouteImageFilenames: List<String>
) {
    class Day {
        companion object {
            const val Mon = "MON"
            const val Fri = "FRI"
            const val Sat = "SAT"
            const val Sun = "SUN"
        }
    }

    companion object {
        private const val TAG = "Bus"
    }

    /*
        fun getAllStopTimesDeprecated(stopNo: String): Map<Int, List<Int>> {
            if (instances.isEmpty()) return HashMap()

            val indexes = ArrayList<Int>(4)
            val map = TreeMap<Int, LinkedList<Int>>()
            for ((i, stop) in instances[0].stops.withIndex()) {
                if (stop.stopNo == stopNo) {
                    indexes.add(i)
                    map[i] = LinkedList()
                }
            }

            for (i in indexes) {
                for (instance in instances) {
                    instance.stops[i].let {
                        map[i]?.add(it.stopTime.toInt())
                    }
                }
            }

            return map
        }
    */

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Bus

        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    data class BusInstance(val stopTimes: List<MinDateTime>, val day: String)

    data class StopDeprecated(val stopNo: String, val stopTime: String)
}