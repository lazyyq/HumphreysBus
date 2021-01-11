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
                ).forEachCursor { c ->
                    // Attributes for a new bus
                    val busName = c.getString(0)
                    val stopsTemp = c.getString(1).split(';')
                    val busStops = ArrayList<BusStop>(stopsTemp.size)
                    stopsTemp.forEach { stopNo ->
                        // TODO: implement a better searching mechanism
                        stops.find { stop -> stop.no == stopNo }?.let { busStops.add(it) }
                    }
                    val instances = ArrayList<Bus.BusInstance>(100)
                    val busColorInt = Color.parseColor(c.getString(2))

                    query(
                        table = "bus_details",
                        columns = arrayOf("stop_times", "is_holiday"),
                        selection = "bus_name=\"$busName\"",
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

    fun getBusStop(stopId: Int?): BusStop? =
        stopId?.let { stops.getOrNull(stopId - 1) }

    fun getBusStop(stopNo: String?): BusStop? =
        stops.find { stop -> stop.no == stopNo }

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

