package dev.banderkat.podtitles.player

import android.app.*
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.media3.common.util.NotificationUtil
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import dev.banderkat.podtitles.MainActivity
import dev.banderkat.podtitles.PodTitlesApplication
import dev.banderkat.podtitles.R

const val FOREGROUND_NOTIFICATION_ID = 4001
const val PLATFORM_SCHEDULER_JOB_ID = 8001
const val DOWNLOAD_NOTIFICATION_CHANNEL_ID = "download_channel"
const val DOWNLOAD_NOTIFICATION_CHANNEL_NAME = "downloads"
const val DOWNLOAD_CONTENT_DIRECTORY = "downloads"
const val DOWNLOAD_FINISHED_ACTION = "download_complete"

// see: https://exoplayer.dev/downloading-media.html#creating-a-downloadservice
class PodTitlesDownloadService : DownloadService(FOREGROUND_NOTIFICATION_ID) {

    val downloadListener = object : DownloadManager.Listener {

        override fun onWaitingForRequirementsChanged(
            downloadManager: DownloadManager,
            waitingForRequirements: Boolean
        ) {
            super.onWaitingForRequirementsChanged(downloadManager, waitingForRequirements)
            Log.d("PodDownloader", "waiting for requirements: ${waitingForRequirements}")
        }

        override fun onInitialized(downloadManager: DownloadManager) {
            super.onInitialized(downloadManager)
            Log.d("PodDownloader", "download initialized!!!!!!!!!!!!!!!!!!!!!!!")
        }

        override fun onDownloadsPausedChanged(
            downloadManager: DownloadManager,
            downloadsPaused: Boolean
        ) {
            super.onDownloadsPausedChanged(downloadManager, downloadsPaused)
            Log.d("PodDownloader", "downloads paused? $downloadsPaused")
        }

        override fun onIdle(downloadManager: DownloadManager) {
            super.onIdle(downloadManager)
            Log.d("PodDownloader", "idling .....................................")
        }

        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?
        ) {
            super.onDownloadChanged(downloadManager, download, finalException)
            val stateString = when (download.state) {
                Download.STATE_DOWNLOADING -> "downloading ~~~~~~~~~~~~~"
                Download.STATE_COMPLETED -> "completed :-))))))))))"
                Download.STATE_FAILED -> "FAILED :-((((((("
                Download.STATE_QUEUED -> "queued . . . ."
                Download.STATE_STOPPED -> "stopped ? ? ? ?"
                else -> {
                    "unknown   ?!?!?"
                }
            }
            Log.d("PodDownloader", "download changed. state: $stateString")

            if (download.state == Download.STATE_COMPLETED) {
                Log.d("PodDownloader", "YAY DOWNLOAD FINISHED")
                sendBroadcast(Intent().setAction(DOWNLOAD_FINISHED_ACTION))
            }
        }
    }

    override fun getDownloadManager(): DownloadManager {
        val app = application as PodTitlesApplication
        app.downloadManager.addListener(downloadListener)
        return app.downloadManager
    }

    override fun getScheduler(): Scheduler? {
        return PlatformScheduler(this, PLATFORM_SCHEDULER_JOB_ID)
    }


    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification {
        createChannel()
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = TaskStackBuilder.create(this).run {
            addNextIntentWithParentStack(intent)
            getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)
        }
        return DownloadNotificationHelper(this, DOWNLOAD_NOTIFICATION_CHANNEL_ID)
            .buildProgressNotification(
                this,
                R.drawable.ic_launcher_foreground,
                pendingIntent,
                this.getString(R.string.download_notification_message),
                downloads,
                notMetRequirements
            )
    }

    private fun createChannel() {
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(
            NotificationChannel(
                DOWNLOAD_NOTIFICATION_CHANNEL_ID,
                DOWNLOAD_NOTIFICATION_CHANNEL_NAME,
                NotificationUtil.IMPORTANCE_DEFAULT
            )
        )
    }
}