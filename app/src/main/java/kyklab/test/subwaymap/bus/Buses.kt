package kyklab.test.subwaymap.bus

import java.util.*

object Buses {
    private const val TAG = "Buses"

    val buses: MutableList<Bus> by lazy { LinkedList() }

    init {
        BusStopSQLiteHelper.query("buses", arrayOf("name"), orderBy = "buses._id ASC").use {
            if (it.moveToFirst()) {
                do {
                    // Add a new bus
                    val busName = it.getString(0)
                    val instances = ArrayList<Bus.BusInstance>(100)
                    //SELECT stop_points FROM buses WHERE name="Red";
                    BusStopSQLiteHelper.query(
                        "buses",
                        arrayOf("stop_points"),
                        "name=\"$busName\"",
                        orderBy = "buses._id ASC"
                    ).use { c1 ->
                        if (c1.moveToFirst()) {
                            do {
                                val stopPoints = c1.getString(0).split(';')

                                //SELECT stop_times FROM bus_details WHERE bus_name="Red";
                                BusStopSQLiteHelper.query(
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
                                            val stops = ArrayList<Bus.Stop>()
                                            for (i in stopPoints.indices) {
                                                stops.add(Bus.Stop(stopPoints[i], stopTimes[i]))
                                            }
                                            instances.add(Bus.BusInstance(stops))
                                        } while (c2.moveToNext())
                                    }
                                }
                            } while (c1.moveToNext())
                        }
                    }

                    buses.add(Bus(busName, instances))
                } while (it.moveToNext())
            }
        }
    }

}