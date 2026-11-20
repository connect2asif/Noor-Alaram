package com.nooraudio.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import java.util.concurrent.Executors

/** A visible foreground service plays a complete playlist even with the screen locked. */
class AudioAlarmService : Service() {
    companion object {
        private const val CHANNEL_ID = "noor_playback_v1"
        private const val NOTIFICATION_ID = 2601
        private const val ACTION_PLAY = "com.nooraudio.alarm.PLAY"
        private const val ACTION_STOP = "com.nooraudio.alarm.STOP"
        private const val ACTION_NEXT = "com.nooraudio.alarm.NEXT"
        private const val EXTRA_SCHEDULED = "scheduled"

        fun play(context: Context, id: Int, scheduled: Boolean = false) {
            context.startForegroundService(Intent(context, AudioAlarmService::class.java).apply {
                action = ACTION_PLAY
                putExtra(AlarmScheduler.EXTRA_ID, id)
                putExtra(EXTRA_SCHEDULED, scheduled)
            })
        }

        fun stop(context: Context) {
            // startService is sufficient when playback is already foreground.
            context.startService(Intent(context, AudioAlarmService::class.java).apply { action = ACTION_STOP })
        }
    }

    private data class Track(val uri: Uri, val title: String)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var token = 0
    private var mediaPlayer: MediaPlayer? = null
    private var tracks = emptyList<Track>()
    private var currentIndex = 0
    private var playlistTitle = "Tilawat Alarm"
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        audioManager = getSystemService(AudioManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { finishPlayback(); return START_NOT_STICKY }
            ACTION_NEXT -> { playNext(); return START_NOT_STICKY }
            ACTION_PLAY -> {
                val id = intent.getIntExtra(AlarmScheduler.EXTRA_ID, -1)
                val alarm = AlarmStore.find(this, id)
                if (alarm == null) { stopSelf(); return START_NOT_STICKY }
                token++
                val thisToken = token
                releasePlayer()
                tracks = emptyList()
                playlistTitle = alarm.name
                currentIndex = 0
                startForeground(NOTIFICATION_ID, buildNotification("Preparing recitation…"))
                worker.execute {
                    val resolved = try { resolveTracks(alarm) } catch (_: Exception) { emptyList() }
                    main.post {
                        if (thisToken != token) return@post
                        tracks = if (alarm.shuffle) resolved.shuffled() else resolved
                        if (tracks.isEmpty()) {
                            showNotification("No playable audio found — check selected files or folder")
                            finishPlayback()
                        } else {
                            takeAudioFocus()
                            playIndex(0)
                        }
                    }
                }
                return START_NOT_STICKY
            }
            else -> { stopSelf(); return START_NOT_STICKY }
        }
    }

    private fun resolveTracks(alarm: AudioAlarm): List<Track> {
        if (alarm.sourceType == "folder") {
            val tree = DocumentFile.fromTreeUri(this, Uri.parse(alarm.folder)) ?: return emptyList()
            val result = mutableListOf<Track>()
            scanFolder(tree, result, depth = 0)
            return result.take(500)
        }
        return alarm.files.mapNotNull { source ->
            try {
                val uri = Uri.parse(source)
                Track(uri, getDisplayName(uri) ?: "Recitation")
            } catch (_: Exception) { null }
        }
    }

    private fun scanFolder(folder: DocumentFile, output: MutableList<Track>, depth: Int) {
        if (depth > 5 || output.size >= 500) return
        val children = folder.listFiles().sortedBy { it.name?.lowercase() ?: "" }
        for (child in children) {
            if (output.size >= 500) break
            if (child.isDirectory) {
                scanFolder(child, output, depth + 1)
            } else if (child.isFile) {
                val fileName = child.name ?: "Recitation"
                val mime = child.type ?: ""
                if (mime.startsWith("audio/") || listOf(".mp3", ".m4a", ".wav", ".ogg", ".aac", ".flac")
                        .any { fileName.endsWith(it, ignoreCase = true) }) {
                    output.add(Track(child.uri, fileName))
                }
            }
        }
    }

    private fun getDisplayName(uri: Uri): String? {
        var cursor: Cursor? = null
        return try {
            cursor = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            if (cursor?.moveToFirst() == true) cursor.getString(0) else null
        } catch (_: Exception) { null } finally { cursor?.close() }
    }

    private fun takeAudioFocus() {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { focus ->
                if (focus == AudioManager.AUDIOFOCUS_LOSS) main.post { finishPlayback() }
            }.build()
        focusRequest = request
        audioManager?.requestAudioFocus(request)
    }

    private fun playNext() = playIndex(currentIndex + 1)

    private fun playIndex(index: Int) {
        releasePlayer()
        if (index >= tracks.size) { finishPlayback(); return }
        currentIndex = index
        val track = tracks[index]
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                setWakeMode(this@AudioAlarmService, PowerManager.PARTIAL_WAKE_LOCK)
                setDataSource(this@AudioAlarmService, track.uri)
                setOnPreparedListener {
                    it.start()
                    showNotification("${index + 1}/${tracks.size}  •  ${track.title}")
                }
                setOnCompletionListener { playNext() }
                setOnErrorListener { _, _, _ ->
                    main.post { playNext() }
                    true
                }
                prepareAsync()
            }
        } catch (_: Exception) {
            main.post { playNext() }
        }
    }

    private fun showNotification(message: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(message))
    }

    private fun buildNotification(message: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, AudioAlarmService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val next = PendingIntent.getService(
            this, 2, Intent(this, AudioAlarmService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(playlistTitle)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openApp)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Next", next).build())
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Recitation playback", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows controls while scheduled audio is playing"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun releasePlayer() {
        mediaPlayer?.run { try { reset(); release() } catch (_: Exception) { } }
        mediaPlayer = null
    }

    private fun finishPlayback() {
        token++
        releasePlayer()
        focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        focusRequest = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        token++
        releasePlayer()
        focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        worker.shutdownNow()
        super.onDestroy()
    }
}
