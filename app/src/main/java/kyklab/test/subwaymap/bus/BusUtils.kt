package kyklab.test.subwaymap.bus

import android.database.Cursor
import android.util.Log

object BusUtils {
    private const val TAG = "BusUtils"

    private val stops: MutableList<BusStop> by lazy { ArrayList(40) }

    fun loadFromDB() {
        val start = System.currentTimeMillis()
        with(BusStopSQLiteHelper) {
            createDatabase()
            openDatabase()
            query(DB_TABLE_STOPS).use {
                if (it.moveToFirst()) {
                    do {
                        stops.add(BusStop(it))
                    } while (it.moveToNext())
                }
            }
        }

        Log.e(TAG, "Took ${System.currentTimeMillis() - start}ms to load from DB")
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

        fun checkDistanceToStop(x: Float, y: Float): Float? {
            val distance = (x - xCenter).square() + (y - yCenter).square()
            return if (distance <= BUS_STOP_TOUCH_RECOGNITION_DISTANCE) distance else null
        }
    }
}

