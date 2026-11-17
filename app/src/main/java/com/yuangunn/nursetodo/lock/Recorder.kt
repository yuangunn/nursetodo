package com.yuangunn.nursetodo.lock

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.yuangunn.nursetodo.R
import com.yuangunn.nursetodo.ui.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 음성 녹음(할 일 화면 '녹음' 바로가기). 한 번 누르면 [RecordService] 가 녹음하고 다시 누르면 멈춥니다.
 * 파일은 앱 안 `files/recordings` 에만 두고 저절로 지우지 않습니다. 듣기·보내기·지우기는 앱 설정 › 녹음.
 * 형식은 AAC ADTS(.aac): MP4(m4a)는 멈출 때 목차를 써서 녹음 중 앱이 죽으면 파일 전체를 못 읽지만, ADTS 는 그때까지 녹음이 남습니다.
 */
object Recorder {
    const val CHANNEL_ID = "record"
    const val MIME = "audio/aac"
    private const val PREFIX = "녹음_"
    private const val EXT = ".aac"
    private val nameFormat get() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    internal val _since = MutableStateFlow<Long?>(null)

    /** 녹음을 시작한 시각(ms). 녹음 중이 아니면 null. */
    val since: StateFlow<Long?> = _since

    /** 지금 녹음 중인 파일(목록에서 뺌). */
    @Volatile internal var current: File? = null

    fun allowed(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun dir(context: Context): File = File(context.filesDir, "recordings").apply { mkdirs() }

    fun fileFor(context: Context, start: Long): File = File(dir(context), PREFIX + nameFormat.format(Date(start)) + EXT)

    /** 파일 이름의 녹음 시작 시각. 이름이 다르면 수정 시각. */
    fun startedAt(file: File): Long =
        runCatching { nameFormat.parse(file.name.removePrefix(PREFIX).removeSuffix(EXT))?.time }.getOrNull() ?: file.lastModified()

    /** 녹음 목록, 새것 먼저(지금 녹음 중인 것은 뺌). */
    fun list(context: Context): List<File> =
        dir(context).listFiles { f -> f.isFile && f.name.endsWith(EXT) && f != current }.orEmpty().sortedByDescending { startedAt(it) }

    /** 걸린 시간 'm:ss', 한 시간 넘으면 'h:mm:ss'. */
    fun elapsed(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }

    /** 녹음을 시작합니다. 앱 화면이 앞에 있을 때만(안드로이드 11+ 마이크는 쓰는 중인 앱만). */
    fun start(context: Context) {
        ContextCompat.startForegroundService(context, Intent(context, RecordService::class.java))
    }

    fun stop(context: Context) {
        context.startService(Intent(context, RecordService::class.java).setAction(RecordService.ACTION_STOP))
    }

    fun shareIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        return Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType(MIME).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            "녹음 보내기",
        )
    }

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "녹음", NotificationManager.IMPORTANCE_LOW).apply {
            description = "녹음하는 동안 상단바에 떠 있어요"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

/** 녹음하는 동안의 포그라운드 서비스(마이크). 화면이 꺼져도 이어서 녹음하고, 알림의 '멈춤'이나 버튼을 다시 누르면 저장합니다. */
class RecordService : Service() {
    private var recorder: MediaRecorder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            finish(saved = true)
            return START_NOT_STICKY
        }
        if (recorder != null) return START_NOT_STICKY
        val start = System.currentTimeMillis()
        val file = Recorder.fileFor(this, start)
        try {
            // 마이크 권한이 없거나 앱이 앞에 없으면 여기서 SecurityException
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification(start),
                if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0,
            )
            @Suppress("DEPRECATION")
            recorder = (if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(64000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            file.delete()
            Toast.makeText(this, "녹음을 시작할 수 없어요. 통화 중이거나 다른 앱이 마이크를 쓰는 중일 수 있어요.", Toast.LENGTH_LONG).show()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        Recorder.current = file
        Recorder._since.value = start
        return START_NOT_STICKY
    }

    /** 멈추고 저장. 1초도 안 돼 멈추면 MediaRecorder 가 실패하고 빈 파일이 남아 지웁니다. */
    private fun finish(saved: Boolean) {
        val r = recorder
        val file = Recorder.current
        recorder = null
        if (r != null) {
            val ok = runCatching { r.stop() }.isSuccess
            r.release()
            if (!ok) file?.delete()
            if (saved) Toast.makeText(this, if (ok) "녹음을 저장했어요. 앱 설정 › 녹음에서 들어요." else "너무 짧아 저장하지 않았어요.", Toast.LENGTH_SHORT).show()
        }
        Recorder.current = null
        Recorder._since.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (recorder != null) finish(saved = false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun notification(start: Long): Notification {
        Recorder.createChannel(this)
        val stop = PendingIntent.getService(
            this, 0, Intent(this, RecordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val open = PendingIntent.getActivity(
            this, 0, MainActivity.recordingsIntent(this),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, Recorder.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("녹음 중")
            .setContentText("멈추면 앱 설정 › 녹음에 저장돼요")
            .setWhen(start)
            .setUsesChronometer(true)
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .addAction(R.drawable.ic_stop, "멈춤", stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.yuangunn.nursetodo.RECORD_STOP"
        private const val NOTIFICATION_ID = 950
    }
}
