package com.yuangunn.nursetodo.lock

import android.app.ActivityOptions
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
    /** 보낸 앱 이름. */
    val app: String = "",
    /** 휴대폰 설정에서 이 알림의 잠금화면 내용을 숨김. 그러면 앱 이름·시각만 보입니다. */
    val hidden: Boolean = false,
)

/**
 * 팀즈 같은 다른 앱 알림을 할 일 화면에 보여 주고 [KeywordAlert] 로 키워드를 찾으려고 읽는 서비스. 사용자가 '알림 접근'을 허용해야 붙습니다.
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

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let { KeywordAlert.check(this, it) }
        refresh()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // 원래 알림을 읽거나 지우면 키워드 팝업도 지웁니다. 키워드 팝업을 알림창에서 지우면 할 일 화면 카드도.
        sbn?.let { if (it.packageName == packageName) KeywordAlert.changed() else KeywordAlert.dismiss(this, it.key) }
        refresh()
    }

    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) = refresh()

    private fun refresh() {
        val ranking = Ranking()
        // 갤럭시 기본처럼 내용까지 보여 주고, 사용자가 앱·알림 종류별로 잠금화면 내용을 숨긴 것만 가립니다.
        // ponytail: 전체 '민감한 내용 숨기기' 설정은 안드로이드 12+ 에서 앱이 읽을 수 없어 따르지 않음
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
                val hide = override == Notification.VISIBILITY_PRIVATE
                val app = appName(sbn.packageName)
                Noti(
                    key = sbn.key,
                    pkg = sbn.packageName,
                    title = if (hide) "" else extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
                    text = if (hide) "" else extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
                    time = sbn.postTime,
                    intent = sbn.notification.contentIntent,
                    autoCancel = sbn.notification.flags and Notification.FLAG_AUTO_CANCEL != 0,
                    app = app,
                    hidden = hide,
                ).takeIf { hide || it.title.isNotBlank() || it.text.isNotBlank() }
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
            Shortcuts.iconBitmap(context.packageManager.getApplicationIcon(pkg), 72).asImageBitmap()
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

        /**
         * 알림 창에서 밀어 지우듯 그 알림들을 지웁니다(되돌릴 수 없음). 목록은 onNotificationRemoved 로 다시 읽힙니다.
         * 묶음의 마지막 알림이면 묶음 요약도 함께 지웁니다 — 요약만 남으면 알림 창에 빈 묶음('앱 이름 · 시각')이 남습니다.
         */
        fun dismiss(keys: List<String>) {
            val l = instance ?: return
            if (keys.isEmpty()) return
            runCatching {
                val gone = keys.toSet()
                val active = l.activeNotifications.orEmpty()
                val summaries = active.filter { s ->
                    s.isClearable && s.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0 &&
                        active.any { it.groupKey == s.groupKey && it.key in gone } &&
                        active.none { it.groupKey == s.groupKey && it.key != s.key && it.key !in gone }
                }.map { it.key }
                l.cancelNotifications((gone + summaries).toTypedArray())
            }
        }
    }
}
