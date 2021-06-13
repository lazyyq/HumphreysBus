package kyklab.humphreysbus.updater

import android.util.Log
import com.google.gson.Gson
import kyklab.humphreysbus.App
import kyklab.humphreysbus.AssetsMetadata
import kyklab.humphreysbus.BuildConfig
import kyklab.humphreysbus.utils.calculateMD5sum
import kyklab.humphreysbus.utils.copyTo
import kyklab.humphreysbus.utils.download
import kyklab.humphreysbus.utils.unzip
import java.io.File

class AssetsManager {
    companion object {
        /*
        @Volatile
        private var instance: AssetsManager? = null

        fun getInstance(): AssetsManager {
            return instance ?: synchronized(this) {
                instance ?: AssetsManager().also {
                    instance = it
                }
            }
        }
        */

        private val TAG = AssetsManager::class.java.simpleName
        private val BASE_DIR = App.context.filesDir.path + "/hb_assets"

        val ASSETS_DIR = "$BASE_DIR/assets"

        private val ASSETS_DIR_LOCAL = ASSETS_DIR

        private val ASSETS_ZIP_LOCAL = "$BASE_DIR/assets.zip"
        private val ASSETS_ZIP_BUNDLED = "assets.zip"
        private val ASSETS_ZIP_DOWNLOADED_TEMP = "$BASE_DIR/assets_downloaded_temp.zip"

        private val ASSETS_DOWNLOADED_UNPACKED_TEMP_DIR =
            "$BASE_DIR/assets_downloaded_unpacked_temp"

        private val METADATA_JSON_LOCAL = "$BASE_DIR/metadata.json"
        private val METADATA_JSON_BUNDLED = "metadata.json"
        private val METADATA_JSON_DOWNLOADED_TEMP = "$BASE_DIR/metadata_downloaded_temp.json"

        private const val ASSETS_ZIP_SERVER_URL =
            "https://raw.githubusercontent.com/lazyyq/HumphreysBusAssets/master/assets.zip"
        private const val METADATA_JSON_SERVER_URL =
            "https://raw.githubusercontent.com/lazyyq/HumphreysBusAssets/master/metadata.json"

        private val UPDATE_READY_FLAG = "$BASE_DIR/update_ready"

        private fun log(msg: String = "") = Log.e(TAG, msg)
    }

    private val gson = Gson()
    private val localMetadata by lazy { parseLocalMetadata() }
    private val bundledMetadata by lazy { parseBundledMetadata() }
    private lateinit var serverMetadata: AssetsMetadata

    fun init() {
        File(BASE_DIR).apply { if (!exists()) mkdirs() }

        if (isUpdateReady()) {
            log("Pending updates found, applying")
            deleteOriginalAssets()
            renameDownloadedAssets()
            cleanup()
            log("Successfully applied updates")
        }
        val json = File(METADATA_JSON_LOCAL)
        if (!json.exists() || localMetadata.assetsVersion < bundledMetadata.assetsVersion || !checkLocalAssetsIntegrity()) {
            log("Either local json doesn't exist, bundled assets are newer, or integrity check failed. Unpacking assets.")
            unpackBundledAssets()
        }
    }

    fun checkAssetsUpdates(): Boolean {
        log("Checking updates")
        serverMetadata = parseServerMetadata()
        val isServerMetadataNewer = localMetadata.assetsVersion < serverMetadata.assetsVersion
        log("Local assets version: ${localMetadata.assetsVersion}, server version; ${serverMetadata.assetsVersion}")
        val isAppVersionSupported =
            BuildConfig.VERSION_CODE in serverMetadata.minAppVersion..serverMetadata.maxAppVersion
        log("Checking version: local ${BuildConfig.VERSION_CODE} server min: ${serverMetadata.minAppVersion} server max: ${serverMetadata.maxAppVersion}")
        return isServerMetadataNewer && isAppVersionSupported
    }

