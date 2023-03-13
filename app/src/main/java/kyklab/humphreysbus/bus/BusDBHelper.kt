package kyklab.humphreysbus.bus

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import kyklab.humphreysbus.App
import kyklab.humphreysbus.utils.Prefs
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

object BusDBHelper :
    SQLiteOpenHelper(App.context, "subway.db", null, 20) {

    private val TAG = BusDBHelper::class.simpleName
    private val DB_DIR: String = App.context.dataDir.toString() + "/databases"
    private const val DB_NAME = "subway.db"
    private const val DB_VERSION = 20

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
        Log.e(TAG, "DB Path: $DB_DIR")
        checkDatabase()
    }

    @Deprecated(
        "DO NOT USE THIS METHOD DIRECTLY",
        ReplaceWith("closeDatabase(Any)", "kyklab.humphreysbus.BusDBHelper")
    )
    override fun close() {
        super.close()
    }

    private fun checkDatabase(): Boolean {
        Log.e("DB", "OLD DB VERSION: ${Prefs.dbVersion}, NEW DB VERSION: ${DB_VERSION}")
        if (!dbExists || Prefs.dbVersion < DB_VERSION) {
            Log.e(TAG, "DB not found or old, trying to copy")
            try {
                // Create an empty db file before copying
                readableDatabase
                close()
                copyDatabase()
                Prefs.dbVersion = DB_VERSION
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
        /*
        if (newVersion > oldVersion) {
            try {
                copyDatabase()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        */
    }
}