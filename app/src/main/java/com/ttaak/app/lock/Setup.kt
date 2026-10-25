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

    /** 잠금화면 알림 설정. 제조사마다 화면이 달라서 되는 것부터 차례로 시도합니다. */
    fun openLockScreenSettings(context: Context) = open(
        context,
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
