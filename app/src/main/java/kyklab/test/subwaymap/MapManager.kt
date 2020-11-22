package kyklab.test.subwaymap

import android.util.Log

object MapManager {
    private const val TAG = "StationManager"

    private val mStationsList = ArrayList<Station>(40)
    private val mDBHelper = kyklab.test.subwaymap.StationSQLiteHelper

    fun loadFromDB() {
        val start = System.currentTimeMillis()
        mDBHelper.createDatabase()
        mDBHelper.openDatabase()
        mDBHelper.query(StationSQLiteHelper.DB_TABLE_STATIONS).use {
            if (it.moveToFirst()) {
                do {
                    mStationsList.add(Station(it))
                } while (it.moveToNext())
            }
        }

        Log.e(TAG, "Took ${System.currentTimeMillis() - start}ms to load from DB")
    }

    fun getStationFromCoord(x: Float, y: Float): Station? {
        val start = System.currentTimeMillis()
        var nearestStation: Station? = null
        var minDistance: Float? = null
        for (station in mStationsList) {
            val distanceToStation = station.checkDistanceToStation(x, y)
            if (distanceToStation != null) {
                if (minDistance == null || distanceToStation < minDistance) {
                    nearestStation = station
                    minDistance = distanceToStation
                }
            }
        }
        Log.e(TAG, "Took ${System.currentTimeMillis() - start}ms to load station info")
        return nearestStation
    }

    fun getStationWithId(id: Int) = mStationsList[id-1]

    fun getStationWithMapNo(mapNo: String): Station? {
        mStationsList.forEach { t -> if (t.mapNo == mapNo) return t }
        return null
    }
}