package com.ttaak.app.lock

import android.app.KeyguardManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ttaak.app.data.TaskStore

/**
 * 화면이 켜질 때마다 잠금화면 위에 할 일 화면(오늘 할 일)을 먼저 띄웁니다. 툭의 잠금화면 카드에 해당.
 *
 * '화면 켜짐' 신호는 실행 중인 앱만 받을 수 있어서 상시(포그라운드) 서비스로 둡니다. 서비스 알림은
 * 잠금화면 카드와 같은 번호를 써서 알림이 하나만 보입니다. 백그라운드에서 화면을 띄우려면
 * '다른 앱 위에 표시' 권한이 있어야 합니다.
 */
class LockPageService : Service() {
    private val screenOn = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // 교대 시각 알람은 화면이 꺼져 있으면 늦게 오므로, 켜질 때 먼저 프리셋을 맞춥니다.
            TaskStore.refreshPreset()
            val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
            // 알람이 울리거나 전화가 와서 화면이 켜진 경우엔 그 화면을 가리지 않습니다.
            if (!locked || !Settings.canDrawOverlays(context) || Alarms.ringing() || inCall(context)) return
            startActivity(
                Intent(context, QuickAddActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(QuickAddActivity.EXTRA_LOOK_FIRST, true),
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        TaskStore.init(this)
        ContextCompat.registerReceiver(this, screenOn, IntentFilter(Intent.ACTION_SCREEN_ON), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this, LockCard.NOTIFICATION_ID, LockCard.build(this),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(screenOn)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    /**
     * 전화가 울리거나 통화 중인지. 일반 전화는 기본 전화 앱이 무엇이든(삼성·에이닷·익시오) 시스템이 오디오 모드를 바꾸고,
     * 인터넷 전화(내선 앱 등)는 통화 모드나 '전화' 종류 알림으로 압니다. 알림은 알림 접근이 켜져 있을 때만 보입니다.
     * ponytail: 둘 다 안 쓰는 인터넷 전화 앱이면 못 알아챔. 그땐 그 앱 패키지를 따로 빼야 함
     */
    private fun inCall(context: Context) =
        context.getSystemService(AudioManager::class.java).mode != AudioManager.MODE_NORMAL || NotiListener.callShowing()

    companion object {
        /** 설정에 맞춰 켜거나 끕니다. 백그라운드에서는 시작이 막힐 수 있어 조용히 넘어갑니다(앱을 열면 다시 시도). */
        fun sync(context: Context) {
            val intent = Intent(context, LockPageService::class.java)
            val on = TaskStore.settings.value.let { it.lockPage && it.lockEnabled } && Settings.canDrawOverlays(context)
            try {
                if (on) ContextCompat.startForegroundService(context, intent) else context.stopService(intent)
            } catch (_: Exception) {
            }
        }
    }
}
