package kyklab.test.subwaymap.bus

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import kyklab.test.subwaymap.App
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object BusSQLiteHelper :
    SQLiteOpenHelper(App.context, "subway.db", null, 10) {

    private val TAG = BusSQLiteHelper::class.simpleName
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

    private lateinit var db: SQLiteDatabase

    private val dbLock = ReentrantLock()

    @set:Synchronized
    var isDBOpen = false

    init {
        Log.e(TAG, "DB Path: $DB_DIR")
    }

    @Synchronized
    fun openDatabase(): Boolean {
        dbLock.withLock {
            if (!isDBOpen) {
                val checkDB = checkDatabase()
                if (!checkDB) {
                    readableDatabase
                    try {
                        copyDatabase()
                    } catch (e: IOException) {
                        Log.e(TAG, "Failed to copy database")
                        return false
                    }
                }
                db = SQLiteDatabase.openDatabase(
                    "$DB_DIR/$DB_NAME", null, SQLiteDatabase.OPEN_READONLY
                )
                isDBOpen = true
            }
            return true
        }
    }

    private fun checkDatabase(): Boolean {
        dbLock.withLock {
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
    }

    private fun copyDatabase() {
        dbLock.withLock {
            val outFilename = "$DB_DIR/$DB_NAME"
            App.context.assets.open(DB_NAME).use { inputStream ->
                FileOutputStream(outFilename).use { outputStream ->
                    val buffer = ByteArray(10)
                    var length: Int
                    while (true) {
                        length = inputStream.read(buffer)
                        if (length > 0) {
                            outputStream.write(buffer, 0, length)
                        } else {
                            break
                        }
                    }
                }
            }
        }
    }

    @JvmOverloads
    fun query(
        table: String, columns: Array<String>? = null, selection: String? = null,
        selectionArgs: Array<String>? = null, groupBy: String? = null, having: String? = null,
        orderBy: String? = null, limit: String? = null
    ): Cursor {
        Log.e(TAG, "query called")
        return db.query(
            table,
            columns,
            selection,
            selectionArgs,
            groupBy,
            having,
            orderBy,
            limit
        )
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

    override fun close() {
        if (isDBOpen) {
            db.close()
            isDBOpen = false
        }
        super.close()
    }
}