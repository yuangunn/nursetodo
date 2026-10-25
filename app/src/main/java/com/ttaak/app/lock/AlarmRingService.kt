package com.ttaak.app.lock

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.ttaak.app.R
import com.ttaak.app.data.Settings
import com.ttaak.app.data.TaskStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 알람이 울리는 동안 소리·진동을 맡는 서비스. 알림 채널 소리로는 알람음·크기를 바꿀 수 없어서 직접 재생합니다.
 * 잠금 위 알람 화면([AlarmActivity])은 알림의 전체 화면 인텐트로 뜨고, '끄기'·10분 경과·[Alarms.stop] 으로 멈춥니다.
 */
class AlarmRingService : Service() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        TaskStore.init(this)
        val id = intent?.getLongExtra(Alarms.EXTRA_ID, -1) ?: -1
        val title = intent?.getStringExtra(Alarms.EXTRA_LABEL).orEmpty()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification(this, id, title),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
        )
        AlarmSound.start(this, TaskStore.settings.value)
        _ringing.value = title
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ stopSelf() }, MAX_RING_MS)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        AlarmSound.stop(this)
        _ringing.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    companion object {
        const val NOTIFICATION_ID = 900

        /** 끄지 않으면 이만큼 울리고 저절로 멈춥니다. */
        private const val MAX_RING_MS = 10 * 60_000L

        private val _ringing = MutableStateFlow<String?>(null)

        /** 지금 울리는 알람의 제목. 안 울리면 null. 알람 화면은 이게 null 이 되면 닫힙니다. */
        val ringing: StateFlow<String?> = _ringing.asStateFlow()

        private fun notification(context: Context, id: Long, title: String): Notification {
            val screen = PendingIntent.getActivity(
                context, NOTIFICATION_ID,
                Intent(context, AlarmActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(Alarms.EXTRA_ID, id)
                    .putExtra(Alarms.EXTRA_LABEL, title),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return NotificationCompat.Builder(context, Alarms.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_alarm)
                .setContentTitle(title)
                .setContentText("알람")
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(screen, true)
                .setContentIntent(screen)
                .addAction(0, "끄기", Alarms.stopIntent(context))
                .setOngoing(true)
                // 딱 카드와 한 묶음으로 섞이지 않게 따로 묶습니다.
                .setGroup(Alarms.CHANNEL_ID)
                .build()
        }
    }
}

/**
 * 알람음 재생 + 진동. 설정의 '알람' 칸을 따릅니다.
 * 소리를 낼 땐 폰의 알람 음량을 설정한 크기로 맞췄다가, 멈추면 원래대로 되돌립니다.
 */
object AlarmSound {
    private var player: MediaPlayer? = null
    private var savedVolume: Int? = null

    private val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun defaultUri(): Uri? =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

    /** 지금 설정과 폰 상태로 소리를 낼지. 폰이 무음·진동인데 '그래도 소리로'를 껐으면 진동만. */
    fun plan(s: Settings, phoneSilent: Boolean): Pair<Boolean, Boolean> {
        val sound = s.alarmMode != 2 && (s.alarmAlwaysSound || !phoneSilent)
        val vibrate = s.alarmMode != 1 || !sound // 소리를 못 내면 진동이라도
        return sound to vibrate
    }

    fun start(context: Context, s: Settings) {
        stop(context)
        val audio = context.getSystemService(AudioManager::class.java)
        val (sound, vibrate) = plan(s, audio.ringerMode != AudioManager.RINGER_MODE_NORMAL)
        if (sound) {
            savedVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, (max * s.alarmVolume + 9) / 10, 0) }
            // 고른 알람음을 못 읽으면(지워진 파일 등) 기본 알람음으로
            player = listOfNotNull(s.alarmSound?.let(Uri::parse), defaultUri()).firstNotNullOfOrNull { uri ->
                runCatching {
                    MediaPlayer().apply {
                        setAudioAttributes(attributes)
                        setDataSource(context, uri)
                        isLooping = true
                        prepare()
                        start()
                    }
                }.getOrNull()
            }
        }
        if (vibrate) {
            @Suppress("DEPRECATION") // 알람 용도로 진동해야 방해 금지 중에도 울립니다.
            vibrator(context).vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0), attributes)
        }
    }

    fun stop(context: Context) {
        player?.release()
        player = null
        vibrator(context).cancel()
        savedVolume?.let { v ->
            runCatching { context.getSystemService(AudioManager::class.java).setStreamVolume(AudioManager.STREAM_ALARM, v, 0) }
        }
        savedVolume = null
    }

    private fun vibrator(context: Context): Vibrator =
        if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java).defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
}
