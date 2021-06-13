package kyklab.humphreysbus.bus

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import kyklab.humphreysbus.App
import kyklab.humphreysbus.BuildConfig
import kyklab.humphreysbus.utils.copyTo
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

private const val DB_NAME = "humphreysbus.db"

object BusDBHelper : SQLiteOpenHelper(App.context, DB_NAME, null, 10) {

    private val TAG = BusDBHelper::class.simpleName
    private val DB_PATH = App.context.getDatabasePath(DB_NAME).path

    const val DB_STOPS_COL_INDEX_ID = 0
    const val DB_STOPS_COL_INDEX_MAPNO = 1
    const val DB_STOPS_COL_INDEX_NAME = 2
    const val DB_STOPS_COL_INDEX_X_CENTER = 3
    const val DB_STOPS_COL_INDEX_Y_CENTER = 4

    const val DB_BUSES_COL_INDEX_ID = 0
    const val DB_BUSES_COL_INDEX_NAME = 1
    const val DB_BUSES_COL_INDEX_STOPS = 2
    const val DB_BUSES_COL_INDEX_TIMES = 3

    const val DB_TABLE_STOPS = "stations"
    const val DB_TABLE_BUSES = "buses"
    const val DB_TABLE_HOLIDAYS = "holidays"

    private val requesters = LinkedList<Any>()

    fun getDatabase(requester: Any): SQLiteDatabase {
        if (!requesters.contains(requester)) {
            requesters.add(requester)
        }
        return readableDatabase
    }

    fun closeDatabase(requester: Any) {
        requesters.remove(requester)
        if (requesters.isEmpty()) {
            close()
        }
    }

    init {
        // checkDatabase()

        // Always copy for now. TODO: Improve this behavior in the future
        copyDatabase()
    }

    @Deprecated(
        "DO NOT USE THIS METHOD DIRECTLY",
        ReplaceWith("closeDatabase(Any)", "kyklab.humphreysbus.BusDBHelper")
    )
    override fun close() {
        super.close()
    }

    private fun checkDatabase(): Boolean {
        if (!dbExists || BuildConfig.DEBUG) {
            Log.e(TAG, "DB does not exist, trying to copy")
            try {
                // Create an empty db file before copying
                readableDatabase
                close()
                copyDatabase()
            } catch (e: IOException) {
                Log.e(TAG, "Failed to copy database")
                return false
            }
        } else {
            Log.e(TAG, "DB exists")
        }
        return true
    }

    private val dbExists: Boolean
        get() {
            var checkDB: SQLiteDatabase? = null
            try {
                checkDB = SQLiteDatabase.openDatabase(DB_PATH, null, SQLiteDatabase.OPEN_READONLY)
            } catch (e: SQLiteException) {
                Log.e(TAG, "checkDatabase(): Failed to open db")
            } finally {
                checkDB?.close()
            }
            return checkDB != null
        }

    @Throws(IOException::class)
    private fun copyDatabase() {
        val src = File(App.context.filesDir.path + "/hb_assets/assets/$DB_NAME")
        src.copyTo(DB_PATH, overwrite = true)
    }

    override fun onCreate(db: SQLiteDatabase?) {
        //TODO("Not yet implemented")
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        if (newVersion > oldVersion) {
            try {
                copyDatabase()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}