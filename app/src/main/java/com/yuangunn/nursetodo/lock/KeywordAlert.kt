package com.yuangunn.nursetodo.lock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.yuangunn.nursetodo.R
import com.yuangunn.nursetodo.data.TaskStore
import com.yuangunn.nursetodo.data.keywordHit
import com.yuangunn.nursetodo.data.keywordList
import com.yuangunn.nursetodo.data.onDuty
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 키워드 알림 (알림판). 고른 앱(문자·팀즈 등)의 새 알림 제목·내용에 키워드('배정되었습니다')가 있으면 진동과 팝업
 * (확인 · 열기 · 할 일로 추가)으로 알리고, 화면이 꺼져 있으면 잠깐 켭니다. 근무 루틴 알림처럼 '근무 교대 받았습니다'를 누른
 * 폰에서만 그 교대 동안 울립니다. 원래 알림이 사라지면(읽음·지움) 팝업도 지웁니다.
 * 시스템 팝업은 몇 초면 접히므로, '확인'할 때까지 할 일 화면 맨 위에도 카드로 남깁니다([active]).
 * 알림 내용은 [NotiListener] 가 읽은 것을 이 폰 안에서만 비교합니다.
 */
object KeywordAlert {
    const val CHANNEL_ID = "keyword"
    private const val ACTION_OK = "com.yuangunn.nursetodo.KEYWORD_OK"
    private const val ACTION_ADD = "com.yuangunn.nursetodo.KEYWORD_ADD"
    private const val EXTRA_TEXT = "text"
    private const val NOTIFICATION_ID = 5_000

    // 대화방 알림은 새 메시지 없이도 다시 올라오므로 (알림, 메시지 시각, 내용)이 같으면 한 번만 울립니다.
    // ponytail: 최근 100건만 기억, 앱이 다시 시작되면 비워짐
    private val seen = LinkedHashSet<String>()

    /** 할 일 화면 카드 하나: 아직 '확인'하지 않은 키워드 팝업. [key] 는 원래 알림, [label] 은 '앱 · 키워드'. */
    data class Hit(val key: String, val pkg: String, val label: String, val title: String, val text: String, val open: PendingIntent?)

    private val _version = MutableStateFlow(0)

    /** 팝업이 생기거나 사라질 때마다 바뀝니다. 할 일 화면이 [active] 를 다시 읽습니다. */
    val version: StateFlow<Int> = _version.asStateFlow()

    internal fun changed() {
        _version.value++
    }

    /** 남은 키워드 팝업, 최근 것부터. 시스템 알림이 원본이라 앱이 다시 떠도 그대로입니다. */
    fun active(context: Context): List<Hit> =
        runCatching { context.getSystemService(NotificationManager::class.java).activeNotifications }.getOrNull().orEmpty()
            .filter { it.id == NOTIFICATION_ID && it.tag != null }
            .sortedByDescending { it.postTime }
            .map { sbn ->
                val e = sbn.notification.extras
                fun get(name: String) = e.getCharSequence(name)?.toString().orEmpty()
                // 원래 알림 key 는 '사용자|패키지|번호|태그|uid'
                Hit(sbn.tag, sbn.tag.split('|').getOrElse(1) { "" }, get(Notification.EXTRA_SUB_TEXT), get(Notification.EXTRA_TITLE), get(Notification.EXTRA_TEXT), sbn.notification.contentIntent)
            }

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "키워드 알림", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "고른 앱 알림에 키워드가 있을 때 (기본은 소리 없이 진동 되풀이)"
            setSound(null, null)
            enableVibration(true)
            // 5번(약 12초) 되풀이. '확인'을 누르거나 알림이 지워지면 그 자리에서 멈춥니다.
            vibrationPattern = longArrayOf(0) + LongArray(20) { i -> listOf(500L, 200L, 500L, 1300L)[i % 4] }
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** 새로 온 내용과 그 시각: 대화형 알림이면 마지막 메시지만(앞의 지난 메시지로 다시 울리지 않게), 아니면 펼친 글이나 한 줄 글. */
    internal fun latest(n: Notification): Pair<Long, String> {
        NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)?.messages?.lastOrNull()?.let {
            return it.timestamp to it.text?.toString().orEmpty()
        }
        val e = n.extras
        return n.`when` to (e.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: e.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
    }

    /** [NotiListener] 가 새 알림을 받을 때마다. */
    fun check(context: Context, sbn: StatusBarNotification) {
        val s = TaskStore.settings.value
        val n = sbn.notification
        if (sbn.packageName == context.packageName || sbn.packageName !in s.keywordApps) return
        if (n.flags and (Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_ONGOING_EVENT) != 0) return
        if (!s.onDuty(System.currentTimeMillis())) return
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val (time, text) = latest(n)
        val hit = keywordHit(s.keywordList, "$title\n$text") ?: return
        if (!seen.add("${sbn.key}|$time|$text")) return
        if (seen.size > 100) seen.remove(seen.first())
        notify(context, sbn, title, text, hit)
        Routine.wake(context)
    }

    private fun notify(context: Context, sbn: StatusBarNotification, title: String, text: String, hit: String) {
        val pm = context.packageManager
        val app = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString() }.getOrDefault(sbn.packageName)
        // '열기'는 원래 알림을 누른 것과 같습니다. 잠금화면에서 누르면 시스템이 잠금을 먼저 풀게 합니다.
        val open = sbn.notification.contentIntent ?: pm.getLaunchIntentForPackage(sbn.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_check)
            .setContentTitle(title.ifBlank { app })
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSubText("$app · $hit")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, "확인", action(context, ACTION_OK, sbn.key, text))
            .apply { open?.let { addAction(0, "열기", it) } }
            .addAction(0, "할 일로 추가", action(context, ACTION_ADD, sbn.key, text.ifBlank { title }))
            .build()
        context.getSystemService(NotificationManager::class.java).notify(sbn.key, NOTIFICATION_ID, n)
        changed()
    }

    /** 원래 알림 [key] 에 대한 팝업을 지웁니다(없으면 그대로). */
    fun dismiss(context: Context, key: String) {
        context.getSystemService(NotificationManager::class.java).cancel(key, NOTIFICATION_ID)
        changed()
    }

    /** 알림마다 data 를 달리 해서 팝업이 여러 개여도 버튼이 서로 덮어쓰지 않게 합니다. */
    private fun action(context: Context, action: String, key: String, text: String) = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, Receiver::class.java).setAction(action).setData(Uri.fromParts("keyword", key, null)).putExtra(EXTRA_TEXT, text),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    class Receiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val key = intent.data?.schemeSpecificPart ?: return
            if (intent.action == ACTION_ADD) {
                TaskStore.init(context)
                TaskStore.add(intent.getStringExtra(EXTRA_TEXT).orEmpty())
            }
            dismiss(context, key)
        }
    }
}
