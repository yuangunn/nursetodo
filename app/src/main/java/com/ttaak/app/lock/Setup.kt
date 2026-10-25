package com.ttaak.app.lock

import android.app.Activity
import android.app.NotificationManager
import android.app.StatusBarManager
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import com.ttaak.app.R
import com.ttaak.app.data.TaskStore
import com.ttaak.app.widget.TaskWidgetProvider

/** 잠금화면 카드·위젯·타일이 제대로 보이는지 점검하고, 바로 고칠 수 있는 화면을 엽니다. */
object Setup {
    fun notificationsAllowed(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    /** 사용자가 '잠금화면 카드' 알림 종류만 따로 끈 경우. */
    fun channelBlocked(context: Context): Boolean {
        val channel = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(LockCard.CHANNEL_ID) ?: return false
        return channel.importance == NotificationManager.IMPORTANCE_NONE
    }

    fun cardShowing(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java)
            .activeNotifications.any { it.id == LockCard.NOTIFICATION_ID }

    /** Notification.FLAG_PROMOTED_ONGOING (API 36). compileSdk 35 라 값으로 씁니다. */
    private const val FLAG_PROMOTED_ONGOING = 0x40000

    /**
     * 안드로이드 16+: 딱 알림이 실제로 라이브 알림으로 올라가 있는지. 앱이 요청해도 사용자가
     * 앱 알림 설정의 '라이브 알림'을 끄면 시스템이 이 표시를 지우고 일반 알림으로 보여 줍니다.
     */
    fun liveShowing(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java).activeNotifications.any {
            it.id == LockCard.NOTIFICATION_ID && it.notification.flags and FLAG_PROMOTED_ONGOING != 0
        }

    /** 앱 알림 설정의 '라이브 알림' 토글 (Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS, API 36). */
    fun openLiveSettings(context: Context) = open(
        context,
        Intent("android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS")
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
    )

    fun widgetAdded(context: Context): Boolean =
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, TaskWidgetProvider::class.java)).isNotEmpty()

    /** 홈 화면에 위젯을 바로 추가하는 시스템 창을 띄웁니다. 런처가 지원하지 않으면 false. */
    fun requestWidget(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        return manager.requestPinAppWidget(ComponentName(context, TaskWidgetProvider::class.java), null, null)
    }

    val canRequestTile: Boolean get() = Build.VERSION.SDK_INT >= 33

    /** 상단바(빠른 설정)에 '딱 추가' 타일을 넣는 시스템 창을 띄웁니다. 안드로이드 13+. */
    fun requestTile(activity: Activity) {
        if (Build.VERSION.SDK_INT < 33) return
        activity.getSystemService(StatusBarManager::class.java).requestAddTileService(
            ComponentName(activity, AddTileService::class.java),
            activity.getString(R.string.tile_label),
            Icon.createWithResource(activity, R.drawable.ic_stat_check),
            activity.mainExecutor,
        ) { result ->
            if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
            ) {
                TaskStore.updateSettings { it.copy(tileAdded = true) }
            }
        }
    }

    fun openAppNotificationSettings(context: Context) = open(
        context,
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
    )

    fun openChannelSettings(context: Context) = open(
        context,
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, LockCard.CHANNEL_ID),
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
    )

    /** 딱 화면 상시 알림('딱 화면 유지')을 꺼 두었는지. 끄면 상태바 아이콘이 사라지고 딱 화면은 그대로입니다. */
    fun pageIconHidden(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(LockCard.PAGE_CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE

    fun openPageChannelSettings(context: Context) = open(
        context,
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, LockCard.PAGE_CHANNEL_ID),
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
    )

    /** '다른 앱 위에 표시' 허용 화면. 화면을 켤 때 잠금 위에 딱 화면을 띄우는 데 필요합니다. 이미 허용됐으면 그냥 넘어감. */
    fun requestOverlay(context: Context) {
        if (Settings.canDrawOverlays(context)) return
        open(
            context,
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}")),
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
        )
    }

    /** 삼성 One UI: 설정 → 알림 → 잠금화면 → 알림 스타일 (0 카드, 1 아이콘, 2 동그란 점). */
    private const val SAMSUNG_LOCK_STYLE = "lockscreen_minimizing_notification"

    /**
     * 삼성 폰의 잠금화면 알림 스타일. 카드(0)가 아니면 딱 카드가 잠금화면에 안 보입니다. 삼성이 아니거나 못 읽으면 null.
     * 앱이 직접 바꾸는 건 안드로이드가 막습니다(시스템 설정 변경 권한이 있어도). 화면을 열어 주는 데까지만 합니다.
     */
    fun samsungLockStyle(context: Context): Int? {
        if (!Build.MANUFACTURER.equals("samsung", ignoreCase = true)) return null
        // 한 번도 안 바꾼 폰은 값이 아예 없고, 그때 잠금화면은 '아이콘'으로 보입니다 (One UI 9 실기기 확인).
        return try {
            Settings.System.getInt(context.contentResolver, SAMSUNG_LOCK_STYLE, 1)
        } catch (_: Exception) {
            null
        }
    }

    /** 잠금화면 알림 설정. 제조사마다 화면이 달라서 되는 것부터 차례로 시도합니다. */
    fun openLockScreenSettings(context: Context) = open(
        context,
        // 삼성 One UI 7+ 의 '알림 → 잠금화면' 화면 (카드/아이콘 선택이 바로 보임)
        Intent().setClassName("com.android.settings", "com.android.settings.Settings\$LockscreenNotificationActivity"),
        Intent("android.settings.LOCK_SCREEN_SETTINGS"),
        Intent("android.settings.NOTIFICATION_SETTINGS"),
        Intent(Settings.ACTION_SETTINGS),
    )

    private fun open(context: Context, vararg intents: Intent?) {
        for (intent in intents.filterNotNull()) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
    }
}
