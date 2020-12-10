package kyklab.test.subwaymap.bus

import android.database.Cursor
import android.util.Log

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
        val list = ArrayList<Bus>(5)
        if (BusStopSQLiteHelper.isDBOpen) {
            with(BusStopSQLiteHelper) {
                query(DB_TABLE_BUSES, arrayOf("name"), orderBy = "buses._id ASC").use {
                    if (it.moveToFirst()) {
                        do {
                            // Add a new bus
                            val busName = it.getString(0)
                            val instances = java.util.ArrayList<Bus.BusInstance>(100)
                            //SELECT stop_points FROM buses WHERE name="Red";
                            query(
                                "buses", arrayOf("stop_points"),
                                "name=\"$busName\"", orderBy = "buses._id ASC"
                            ).use { c1 ->
                                if (c1.moveToFirst()) {
                                    do {
                                        val stopPoints = c1.getString(0).split(';')
                                        //SELECT stop_times FROM bus_details WHERE bus_name="Red";
                                        query(
                                            "bus_details",
                                            arrayOf("stop_times"),
                                            "bus_name=\"$busName\""
                                        ).use { c2 ->
                                            // Create new bus instances, with stopPoints and newly created stopTimes
                                            if (c2.moveToFirst()) {
                                                do {
                                                    // Create a new instance
                                                    val stopTimes = c2.getString(0).split(';')
                                                    if (stopPoints.size != stopTimes.size) continue
                                                    val stops = java.util.ArrayList<Bus.Stop>()
                                                    for (i in stopPoints.indices) {
                                                        stops.add(
                                                            Bus.Stop(stopPoints[i], stopTimes[i])
                                                        )
                                                    }
                                                    instances.add(Bus.BusInstance(stops))
                                                } while (c2.moveToNext())
                                            }
                                        }
                                    } while (c1.moveToNext())
                                }
                            }
                            list.add(Bus(busName, instances))
                        } while (it.moveToNext())
                    }
                }
            }
        }
        list
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
        stops.forEach { t -> if (t.stopNo == stopNo) return t }
        return null
    }

    fun getBusStop(stopIndex: Int?): BusStop? {
        return if (stopIndex == null) null
        else stops.getOrNull(stopIndex - 1)
    }

    fun getBusStop(stopNo: String?): BusStop? {
        stopNo ?: return null
        stops.forEach { t -> if (t.stopNo == stopNo) return t }
        return null
    }

    data class BusStop(val cursor: Cursor) {
        companion object {
            private const val BUS_STOP_TOUCH_RECOGNITION_DISTANCE = 150 * 150

            private fun Int.square() = this * this
            private fun Float.square() = this * this
        }

        val id: Int = cursor.getInt(BusStopSQLiteHelper.DB_STOPS_COL_INDEX_ID)
        val stopNo: String = cursor.getString(BusStopSQLiteHelper.DB_STOPS_COL_INDEX_MAPNO)
        val stopName: String = cursor.getString(BusStopSQLiteHelper.DB_STOPS_COL_INDEX_NAME)
        val xCenter: Int = cursor.getInt(BusStopSQLiteHelper.DB_STOPS_COL_INDEX_X_CENTER)
        val yCenter: Int = cursor.getInt(BusStopSQLiteHelper.DB_STOPS_COL_INDEX_Y_CENTER)
        // TODO: Fix stops without coordinates

        fun checkDistanceToStop(x: Float, y: Float): Float? {
            val distance = (x - xCenter).square() + (y - yCenter).square()
            return if (distance <= BUS_STOP_TOUCH_RECOGNITION_DISTANCE) distance else null
        }
    }
}

