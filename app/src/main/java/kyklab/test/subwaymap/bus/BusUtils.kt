package kyklab.test.subwaymap.bus

import android.database.Cursor
import android.graphics.Color
import android.util.Log
import androidx.core.database.getIntOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kyklab.test.subwaymap.bus.BusDBHelper.DB_TABLE_BUSES
import kyklab.test.subwaymap.forEachCursor
import kyklab.test.subwaymap.kQuery
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object BusUtils {
    private const val TAG = "BusUtils"

    var stops: List<BusStop> = emptyList()
    var buses: List<Bus> = emptyList()

    // Whether load from db is done. This should be true even when nothing is fetched from db.
    @set:Synchronized
    var isLoadDone = false

    // Whether load from db is successfully done.
    // @set: Synchronized
    // var isLoadSuccessful = false

    private val lock = ReentrantLock()
    private val cond = lock.newCondition()

    fun loadData() {
        GlobalScope.launch(Dispatchers.IO) {
            lock.withLock {
                Log.e(TAG, "Start loading data")
                loadBusStops()
                loadBuses()
                isLoadDone = true
                cond.signalAll()
                Log.e(TAG, "Done loading data")
            }
        }
    }

    fun getStopFromCoord(x: Float, y: Float): BusStop? {
        lock.withLock {
            while (!isLoadDone) cond.await()

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
    }

    fun getBusStop(stopId: Int?): BusStop? {
        lock.withLock {
            while (!isLoadDone) cond.await()
            return stopId?.let { stops.getOrNull(stopId - 1) }
        }
    }

    fun getBusStop(stopNo: String?): BusStop? {
        lock.withLock {
            while (!isLoadDone) cond.await()
            return stops.find { stop -> stop.no == stopNo }
        }
    }

    private fun loadBusStops() {
        lock.withLock {
            BusDBHelper.db.use { db ->
                db.kQuery(BusDBHelper.DB_TABLE_STOPS).use { cursor ->
                    stops = ArrayList(cursor.count)
                    cursor.forEachCursor { (stops as ArrayList<BusStop>).add(BusStop(it)) }
                    cursor.close()
                }
            }
        }
    }

    private fun loadBuses() {
        lock.withLock {
            if (stops.isEmpty()) loadBusStops()

            BusDBHelper.db.use { db ->
                val cursor = db.kQuery(
                    table = DB_TABLE_BUSES,
                    columns = arrayOf("name", "stop_points", "color"),
                    orderBy = "buses._id ASC"
                )
                buses = ArrayList(cursor.count)
                cursor.forEachCursor { c ->
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

                    val cursor2 = db.kQuery(
                        table = "bus_details",
                        columns = arrayOf("stop_times", "is_holiday"),
                        selection = "bus_name=\"$busName\"",
                        orderBy = "bus_details._id ASC"
                    )
                    cursor2.forEachCursor { c1 ->
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
                    cursor2.close()

                    (buses as ArrayList<Bus>).add(
                        Bus(
                            busName,
                            busColorInt,
                            busStops,
                            instances
                        )
                    )
                }
                cursor.close()
            }
        }
    }

    data class BusStop(val cursor: Cursor) {
        companion object {
            private const val BUS_STOP_TOUCH_RECOGNITION_DISTANCE = 150 * 150

            private fun Int.square() = this * this
            private fun Float.square() = this * this
        }

        val id: Int = cursor.getInt(BusDBHelper.DB_STOPS_COL_INDEX_ID)
        val no: String = cursor.getString(BusDBHelper.DB_STOPS_COL_INDEX_MAPNO)
        val name: String = cursor.getString(BusDBHelper.DB_STOPS_COL_INDEX_NAME)
        val xCenter: Int = cursor.getInt(BusDBHelper.DB_STOPS_COL_INDEX_X_CENTER)
        val yCenter: Int = cursor.getInt(BusDBHelper.DB_STOPS_COL_INDEX_Y_CENTER)
        // TODO: Fix stops without coordinates

        fun checkDistanceToStop(x: Float, y: Float): Float? {
            val distance = (x - xCenter).square() + (y - yCenter).square()
            return if (distance <= BUS_STOP_TOUCH_RECOGNITION_DISTANCE) distance else null
        }
    }
}


