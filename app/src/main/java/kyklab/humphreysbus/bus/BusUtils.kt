package kyklab.humphreysbus.bus

import android.database.sqlite.SQLiteDatabase
import android.graphics.Color
import android.graphics.PointF
import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import android.util.Log
import androidx.core.database.getIntOrNull
import androidx.core.database.getStringOrNull
import kotlinx.coroutines.*
import kyklab.humphreysbus.data.BusStop
import kyklab.humphreysbus.utils.MinDateTime
import kyklab.humphreysbus.utils.forEachCursor
import kyklab.humphreysbus.utils.kQuery
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.ArrayList
import kotlin.concurrent.withLock

object BusUtils {
    private const val TAG = "BusUtils"

    @get:Synchronized
    @set:Synchronized
    private var loadJob: Job? = null

    private var db: SQLiteDatabase? = null

    var stops: List<BusStop> = emptyList()
    var buses: List<Bus> = emptyList()
    var holidays: List<String> = emptyList()

    private val dateFormat = SimpleDateFormat("yyyyMMdd")

    // Whether load from db is done. This should be true even when nothing is fetched from db.
    @set:Synchronized
    var isLoadDone = false

    // Whether load from db is successfully done.
    // @set: Synchronized
    // var isLoadSuccessful = false

    private val lock = ReentrantLock()
    private val cond = lock.newCondition()

    fun loadData(requester: Any) {
        loadJob = CoroutineScope(Dispatchers.IO).launch {
            lock.withLock {
                isLoadDone = false
                openDatabase(requester)
                val job = coroutineContext[Job] ?: return@launch

                if (!job.isActive) return@launch
                Log.e("LOADER", "launching loadbusstops")
                loadBusStops()
//                Thread.sleep(2000L)
                if (!job.isActive) return@launch
                Log.e("LOADER", "launching loadbuses")
                loadBuses()
//                Thread.sleep(2000L)
                if (!job.isActive) return@launch
                Log.e("LOADER", "launching loadholidays")
                loadHolidays()
                Log.e("LOADER", "isLoadDone set $isLoadDone => true")
                cond.signalAll()
                Log.e(TAG, "Done loading data")
                closeDatabase(requester)
                loadJob = null
                isLoadDone = true
            }
        }
    }

    fun cancelLoad(requester: Any) {
        Log.e("cancelLoad()", "called coroutine cancel")
        CoroutineScope(Dispatchers.Default).launch {
            loadJob?.cancelAndJoin()
            closeDatabase(requester)
            Log.e("cancelLoad()", "successfully closed db after canceled")
        }
    }

    fun onLoadDone(block: () -> Unit) {
        lock.withLock {
            while (!isLoadDone) cond.await()
            block()
        }
    }

    private fun openDatabase(requester: Any) {
        db = BusDBHelper.getDatabase(requester)
    }

    private fun closeDatabase(requester: Any) {
        BusDBHelper.closeDatabase(requester)
        db = null
    }

    fun getStopFromCoord(x: Float, y: Float): BusStop? {
        lock.withLock {
            while (!isLoadDone) cond.await()

            val start = System.currentTimeMillis()
            var nearestStop: BusStop? = null
            var minDistance: Float? = null
            for (stop in stops) {
                val distanceToStop = stop.checkDistance(x, y)
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

    fun isHoliday() = isHoliday(Date())

    fun isHoliday(date: Date): Boolean {
        val calendar = Calendar.getInstance()
        calendar.time = date
        return isHoliday(calendar)
    }

    fun isHoliday(calendar: Calendar): Boolean {
        val isWeekend =
            (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY) ||
                    (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
        val isUsHoliday = holidays.contains(dateFormat.format(calendar))
        return isWeekend || isUsHoliday
    }

    private fun loadBusStops() {
        lock.withLock {
            db?.kQuery(BusDBHelper.DB_TABLE_STOPS)?.use { cursor ->
                stops = ArrayList(cursor.count)
                cursor.forEachCursor {
                    val id: Int = it.getInt(BusDBHelper.DB_STOPS_COL_INDEX_ID)
                    val no: String = it.getString(BusDBHelper.DB_STOPS_COL_INDEX_MAPNO)
                    val name: String = it.getString(BusDBHelper.DB_STOPS_COL_INDEX_NAME)
                    val xCenter: Int = it.getInt(BusDBHelper.DB_STOPS_COL_INDEX_X_CENTER)
                    val yCenter: Int = it.getInt(BusDBHelper.DB_STOPS_COL_INDEX_Y_CENTER)
                    val newStop = BusStop(id, no, name, xCenter, yCenter)
                    (stops as ArrayList<BusStop>).add(newStop)
                }
                cursor.close()
            }
        }
    }

    private fun loadBuses() {
        lock.withLock {
            if (stops.isEmpty()) loadBusStops()

            if (db == null) return@withLock

            val cursor = db!!.kQuery(
                table = BusDBHelper.DB_TABLE_BUSES,
                columns = arrayOf(
                    "name",
                    "stop_points",
                    "color",
                    "route_image_coords",
                    "route_image_filenames"
                ),
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
                val busRouteImageCoordsRaw = c.getStringOrNull(3)?.split(';')
                val busRouteImageCoords = ArrayList<PointF>(0)
                busRouteImageCoordsRaw?.let {
                    busRouteImageCoords.ensureCapacity(it.size + 1)
                    for (i in 1 until it.size step 2) {
                        val point = PointF(it[i - 1].toFloat(), it[i].toFloat())
                        busRouteImageCoords.add(point)
                    }
                }
                val busRouteImageFilenamesRaw = c.getStringOrNull(4)?.split(';')
                val busRouteImageFilenames = ArrayList<String>(0)
                busRouteImageFilenamesRaw?.let {
                    busRouteImageFilenames.ensureCapacity(it.size + 1)
                    it.forEach { s -> busRouteImageFilenames.add(s) }
                }

                val cursor2 = db!!.kQuery(
                    table = "bus_details",
                    columns = arrayOf("stop_times", "is_holiday"),
                    selection = "bus_name=\"$busName\"",
                    orderBy = "bus_details._id ASC"
                )
                cursor2.forEachCursor { c1 ->
                    val split = c1.getString(0).split(';')
                    val stopTimes = ArrayList(split.map { MinDateTime().apply { hm = it } })
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
                        instances,
                        busRouteImageCoords,
                        busRouteImageFilenames
                    )
                )
            }
            cursor.close()
        }
    }

    private fun loadHolidays() {
        lock.withLock {
            Log.e("loadHolidays()", db?.isOpen.toString())
            db?.use { db ->
                val cursor = db.kQuery(
                    table = BusDBHelper.DB_TABLE_HOLIDAYS,
                    columns = arrayOf("date"),
                    orderBy = "${BusDBHelper.DB_TABLE_HOLIDAYS}.date ASC"
                )
                holidays = ArrayList(cursor.count)
                cursor.forEachCursor {
                    val date = it.getString(0)
                    (holidays as ArrayList<String>).add(date)
                }
                cursor.close()
            }
        }
    }
}
