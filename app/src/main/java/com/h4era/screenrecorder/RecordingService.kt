package com.h4era.screenrecorder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodecList
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.text.format.Formatter
import android.util.DisplayMetrics
import android.view.Display

class RecordingService : Service() {

    private var projection: MediaProjection? = null
    private var recorder: MediaRecorder? = null
    private var vdisplay: VirtualDisplay? = null
    private var pfd: ParcelFileDescriptor? = null
    private var uri: Uri? = null
    private var active = false
    private var paused = false
    private var segmentStart = 0L
    private var accumulated = 0L
    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            if (active) {
                RecorderState.elapsedSec = currentMs() / 1000
                handler.postDelayed(this, 500)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun currentMs(): Long {
        return accumulated + (if (paused) 0L else SystemClock.elapsedRealtime() - segmentStart)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "STOP") {
            if (active) finish() else stopSelf()
            return START_NOT_STICKY
        }
        if (action == "TOGGLE") {
            if (active) {
                if (paused) resume() else pause()
            } else {
                stopSelf()
            }
            return START_NOT_STICKY
        }

        val code = intent?.getIntExtra("code", 0) ?: 0
        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>("data")
        val audio = Prefs.mic(this) &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        if (audio) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        startForeground(1, buildNotification(), type)

        if (active) return START_NOT_STICKY // prevent duplicate sessions

        if (data == null) {
            fail("Missing screen-capture permission data")
            return START_NOT_STICKY
        }
        try {
            begin(code, data, audio)
        } catch (e: Exception) {
            fail("Could not start recording: " + e.message)
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("rec", "Recording", NotificationManager.IMPORTANCE_LOW)
        )
        fun act(a: String, req: Int): PendingIntent {
            return PendingIntent.getService(
                this, req,
                Intent(this, RecordingService::class.java).setAction(a),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, "rec")
            .setContentTitle(if (paused) "H4ERA - paused" else "H4ERA - recording")
            .setContentText("Use the buttons below to control recording")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_media_pause,
                if (paused) "Resume" else "Pause",
                act("TOGGLE", 1)
            )
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", act("STOP", 2))
            .build()
    }

    private fun supported(w: Int, h: Int, fps: Int): Boolean {
        return try {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
                info.isEncoder &&
                        info.supportedTypes.any { it.equals("video/avc", true) } &&
                        runCatching {
                            info.getCapabilitiesForType("video/avc").videoCapabilities
                                .areSizeAndRateSupported(w, h, fps.toDouble())
                        }.getOrDefault(false)
            }
        } catch (e: Exception) {
            true
        }
    }

    private fun begin(code: Int, data: Intent, audio: Boolean) {
        val free = Environment.getExternalStorageDirectory().usableSpace
        if (free < 200L * 1024 * 1024) {
            throw Exception("Not enough storage (need at least 200 MB free)")
        }

        val dm = DisplayMetrics()
        val dmgr = getSystemService(DISPLAY_SERVICE) as DisplayManager
        dmgr.getDisplay(Display.DEFAULT_DISPLAY).getRealMetrics(dm)

        val shortSide = minOf(dm.widthPixels, dm.heightPixels)
        val target = Prefs.height(this)
        var scale = if (target <= 0 || target >= shortSide) 1.0 else target.toDouble() / shortSide
        var fps = Prefs.fps(this)

        fun even(v: Double): Int = (v.toInt() / 2) * 2
        var w = even(dm.widthPixels * scale)
        var h = even(dm.heightPixels * scale)

        // Capability check with graceful fallback
        if (!supported(w, h, fps) && fps > 30 && supported(w, h, 30)) fps = 30
        var guard = 0
        while (!supported(w, h, fps) && guard < 12) {
            scale *= 0.85
            w = even(dm.widthPixels * scale)
            h = even(dm.heightPixels * scale)
            guard++
        }

        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val p = mpm.getMediaProjection(code, data)
        projection = p
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                if (active) finish()
            }
        }, handler)

        val u = MediaStoreManager.createPending(this) ?: throw Exception("Cannot create file in Movies")
        uri = u
        pfd = contentResolver.openFileDescriptor(u, "rw")

        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()
        recorder = r
        if (audio) r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setOutputFile(pfd!!.fileDescriptor)
        r.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        if (audio) {
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(128000)
            r.setAudioSamplingRate(44100)
        }
        r.setVideoSize(w, h)
        r.setVideoFrameRate(fps)
        r.setVideoEncodingBitRate(Prefs.mbps(this) * 1_000_000)
        r.setOnErrorListener { _, _, _ ->
            handler.post { if (active) finish() }
        }
        r.prepare()

        vdisplay = p.createVirtualDisplay(
            "H4ERA", w, h, dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            r.surface, null, null
        )
        r.start()

        active = true
        paused = false
        accumulated = 0L
        segmentStart = SystemClock.elapsedRealtime()
        RecorderState.recording = true
        RecorderState.paused = false
        RecorderState.elapsedSec = 0L
        RecorderState.message = null
        RecorderState.info = "${w}x$h • $fps FPS" + if (audio) " • Mic" else ""
        handler.post(ticker)
    }

    private fun pause() {
        try {
            recorder?.pause()
            accumulated = currentMs()
            paused = true
            RecorderState.paused = true
            getSystemService(NotificationManager::class.java).notify(1, buildNotification())
        } catch (e: Exception) {
        }
    }

    private fun resume() {
        try {
            recorder?.resume()
            segmentStart = SystemClock.elapsedRealtime()
            paused = false
            RecorderState.paused = false
            getSystemService(NotificationManager::class.java).notify(1, buildNotification())
        } catch (e: Exception) {
        }
    }

    private fun release() {
        try { recorder?.release() } catch (e: Exception) { }
        try { vdisplay?.release() } catch (e: Exception) { }
        try { projection?.stop() } catch (e: Exception) { }
        try { pfd?.close() } catch (e: Exception) { }
        recorder = null
        vdisplay = null
        projection = null
        pfd = null
    }

    private fun finish() {
        val wasActive = active
        active = false
        handler.removeCallbacks(ticker)
        val dur = currentMs()
        var ok = wasActive
        try {
            recorder?.stop()
        } catch (e: Exception) {
            ok = false // too short / no valid data
        }
        release() // closes the file descriptor before publishing
        val u = uri
        uri = null

        if (ok && u != null) {
            MediaStoreManager.publish(this, u)
            val (name, size) = MediaStoreManager.info(this, u)
            RecorderState.lastSaved = u
            RecorderState.libraryVersion++
            RecorderState.message = "Saved: $name\n" +
                    "Duration ${fmtTime(dur / 1000)} • ${Formatter.formatShortFileSize(this, size)}\n" +
                    "Folder: Movies/H4ERA Screen Recorder"
        } else {
            if (u != null) MediaStoreManager.discard(this, u)
            RecorderState.lastSaved = null
            RecorderState.message = "Recording was too short or failed. Nothing was saved."
        }
        RecorderState.recording = false
        RecorderState.paused = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun fail(msg: String) {
        active = false
        handler.removeCallbacks(ticker)
        release()
        uri?.let { MediaStoreManager.discard(this, it) }
        uri = null
        RecorderState.recording = false
        RecorderState.paused = false
        RecorderState.message = msg
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (active) finish()
        handler.removeCallbacks(ticker)
        super.onDestroy()
    }
}
