package kyklab.humphreysbus.bus

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import kyklab.humphreysbus.App
import kyklab.humphreysbus.BuildConfig
import java.io.FileOutputStream
import java.io.IOException

object BusDBHelper :
    SQLiteOpenHelper(App.context, "subway.db", null, 10) {

    private val TAG = BusDBHelper::class.simpleName
    private val DB_DIR: String = App.context.dataDir.toString() + "/databases"
    private const val DB_NAME = "subway.db"

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

    val db: SQLiteDatabase
        get() = readableDatabase

    init {
        Log.e(TAG, "DB Path: $DB_DIR")
        checkDatabase()
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
            val path = "$DB_DIR/$DB_NAME"
            try {
                checkDB = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY)
            } catch (e: SQLiteException) {
                Log.e(TAG, "checkDatabase(): Failed to open db")
            } finally {
                checkDB?.close()
            }
            return checkDB != null
        }

    @Throws(IOException::class)
    private fun copyDatabase() {
        val outFilename = "$DB_DIR/$DB_NAME"
        App.context.assets.open(DB_NAME).use { inputStream ->
            FileOutputStream(outFilename).use { outputStream ->
                val buffer = ByteArray(1024)
                var length: Int
                try {
                    while (true) {
                        length = inputStream.read(buffer)
                        if (length > 0) {
                            outputStream.write(buffer, 0, length)
                        } else {
                            break
                        }
                    }
                } catch (e: IOException) {
                    throw e
                }
            }
        }
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