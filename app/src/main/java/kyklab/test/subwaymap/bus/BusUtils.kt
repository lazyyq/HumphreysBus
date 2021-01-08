package kyklab.test.subwaymap.bus

import android.database.Cursor
import android.graphics.Color
import android.util.Log
import androidx.core.database.getIntOrNull
import kyklab.test.subwaymap.forEachCursor

object BusUtils {
    private const val TAG = "BusUtils"

    val stops: MutableList<BusStop> by lazy {
        val list = ArrayList<BusStop>(40)
        if (BusStopSQLiteHelper.isDBOpen) {
            BusStopSQLiteHelper.query(BusStopSQLiteHelper.DB_TABLE_STOPS).use {
                if (it.moveToFirst()) {
                    do {
                        list.add(BusStop(it))
                    } while (it.moveToNext())
                }
            }
        }
        list
    }
    val buses: MutableList<Bus> by lazy {
        val busList = ArrayList<Bus>(5)
        if (BusStopSQLiteHelper.isDBOpen) {
            with(BusStopSQLiteHelper) {
                query(
                    table = DB_TABLE_BUSES, columns = arrayOf("name", "stop_points", "color"),
                    orderBy = "buses._id ASC"
                ).forEachCursor {
                    // Attributes for a new bus
                    val busName = it.getString(0)
                    val stopsTemp = it.getString(1).split(';')
                    val busStops = ArrayList<BusStop>(stopsTemp.size)
                    stopsTemp.forEach { stopNo ->
                        val index = stops.binarySearch { s -> s.no.compareTo(stopNo) }
                        if (index > -1) busStops.add(stops[index])
                    }
                    val instances = ArrayList<Bus.BusInstance>(100)
                    val busColorInt = Color.parseColor(it.getString(2))

                    query(
                        table = "bus_details",
                        columns = arrayOf("stop_times", "is_holiday"),
                        selection = "name=\"$busName\"",
                        orderBy = "bus_details._id ASC"
                    ).forEachCursor { c1 ->
                        val stopTimes = ArrayList(c1.getString(0).split(';'))
                        if (stopTimes.size == busStops.size) {
                            val isHoliday =
                                when (c1.getIntOrNull(1)) {
                                    1 -> true
                                    else -> false
                                }
                            instances.add(Bus.BusInstance(stopTimes, isHoliday))
                        }
                    }
                    busList.add(Bus(busName, busColorInt, busStops, instances))
                }
            }
        }
        busList
    }

    init {
        // Open DB
        with(BusStopSQLiteHelper) {
            createDatabase()
            openDatabase()
        }
    }

    fun getStopFromCoord(x: Float, y: Float): BusStop? {
        val start = System.currentTimeMillis()
        var nearestStop: BusStop? = null
        var minDistance: Float? = null
        for (stop in stops) {
            val distanceToStop = stop.checkDistanceToStop(x, y)
            if (distanceToStop != null) {
                if (minDistance == null || distanceToStop < minDistance) {
                    nearestStop = stop
                    minDistance = distanceToStop
                }
            }
        }
        Log.e(TAG, "Took ${System.currentTimeMillis() - start}ms to load stop info")
        return nearestStop
    }

    fun getStopWithId(id: Int) = stops[id - 1]

    fun getStopWithStopNo(stopNo: String): BusStop? {
        stops.forEach { t -> if (t.no == stopNo) return t }
        return null
    }

    fun getBusStop(stopIndex: Int?): BusStop? {
        return if (stopIndex == null) null
        else stops.getOrNull(stopIndex - 1)
    }

    fun getBusStop(stopNo: String?): BusStop? {
        stopNo ?: return null
        stops.forEach { t -> if (t.no == stopNo) return t }
        return null
    }

    data class BusStop(val cursor: Cursor) {
        companion object {
            private const val BUS_STOP_TOUCH_RECOGNITION_DISTANCE = 150 * 150

            private fun Int.square() = this * this
            private fun Float.square() = this * this
        }

        val id: Int = cursor.getInt(BusStopSQLiteHelper.DB_STOPS_COL_INDEX_ID)
        val no: String = cursor.getString(BusStopSQLiteHelper.DB_STOPS_COL_INDEX_MAPNO)
        val name: String = cursor.getString(BusStopSQLiteHelper.DB_STOPS_COL_INDEX_NAME)
        val xCenter: Int = cursor.getInt(BusStopSQLiteHelper.DB_STOPS_COL_INDEX_X_CENTER)
        val yCenter: Int = cursor.getInt(BusStopSQLiteHelper.DB_STOPS_COL_INDEX_Y_CENTER)
        // TODO: Fix stops without coordinates

        fun checkDistanceToStop(x: Float, y: Float): Float? {
            val distance = (x - xCenter).square() + (y - yCenter).square()
            return if (distance <= BUS_STOP_TOUCH_RECOGNITION_DISTANCE) distance else null
        }
    }
}

