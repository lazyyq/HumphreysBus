package kyklab.test.subwaymap.bus

import java.util.*
import kotlin.collections.HashMap

data class Bus(val name: String, val instances: List<BusInstance>) {
    companion object {
        private const val TAG = "Bus"
    }

    fun getAllStopTimes(stopNo: String): Map<Int, List<Int>> {
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

    data class BusInstance(val stops: List<Stop>)

    data class Stop(val stopNo: String, val stopTime: String)
}