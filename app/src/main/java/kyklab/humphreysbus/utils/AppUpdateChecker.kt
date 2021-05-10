package kyklab.humphreysbus.utils

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.net.toFile
import com.github.javiersantos.appupdater.AppUpdaterUtils
import com.github.javiersantos.appupdater.enums.AppUpdaterError
import com.github.javiersantos.appupdater.enums.UpdateFrom
import com.github.javiersantos.appupdater.objects.Update
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kyklab.humphreysbus.App
import kyklab.humphreysbus.BuildConfig
import kyklab.humphreysbus.Const
import kyklab.humphreysbus.R
import java.io.File
import java.util.*

class AppUpdateChecker(private val activity: Activity) {
    companion object {
        private val TAG = AppUpdateChecker::class.simpleName
    }

    private var appUpdaterUtils: AppUpdaterUtils? = null
    private var downloadNewVersionId: Long? = null
    private val downloadManager =
        activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private var isReceiverRegistered = false
    private val onDownloadCompleteReceiver: BroadcastReceiver by lazy {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id != downloadNewVersionId) return

                val query = DownloadManager.Query()
                query.setFilterById(id)
                val cursor = downloadManager.query(query)
                if (!cursor.moveToFirst()) {
                    return
                }

                val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
                val uri = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))

                // Install apk on download success
                if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        installApk(uri)
                    } else {
                        activity.toast("Download failed")
                    }
                }
                if (isReceiverRegistered) {
                    activity.unregisterReceiver(onDownloadCompleteReceiver)
                    isReceiverRegistered = false
                }
            }
        }
    }

    fun checkAppUpdate(
        alwaysShowNewVersionFound: Boolean = false,
        notifyIfLatest: Boolean = false
    ) {
        Log.e(TAG, "Starting update checker")
        appUpdaterUtils?.stop()
        appUpdaterUtils = AppUpdaterUtils(activity)
            .setUpdateFrom(UpdateFrom.JSON)
            .setUpdateJSON(Const.APP_UPDATE_JSON)
            .withListener(object : AppUpdaterUtils.UpdateListener {
                override fun onSuccess(update: Update?, isUpdateAvailable: Boolean?) {
                    Prefs.lastUpdateChecked = Date().time
                    update ?: return

                    Log.e(
                        TAG,
                        "Local: ${BuildConfig.VERSION_CODE}, Server: ${update.latestVersionCode}"
                    )
                    if (isUpdateAvailable == true) {
                        if (alwaysShowNewVersionFound ||
                            update.latestVersionCode > Prefs.ignoreUpdateVersionCode
                        ) {
                            MaterialAlertDialogBuilder(
                                activity,
                                R.style.ThemeOverlay_App_MaterialAlertDialog
                            )
                                .setTitle("New version available: ${update.latestVersion} (${update.latestVersionCode})")
                                .setMessage(update.releaseNotes)
                                .setNegativeButton("No thanks", null)
                                .setNeutralButton("Ignore this version") { dialog, which ->
                                    Prefs.ignoreUpdateVersionCode = update.latestVersionCode
                                }
                                .setPositiveButton("Download") { dialog, which ->
                                    downloadNewVersion(
                                        update.urlToDownload.toString(),
                                        "latest.apk"
                                    )
                                }
                                .show()
                        } else {
                            Log.e(
                                TAG,
                                "Skipping update as user decided to ignore up to ${Prefs.ignoreUpdateVersionCode}"
                            )
                        }
                    } else {
                        if (notifyIfLatest) {
                            activity.toast("App is already up to date")
                        }
                    }
                }

                override fun onFailed(error: AppUpdaterError?) {
                    Prefs.lastUpdateChecked = Date().time
                    Log.e("AppUpdater", "Failed to update")
                    error?.let { Log.e("AppUpdater", it.toString()) }
                    activity.toast("Failed to fetch update info")
                }
            })
        appUpdaterUtils?.start()
    }

    fun unregisterDownloadReceiver() {
        if (isReceiverRegistered) {
            activity.unregisterReceiver(onDownloadCompleteReceiver)
            isReceiverRegistered = false
        }
    }

    private fun downloadNewVersion(url: String, fileName: String) {
        val intentFilter = IntentFilter()
        intentFilter.addAction(DownloadManager.ACTION_DOWNLOAD_COMPLETE)

        activity.registerReceiver(onDownloadCompleteReceiver, intentFilter)
        isReceiverRegistered = true

        val file = File(activity.getExternalFilesDir(null), fileName)
        if (file.exists()) file.delete()
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("${activity.getString(R.string.app_name)} Update")
            setDescription("Downloading new version")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            setDestinationUri(Uri.fromFile(file))
        }
        downloadNewVersionId = downloadManager.enqueue(request)
    }

    private fun installApk(uriString: String) {
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                FileProvider.getUriForFile(
                    activity, App.context.packageName + ".fileprovider",
                    Uri.parse(uriString).toFile()
                ),
                "application/vnd.android.package-archive"
            )
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            activity.startActivity(this)
        }
    }
}