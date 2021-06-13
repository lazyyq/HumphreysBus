package kyklab.humphreysbus.utils

import android.util.Log
import java.io.*
import java.math.BigInteger
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

private const val TAG = "FileUtils"

fun File.copyTo(
    path: String,
    overwrite: Boolean = false,
    bufferSize: Int = DEFAULT_BUFFER_SIZE
): File =
    copyTo(File(path), overwrite = overwrite, bufferSize = DEFAULT_BUFFER_SIZE)

fun InputStream.copyTo(path: String, overwrite: Boolean = false): File = copyTo(File(path), overwrite)

fun InputStream.copyTo(target: File, overwrite: Boolean = false): File {
    if (overwrite && target.exists()) {
        if (!target.delete()) {
            throw IOException("Tried to overwrite the destination, but failed to delete it.")
        }
    }

    use { input ->
        target.outputStream().use { output ->
            input.copyTo(output)
        }
    }

    return target
}

// https://github.com/topjohnwu/Magisk/blob/master/app/src/main/java/com/topjohnwu/magisk/core/utils/ZipUtils.kt
@Throws(IOException::class)
fun File.unzip(folder: String, path: String = "", overwrite: Boolean = false) =
    unzip(File(folder), path, overwrite)

@Throws(IOException::class)
fun File.unzip(folder: File, path: String = "", overwrite: Boolean = false) {
    inputStream().buffered().use {
        it.unzip(folder, path, overwrite)
    }
}

@Throws(IOException::class)
fun InputStream.unzip(folder: String, path: String = "", overwrite: Boolean = false) =
    unzip(File(folder), path, overwrite)

@Throws(IOException::class)
fun InputStream.unzip(folder: File, path: String = "", overwrite: Boolean = false) {
    try {
        val zin = ZipInputStream(this)
        var entry: ZipEntry
        while (true) {
            entry = zin.nextEntry ?: break
            if (!entry.name.startsWith(path) || entry.isDirectory) {
                // Ignore directories, only create files
                continue
            }
            val name = entry.name
            val dest = File(folder, name)
            if (!dest.parentFile!!.exists()) {
                dest.parentFile!!.mkdirs()
            }
            if (overwrite && dest.exists()) {
                if (!dest.delete()) {
                    throw IOException("Tried to overwrite the destination, but failed to delete it.")
                }
            }
            FileOutputStream(dest).use { out -> zin.copyTo(out) }
        }
    } catch (e: IOException) {
        e.printStackTrace()
        throw e
    }
}

// https://github.com/CyanogenMod/android_packages_apps_CMUpdater/blob/cm-14.1/src/com/cyanogenmod/updater/utils/MD5.java
@Throws(FileNotFoundException::class)
fun File.calculateMD5sum(): String = try {
    FileInputStream(this).calculateMD5sum()
} catch (e: FileNotFoundException) {
    Log.e(TAG, "Error while getting FileInputStream", e)
    throw e
}

@Throws(IOException::class)
fun InputStream.calculateMD5sum(): String = try {
    val digest = MessageDigest.getInstance("MD5")
    val buffer = ByteArray(8192)
    var read: Int
    this.use {
        while (this.read(buffer).also { read = it } > 0) {
            digest.update(buffer, 0, read)
        }
        val md5sum = digest.digest()
        val bigInt = BigInteger(1, md5sum)
        var output = bigInt.toString(16)
        // Fill to 32 chars
        output = String.format("%32s", output).replace(' ', '0')
        output
    }
} catch (e: IOException) {
    Log.e(TAG, "Error while calculating MD5sum", e)
    throw e
}

fun download(url: String, path: String, overwrite: Boolean = false): File? {
    val dest = File(path)
    if (overwrite && dest.exists()) {
        if (!dest.delete()) {
            throw IOException("Tried to overwrite the destination, but failed to delete it.")
        }
    }

    return try {
        URL(url).openStream().use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        }
        dest
    } catch (e: Exception) {
        Log.e(TAG, "Error while downloading file", e)
        null
    }
}

