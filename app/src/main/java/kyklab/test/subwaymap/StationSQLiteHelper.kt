package kyklab.test.subwaymap

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.io.FileOutputStream
import java.io.IOException

object StationSQLiteHelper:
    SQLiteOpenHelper(App.context, "subway.db", null, 10) {

    private const val TAG = "MySQLiteHelper"
    private const val DB_NAME = "subway.db"

    const val DB_STATIONS_COL_INDEX_ID = 0
    const val DB_STATIONS_COL_INDEX_MAPNO = 1
    const val DB_STATIONS_COL_INDEX_NAME = 2
    const val DB_STATIONS_COL_INDEX_X_CENTER = 3
    const val DB_STATIONS_COL_INDEX_Y_CENTER = 4

    const val DB_BUSES_COL_INDEX_ID = 0
    const val DB_BUSES_COL_INDEX_NAME = 1
    const val DB_BUSES_COL_INDEX_STOPS = 2
    const val DB_BUSES_COL_INDEX_TIMES = 3

    const val DB_TABLE_STATIONS = "stations"
    const val DB_TABLE_BUSES = "buses"

    private val mDBPath: String = "/data/data/" + App.context.packageName + "/databases"
    private lateinit var myDatabase: SQLiteDatabase

    init {
        Log.e(TAG, "DB Path: $mDBPath")
    }

    fun createDatabase() {
        val dbExist = checkDatabase()
        if (dbExist) {
        } else {
            readableDatabase
            try {
                copyDatabase()
            } catch (e: IOException) {
                throw Error("Error copying database")
            }
        }
    }

    private fun checkDatabase(): Boolean {
        var checkDB: SQLiteDatabase? = null
        val path = "$mDBPath/$DB_NAME"
        try {
            checkDB = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: SQLiteException) {

        }
        checkDB?.close()
        return checkDB != null
    }

    private fun copyDatabase() {
        val myInput = App.context.assets.open(DB_NAME)
        val outFilename = "$mDBPath/$DB_NAME"
        val myOutput = FileOutputStream(outFilename)
        val buffer = ByteArray(10)
        var length: Int
        while (true) {
            length = myInput.read(buffer)
            if (length > 0) {
                myOutput.write(buffer, 0, length)
            } else {
                break
            }
        }
        myOutput.flush()
        myOutput.close()
        myInput.close()
    }

    fun openDatabase() {
        val myPath = "$mDBPath/$DB_NAME"
        // TODO("test")
        copyDatabase()
        myDatabase = SQLiteDatabase.openDatabase(myPath, null, SQLiteDatabase.OPEN_READONLY)
    }

    @JvmOverloads
    fun query(
        table: String, columns: Array<String>? = null, selection: String? = null,
        selectionArgs: Array<String>? = null, groupBy: String? = null, having: String? = null,
        orderBy: String? = null, limit: String? = null
    ): Cursor {
        return myDatabase.query(
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
}