    /**
     * Do note that updates of assets files do not occur immediately.
     * Rather, downloaded files will stay in temp directories
     * until the app restarts and calls [init] again
     */
    fun fetchAssetsUpdates() {
        log("Fetching updates")
        download(
            ASSETS_ZIP_SERVER_URL,
            ASSETS_ZIP_DOWNLOADED_TEMP,
            overwrite = true
        )
        val downloaded = File(ASSETS_ZIP_DOWNLOADED_TEMP)
        // Check downloaded zip's md5sum
        val zipMd5 = downloaded.calculateMD5sum()
        log("downloaded zip: $zipMd5, server zip: ${serverMetadata.assetsZipMd5}")
        if (!zipMd5.equals(serverMetadata.assetsZipMd5, ignoreCase = true)) {
            log("zip md5 doesn't match")
            cleanup()
            return
        }
        log("zip md5 matches")
        downloaded.unzip(ASSETS_DOWNLOADED_UNPACKED_TEMP_DIR, overwrite = true)
        // Check unpacked files' md5sums
        val unpackDir = File(ASSETS_DOWNLOADED_UNPACKED_TEMP_DIR)
        serverMetadata.files.forEach {
            val unpacked = unpackDir.path + "/" + it.name
            val md5 = File(unpacked).calculateMD5sum()
            if (!md5.equals(it.md5, ignoreCase = true)) {
                log("files md5 check failed")
                cleanup()
                return@forEach
            }
        }
        setUpdateReady()
        log("files md5 check complete")
    }

    private fun parseLocalMetadata(): AssetsMetadata {
        log("Parsing local metadata")
        val json = File(METADATA_JSON_LOCAL)
        val jsonString = json.bufferedReader().readText()
        return gson.fromJson(jsonString, AssetsMetadata::class.java)
    }

    private fun parseBundledMetadata(): AssetsMetadata {
        log("Parsing bundled metadata")
        val json = App.context.assets.open(METADATA_JSON_BUNDLED)
        val jsonString = json.bufferedReader().readText()
        return gson.fromJson(jsonString, AssetsMetadata::class.java)
    }

    private fun parseServerMetadata(): AssetsMetadata {
        log("Parsing server metadata")
        download(METADATA_JSON_SERVER_URL, METADATA_JSON_DOWNLOADED_TEMP, overwrite = true)
        val json = File(METADATA_JSON_DOWNLOADED_TEMP)
        val jsonString = json.bufferedReader().readText()
        return gson.fromJson(jsonString, AssetsMetadata::class.java)
    }

    private fun unpackBundledAssets() {
        log("Unpacking bundled assets")
        val metadata = App.context.assets.open(METADATA_JSON_BUNDLED)
        metadata.copyTo(METADATA_JSON_LOCAL, overwrite = true)
        val assets = App.context.assets.open(ASSETS_ZIP_BUNDLED)
        assets.unzip(ASSETS_DIR_LOCAL, overwrite = true)
    }

    private fun checkLocalAssetsIntegrity(): Boolean {
        log("Checking local assets integrity")
        return localMetadata.files.find {
            val local = ASSETS_DIR_LOCAL + "/" + it.name
            val md5 = File(local).calculateMD5sum()
            !md5.equals(it.md5, ignoreCase = true)
        } == null
    }

    private fun setUpdateReady() = File(UPDATE_READY_FLAG).createNewFile()

    private fun isUpdateReady() = File(UPDATE_READY_FLAG).exists()

    private fun deleteOriginalAssets() {
        val assetsDir = File(ASSETS_DIR_LOCAL)
        val metadataJson = File(METADATA_JSON_LOCAL)
        assetsDir.deleteRecursively()
        metadataJson.delete()
    }

    private fun renameDownloadedAssets() {
        val unpackedAssets = File(ASSETS_DOWNLOADED_UNPACKED_TEMP_DIR)
        val unpackedMetadata = File(METADATA_JSON_DOWNLOADED_TEMP)
        val assetsTarget = File(ASSETS_DIR_LOCAL)
        val metadataTarget = File(METADATA_JSON_LOCAL)
        unpackedAssets.renameTo(assetsTarget)
        unpackedMetadata.renameTo(metadataTarget)
    }

    private fun cleanup() {
        log("Cleaning up")
        val files = arrayOf(
            ASSETS_ZIP_DOWNLOADED_TEMP,
            ASSETS_DOWNLOADED_UNPACKED_TEMP_DIR,
            METADATA_JSON_DOWNLOADED_TEMP,
            UPDATE_READY_FLAG,
        )
        files.forEach { File(it).deleteRecursively() }
    }
}