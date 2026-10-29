package com.ttaak.app.lock

import android.app.ActivityOptions
import android.app.KeyguardManager
import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 할 일 화면에 보이는 다른 앱 알림 하나. */
data class Noti(
    val key: String,
    val pkg: String,
    val title: String,
    val text: String,
    val time: Long,
    val intent: PendingIntent?,
    val autoCancel: Boolean,
)

/**
 * 팀즈 같은 다른 앱 알림을 할 일 화면에 보여 주려고 읽는 서비스. 사용자가 '알림 접근'을 허용해야 붙습니다.
 * 시스템 잠금화면에 원래 안 뜨는 알림(계속 떠 있는 알림, 조용한 알림, 잠금화면에서 숨긴 앱·종류)은 똑같이 뺍니다.
 * 읽은 알림은 기기 밖으로 나가지 않습니다(인터넷 권한 없음).
 */
class NotiListener : NotificationListenerService() {
    override fun onListenerConnected() {
        instance = this
        refresh()
    }

    override fun onListenerDisconnected() {
        instance = null
        _notis.value = emptyList()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) = refresh()

    private fun refresh() {
        val ranking = Ranking()
        // PIN 이 걸린 폰이면 시스템처럼 내용은 가리고 앱만 알립니다.
        // ponytail: '민감한 내용도 표시' 시스템 설정까지는 안 따짐, 필요하면 Settings.Secure 를 읽을 것
        val secure = getSystemService(KeyguardManager::class.java).isDeviceSecure
        _notis.value = runCatching { activeNotifications }.getOrNull().orEmpty()
            .filter { it.packageName != packageName && it.isClearable && it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
            .mapNotNull { sbn ->
                if (!currentRanking.getRanking(sbn.key, ranking) || ranking.isAmbient) return@mapNotNull null
                // 사용자가 알림 종류별로 바꾼 잠금화면 표시는 안드로이드 12+ 에서만 읽힘. 그 전엔 앱이 정한 값만.
                val override = if (Build.VERSION.SDK_INT >= 31) {
                    ranking.lockscreenVisibilityOverride.takeIf { it != NotificationListenerService.Ranking.VISIBILITY_NO_OVERRIDE }
                } else {
                    null
                }
                val visibility = override ?: sbn.notification.visibility
                if (visibility == Notification.VISIBILITY_SECRET) return@mapNotNull null
                val extras = sbn.notification.extras
                val hide = secure && visibility == Notification.VISIBILITY_PRIVATE
                Noti(
                    key = sbn.key,
                    pkg = sbn.packageName,
                    title = if (hide) appName(sbn.packageName) else extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
                    text = if (hide) "새 알림" else extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
                    time = sbn.postTime,
                    intent = sbn.notification.contentIntent,
                    autoCancel = sbn.notification.flags and Notification.FLAG_AUTO_CANCEL != 0,
                ).takeIf { it.title.isNotBlank() || it.text.isNotBlank() }
            }
            .sortedByDescending { it.time }
    }

    private fun appName(pkg: String) = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    companion object {
        private var instance: NotiListener? = null
        private val _notis = MutableStateFlow(emptyList<Noti>())

        /** 지금 떠 있는 다른 앱 알림, 최근 것부터. 알림 접근이 꺼져 있으면 비어 있음. */
        val notis: StateFlow<List<Noti>> = _notis.asStateFlow()

        /** 이 설치본에 알림 읽기 서비스가 있는지 (알림판만). */
        fun available(context: Context): Boolean =
            runCatching { context.packageManager.getServiceInfo(ComponentName(context, NotiListener::class.java), 0) }.isSuccess

        /**
         * 지금 시스템에 붙어 있는지. 기본판을 깔았다가 알림판으로 다시 깔면 허용은 남아도 시스템이 다시 붙이지 않아서
         * (requestRebind 로도 안 됨, 에뮬레이터 확인) 사용자가 알림 접근을 껐다 켜야 합니다.
         */
        val connected: Boolean get() = instance != null

        fun allowed(context: Context): Boolean =
            context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

        /** 다른 앱이 수신·통화 중 '전화' 알림을 띄우고 있는지 (인터넷 전화 포함). 알림 접근이 꺼져 있으면 false. */
        fun callShowing(): Boolean = instance?.let { runCatching { it.activeNotifications }.getOrNull() }.orEmpty().any {
            val n = it.notification
            // 지난 통화 알림이 남아 있어도 할 일 화면이 영영 안 뜨지 않게, 울리는 중(전체 화면)이거나 통화 중(계속 떠 있음)만
            n.category == Notification.CATEGORY_CALL && (n.flags and Notification.FLAG_ONGOING_EVENT != 0 || n.fullScreenIntent != null)
        }

        fun icon(context: Context, pkg: String): ImageBitmap? = runCatching {
            context.packageManager.getApplicationIcon(pkg).toBitmap(72, 72).asImageBitmap()
        }.getOrNull()

        /** 알림을 눌렀을 때처럼 그 앱 화면을 엽니다. 잠금은 먼저 풀어 둔 상태여야 합니다. */
        fun open(context: Context, n: Noti) {
            // 안드로이드 14+ 는 다른 앱의 PendingIntent 로 화면을 띄우려면 보내는 쪽이 허락해야 합니다.
            val options = if (Build.VERSION.SDK_INT >= 34) {
                ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    .toBundle()
            } else {
                null
            }
            val sent = n.intent != null && runCatching { n.intent.send(context, 0, null, null, null, null, options) }.isSuccess
            if (!sent) {
                context.packageManager.getLaunchIntentForPackage(n.pkg)
                    ?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            if (n.autoCancel) instance?.cancelNotification(n.key)
        }
    }
}
