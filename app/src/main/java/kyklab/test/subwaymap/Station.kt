package kyklab.test.subwaymap

import android.database.Cursor

data class Station(val cursor: Cursor) {
    companion object {
        private const val STATION_TOUCH_RECOGNITION_DISTANCE = 150*150

        private fun Int.square() = this * this
        private fun Float.square() = this * this
    }

    val id: Int = cursor.getInt(StationSQLiteHelper.DB_STATIONS_COL_INDEX_ID)
    val mapNo: String = cursor.getString(StationSQLiteHelper.DB_STATIONS_COL_INDEX_MAPNO)
    val name: String = cursor.getString(StationSQLiteHelper.DB_STATIONS_COL_INDEX_NAME)
    val xCenter: Int = cursor.getInt(StationSQLiteHelper.DB_STATIONS_COL_INDEX_X_CENTER)
    val yCenter: Int = cursor.getInt(StationSQLiteHelper.DB_STATIONS_COL_INDEX_Y_CENTER)

    fun checkDistanceToStation(x: Float, y: Float): Float? {
        val distance = (x - xCenter).square() + (y-yCenter).square()
        return if (distance <= STATION_TOUCH_RECOGNITION_DISTANCE) distance else null
//        (xCenter - STATION_TOUCH_RECOGNITION_DISTANCE < x && x < xCenter + STATION_TOUCH_RECOGNITION_DISTANCE) &&
//                (yCenter - STATION_TOUCH_RECOGNITION_DISTANCE < y && y < yCenter + STATION_TOUCH_RECOGNITION_DISTANCE)
    }
}