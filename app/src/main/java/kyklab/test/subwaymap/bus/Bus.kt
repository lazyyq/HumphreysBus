package kyklab.test.subwaymap.bus

data class Bus(
    val name: String, val colorRes: Int,
    val stopPoints: List<BusUtils.BusStop>,
    val instances: List<BusInstance>
) {
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
    data class BusInstance(val stopTimes: List<String>, val isHoliday: Boolean)

    data class StopDeprecated(val stopNo: String, val stopTime: String)
}